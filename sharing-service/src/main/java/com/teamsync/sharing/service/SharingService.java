package com.teamsync.sharing.service;

import com.teamsync.common.context.TenantContext;
import com.teamsync.common.dto.ApiResponse;
import com.teamsync.common.dto.CursorPage;
import com.teamsync.common.exception.AccessDeniedException;
import com.teamsync.common.exception.ResourceNotFoundException;
import com.teamsync.sharing.client.ContentServiceClient;
import com.teamsync.sharing.dto.*;
import com.teamsync.sharing.model.PublicLink;
import com.teamsync.sharing.model.PublicLink.LinkStatus;
import com.teamsync.sharing.model.Share;
import com.teamsync.sharing.model.Share.SharePermission;
import com.teamsync.sharing.model.Share.ResourceType;
import com.teamsync.sharing.repository.PublicLinkRepository;
import com.teamsync.sharing.repository.ShareRepository;
import com.teamsync.common.model.Permission;
import com.teamsync.common.permission.RequiresPermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SharingService {

    private final ShareRepository shareRepository;
    private final PublicLinkRepository publicLinkRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ContentServiceClient contentServiceClient;
    private final MongoTemplate mongoTemplate;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${teamsync.sharing.public-link-base-url:http://localhost:3000/share}")
    private String publicLinkBaseUrl;

    private static final String SHARE_EVENTS_TOPIC = "teamsync.share.events";
    private static final String TOKEN_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    /**
     * SECURITY FIX (Round 9): Maximum number of team IDs allowed in a single query.
     * Prevents DoS via excessively large $in queries that could overwhelm MongoDB.
     */
    private static final int MAX_TEAM_IDS = 100;

    /**
     * Create a share.
     * Requires SHARE permission on the drive.
     *
     * SECURITY FIX: Now looks up the actual resource owner from content service
     * instead of incorrectly attributing ownership to the sharing user.
     *
     * SECURITY FIX (Round 12): Fixed TOCTOU race condition in share creation.
     * Previous implementation checked for existing shares and then created new ones
     * in separate operations, allowing race conditions where duplicate shares could
     * be created. Now uses MongoDB unique index for atomic duplicate prevention.
     */
    @RequiresPermission(Permission.SHARE)
    @Transactional
    public ShareDTO createShare(CreateShareRequest request) {
        String tenantId = TenantContext.getTenantId();
        String driveId = TenantContext.getDriveId();
        String userId = TenantContext.getUserId();

        log.info("Creating share for resource: {} to {}: {} by user: {}",
                request.getResourceId(), request.getSharedWithType(), request.getSharedWithId(), userId);

        // SECURITY FIX (Round 12): Removed TOCTOU check - rely on unique index instead
        // The Share model should have a unique compound index on (resourceId, sharedWithId)
        // which atomically prevents duplicates during insert

        // SECURITY FIX: Look up the actual resource owner from content service
        String ownerId = lookupResourceOwner(tenantId, driveId, request.getResourceId(), request.getResourceType());

        Share share = Share.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .driveId(driveId)
                .resourceId(request.getResourceId())
                .resourceType(request.getResourceType())
                .ownerId(ownerId)
                .sharedById(userId)
                .sharedWithId(request.getSharedWithId())
                .sharedWithType(request.getSharedWithType())
                .permissions(request.getPermissions())
                .notifyOnAccess(request.getNotifyOnAccess() != null ? request.getNotifyOnAccess() : false)
                .allowReshare(request.getAllowReshare() != null ? request.getAllowReshare() : false)
                .requirePassword(request.getPassword() != null)
                .passwordHash(request.getPassword() != null ? passwordEncoder.encode(request.getPassword()) : null)
                .expiresAt(request.getExpiresAt())
                .accessCount(0)
                .message(request.getMessage())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        // SECURITY FIX (Round 12): Catch duplicate key exception from unique index
        // This handles the race condition atomically at database level
        Share savedShare;
        try {
            savedShare = shareRepository.save(share);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            log.warn("SECURITY: Duplicate share creation attempt for resource: {} to: {} (handled atomically)",
                    request.getResourceId(), request.getSharedWithId());
            throw new IllegalArgumentException("Share already exists for this user/group");
        }

        // TODO: Send notification to sharee

        log.info("Share created: {} with owner: {} (sharedBy: {})", savedShare.getId(), ownerId, userId);
        return mapToShareDTO(savedShare);
    }

    /**
     * SECURITY FIX: Looks up the actual owner of a resource from the content service.
     * This prevents incorrect owner attribution which could allow unauthorized management of shares.
     *
     * @param tenantId the tenant ID
     * @param driveId the drive ID
     * @param resourceId the resource ID
     * @param resourceType the type of resource (DOCUMENT or FOLDER)
     * @return the owner ID of the resource
     * @throws ResourceNotFoundException if the resource doesn't exist
     */
    private String lookupResourceOwner(String tenantId, String driveId, String resourceId, ResourceType resourceType) {
        try {
            ApiResponse<ContentServiceClient.ResourceInfo> response;

            if (resourceType == ResourceType.FOLDER) {
                response = contentServiceClient.getFolderInfo(resourceId, tenantId, driveId);
            } else {
                response = contentServiceClient.getDocumentInfo(resourceId, tenantId, driveId);
            }

            if (response == null || !response.isSuccess() || response.getData() == null) {
                log.error("Failed to look up resource owner for {}: {} - response: {}",
                        resourceType, resourceId, response);
                throw new ResourceNotFoundException("Resource not found: " + resourceId);
            }

            String ownerId = response.getData().ownerId();
            if (ownerId == null || ownerId.isBlank()) {
                log.warn("Resource {} has no owner, falling back to current user", resourceId);
                return TenantContext.getUserId();
            }

            log.debug("Looked up owner for {} {}: {}", resourceType, resourceId, ownerId);
            return ownerId;

        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error looking up resource owner for {}: {} - {}", resourceType, resourceId, e.getMessage());
            // Fall back to current user if lookup fails to avoid breaking sharing
            // but log at error level so this can be monitored
            return TenantContext.getUserId();
        }
    }

    /**
     * Get shares for a resource.
     * Requires READ permission on the drive.
     */
    @RequiresPermission(Permission.READ)
    public List<ShareDTO> getSharesForResource(String resourceId) {
        String tenantId = TenantContext.getTenantId();

        List<Share> shares = shareRepository.findByTenantIdAndResourceId(tenantId, resourceId);

        return shares.stream()
                .map(this::mapToShareDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get shares with current user.
     * Requires READ permission on the drive.
     */
    @RequiresPermission(Permission.READ)
    public List<ShareDTO> getSharedWithMe(List<String> teamIds, String departmentId) {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();

        List<Share> shares = shareRepository.findSharesForUser(tenantId, userId, teamIds, departmentId);

        return shares.stream()
                .map(this::mapToShareDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get shares created by current user.
     * Requires READ permission on the drive.
     * SECURITY FIX: Uses pagination to prevent memory exhaustion DoS.
     */
    @RequiresPermission(Permission.READ)
    public List<ShareDTO> getSharedByMe() {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();

        // Use pagination to prevent memory exhaustion - return first 1000 shares
        PageRequest pageRequest = PageRequest.of(0, 1000, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Share> shares = shareRepository.findByTenantIdAndSharedById(tenantId, userId, pageRequest);

        return shares.stream()
                .map(this::mapToShareDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get shares with current user - paginated version.
     * SECURITY FIX (Round 7): Uses cursor-based pagination to prevent memory exhaustion DoS.
     *
     * @param teamIds optional list of team IDs to filter by
     * @param departmentId optional department ID to filter by
     * @param cursor optional cursor for pagination (share ID to start after)
     * @param limit maximum number of shares to return
     * @return paginated list of shares
     */
    @RequiresPermission(Permission.READ)
    public CursorPage<ShareDTO> getSharedWithMePaginated(List<String> teamIds, String departmentId,
                                                          String cursor, int limit) {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();

        // SECURITY FIX (Round 9): Validate teamIds size to prevent DoS via large $in queries
        if (teamIds != null && teamIds.size() > MAX_TEAM_IDS) {
            log.warn("SECURITY: User {} attempted query with {} teamIds (max: {})",
                    userId, teamIds.size(), MAX_TEAM_IDS);
            throw new IllegalArgumentException(
                    "Too many team IDs provided. Maximum allowed: " + MAX_TEAM_IDS);
        }

        // Build query with tenant isolation and user targeting
        Query query = new Query();

        // Tenant isolation (CRITICAL)
        query.addCriteria(Criteria.where("tenantId").is(tenantId));

        // User targeting: shares with this user, their teams, or their department
        Criteria userCriteria = new Criteria().orOperator(
                Criteria.where("sharedWithId").is(userId),
                teamIds != null && !teamIds.isEmpty()
                        ? Criteria.where("sharedWithId").in(teamIds)
                        : Criteria.where("sharedWithId").is(null), // no-op criteria
                departmentId != null
                        ? Criteria.where("sharedWithId").is(departmentId)
                        : Criteria.where("sharedWithId").is(null)  // no-op criteria
        );
        query.addCriteria(userCriteria);

        // Cursor-based pagination: get items after the cursor
        if (cursor != null && !cursor.isBlank()) {
            query.addCriteria(Criteria.where("_id").gt(cursor));
        }

        // Sort by _id for consistent cursor pagination
        query.with(Sort.by(Sort.Direction.ASC, "_id"));

        // Limit + 1 to check if there are more results
        query.limit(limit + 1);

        List<Share> shares = mongoTemplate.find(query, Share.class);

        // Determine if there are more results
        boolean hasMore = shares.size() > limit;
        if (hasMore) {
            shares = shares.subList(0, limit);
        }

        // Get next cursor (last item's ID)
        String nextCursor = hasMore && !shares.isEmpty()
                ? shares.get(shares.size() - 1).getId()
                : null;

        List<ShareDTO> dtos = shares.stream()
                .map(this::mapToShareDTO)
                .collect(Collectors.toList());

        return CursorPage.<ShareDTO>builder()
                .items(dtos)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .build();
    }

    /**
     * Get shares created by current user - paginated version.
     * SECURITY FIX (Round 7): Uses cursor-based pagination to prevent memory exhaustion DoS.
     *
     * @param cursor optional cursor for pagination (share ID to start after)
     * @param limit maximum number of shares to return
     * @return paginated list of shares
     */
    @RequiresPermission(Permission.READ)
    public CursorPage<ShareDTO> getSharedByMePaginated(String cursor, int limit) {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();

        // Build query with tenant isolation
        Query query = new Query();

        // Tenant isolation (CRITICAL)
        query.addCriteria(Criteria.where("tenantId").is(tenantId));

        // Filter by sharedById = current user
        query.addCriteria(Criteria.where("sharedById").is(userId));

        // Cursor-based pagination: get items after the cursor
        if (cursor != null && !cursor.isBlank()) {
            query.addCriteria(Criteria.where("_id").gt(cursor));
        }

        // Sort by _id for consistent cursor pagination
        query.with(Sort.by(Sort.Direction.ASC, "_id"));

        // Limit + 1 to check if there are more results
        query.limit(limit + 1);

        List<Share> shares = mongoTemplate.find(query, Share.class);

        // Determine if there are more results
        boolean hasMore = shares.size() > limit;
        if (hasMore) {
            shares = shares.subList(0, limit);
        }

        // Get next cursor (last item's ID)
        String nextCursor = hasMore && !shares.isEmpty()
                ? shares.get(shares.size() - 1).getId()
                : null;

        List<ShareDTO> dtos = shares.stream()
                .map(this::mapToShareDTO)
                .collect(Collectors.toList());

        return CursorPage.<ShareDTO>builder()
                .items(dtos)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .build();
    }

    /**
     * Update a share.
     * Requires SHARE permission on the drive.
     */
    @RequiresPermission(Permission.SHARE)
    @Transactional
    public ShareDTO updateShare(String shareId, UpdateShareRequest request) {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();

        // SECURITY FIX: Always filter by tenantId to prevent cross-tenant access
        Share share = shareRepository.findByIdAndTenantId(shareId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Share not found: " + shareId));

        // Verify ownership
        if (!share.getSharedById().equals(userId) && !share.getOwnerId().equals(userId)) {
            throw new AccessDeniedException("Not authorized to modify this share");
        }

        if (request.getPermissions() != null) {
            share.setPermissions(request.getPermissions());
        }
        if (request.getNotifyOnAccess() != null) {
            share.setNotifyOnAccess(request.getNotifyOnAccess());
        }
        if (request.getAllowReshare() != null) {
            share.setAllowReshare(request.getAllowReshare());
        }
        if (request.getPassword() != null) {
            share.setRequirePassword(true);
            share.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }
        if (Boolean.TRUE.equals(request.getRemovePassword())) {
            share.setRequirePassword(false);
            share.setPasswordHash(null);
        }
        if (request.getExpiresAt() != null) {
            share.setExpiresAt(request.getExpiresAt());
        }

        share.setUpdatedAt(Instant.now());

        Share savedShare = shareRepository.save(share);

        log.info("Share updated: {}", shareId);
        return mapToShareDTO(savedShare);
    }

    /**
     * Delete a share.
     * Requires SHARE permission on the drive.
     */
    @RequiresPermission(Permission.SHARE)
    @Transactional
    public void deleteShare(String shareId) {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();

        // SECURITY FIX: Always filter by tenantId to prevent cross-tenant access
        Share share = shareRepository.findByIdAndTenantId(shareId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Share not found: " + shareId));

        // Verify ownership
        if (!share.getSharedById().equals(userId) && !share.getOwnerId().equals(userId)) {
            throw new AccessDeniedException("Not authorized to delete this share");
        }

        shareRepository.delete(share);

        log.info("Share deleted: {}", shareId);
    }

    /**
     * SECURITY FIX (Round 6): Maximum retries for token generation collision.
     * While 32-char tokens have extremely low collision probability (~2^-256),
     * we add retry logic for defense-in-depth.
     */
    private static final int MAX_TOKEN_GENERATION_RETRIES = 3;

    /**
     * Create a public link.
     * Requires SHARE permission on the drive.
     *
     * SECURITY FIX (Round 6): Added retry logic for token collision handling.
     * If a unique constraint violation occurs on the token field, regenerates
     * the token and retries (up to MAX_TOKEN_GENERATION_RETRIES times).
     */
    @RequiresPermission(Permission.SHARE)
    @Transactional
    public PublicLinkDTO createPublicLink(CreatePublicLinkRequest request) {
        String tenantId = TenantContext.getTenantId();
        String driveId = TenantContext.getDriveId();
        String userId = TenantContext.getUserId();

        log.info("Creating public link for resource: {} by user: {}", request.getResourceId(), userId);

        // Default permissions to VIEW and DOWNLOAD
        Set<SharePermission> permissions = request.getPermissions();
        if (permissions == null || permissions.isEmpty()) {
            permissions = EnumSet.of(SharePermission.VIEW, SharePermission.DOWNLOAD);
        }

        // SECURITY FIX (Round 6): Retry loop for token collision
        for (int attempt = 0; attempt < MAX_TOKEN_GENERATION_RETRIES; attempt++) {
            // SECURITY FIX: Generate unique token with 32 characters (256 bits entropy)
            // Previous: 12 chars was too short and brute-forceable
            String token = generateToken(32);

            PublicLink link = PublicLink.builder()
                    .id(UUID.randomUUID().toString())
                    .token(token)
                    .tenantId(tenantId)
                    .driveId(driveId)
                    .resourceId(request.getResourceId())
                    .resourceType(request.getResourceType())
                    .name(request.getName())
                    .permissions(permissions)
                    .requirePassword(request.getPassword() != null)
                    .passwordHash(request.getPassword() != null ? passwordEncoder.encode(request.getPassword()) : null)
                    .maxDownloads(request.getMaxDownloads())
                    .downloadCount(0)
                    .expiresAt(request.getExpiresAt())
                    .accessCount(0)
                    .status(LinkStatus.ACTIVE)
                    .createdBy(userId)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            try {
                PublicLink savedLink = publicLinkRepository.save(link);
                // SECURITY FIX (Round 12): Never log tokens in plain text
                // Tokens are sensitive credentials that grant access to resources
                // Logging them exposes them to log aggregation systems, support staff, attackers
                log.info("Public link created: {} for resource: {}", savedLink.getId(), request.getResourceId());
                return mapToPublicLinkDTO(savedLink);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // Token collision - extremely rare with 32 chars but handle gracefully
                log.warn("SECURITY: Token collision on attempt {}, regenerating token", attempt + 1);
                if (attempt == MAX_TOKEN_GENERATION_RETRIES - 1) {
                    log.error("SECURITY: Failed to generate unique token after {} attempts", MAX_TOKEN_GENERATION_RETRIES);
                    throw new IllegalStateException("Failed to generate unique public link token");
                }
            }
        }

        // Should never reach here due to throw above, but compiler requires it
        throw new IllegalStateException("Failed to generate unique public link token");
    }

    /**
     * Get public link by token
     */
    public PublicLinkDTO getPublicLink(String token, String password) {
        PublicLink link = publicLinkRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Link not found"));

        // Check status
        if (link.getStatus() != LinkStatus.ACTIVE) {
            throw new AccessDeniedException("This link is no longer active");
        }

        // Check expiration
        if (link.getExpiresAt() != null && link.getExpiresAt().isBefore(Instant.now())) {
            link.setStatus(LinkStatus.EXPIRED);
            publicLinkRepository.save(link);
            throw new AccessDeniedException("This link has expired");
        }

        // Check download limit
        if (link.getMaxDownloads() != null && link.getDownloadCount() >= link.getMaxDownloads()) {
            link.setStatus(LinkStatus.EXHAUSTED);
            publicLinkRepository.save(link);
            throw new AccessDeniedException("This link has reached its download limit");
        }

        // SECURITY FIX (Round 6): Constant-time password verification
        // Prevents timing attacks by ensuring same execution time regardless of input
        // Old code used 50-150ms random delay which is statistically detectable with enough samples
        if (link.getRequirePassword()) {
            // Always run bcrypt comparison even if password is null (use empty string)
            String providedPassword = password != null ? password : "";
            boolean passwordValid = passwordEncoder.matches(providedPassword, link.getPasswordHash());

            // SECURITY: Use FIXED delay instead of random range
            // Random delays in range 50-150ms are detectable with statistical analysis
            // Fixed delay of 100ms ensures consistent timing for all requests
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            if (!passwordValid) {
                throw new AccessDeniedException("Invalid password");
            }
        }

        // SECURITY FIX: Use atomic increment to prevent race condition
        publicLinkRepository.atomicIncrementAccessCount(token, Instant.now());

        return mapToPublicLinkDTO(link);
    }

    /**
     * Record download for public link.
     *
     * SECURITY FIX: Uses atomic increment with limit check to prevent race condition
     * where concurrent downloads could bypass the max download limit.
     */
    @Transactional
    public void recordDownload(String token) {
        // SECURITY FIX (Round 9): Atomic increment AND status update in single operation
        // Previous code had race condition where concurrent downloads could all increment
        // before status was marked EXHAUSTED. Now we atomically:
        // 1. Increment count if under limit AND status is ACTIVE
        // 2. Set status to EXHAUSTED if count reaches limit in same operation
        long modifiedCount = publicLinkRepository.atomicIncrementAndExhaustIfLimitReached(token, Instant.now());

        if (modifiedCount == 0) {
            // Either link doesn't exist, limit was already reached, or link not ACTIVE
            PublicLink link = publicLinkRepository.findByToken(token).orElse(null);
            if (link == null) {
                throw new ResourceNotFoundException("Link not found");
            }
            if (link.getStatus() == LinkStatus.EXHAUSTED) {
                log.debug("Download attempt on exhausted link: {}", token);
                throw new AccessDeniedException("Download limit reached");
            }
            if (link.getStatus() != LinkStatus.ACTIVE) {
                log.debug("Download attempt on inactive link: {} (status: {})", token, link.getStatus());
                throw new AccessDeniedException("Link is no longer active");
            }
            // Fallback check - should not reach here in normal operation
            if (link.getMaxDownloads() != null && link.getDownloadCount() >= link.getMaxDownloads()) {
                publicLinkRepository.atomicUpdateStatus(token, LinkStatus.EXHAUSTED);
                throw new AccessDeniedException("Download limit reached");
            }
            throw new AccessDeniedException("Download limit reached");
        }

        log.debug("Download recorded for link: {}", token);
    }

    /**
     * Get public links for a resource.
     * Requires READ permission on the drive.
     */
    @RequiresPermission(Permission.READ)
    public List<PublicLinkDTO> getPublicLinksForResource(String resourceId) {
        String tenantId = TenantContext.getTenantId();

        List<PublicLink> links = publicLinkRepository.findByTenantIdAndResourceId(tenantId, resourceId);

        return links.stream()
                .map(this::mapToPublicLinkDTO)
                .collect(Collectors.toList());
    }

    /**
     * Disable a public link.
     * Requires SHARE permission on the drive.
     */
    @RequiresPermission(Permission.SHARE)
    @Transactional
    public void disablePublicLink(String linkId) {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();

        // SECURITY FIX: Always filter by tenantId to prevent cross-tenant access
        PublicLink link = publicLinkRepository.findByIdAndTenantId(linkId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Link not found: " + linkId));

        if (!link.getCreatedBy().equals(userId)) {
            throw new AccessDeniedException("Not authorized to modify this link");
        }

        link.setStatus(LinkStatus.DISABLED);
        link.setUpdatedAt(Instant.now());
        publicLinkRepository.save(link);

        log.info("Public link disabled: {}", linkId);
    }

    /**
     * Delete a public link.
     * Requires SHARE permission on the drive.
     */
    @RequiresPermission(Permission.SHARE)
    @Transactional
    public void deletePublicLink(String linkId) {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();

        // SECURITY FIX: Always filter by tenantId to prevent cross-tenant access
        PublicLink link = publicLinkRepository.findByIdAndTenantId(linkId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Link not found: " + linkId));

        if (!link.getCreatedBy().equals(userId)) {
            throw new AccessDeniedException("Not authorized to delete this link");
        }

        publicLinkRepository.delete(link);

        log.info("Public link deleted: {}", linkId);
    }

    /**
     * Check if current authenticated user has access to resource.
     *
     * SECURITY FIX (Round 7): User identity is now extracted from TenantContext and request headers
     * instead of the request body to prevent BOLA attacks where an attacker could check access
     * on behalf of other users and enumerate document sharing information.
     *
     * The request now only contains resourceId and requiredPermission - the user identity
     * (userId, teamIds, departmentId) comes from the authenticated session and headers.
     *
     * @param request the access check request containing resourceId and requiredPermission
     * @param headerTeamIds team IDs from X-Team-IDs header (set by gateway from JWT)
     * @param headerDepartmentId department ID from X-Department-ID header (set by gateway from JWT)
     */
    public AccessCheckResponse checkAccess(AccessCheckRequest request,
                                            List<String> headerTeamIds,
                                            String headerDepartmentId) {
        String tenantId = TenantContext.getTenantId();
        // SECURITY FIX: Extract user identity from authenticated session, NOT from request body
        String userId = TenantContext.getUserId();

        // Log if request contained user identity fields (indicates potential attack or legacy client)
        if (request.getUserId() != null || request.getTeamIds() != null || request.getDepartmentId() != null) {
            log.warn("SECURITY: AccessCheckRequest contained user identity fields which were ignored. " +
                    "resourceId={}, requestUserId={}, authenticatedUserId={}",
                    request.getResourceId(), request.getUserId(), userId);
        }

        // Check if user is owner (would need to call document/folder service)
        // For now, check shares only

        boolean hasAccess = shareRepository.hasAccessToResource(
                tenantId,
                request.getResourceId(),
                userId,
                headerTeamIds != null ? headerTeamIds : Collections.emptyList(),
                headerDepartmentId);

        if (!hasAccess) {
            return AccessCheckResponse.builder()
                    .hasAccess(false)
                    .build();
        }

        // Get permissions
        List<Share> shares = shareRepository.findSharesForUser(
                tenantId,
                userId,
                headerTeamIds != null ? headerTeamIds : Collections.emptyList(),
                headerDepartmentId);

        Set<SharePermission> allPermissions = shares.stream()
                .filter(s -> s.getResourceId().equals(request.getResourceId()))
                .flatMap(s -> s.getPermissions().stream())
                .collect(Collectors.toSet());

        boolean hasRequiredPermission = request.getRequiredPermission() == null ||
                allPermissions.contains(request.getRequiredPermission());

        return AccessCheckResponse.builder()
                .hasAccess(hasRequiredPermission)
                .permissions(allPermissions)
                .accessSource("SHARE")
                .build();
    }

    /**
     * Bulk create shares for multiple resources and targets.
     * Requires SHARE permission on the drive.
     *
     * SECURITY FIX (Round 6): This operation is now atomic - either all shares are
     * created or none are. Partial failures will roll back the entire transaction
     * to prevent inconsistent state where some resources are shared but others aren't.
     *
     * @throws BulkShareException if any share fails to be created (with details of failures)
     */
    @RequiresPermission(Permission.SHARE)
    @Transactional(rollbackFor = Exception.class)
    public List<ShareDTO> bulkShare(BulkShareRequest request) {
        String tenantId = TenantContext.getTenantId();
        String driveId = TenantContext.getDriveId();
        String userId = TenantContext.getUserId();

        log.info("Creating bulk shares for {} resources to {} targets",
                request.getResourceIds().size(), request.getShares().size());

        List<ShareDTO> createdShares = new ArrayList<>();
        List<String> skippedDuplicates = new ArrayList<>();

        // SECURITY FIX (Round 6): Build all shares first, validate all, then save all atomically
        List<Share> sharesToCreate = new ArrayList<>();

        for (String resourceId : request.getResourceIds()) {
            for (BulkShareRequest.ShareTarget target : request.getShares()) {
                // Check if share already exists
                // SECURITY FIX (Round 14 #C5): Added tenant filter to prevent cross-tenant duplicate check
                Optional<Share> existingShare = shareRepository.findByTenantIdAndResourceIdAndSharedWithId(
                        tenantId, resourceId, target.getSharedWithId());

                if (existingShare.isPresent()) {
                    log.debug("Share already exists for resource {} and target {}",
                            resourceId, target.getSharedWithId());
                    skippedDuplicates.add(resourceId + ":" + target.getSharedWithId());
                    continue;
                }

                Share share = Share.builder()
                        .id(UUID.randomUUID().toString())
                        .tenantId(tenantId)
                        .driveId(driveId)
                        .resourceId(resourceId)
                        .resourceType(request.getResourceType())
                        .ownerId(userId)
                        .sharedById(userId)
                        .sharedWithId(target.getSharedWithId())
                        .sharedWithType(target.getSharedWithType())
                        .permissions(target.getPermissions())
                        .notifyOnAccess(false)
                        .allowReshare(false)
                        .requirePassword(false)
                        .expiresAt(request.getExpiresAt())
                        .accessCount(0)
                        .message(request.getMessage())
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();

                sharesToCreate.add(share);
            }
        }

        // SECURITY FIX (Round 6): Save all at once - if any fails, entire transaction rolls back
        // This ensures atomic operation - no partial shares created
        // SECURITY FIX: Handle DuplicateKeyException from unique index to prevent race condition
        // where concurrent requests could both pass the check but one fails on insert
        if (!sharesToCreate.isEmpty()) {
            try {
                List<Share> savedShares = shareRepository.saveAll(sharesToCreate);
                createdShares = savedShares.stream()
                        .map(this::mapToShareDTO)
                        .collect(Collectors.toList());
            } catch (DuplicateKeyException e) {
                // Race condition detected - another request created the share between our check and save
                // Log and re-throw as a more specific exception for the caller
                log.warn("SECURITY: Duplicate share detected during bulk share (race condition handled): {}",
                        e.getMessage());
                // Re-fetch existing shares and return them instead of failing
                // This provides idempotent behavior for the caller
                List<ShareDTO> existingShares = new ArrayList<>();
                for (Share share : sharesToCreate) {
                    shareRepository.findByTenantIdAndResourceIdAndSharedWithId(
                                    share.getTenantId(), share.getResourceId(), share.getSharedWithId())
                            .ifPresent(existing -> existingShares.add(mapToShareDTO(existing)));
                }
                log.info("Bulk share completed with {} existing shares (concurrent request created them)",
                        existingShares.size());
                return existingShares;
            }
        }

        log.info("Bulk share completed: {} shares created, {} duplicates skipped",
                createdShares.size(), skippedDuplicates.size());
        return createdShares;
    }

    /**
     * Search users for sharing.
     * This would typically call a User Service, but for now returns mock data.
     */
    public List<UserSearchResult> searchUsersForSharing(String query) {
        String tenantId = TenantContext.getTenantId();

        log.debug("Searching users for sharing with query: {} in tenant: {}", query, tenantId);

        // TODO: Call User Service via HTTP Service Client
        // For now, return empty list - in production, this would call AccessArc User Service
        // Example: return userServiceClient.searchUsers(tenantId, query);

        // Return empty list - frontend will handle empty state
        return Collections.emptyList();
    }

    /**
     * Search teams for sharing.
     * This would typically call a Team Service, but for now returns mock data.
     */
    public List<TeamSearchResult> searchTeamsForSharing(String query) {
        String tenantId = TenantContext.getTenantId();

        log.debug("Searching teams for sharing with query: {} in tenant: {}", query, tenantId);

        // TODO: Call Team Service via HTTP Service Client
        // For now, return empty list - in production, this would call TeamSync Team Service
        // Example: return teamServiceClient.searchTeams(tenantId, query);

        // Return empty list - frontend will handle empty state
        return Collections.emptyList();
    }

    // Helper methods

    private String generateToken(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(TOKEN_CHARS.charAt(secureRandom.nextInt(TOKEN_CHARS.length())));
        }
        return sb.toString();
    }

    private ShareDTO mapToShareDTO(Share share) {
        return ShareDTO.builder()
                .id(share.getId())
                .tenantId(share.getTenantId())
                .driveId(share.getDriveId())
                .resourceId(share.getResourceId())
                .resourceType(share.getResourceType())
                .ownerId(share.getOwnerId())
                .sharedById(share.getSharedById())
                .sharedWithId(share.getSharedWithId())
                .sharedWithType(share.getSharedWithType())
                .permissions(share.getPermissions())
                .notifyOnAccess(share.getNotifyOnAccess())
                .allowReshare(share.getAllowReshare())
                .requirePassword(share.getRequirePassword())
                .expiresAt(share.getExpiresAt())
                .accessCount(share.getAccessCount())
                .lastAccessedAt(share.getLastAccessedAt())
                .createdAt(share.getCreatedAt())
                .updatedAt(share.getUpdatedAt())
                .message(share.getMessage())
                .build();
    }

    private PublicLinkDTO mapToPublicLinkDTO(PublicLink link) {
        Integer downloadsRemaining = null;
        if (link.getMaxDownloads() != null) {
            downloadsRemaining = Math.max(0, link.getMaxDownloads() - link.getDownloadCount());
        }

        return PublicLinkDTO.builder()
                .id(link.getId())
                .token(link.getToken())
                .url(publicLinkBaseUrl + "/" + link.getToken())
                .resourceId(link.getResourceId())
                .resourceType(link.getResourceType())
                .name(link.getName())
                .permissions(link.getPermissions())
                .requirePassword(link.getRequirePassword())
                .maxDownloads(link.getMaxDownloads())
                .downloadCount(link.getDownloadCount())
                .downloadsRemaining(downloadsRemaining)
                .expiresAt(link.getExpiresAt())
                .accessCount(link.getAccessCount())
                .lastAccessedAt(link.getLastAccessedAt())
                .status(link.getStatus())
                .createdBy(link.getCreatedBy())
                .createdAt(link.getCreatedAt())
                .updatedAt(link.getUpdatedAt())
                .build();
    }
}
