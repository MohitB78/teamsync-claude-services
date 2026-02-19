package com.teamsync.permission.service;

import com.teamsync.common.context.TenantContext;
import com.teamsync.common.dto.CursorPage;
import com.teamsync.common.exception.AccessDeniedException;
import com.teamsync.common.exception.ResourceNotFoundException;
import com.teamsync.common.model.DriveType;
import com.teamsync.common.model.Permission;
import com.teamsync.permission.dto.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.teamsync.permission.model.Drive;
import com.teamsync.permission.model.DriveAssignment;
import com.teamsync.permission.model.DriveAssignment.AssignmentSource;
import com.teamsync.permission.model.DriveRole;
import com.teamsync.permission.repository.DriveAssignmentRepository;
import com.teamsync.permission.repository.DriveRepository;
import com.teamsync.permission.repository.DriveRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Core Permission Manager Service providing O(1) permission checks.
 *
 * Architecture:
 * 1. Check Redis cache first (O(1))
 * 2. On cache miss, query MongoDB with indexed lookup (O(1))
 * 3. Cache result for future lookups
 * 4. Invalidate cache on permission changes
 *
 * This service is the single source of truth for drive-level RBAC.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PermissionManagerService {

    private final DriveRepository driveRepository;
    private final DriveRoleRepository roleRepository;
    private final DriveAssignmentRepository assignmentRepository;
    private final PermissionCacheService cacheService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String PERMISSION_EVENTS_TOPIC = "teamsync.permissions.events";
    private static final String ADMIN_CACHE_PREFIX = "admin:";

    /**
     * SECURITY FIX (Round 10 #19): Admin status cache TTL reduced to 10 seconds.
     * Admin status changes are security-sensitive - a revoked admin should lose
     * access within 10 seconds, not minutes. This minimizes the window where
     * a revoked admin can still perform privileged operations.
     *
     * For high-security environments, consider using event-driven cache invalidation
     * via Kafka when role assignments change.
     */
    @Value("${permission.cache.admin-ttl-seconds:10}")
    private int adminCacheTtlSeconds;

    // ============== PERMISSION CHECK (O(1)) ==============

    /**
     * Check if user has access to a drive with optional permission check.
     * This is the primary method for permission validation.
     *
     * Performance: O(1) with cache hit, O(1) with indexed DB lookup on miss.
     */
    public PermissionCheckResponse checkPermission(PermissionCheckRequest request) {
        long startTime = System.currentTimeMillis();
        String userId = request.getUserId();
        String driveId = request.getDriveId();
        String tenantId = TenantContext.getTenantId();

        // 1. Check cache first (O(1))
        Optional<CachedPermission> cached = cacheService.get(tenantId, userId, driveId);
        if (cached.isPresent()) {
            CachedPermission perm = cached.get();
            long duration = System.currentTimeMillis() - startTime;

            if (!perm.isHasAccess()) {
                return PermissionCheckResponse.noAccess("CACHE", duration);
            }

            return PermissionCheckResponse.granted(
                    perm.getPermissions(),
                    request.getRequiredPermission(),
                    perm.getRoleName(),
                    perm.isOwner(),
                    "CACHE",
                    duration
            );
        }

        // 2. Cache miss - check if personal drive owner
        if (isPersonalDriveOwner(userId, driveId)) {
            Set<Permission> ownerPerms = EnumSet.allOf(Permission.class);
            CachedPermission cachePerm = CachedPermission.fromAssignment(
                    tenantId, userId, driveId, null, DriveRole.ROLE_OWNER, ownerPerms, true, null);
            cacheService.put(cachePerm);

            long duration = System.currentTimeMillis() - startTime;
            return PermissionCheckResponse.granted(
                    ownerPerms,
                    request.getRequiredPermission(),
                    DriveRole.ROLE_OWNER,
                    true,
                    "DATABASE",
                    duration
            );
        }

        // 3. Check assignment in database (O(1) indexed lookup)
        Optional<DriveAssignment> assignment = assignmentRepository
                .findByUserIdAndDriveIdAndIsActiveTrue(userId, driveId);

        long duration = System.currentTimeMillis() - startTime;

        if (assignment.isEmpty() || !assignment.get().isValid()) {
            // Cache negative result
            cacheService.put(CachedPermission.noAccess(userId, driveId, tenantId));
            return PermissionCheckResponse.noAccess("DATABASE", duration);
        }

        DriveAssignment assign = assignment.get();

        // 4. Cache the result
        CachedPermission cachePerm = CachedPermission.fromAssignment(
                tenantId,
                userId,
                driveId,
                assign.getRoleId(),
                assign.getRoleName(),
                assign.getPermissions(),
                assign.getSource() == AssignmentSource.OWNER,
                assign.getExpiresAt()
        );
        cacheService.put(cachePerm);

        return PermissionCheckResponse.granted(
                assign.getPermissions(),
                request.getRequiredPermission(),
                assign.getRoleName(),
                assign.getSource() == AssignmentSource.OWNER,
                "DATABASE",
                duration
        );
    }

    /**
     * Quick check if user has a specific permission on a drive.
     * Throws AccessDeniedException if not.
     *
     * SECURITY: Returns generic message to prevent enumeration attacks.
     * Do not reveal whether user/drive exists or what permission was required.
     */
    public void requirePermission(String userId, String driveId, Permission permission) {
        PermissionCheckResponse response = checkPermission(
                PermissionCheckRequest.builder()
                        .userId(userId)
                        .driveId(driveId)
                        .requiredPermission(permission)
                        .build()
        );

        if (!response.isHasPermission()) {
            // SECURITY FIX: Generic message prevents permission enumeration
            log.warn("SECURITY: Permission denied - user {} requested {} on drive {}",
                    userId, permission, driveId);
            throw new AccessDeniedException("Access denied");
        }
    }

    /**
     * Check if user has any access to drive.
     */
    public boolean hasAccess(String userId, String driveId) {
        PermissionCheckResponse response = checkPermission(
                PermissionCheckRequest.builder()
                        .userId(userId)
                        .driveId(driveId)
                        .build()
        );
        return response.isHasAccess();
    }

    /**
     * Check if user is a tenant admin.
     * A tenant admin is defined as a user who has MANAGE_ROLES permission on any drive
     * within the tenant, or is explicitly marked as admin.
     *
     * SECURITY: This method is used for authorization of sensitive operations like
     * creating department drives.
     *
     * PERFORMANCE: Result is cached in Redis for 5 minutes to avoid repeated DB queries.
     * Uses optimized exists query that stops at first match instead of fetching all assignments.
     */
    public boolean isTenantAdmin(String userId) {
        String tenantId = TenantContext.getTenantId();
        String cacheKey = ADMIN_CACHE_PREFIX + tenantId + ":" + userId;

        // Check Redis cache first
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                boolean isAdmin = Boolean.parseBoolean(cached);
                log.debug("Admin status cache hit for user {}: {}", userId, isAdmin);
                return isAdmin;
            }
        } catch (Exception e) {
            log.warn("Failed to check admin cache for user {}: {}", userId, e.getMessage());
            // Continue with DB lookup on cache error
        }

        // Optimized query: checks if ANY assignment has MANAGE_ROLES, stops at first match
        boolean isAdmin = assignmentRepository.existsByTenantIdAndUserIdAndPermissionsContainingAndIsActiveTrue(
                tenantId, userId, Permission.MANAGE_ROLES);

        // Cache the result with short TTL for security
        try {
            redisTemplate.opsForValue().set(cacheKey, String.valueOf(isAdmin),
                    Duration.ofSeconds(adminCacheTtlSeconds));
            log.debug("Cached admin status for user {} (TTL={}s): {}", userId, adminCacheTtlSeconds, isAdmin);
        } catch (Exception e) {
            log.warn("Failed to cache admin status for user {}: {}", userId, e.getMessage());
        }

        if (isAdmin) {
            log.debug("User {} is tenant admin (has MANAGE_ROLES permission)", userId);
        }

        return isAdmin;
    }

    /**
     * Invalidate admin status cache for a user.
     * Called when user's role assignments change.
     */
    public void invalidateAdminCache(String userId) {
        String tenantId = TenantContext.getTenantId();
        String cacheKey = ADMIN_CACHE_PREFIX + tenantId + ":" + userId;
        try {
            redisTemplate.delete(cacheKey);
            log.debug("Invalidated admin cache for user {}", userId);
        } catch (Exception e) {
            log.warn("Failed to invalidate admin cache for user {}: {}", userId, e.getMessage());
        }
    }

    // ============== DRIVE MANAGEMENT ==============

    /**
     * Create a new drive.
     */
    @Transactional
    public DriveDTO createDrive(CreateDriveRequest request) {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();

        // Validate based on drive type
        if (request.getType() == DriveType.PERSONAL) {
            if (request.getOwnerId() == null) {
                throw new IllegalArgumentException("Owner ID is required for personal drives");
            }
        } else if (request.getType() == DriveType.DEPARTMENT) {
            if (request.getDepartmentId() == null) {
                throw new IllegalArgumentException("Department ID is required for department drives");
            }
        }

        // Generate drive ID
        String driveId = request.getType() == DriveType.PERSONAL
                ? Drive.personalDriveId(request.getOwnerId())
                : Drive.departmentDriveId(request.getDepartmentId());

        // Check if drive already exists
        if (driveRepository.existsByIdAndTenantId(driveId, tenantId)) {
            throw new IllegalArgumentException("Drive already exists: " + driveId);
        }

        // Ensure system roles exist for tenant
        ensureSystemRoles(tenantId);

        // Get owner role
        DriveRole ownerRole = roleRepository
                .findByTenantIdAndNameAndDriveIdIsNull(tenantId, DriveRole.ROLE_OWNER)
                .orElseThrow(() -> new IllegalStateException("Owner role not found"));

        // Create drive
        Drive drive = Drive.builder()
                .id(driveId)
                .tenantId(tenantId)
                .name(request.getName())
                .description(request.getDescription())
                .type(request.getType())
                .ownerId(request.getOwnerId())
                .departmentId(request.getDepartmentId())
                .quotaBytes(request.getQuotaBytes())
                .usedBytes(0L)
                .defaultRoleId(request.getDefaultRoleId())
                .status(Drive.DriveStatus.ACTIVE)
                .settings(request.getSettings() != null ? request.getSettings() : new Drive.DriveSettings())
                .createdAt(Instant.now())
                .createdBy(userId)
                .build();

        drive = driveRepository.save(drive);

        // Create owner assignment for personal drives
        if (request.getType() == DriveType.PERSONAL) {
            DriveAssignment ownerAssignment = DriveAssignment.createOwnerAssignment(
                    tenantId,
                    driveId,
                    request.getOwnerId(),
                    ownerRole.getId(),
                    ownerRole.getPermissions()
            );
            assignmentRepository.save(ownerAssignment);
        }

        log.info("Created drive: {} of type {} in tenant {}", driveId, request.getType(), tenantId);

        // Publish event
        publishEvent("drive.created", Map.of(
                "driveId", driveId,
                "type", request.getType().name(),
                "ownerId", request.getOwnerId() != null ? request.getOwnerId() : "",
                "departmentId", request.getDepartmentId() != null ? request.getDepartmentId() : ""
        ));

        return mapToDriveDTO(drive);
    }

    /**
     * Get drive by ID.
     */
    public DriveDTO getDrive(String driveId) {
        String tenantId = TenantContext.getTenantId();

        Drive drive = driveRepository.findByIdAndTenantId(driveId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Drive not found: " + driveId));

        DriveDTO dto = mapToDriveDTO(drive);
        dto.setUserCount(assignmentRepository.countByTenantIdAndDriveIdAndIsActiveTrue(tenantId, driveId));

        return dto;
    }

    /**
     * Get all drives accessible by a user (legacy method - returns all drives).
     * @deprecated Use {@link #getUserDrivesPaginated(String, String, int)} for cursor-based pagination.
     */
    @Deprecated
    public List<DriveDTO> getUserDrives(String userId) {
        String tenantId = TenantContext.getTenantId();

        List<DriveAssignment> assignments = assignmentRepository
                .findByTenantIdAndUserIdAndIsActiveTrue(tenantId, userId);

        List<String> driveIds = assignments.stream()
                .map(DriveAssignment::getDriveId)
                .collect(Collectors.toList());

        return driveRepository.findAllById(driveIds).stream()
                .map(this::mapToDriveDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get drives accessible by a user with cursor-based pagination.
     * Handles users with many drive assignments efficiently.
     *
     * @param userId the user ID
     * @param cursor the cursor from previous page (null for first page)
     * @param limit maximum items to return (default 50, max 100)
     * @return paginated list of drives
     */
    public CursorPage<DriveDTO> getUserDrivesPaginated(String userId, String cursor, int limit) {
        String tenantId = TenantContext.getTenantId();

        // Clamp limit to reasonable bounds
        limit = Math.min(Math.max(limit, 1), 100);

        Pageable pageable = PageRequest.of(0, limit + 1, Sort.by(Sort.Direction.ASC, "_id"));
        List<DriveAssignment> assignments;

        if (cursor != null && !cursor.isEmpty()) {
            assignments = assignmentRepository.findByTenantIdAndUserIdAndIsActiveTrueAfterCursor(
                    tenantId, userId, cursor, pageable);
        } else {
            assignments = assignmentRepository.findByTenantIdAndUserIdAndIsActiveTrueOrderByIdAsc(
                    tenantId, userId, pageable);
        }

        boolean hasMore = assignments.size() > limit;
        if (hasMore) {
            assignments = assignments.subList(0, limit);
        }

        String nextCursor = hasMore && !assignments.isEmpty()
                ? assignments.get(assignments.size() - 1).getId()
                : null;

        // Batch fetch drives to avoid N+1
        List<String> driveIds = assignments.stream()
                .map(DriveAssignment::getDriveId)
                .collect(Collectors.toList());

        Map<String, Drive> driveMap = driveRepository.findAllById(driveIds).stream()
                .collect(Collectors.toMap(Drive::getId, d -> d));

        List<DriveDTO> dtos = assignments.stream()
                .map(a -> driveMap.get(a.getDriveId()))
                .filter(Objects::nonNull)
                .map(this::mapToDriveDTO)
                .collect(Collectors.toList());

        // Get total count for first page only (expensive operation)
        long totalCount = cursor == null
                ? assignmentRepository.countByTenantIdAndUserIdAndIsActiveTrue(tenantId, userId)
                : -1; // Don't compute total on subsequent pages

        return CursorPage.<DriveDTO>builder()
                .items(dtos)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .totalCount(totalCount)
                .limit(limit)
                .build();
    }

    // ============== ROLE ASSIGNMENT ==============

    /**
     * Assign a role to a user for a drive.
     *
     * SECURITY FIX: Added privilege escalation prevention. Users can only assign roles
     * that have equal or fewer permissions than their own role on the drive. This prevents
     * a user with MANAGE_USERS from granting MANAGE_ROLES (a higher privilege).
     */
    @Transactional
    public DriveAssignmentDTO assignRole(AssignRoleRequest request) {
        String tenantId = TenantContext.getTenantId();
        String grantedBy = TenantContext.getUserId();

        // Verify drive exists
        if (!driveRepository.existsByIdAndTenantId(request.getDriveId(), tenantId)) {
            throw new ResourceNotFoundException("Drive not found: " + request.getDriveId());
        }

        // Verify role exists and get permissions
        DriveRole role = roleRepository.findByIdAndTenantId(request.getRoleId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + request.getRoleId()));

        // SECURITY FIX: Privilege escalation prevention
        // Check if the granter has all permissions they're trying to grant
        PermissionCheckResponse granterPerms = checkPermission(
                PermissionCheckRequest.builder()
                        .userId(grantedBy)
                        .driveId(request.getDriveId())
                        .build()
        );

        if (!granterPerms.isHasAccess() || granterPerms.getPermissions() == null) {
            log.warn("SECURITY: Permission escalation attempt - user {} has no access to drive {}",
                    grantedBy, request.getDriveId());
            throw new AccessDeniedException("Access denied");
        }

        // Check if role contains permissions the granter doesn't have
        Set<Permission> granterPermissions = granterPerms.getPermissions();
        Set<Permission> rolePermissions = role.getPermissions();

        for (Permission rolePerm : rolePermissions) {
            if (!granterPermissions.contains(rolePerm)) {
                log.warn("SECURITY: Privilege escalation attempt - user {} tried to grant {} permission " +
                         "on drive {} which they don't have",
                        grantedBy, rolePerm, request.getDriveId());
                throw new AccessDeniedException("Cannot assign role with permissions you don't have");
            }
        }

        // Check if assignment already exists
        Optional<DriveAssignment> existing = assignmentRepository
                .findByTenantIdAndUserIdAndDriveId(tenantId, request.getUserId(), request.getDriveId());

        DriveAssignment assignment;
        if (existing.isPresent()) {
            // Update existing assignment
            assignment = existing.get();
            assignment.setRoleId(request.getRoleId());
            assignment.setRoleName(role.getName());
            assignment.setPermissions(role.getPermissions());
            assignment.setIsActive(true);
            assignment.setUpdatedAt(Instant.now());
        } else {
            // Create new assignment
            assignment = DriveAssignment.builder()
                    .tenantId(tenantId)
                    .driveId(request.getDriveId())
                    .userId(request.getUserId())
                    .roleId(request.getRoleId())
                    .roleName(role.getName())
                    .permissions(role.getPermissions())
                    .source(request.getSource())
                    .assignedViaDepartment(request.getDepartmentId())
                    .assignedViaTeam(request.getTeamId())
                    .grantedBy(grantedBy)
                    .grantedAt(Instant.now())
                    .expiresAt(request.getExpiresAt())
                    .isActive(true)
                    .build();
        }

        assignment = assignmentRepository.save(assignment);

        // Invalidate caches
        cacheService.delete(tenantId, request.getUserId(), request.getDriveId());
        invalidateAdminCache(request.getUserId());

        log.info("Assigned role {} to user {} on drive {}",
                role.getName(), request.getUserId(), request.getDriveId());

        // Publish event
        publishEvent("role.assigned", Map.of(
                "userId", request.getUserId(),
                "driveId", request.getDriveId(),
                "roleId", request.getRoleId(),
                "roleName", role.getName()
        ));

        return mapToAssignmentDTO(assignment);
    }

    /**
     * Remove a user's access to a drive.
     */
    @Transactional
    public void revokeAccess(String userId, String driveId) {
        String tenantId = TenantContext.getTenantId();

        Optional<DriveAssignment> assignment = assignmentRepository
                .findByTenantIdAndUserIdAndDriveId(tenantId, userId, driveId);

        if (assignment.isEmpty()) {
            throw new ResourceNotFoundException("Assignment not found");
        }

        // Check if trying to revoke owner access
        if (assignment.get().getSource() == AssignmentSource.OWNER) {
            throw new IllegalArgumentException("Cannot revoke owner access from personal drive");
        }

        assignmentRepository.deactivateAssignment(userId, driveId, Instant.now());

        // Invalidate caches
        cacheService.delete(tenantId, userId, driveId);
        invalidateAdminCache(userId);

        log.info("Revoked access for user {} on drive {}", userId, driveId);

        // Publish event
        publishEvent("access.revoked", Map.of(
                "userId", userId,
                "driveId", driveId
        ));
    }

    /**
     * Get all users with access to a drive (legacy method - returns all users).
     * @deprecated Use {@link #getDriveUsersPaginated(String, String, int)} for cursor-based pagination.
     */
    @Deprecated
    public List<DriveAssignmentDTO> getDriveUsers(String driveId) {
        String tenantId = TenantContext.getTenantId();

        return assignmentRepository.findByTenantIdAndDriveIdAndIsActiveTrue(tenantId, driveId)
                .stream()
                .map(this::mapToAssignmentDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get users with access to a drive with cursor-based pagination.
     * Handles drives with many users (e.g., department drives) efficiently.
     *
     * @param driveId the drive ID
     * @param cursor the cursor from previous page (null for first page)
     * @param limit maximum items to return (default 50, max 100)
     * @return paginated list of drive assignments
     */
    public CursorPage<DriveAssignmentDTO> getDriveUsersPaginated(String driveId, String cursor, int limit) {
        String tenantId = TenantContext.getTenantId();

        // Clamp limit to reasonable bounds
        limit = Math.min(Math.max(limit, 1), 100);

        Pageable pageable = PageRequest.of(0, limit + 1, Sort.by(Sort.Direction.ASC, "_id"));
        List<DriveAssignment> assignments;

        if (cursor != null && !cursor.isEmpty()) {
            assignments = assignmentRepository.findByTenantIdAndDriveIdAndIsActiveTrueAfterCursor(
                    tenantId, driveId, cursor, pageable);
        } else {
            assignments = assignmentRepository.findByTenantIdAndDriveIdAndIsActiveTrueOrderByIdAsc(
                    tenantId, driveId, pageable);
        }

        boolean hasMore = assignments.size() > limit;
        if (hasMore) {
            assignments = assignments.subList(0, limit);
        }

        String nextCursor = hasMore && !assignments.isEmpty()
                ? assignments.get(assignments.size() - 1).getId()
                : null;

        List<DriveAssignmentDTO> dtos = assignments.stream()
                .map(this::mapToAssignmentDTO)
                .collect(Collectors.toList());

        // Get total count for first page only (expensive operation)
        long totalCount = cursor == null
                ? assignmentRepository.countByTenantIdAndDriveIdAndIsActiveTrue(tenantId, driveId)
                : -1; // Don't compute total on subsequent pages

        return CursorPage.<DriveAssignmentDTO>builder()
                .items(dtos)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .totalCount(totalCount)
                .limit(limit)
                .build();
    }

    // ============== ROLE MANAGEMENT ==============

    /**
     * Get all roles for a tenant.
     * Cached: Roles change infrequently, so cache for 5 minutes.
     */
    @Cacheable(value = "tenantRoles", key = "#root.target.currentTenantId")
    public List<DriveRoleDTO> getTenantRoles() {
        String tenantId = TenantContext.getTenantId();
        return roleRepository.findByTenantIdAndDriveIdIsNull(tenantId)
                .stream()
                .map(this::mapToRoleDTO)
                .collect(Collectors.toList());
    }

    /**
     * Helper method to get current tenant ID for cache key.
     * SpEL cannot directly call TenantContext.getTenantId() from @Cacheable.
     */
    public String getCurrentTenantId() {
        return TenantContext.getTenantId();
    }

    /**
     * Update role permissions (cascades to all assignments).
     * Optimized: Uses batch cache invalidation instead of sequential deletes.
     */
    @Transactional
    @CacheEvict(value = "tenantRoles", key = "#root.target.currentTenantId")
    public DriveRoleDTO updateRolePermissions(String roleId, Set<Permission> permissions) {
        String tenantId = TenantContext.getTenantId();

        DriveRole role = roleRepository.findByIdAndTenantId(roleId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleId));

        if (Boolean.TRUE.equals(role.getIsSystemRole())) {
            throw new IllegalArgumentException("Cannot modify system role permissions");
        }

        // Get affected assignments BEFORE updating (we need the user/drive pairs for cache invalidation)
        List<DriveAssignment> affected = assignmentRepository.findByTenantIdAndRoleId(tenantId, roleId);

        role.setPermissions(permissions);
        role.setUpdatedAt(Instant.now());
        role = roleRepository.save(role);

        // Bulk update all assignments with this role (single MongoDB operation)
        // SECURITY FIX (Round 14 #C4): Added tenant filter to prevent cross-tenant privilege escalation
        Instant now = Instant.now();
        assignmentRepository.updatePermissionsByTenantIdAndRoleId(tenantId, roleId, permissions, now);

        // Batch invalidate cache - group by driveId for efficient invalidation
        if (!affected.isEmpty()) {
            // Group users by drive for more efficient batch invalidation
            Map<String, List<String>> usersByDrive = affected.stream()
                    .collect(Collectors.groupingBy(
                            DriveAssignment::getDriveId,
                            Collectors.mapping(DriveAssignment::getUserId, Collectors.toList())
                    ));

            // Invalidate in batches per drive
            for (Map.Entry<String, List<String>> entry : usersByDrive.entrySet()) {
                cacheService.invalidateUsersOnDrive(tenantId, entry.getValue(), entry.getKey());
            }
        }

        log.info("Updated permissions for role {} affecting {} assignments",
                roleId, affected.size());

        // Publish event
        publishEvent("role.updated", Map.of(
                "roleId", roleId,
                "roleName", role.getName(),
                "affectedUsers", affected.size()
        ));

        return mapToRoleDTO(role);
    }

    // ============== BULK OPERATIONS ==============

    /**
     * Assign default role to all members of a department.
     * Called when AccessArc creates a new department.
     * Optimized: Uses batch insert and pre-filters existing assignments.
     *
     * SECURITY FIX (Round 6): Validates that the target drive belongs to the current tenant.
     * This prevents cross-tenant privilege escalation where an attacker could assign
     * their department's users to a drive belonging to another tenant.
     */
    @Transactional
    public int assignDepartmentMembers(String driveId, String departmentId, List<String> userIds, String defaultRoleId) {
        String tenantId = TenantContext.getTenantId();

        if (userIds == null || userIds.isEmpty()) {
            return 0;
        }

        // SECURITY FIX (Round 6): Validate drive belongs to the current tenant
        // Without this check, an attacker could assign users to drives in other tenants
        Drive drive = driveRepository.findByIdAndTenantId(driveId, tenantId)
                .orElseThrow(() -> {
                    log.warn("SECURITY: Cross-tenant drive access attempt - driveId: {}, tenant: {}",
                            driveId, tenantId);
                    return new AccessDeniedException("Drive not found or access denied: " + driveId);
                });

        // Additional validation: drive must be ACTIVE
        if (drive.getStatus() != Drive.DriveStatus.ACTIVE) {
            throw new IllegalStateException("Cannot assign to inactive drive: " + driveId);
        }

        DriveRole role = roleRepository.findByIdAndTenantId(defaultRoleId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + defaultRoleId));

        // Get existing assignments in one query to avoid N+1 exists checks
        List<DriveAssignment> existingAssignments = assignmentRepository
                .findByTenantIdAndDriveIdAndIsActiveTrue(tenantId, driveId);
        Set<String> existingUserIds = existingAssignments.stream()
                .map(DriveAssignment::getUserId)
                .collect(Collectors.toSet());

        // Build list of new assignments (filter out existing)
        Instant now = Instant.now();
        List<DriveAssignment> newAssignments = new ArrayList<>();

        for (String userId : userIds) {
            // Skip if assignment already exists
            if (existingUserIds.contains(userId)) {
                continue;
            }

            DriveAssignment assignment = DriveAssignment.builder()
                    .tenantId(tenantId)
                    .driveId(driveId)
                    .userId(userId)
                    .roleId(defaultRoleId)
                    .roleName(role.getName())
                    .permissions(role.getPermissions())
                    .source(AssignmentSource.DEPARTMENT)
                    .assignedViaDepartment(departmentId)
                    .grantedBy("system")
                    .grantedAt(now)
                    .isActive(true)
                    .build();

            newAssignments.add(assignment);
        }

        // Batch insert all new assignments
        if (!newAssignments.isEmpty()) {
            assignmentRepository.saveAll(newAssignments);
        }

        log.info("Assigned {} users from department {} to drive {} (skipped {} existing)",
                newAssignments.size(), departmentId, driveId, userIds.size() - newAssignments.size());

        return newAssignments.size();
    }

    /**
     * Remove all department-based assignments for a user.
     * Called when user leaves a department.
     * Optimized: Filters at database level and uses batch operations.
     */
    @Transactional
    public void removeDepartmentAccess(String userId, String departmentId) {
        String tenantId = TenantContext.getTenantId();

        // Query filters at database level (not in memory) - much more efficient for large departments
        List<DriveAssignment> assignments = assignmentRepository
                .findByTenantIdAndUserIdAndAssignedViaDepartmentAndIsActiveTrue(tenantId, userId, departmentId);

        if (assignments.isEmpty()) {
            return;
        }

        // Collect drive IDs for batch cache invalidation
        List<String> driveIds = new ArrayList<>(assignments.size());

        // Update all assignments in memory
        Instant now = Instant.now();
        for (DriveAssignment assignment : assignments) {
            assignment.setIsActive(false);
            assignment.setUpdatedAt(now);
            driveIds.add(assignment.getDriveId());
        }

        // Batch save all updates
        assignmentRepository.saveAll(assignments);

        // Batch invalidate cache
        cacheService.invalidateUserOnDrives(tenantId, userId, driveIds);

        log.info("Removed {} department-based assignments for user {}", assignments.size(), userId);
    }

    /**
     * Warm cache for a user (call on login).
     */
    public void warmUserCache(String userId) {
        String tenantId = TenantContext.getTenantId();

        List<DriveAssignment> assignments = assignmentRepository
                .findByTenantIdAndUserIdAndIsActiveTrue(tenantId, userId);

        List<CachedPermission> cached = assignments.stream()
                .filter(DriveAssignment::isValid)
                .map(a -> CachedPermission.fromAssignment(
                        tenantId,
                        userId,
                        a.getDriveId(),
                        a.getRoleId(),
                        a.getRoleName(),
                        a.getPermissions(),
                        a.getSource() == AssignmentSource.OWNER,
                        a.getExpiresAt()
                ))
                .collect(Collectors.toList());

        cacheService.warmCache(cached);
        log.info("Warmed cache for user {} with {} permissions", userId, cached.size());
    }

    // ============== HELPER METHODS ==============

    private boolean isPersonalDriveOwner(String userId, String driveId) {
        return driveId.equals(Drive.personalDriveId(userId));
    }

    private void ensureSystemRoles(String tenantId) {
        if (roleRepository.findByTenantIdAndIsSystemRoleTrue(tenantId).isEmpty()) {
            List<DriveRole> systemRoles = List.of(
                    DriveRole.createOwnerRole(tenantId),
                    DriveRole.createAdminRole(tenantId),
                    DriveRole.createEditorRole(tenantId),
                    DriveRole.createCommenterRole(tenantId),
                    DriveRole.createViewerRole(tenantId)
            );
            roleRepository.saveAll(systemRoles);
            log.info("Created system roles for tenant {}", tenantId);
        }
    }

    /**
     * Publish event to Kafka asynchronously.
     * Uses CompletableFuture callback to avoid blocking the request thread.
     */
    private void publishEvent(String eventType, Map<String, Object> data) {
        try {
            Map<String, Object> event = new HashMap<>(data);
            event.put("eventType", eventType);
            event.put("tenantId", TenantContext.getTenantId());
            event.put("timestamp", Instant.now().toString());

            // Async send with callback - doesn't block the request thread
            kafkaTemplate.send(PERMISSION_EVENTS_TOPIC, event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish permission event: {} - {}", eventType, ex.getMessage());
                        } else {
                            log.debug("Published permission event: {} to partition {}",
                                    eventType, result.getRecordMetadata().partition());
                        }
                    });
        } catch (Exception e) {
            log.error("Error preparing permission event: {}", eventType, e);
        }
    }

    private DriveDTO mapToDriveDTO(Drive drive) {
        Double usagePercent = null;
        if (drive.getQuotaBytes() != null && drive.getQuotaBytes() > 0) {
            usagePercent = (drive.getUsedBytes() * 100.0) / drive.getQuotaBytes();
        }

        return DriveDTO.builder()
                .id(drive.getId())
                .tenantId(drive.getTenantId())
                .name(drive.getName())
                .description(drive.getDescription())
                .type(drive.getType())
                .ownerId(drive.getOwnerId())
                .departmentId(drive.getDepartmentId())
                .quotaBytes(drive.getQuotaBytes())
                .usedBytes(drive.getUsedBytes())
                .usagePercent(usagePercent)
                .defaultRoleId(drive.getDefaultRoleId())
                .status(drive.getStatus())
                .settings(drive.getSettings())
                .createdAt(drive.getCreatedAt())
                .updatedAt(drive.getUpdatedAt())
                .createdBy(drive.getCreatedBy())
                .build();
    }

    private DriveAssignmentDTO mapToAssignmentDTO(DriveAssignment assignment) {
        return DriveAssignmentDTO.builder()
                .id(assignment.getId())
                .tenantId(assignment.getTenantId())
                .driveId(assignment.getDriveId())
                .userId(assignment.getUserId())
                .roleId(assignment.getRoleId())
                .roleName(assignment.getRoleName())
                .permissions(assignment.getPermissions())
                .source(assignment.getSource())
                .assignedViaDepartment(assignment.getAssignedViaDepartment())
                .assignedViaTeam(assignment.getAssignedViaTeam())
                .grantedBy(assignment.getGrantedBy())
                .grantedAt(assignment.getGrantedAt())
                .expiresAt(assignment.getExpiresAt())
                .isActive(assignment.getIsActive())
                .build();
    }

    private DriveRoleDTO mapToRoleDTO(DriveRole role) {
        return DriveRoleDTO.builder()
                .id(role.getId())
                .tenantId(role.getTenantId())
                .driveId(role.getDriveId())
                .name(role.getName())
                .description(role.getDescription())
                .permissions(role.getPermissions())
                .isSystemRole(role.getIsSystemRole())
                .priority(role.getPriority())
                .createdAt(role.getCreatedAt())
                .updatedAt(role.getUpdatedAt())
                .createdBy(role.getCreatedBy())
                .build();
    }

    // ============== SECURITY VALIDATION METHODS (Round 7) ==============

    /**
     * Check if a role belongs to the specified tenant.
     * SECURITY (Round 7): Used to prevent cross-tenant role manipulation attacks.
     *
     * @param roleId   the role ID to check
     * @param tenantId the expected tenant ID
     * @return true if the role exists and belongs to the tenant
     */
    public boolean isRoleInTenant(String roleId, String tenantId) {
        if (roleId == null || tenantId == null) {
            return false;
        }
        return roleRepository.existsByIdAndTenantId(roleId, tenantId);
    }

    /**
     * Check if a department belongs to the specified tenant.
     * SECURITY (Round 7): Used to prevent cross-organization department assignment attacks.
     *
     * This validates by checking if any drive exists for the department in the tenant.
     * In a full implementation, this would query the Department Service via AccessArc.
     *
     * @param departmentId the department ID to check
     * @param tenantId     the expected tenant ID
     * @return true if the department belongs to the tenant
     */
    public boolean isDepartmentInTenant(String departmentId, String tenantId) {
        if (departmentId == null || tenantId == null) {
            return false;
        }
        // Check if any drive exists with this department in this tenant
        // This is a proxy check - in production, call AccessArc Department Service
        return driveRepository.existsByTenantIdAndDepartmentId(tenantId, departmentId);
    }

    /**
     * SECURITY FIX (Round 12): Check if a tenant exists in the system.
     * Used by Kafka event listeners to prevent cross-tenant attacks via event injection.
     *
     * @param tenantId The tenant ID to validate
     * @return true if the tenant exists
     */
    public boolean tenantExists(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return false;
        }
        // Check if any drives exist for this tenant
        // In production, this would also check the Tenant Service via AccessArc
        return driveRepository.existsByTenantId(tenantId);
    }

    /**
     * SECURITY FIX (Round 12): Archive department drive when department is deleted.
     * This prevents orphaned shares and permissions from accumulating.
     *
     * When a department is deleted:
     * 1. Mark the department drive as ARCHIVED
     * 2. Remove all user assignments to the drive
     * 3. Invalidate all permission caches for affected users
     * 4. Publish event for other services to clean up shares
     *
     * @param tenantId The tenant ID
     * @param departmentId The department ID being deleted
     */
    @Transactional
    public void archiveDepartmentDrive(String tenantId, String departmentId) {
        log.info("Archiving department drive: tenantId={}, departmentId={}", tenantId, departmentId);

        String driveId = "dept-" + departmentId;

        // Find the department drive
        Optional<Drive> driveOpt = driveRepository.findByIdAndTenantId(driveId, tenantId);
        if (driveOpt.isEmpty()) {
            log.warn("Department drive not found for archival: {}", driveId);
            return;
        }

        Drive drive = driveOpt.get();

        // Get all users assigned to this drive (for cache invalidation)
        List<DriveAssignment> assignments = assignmentRepository.findByTenantIdAndDriveId(tenantId, driveId);
        List<String> affectedUserIds = assignments.stream()
                .map(DriveAssignment::getUserId)
                .distinct()
                .toList();

        // Mark drive as archived
        drive.setStatus(Drive.DriveStatus.ARCHIVED);
        drive.setUpdatedAt(Instant.now());
        driveRepository.save(drive);

        // Remove all assignments
        assignmentRepository.deleteByTenantIdAndDriveId(tenantId, driveId);

        // Invalidate caches for all affected users
        for (String userId : affectedUserIds) {
            cacheService.delete(tenantId, userId, driveId);
            invalidateAdminCache(userId);
        }

        // Publish event for sharing service to clean up shares
        Map<String, Object> archiveEvent = Map.of(
                "eventType", "DEPARTMENT_DRIVE_ARCHIVED",
                "tenantId", tenantId,
                "driveId", driveId,
                "departmentId", departmentId,
                "affectedUserIds", affectedUserIds,
                "timestamp", Instant.now().toString()
        );
        kafkaTemplate.send(PERMISSION_EVENTS_TOPIC, driveId, archiveEvent);

        log.info("Department drive archived successfully: driveId={}, affectedUsers={}",
                driveId, affectedUserIds.size());
    }

    // ============== TEAM DRIVE MANAGEMENT ==============

    /**
     * Create a team drive when a new team is created.
     * The team owner gets OWNER role automatically.
     *
     * @param tenantId The tenant ID
     * @param teamId The team ID
     * @param teamName The team name (used for drive name)
     * @param ownerId The team owner's user ID
     * @param quotaBytes Optional quota limit for the team drive
     * @return The created drive DTO
     */
    @Transactional
    public DriveDTO createTeamDrive(String tenantId, String teamId, String teamName, String ownerId, Long quotaBytes) {
        log.info("Creating team drive: tenantId={}, teamId={}, owner={}", tenantId, teamId, ownerId);

        String driveId = Drive.teamDriveId(teamId);

        // Check if drive already exists
        if (driveRepository.existsByIdAndTenantId(driveId, tenantId)) {
            log.warn("Team drive already exists: {}", driveId);
            return getDrive(driveId);
        }

        // Ensure system roles exist for tenant
        ensureSystemRoles(tenantId);

        // Get owner role
        DriveRole ownerRole = roleRepository
                .findByTenantIdAndNameAndDriveIdIsNull(tenantId, DriveRole.ROLE_OWNER)
                .orElseThrow(() -> new IllegalStateException("Owner role not found"));

        // Create team drive
        Drive drive = Drive.builder()
                .id(driveId)
                .tenantId(tenantId)
                .name(teamName + " Drive")
                .description("Shared drive for team: " + teamName)
                .type(DriveType.TEAM)
                .teamId(teamId)
                .quotaBytes(quotaBytes)
                .usedBytes(0L)
                .defaultRoleId(ownerRole.getId()) // Default to owner role for now
                .status(Drive.DriveStatus.ACTIVE)
                .settings(new Drive.DriveSettings())
                .createdAt(Instant.now())
                .createdBy(ownerId)
                .build();

        drive = driveRepository.save(drive);

        // Create owner assignment
        DriveAssignment ownerAssignment = DriveAssignment.createOwnerAssignment(
                tenantId,
                driveId,
                ownerId,
                ownerRole.getId(),
                ownerRole.getPermissions()
        );
        ownerAssignment.setAssignedViaTeam(teamId);
        assignmentRepository.save(ownerAssignment);

        log.info("Created team drive: {} for team {} with owner {}", driveId, teamId, ownerId);

        // Publish event
        publishEvent("team.drive.created", Map.of(
                "driveId", driveId,
                "teamId", teamId,
                "ownerId", ownerId
        ));

        return mapToDriveDTO(drive);
    }

    /**
     * Grant a team member access to the team drive.
     * Called when a member is added to a team.
     *
     * @param tenantId The tenant ID
     * @param teamId The team ID
     * @param userId The user ID to grant access to
     * @param roleId The role to assign (from team's role system)
     * @param roleName The role name for denormalization
     * @param permissions The permissions for the role
     */
    @Transactional
    public void grantTeamMemberAccess(String tenantId, String teamId, String userId,
                                      String roleId, String roleName, Set<Permission> permissions) {
        log.info("Granting team drive access: tenantId={}, teamId={}, userId={}, role={}",
                tenantId, teamId, userId, roleName);

        String driveId = Drive.teamDriveId(teamId);

        // Verify drive exists
        if (!driveRepository.existsByIdAndTenantId(driveId, tenantId)) {
            log.error("Team drive not found: {}", driveId);
            throw new ResourceNotFoundException("Team drive not found: " + driveId);
        }

        // Check if assignment already exists
        Optional<DriveAssignment> existing = assignmentRepository
                .findByTenantIdAndUserIdAndDriveId(tenantId, userId, driveId);

        DriveAssignment assignment;
        if (existing.isPresent()) {
            // Update existing assignment
            assignment = existing.get();
            assignment.setRoleId(roleId);
            assignment.setRoleName(roleName);
            assignment.setPermissions(permissions);
            assignment.setIsActive(true);
            assignment.setUpdatedAt(Instant.now());
        } else {
            // Create new assignment
            assignment = DriveAssignment.builder()
                    .tenantId(tenantId)
                    .driveId(driveId)
                    .userId(userId)
                    .roleId(roleId)
                    .roleName(roleName)
                    .permissions(permissions)
                    .source(AssignmentSource.TEAM)
                    .assignedViaTeam(teamId)
                    .grantedBy("system")
                    .grantedAt(Instant.now())
                    .isActive(true)
                    .build();
        }

        assignmentRepository.save(assignment);

        // Invalidate cache
        cacheService.delete(tenantId, userId, driveId);

        log.info("Granted team drive access: {} to {} with role {}", driveId, userId, roleName);
    }

    /**
     * Revoke a team member's access to the team drive.
     * Called when a member is removed from a team.
     *
     * @param tenantId The tenant ID
     * @param teamId The team ID
     * @param userId The user ID to revoke access from
     */
    @Transactional
    public void revokeTeamMemberAccess(String tenantId, String teamId, String userId) {
        log.info("Revoking team drive access: tenantId={}, teamId={}, userId={}", tenantId, teamId, userId);

        String driveId = Drive.teamDriveId(teamId);

        // Find and deactivate assignment
        Optional<DriveAssignment> assignment = assignmentRepository
                .findByTenantIdAndUserIdAndDriveId(tenantId, userId, driveId);

        if (assignment.isEmpty()) {
            log.warn("No assignment found to revoke: driveId={}, userId={}", driveId, userId);
            return;
        }

        // Don't revoke owner access
        if (assignment.get().getSource() == AssignmentSource.OWNER) {
            log.warn("Cannot revoke owner access from team drive: {}", driveId);
            return;
        }

        assignment.get().setIsActive(false);
        assignment.get().setUpdatedAt(Instant.now());
        assignmentRepository.save(assignment.get());

        // Invalidate cache
        cacheService.delete(tenantId, userId, driveId);

        log.info("Revoked team drive access: {} from {}", driveId, userId);
    }

    /**
     * Archive a team drive when a team is deleted or archived.
     *
     * @param tenantId The tenant ID
     * @param teamId The team ID
     */
    @Transactional
    public void archiveTeamDrive(String tenantId, String teamId) {
        log.info("Archiving team drive: tenantId={}, teamId={}", tenantId, teamId);

        String driveId = Drive.teamDriveId(teamId);

        // Find the team drive
        Optional<Drive> driveOpt = driveRepository.findByIdAndTenantId(driveId, tenantId);
        if (driveOpt.isEmpty()) {
            log.warn("Team drive not found for archival: {}", driveId);
            return;
        }

        Drive drive = driveOpt.get();

        // Get all users assigned to this drive (for cache invalidation)
        List<DriveAssignment> assignments = assignmentRepository.findByTenantIdAndDriveId(tenantId, driveId);
        List<String> affectedUserIds = assignments.stream()
                .map(DriveAssignment::getUserId)
                .distinct()
                .toList();

        // Mark drive as archived
        drive.setStatus(Drive.DriveStatus.ARCHIVED);
        drive.setUpdatedAt(Instant.now());
        driveRepository.save(drive);

        // Deactivate all assignments (don't delete - keep for audit)
        for (DriveAssignment assignment : assignments) {
            assignment.setIsActive(false);
            assignment.setUpdatedAt(Instant.now());
        }
        assignmentRepository.saveAll(assignments);

        // Invalidate caches for all affected users
        for (String userId : affectedUserIds) {
            cacheService.delete(tenantId, userId, driveId);
        }

        // Publish event
        publishEvent("team.drive.archived", Map.of(
                "driveId", driveId,
                "teamId", teamId,
                "affectedUserIds", affectedUserIds
        ));

        log.info("Team drive archived successfully: driveId={}, affectedUsers={}",
                driveId, affectedUserIds.size());
    }

    /**
     * Check if a team exists in the given tenant.
     * Uses the team drive as a proxy for team existence.
     *
     * @param teamId The team ID
     * @param tenantId The tenant ID
     * @return true if the team exists
     */
    public boolean isTeamInTenant(String teamId, String tenantId) {
        if (teamId == null || tenantId == null) {
            return false;
        }
        String driveId = Drive.teamDriveId(teamId);
        return driveRepository.existsByIdAndTenantId(driveId, tenantId);
    }
}
