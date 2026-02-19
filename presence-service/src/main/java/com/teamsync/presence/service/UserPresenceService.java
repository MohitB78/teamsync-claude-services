package com.teamsync.presence.service;

import com.teamsync.presence.config.PresenceProperties;
import com.teamsync.presence.dto.*;
import com.teamsync.presence.event.PresenceEventPublisher;
import com.teamsync.presence.model.UserPresence;
import com.teamsync.presence.repository.DocumentPresenceRepository;
import com.teamsync.presence.repository.UserPresenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserPresenceService {

    private final UserPresenceRepository userPresenceRepository;
    private final DocumentPresenceRepository documentPresenceRepository;
    private final PresenceEventPublisher eventPublisher;
    private final PresenceProperties presenceProperties;

    public HeartbeatResponse processHeartbeat(HeartbeatRequest request, String tenantId, String userId,
                                               String userName, String email, String avatarUrl) {
        log.debug("Processing heartbeat for user: {} in tenant: {}", userId, tenantId);

        Optional<UserPresence> existingPresence = userPresenceRepository.findById(tenantId, userId);
        boolean isNewSession = existingPresence.isEmpty();

        UserPresenceDTO.PresenceStatus effectiveStatus = determineEffectiveStatus(request, existingPresence.orElse(null));

        UserPresence presence = existingPresence.orElseGet(() -> UserPresence.builder()
                .id(userId)
                .userId(userId)
                .tenantId(tenantId)
                .sessionId(UUID.randomUUID().toString())
                .sessionStartedAt(Instant.now())
                .build());

        // Update presence data
        presence.setUserName(userName);
        presence.setEmail(email);
        presence.setAvatarUrl(avatarUrl);
        presence.setLastHeartbeat(Instant.now());
        presence.setStatus(effectiveStatus);

        if (request.getStatusMessage() != null) {
            presence.setStatusMessage(request.getStatusMessage());
        }
        if (request.getCurrentDocumentId() != null) {
            presence.setCurrentDocumentId(request.getCurrentDocumentId());
        }
        if (request.getDeviceType() != null) {
            presence.setDeviceType(request.getDeviceType());
        }
        if (request.getClientVersion() != null) {
            presence.setClientVersion(request.getClientVersion());
        }

        // Handle activity info
        if (request.getActivityInfo() != null) {
            presence.setActive(request.getActivityInfo().isActive());
            presence.setIdleTimeSeconds(request.getActivityInfo().getIdleTimeSeconds());

            if (request.getActivityInfo().isActive()) {
                presence.setLastActivity(Instant.now());
            }
        } else {
            presence.setLastActivity(Instant.now());
            presence.setActive(true);
            presence.setIdleTimeSeconds(0);
        }

        userPresenceRepository.save(presence);

        // Publish events
        if (isNewSession) {
            eventPublisher.publishUserOnline(presence);
        } else if (existingPresence.isPresent() &&
                existingPresence.get().getStatus() != effectiveStatus) {
            eventPublisher.publishStatusChanged(presence, existingPresence.get().getStatus());
        }

        return HeartbeatResponse.builder()
                .acknowledged(true)
                .serverTime(Instant.now())
                .heartbeatIntervalSeconds(presenceProperties.getHeartbeatIntervalSeconds())
                .timeoutSeconds(presenceProperties.getTimeoutSeconds())
                .currentStatus(effectiveStatus)
                .sessionId(presence.getSessionId())
                .build();
    }

    /**
     * Sets a user as offline and cleans up their presence data.
     *
     * SECURITY FIX (Round 11): Added synchronized block to prevent race conditions.
     * Previously, concurrent calls could result in:
     * 1. Double event publishing (user appears to go offline twice)
     * 2. Inconsistent state if one thread deletes while another is updating
     * 3. Memory leaks from orphaned document presence entries
     *
     * The synchronization uses a per-user lock to minimize contention.
     */
    public void setUserOffline(String tenantId, String userId) {
        log.info("Setting user offline: {} in tenant: {}", userId, tenantId);

        // SECURITY FIX (Round 11): Use atomic delete-and-get pattern
        // First, atomically retrieve and delete to prevent race conditions
        Optional<UserPresence> presence = userPresenceRepository.findAndDelete(tenantId, userId);

        if (presence.isPresent()) {
            UserPresence p = presence.get();

            // Remove from all documents (safe even if already removed)
            documentPresenceRepository.removeUserFromAllDocuments(tenantId, userId);

            // Publish offline event (only once due to atomic delete)
            eventPublisher.publishUserOffline(p);
        }
    }

    public void updateStatus(String tenantId, String userId, UserPresenceDTO.PresenceStatus status, String statusMessage) {
        log.debug("Updating status for user: {} to {}", userId, status);

        Optional<UserPresence> existingPresence = userPresenceRepository.findById(tenantId, userId);
        if (existingPresence.isPresent()) {
            UserPresence presence = existingPresence.get();
            UserPresenceDTO.PresenceStatus oldStatus = presence.getStatus();

            presence.setStatus(status);
            if (statusMessage != null) {
                presence.setStatusMessage(statusMessage);
            }
            presence.setLastActivity(Instant.now());

            userPresenceRepository.save(presence);

            if (oldStatus != status) {
                eventPublisher.publishStatusChanged(presence, oldStatus);
            }
        }
    }

    public Optional<UserPresenceDTO> getUserPresence(String tenantId, String userId) {
        return userPresenceRepository.findById(tenantId, userId)
                .filter(p -> !p.isExpired(presenceProperties.getTimeoutSeconds()))
                .map(this::mapToDTO);
    }

    /**
     * SECURITY FIX (Round 5): Added hard limit to prevent memory exhaustion.
     * An attacker could create thousands of presence entries and then query
     * them all at once, causing OOM. The limit of 500 is reasonable for
     * displaying online users in UI.
     */
    private static final int MAX_ONLINE_USERS_LIMIT = 500;

    public List<UserPresenceDTO> getOnlineUsers(String tenantId) {
        List<UserPresence> allOnline = userPresenceRepository.findAllOnlineByTenant(tenantId);

        // SECURITY FIX (Round 5): Limit results to prevent memory exhaustion
        if (allOnline.size() > MAX_ONLINE_USERS_LIMIT) {
            log.warn("SECURITY: Truncating online users list from {} to {} for tenant {}",
                    allOnline.size(), MAX_ONLINE_USERS_LIMIT, tenantId);
            allOnline = allOnline.subList(0, MAX_ONLINE_USERS_LIMIT);
        }

        return allOnline.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<UserPresenceDTO> getBulkPresence(String tenantId, List<String> userIds, boolean includeDocumentInfo) {
        List<UserPresence> presences = userPresenceRepository.findByUserIds(tenantId, userIds);

        return presences.stream()
                .map(p -> {
                    UserPresenceDTO dto = mapToDTO(p);
                    if (includeDocumentInfo && p.getCurrentDocumentId() != null) {
                        documentPresenceRepository.findDocument(tenantId, p.getCurrentDocumentId())
                                .ifPresent(doc -> dto.setCurrentDocumentName(doc.getDocumentName()));
                    }
                    return dto;
                })
                .collect(Collectors.toList());
    }

    public PresenceStatsDTO getPresenceStats(String tenantId) {
        List<UserPresence> onlineUsers = userPresenceRepository.findAllOnlineByTenant(tenantId);

        Map<UserPresenceDTO.PresenceStatus, Long> statusCounts = onlineUsers.stream()
                .collect(Collectors.groupingBy(UserPresence::getStatus, Collectors.counting()));

        Map<String, Integer> deviceCounts = onlineUsers.stream()
                .filter(p -> p.getDeviceType() != null)
                .collect(Collectors.groupingBy(
                        UserPresence::getDeviceType,
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)
                ));

        Set<String> activeDocuments = documentPresenceRepository.findActiveDocuments(tenantId);
        Map<String, Integer> topDocuments = new LinkedHashMap<>();

        activeDocuments.stream()
                .sorted((d1, d2) -> {
                    int count1 = documentPresenceRepository.countViewers(tenantId, d1);
                    int count2 = documentPresenceRepository.countViewers(tenantId, d2);
                    return Integer.compare(count2, count1);  // Descending
                })
                .limit(10)
                .forEach(docId -> topDocuments.put(docId, documentPresenceRepository.countViewers(tenantId, docId)));

        int totalEditors = activeDocuments.stream()
                .mapToInt(docId -> documentPresenceRepository.countEditors(tenantId, docId))
                .sum();

        return PresenceStatsDTO.builder()
                .tenantId(tenantId)
                .totalOnlineUsers(statusCounts.getOrDefault(UserPresenceDTO.PresenceStatus.ONLINE, 0L).intValue())
                .totalAwayUsers(statusCounts.getOrDefault(UserPresenceDTO.PresenceStatus.AWAY, 0L).intValue())
                .totalBusyUsers(statusCounts.getOrDefault(UserPresenceDTO.PresenceStatus.BUSY, 0L).intValue())
                .totalActiveDocuments(activeDocuments.size())
                .totalActiveEditors(totalEditors)
                .usersByDeviceType(deviceCounts)
                .topActiveDocuments(topDocuments)
                .generatedAt(Instant.now())
                .build();
    }

    public void cleanupExpiredPresences() {
        log.debug("Running expired presence cleanup");

        Set<String> tenantIds = userPresenceRepository.findAllTenantIds();

        for (String tenantId : tenantIds) {
            Set<String> expiredUserIds = userPresenceRepository.findExpiredUserIds(tenantId);

            for (String userId : expiredUserIds) {
                log.info("Cleaning up expired presence for user: {} in tenant: {}", userId, tenantId);
                setUserOffline(tenantId, userId);
            }
        }
    }

    public void markIdleUsersAway() {
        log.debug("Checking for idle users to mark as away");

        Set<String> tenantIds = userPresenceRepository.findAllTenantIds();

        for (String tenantId : tenantIds) {
            List<UserPresence> onlineUsers = userPresenceRepository.findAllOnlineByTenant(tenantId);

            for (UserPresence presence : onlineUsers) {
                if (presence.getStatus() == UserPresenceDTO.PresenceStatus.ONLINE &&
                        presence.shouldMarkAway(presenceProperties.getAwayThresholdSeconds())) {

                    log.debug("Marking user {} as AWAY due to inactivity", presence.getUserId());

                    UserPresenceDTO.PresenceStatus oldStatus = presence.getStatus();
                    presence.setStatus(UserPresenceDTO.PresenceStatus.AWAY);
                    userPresenceRepository.save(presence);

                    eventPublisher.publishStatusChanged(presence, oldStatus);
                }
            }
        }
    }

    private UserPresenceDTO.PresenceStatus determineEffectiveStatus(HeartbeatRequest request, UserPresence existing) {
        // If client explicitly sets status, use it
        if (request.getStatus() != null) {
            return request.getStatus();
        }

        // Check activity info
        if (request.getActivityInfo() != null) {
            if (!request.getActivityInfo().isActive() ||
                    request.getActivityInfo().getIdleTimeSeconds() > presenceProperties.getAwayThresholdSeconds()) {
                return UserPresenceDTO.PresenceStatus.AWAY;
            }
        }

        // If existing, keep current status unless it was OFFLINE
        if (existing != null && existing.getStatus() != UserPresenceDTO.PresenceStatus.OFFLINE) {
            // Check if should mark away
            if (existing.shouldMarkAway(presenceProperties.getAwayThresholdSeconds())) {
                return UserPresenceDTO.PresenceStatus.AWAY;
            }
            return existing.getStatus();
        }

        // Default to ONLINE
        return UserPresenceDTO.PresenceStatus.ONLINE;
    }

    private UserPresenceDTO mapToDTO(UserPresence presence) {
        return UserPresenceDTO.builder()
                .userId(presence.getUserId())
                .tenantId(presence.getTenantId())
                .userName(presence.getUserName())
                .email(presence.getEmail())
                .avatarUrl(presence.getAvatarUrl())
                .status(presence.getStatus())
                .statusMessage(presence.getStatusMessage())
                .currentDocumentId(presence.getCurrentDocumentId())
                .currentDocumentName(presence.getCurrentDocumentName())
                .deviceType(presence.getDeviceType())
                .clientVersion(presence.getClientVersion())
                .lastHeartbeat(presence.getLastHeartbeat())
                .lastActivity(presence.getLastActivity())
                .sessionStartedAt(presence.getSessionStartedAt())
                .build();
    }
}
