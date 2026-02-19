package com.teamsync.presence.repository;

import com.teamsync.presence.config.PresenceProperties;
import com.teamsync.presence.dto.UserPresenceDTO;
import com.teamsync.presence.model.UserPresence;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
@Slf4j
public class UserPresenceRepository {

    private final RedisTemplate<String, Object> redisTemplate;
    private final PresenceProperties presenceProperties;

    public void save(UserPresence presence) {
        String key = UserPresence.buildRedisKey(presence.getTenantId(), presence.getUserId());
        Duration ttl = Duration.ofSeconds(presenceProperties.getTimeoutSeconds() * 2L);

        redisTemplate.opsForValue().set(key, presence, ttl);

        // Add to online sets
        if (presence.getStatus() != UserPresenceDTO.PresenceStatus.OFFLINE) {
            String tenantSetKey = UserPresence.buildTenantOnlineSetKey(presence.getTenantId());
            String globalSetKey = UserPresence.buildGlobalOnlineSetKey();

            redisTemplate.opsForSet().add(tenantSetKey, presence.getUserId());
            redisTemplate.opsForSet().add(globalSetKey, presence.getTenantId() + ":" + presence.getUserId());

            redisTemplate.expire(tenantSetKey, Duration.ofHours(24));
            redisTemplate.expire(globalSetKey, Duration.ofHours(24));
        }

        log.debug("Saved presence for user: {} in tenant: {}", presence.getUserId(), presence.getTenantId());
    }

    public Optional<UserPresence> findById(String tenantId, String userId) {
        String key = UserPresence.buildRedisKey(tenantId, userId);
        Object result = redisTemplate.opsForValue().get(key);

        if (result instanceof UserPresence) {
            return Optional.of((UserPresence) result);
        }
        return Optional.empty();
    }

    /**
     * Maximum number of online users to return per tenant to prevent memory exhaustion.
     * SECURITY FIX (Round 14 #M5): Added limit to prevent DoS.
     */
    private static final int MAX_ONLINE_USERS_PER_TENANT = 1000;

    /**
     * Find all online users for a tenant.
     * SECURITY FIX (Round 14 #M5): Changed from N+1 query pattern to batch multiGet
     * and added limit to prevent memory exhaustion from very large tenants.
     */
    public List<UserPresence> findAllOnlineByTenant(String tenantId) {
        String setKey = UserPresence.buildTenantOnlineSetKey(tenantId);
        Set<Object> userIds = redisTemplate.opsForSet().members(setKey);

        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }

        // SECURITY: Limit to prevent memory exhaustion from very large sets
        List<String> userIdList = userIds.stream()
                .map(Object::toString)
                .limit(MAX_ONLINE_USERS_PER_TENANT)
                .toList();

        if (userIdList.size() < userIds.size()) {
            log.warn("SECURITY: Tenant {} has {} online users, limited to {}",
                    tenantId, userIds.size(), MAX_ONLINE_USERS_PER_TENANT);
        }

        // SECURITY FIX: Use multiGet instead of N individual calls
        List<String> keys = userIdList.stream()
                .map(userId -> UserPresence.buildRedisKey(tenantId, userId))
                .toList();

        List<Object> results = redisTemplate.opsForValue().multiGet(keys);

        if (results == null) {
            return Collections.emptyList();
        }

        // Filter expired entries and schedule cleanup
        List<UserPresence> validPresences = new ArrayList<>();
        List<String> expiredUserIds = new ArrayList<>();

        for (int i = 0; i < results.size(); i++) {
            Object result = results.get(i);
            if (result instanceof UserPresence presence) {
                if (!presence.isExpired(presenceProperties.getTimeoutSeconds())) {
                    validPresences.add(presence);
                } else {
                    expiredUserIds.add(userIdList.get(i));
                }
            } else {
                // Orphaned set entry - presence key doesn't exist
                expiredUserIds.add(userIdList.get(i));
            }
        }

        // Clean up expired entries asynchronously
        if (!expiredUserIds.isEmpty()) {
            for (String userId : expiredUserIds) {
                removeFromOnlineSets(tenantId, userId);
            }
        }

        return validPresences;
    }

    public List<UserPresence> findByUserIds(String tenantId, List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> keys = userIds.stream()
                .map(userId -> UserPresence.buildRedisKey(tenantId, userId))
                .collect(Collectors.toList());

        List<Object> results = redisTemplate.opsForValue().multiGet(keys);

        if (results == null) {
            return Collections.emptyList();
        }

        return results.stream()
                .filter(Objects::nonNull)
                .filter(obj -> obj instanceof UserPresence)
                .map(obj -> (UserPresence) obj)
                .filter(p -> !p.isExpired(presenceProperties.getTimeoutSeconds()))
                .collect(Collectors.toList());
    }

    public void delete(String tenantId, String userId) {
        String key = UserPresence.buildRedisKey(tenantId, userId);
        redisTemplate.delete(key);
        removeFromOnlineSets(tenantId, userId);
        log.debug("Deleted presence for user: {} in tenant: {}", userId, tenantId);
    }

    /**
     * SECURITY FIX (Round 11): Atomic find-and-delete to prevent race conditions.
     * This ensures that if two threads call setUserOffline simultaneously,
     * only one will get the presence object and publish the offline event.
     */
    public Optional<UserPresence> findAndDelete(String tenantId, String userId) {
        String key = UserPresence.buildRedisKey(tenantId, userId);

        // Use Redis GETDEL for atomic get-and-delete (Redis 6.2+)
        // Falls back to GET + DELETE for older Redis versions
        Object result = redisTemplate.opsForValue().getAndDelete(key);

        if (result instanceof UserPresence presence) {
            // Clean up online sets after successful deletion
            removeFromOnlineSets(tenantId, userId);
            log.debug("Atomically deleted presence for user: {} in tenant: {}", userId, tenantId);
            return Optional.of(presence);
        }

        // Even if presence not found, clean up sets to prevent orphaned entries
        removeFromOnlineSets(tenantId, userId);
        return Optional.empty();
    }

    public void updateStatus(String tenantId, String userId, UserPresenceDTO.PresenceStatus status) {
        findById(tenantId, userId).ifPresent(presence -> {
            presence.setStatus(status);
            presence.setLastActivity(Instant.now());
            save(presence);
        });
    }

    public void updateHeartbeat(String tenantId, String userId) {
        findById(tenantId, userId).ifPresent(presence -> {
            presence.setLastHeartbeat(Instant.now());
            save(presence);
        });
    }

    public long countOnlineByTenant(String tenantId) {
        String setKey = UserPresence.buildTenantOnlineSetKey(tenantId);
        Long size = redisTemplate.opsForSet().size(setKey);
        return size != null ? size : 0L;
    }

    public long countTotalOnline() {
        String globalSetKey = UserPresence.buildGlobalOnlineSetKey();
        Long size = redisTemplate.opsForSet().size(globalSetKey);
        return size != null ? size : 0L;
    }

    public Set<String> findExpiredUserIds(String tenantId) {
        String setKey = UserPresence.buildTenantOnlineSetKey(tenantId);
        Set<Object> userIds = redisTemplate.opsForSet().members(setKey);

        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> expired = new HashSet<>();
        for (Object userIdObj : userIds) {
            String userId = userIdObj.toString();
            findById(tenantId, userId).ifPresentOrElse(
                    presence -> {
                        if (presence.isExpired(presenceProperties.getTimeoutSeconds())) {
                            expired.add(userId);
                        }
                    },
                    () -> expired.add(userId)  // Key missing, consider expired
            );
        }

        return expired;
    }

    /**
     * Find all tenant IDs that have online users.
     * SECURITY FIX (Round 14 #M19): Added safe string split with validation.
     * Format stored in Redis: "tenantId:userId"
     */
    public Set<String> findAllTenantIds() {
        String globalSetKey = UserPresence.buildGlobalOnlineSetKey();
        Set<Object> entries = redisTemplate.opsForSet().members(globalSetKey);

        if (entries == null || entries.isEmpty()) {
            return Collections.emptySet();
        }

        return entries.stream()
                .map(Object::toString)
                .map(this::extractTenantIdSafely)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * SECURITY FIX (Round 14 #M19): Safely extract tenant ID from "tenantId:userId" format.
     * Returns null for malformed entries to prevent ArrayIndexOutOfBoundsException.
     */
    private String extractTenantIdSafely(String entry) {
        if (entry == null || entry.isBlank()) {
            return null;
        }
        int colonIndex = entry.indexOf(':');
        if (colonIndex <= 0) {
            log.warn("SECURITY: Malformed presence entry in Redis (missing colon): {}", entry);
            return null;
        }
        return entry.substring(0, colonIndex);
    }

    private void removeFromOnlineSets(String tenantId, String userId) {
        String tenantSetKey = UserPresence.buildTenantOnlineSetKey(tenantId);
        String globalSetKey = UserPresence.buildGlobalOnlineSetKey();

        redisTemplate.opsForSet().remove(tenantSetKey, userId);
        redisTemplate.opsForSet().remove(globalSetKey, tenantId + ":" + userId);
    }
}
