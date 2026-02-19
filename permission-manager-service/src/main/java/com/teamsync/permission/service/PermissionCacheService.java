package com.teamsync.permission.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamsync.permission.dto.CachedPermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Redis-based permission caching service for O(1) permission lookups.
 *
 * Cache Strategy:
 * 1. Cache key format: perm:u:{userId}:d:{driveId} (optimized for efficient scanning)
 * 2. TTL: 5 minutes by default (configurable)
 * 3. Cache-aside pattern with write-through on updates
 * 4. Bulk invalidation on role/assignment changes using cursor-based SCAN
 *
 * Performance optimizations:
 * - Uses SCAN instead of KEYS to avoid blocking Redis
 * - Uses pipelining for bulk operations
 * - Batched deletes to reduce round-trips
 *
 * Performance targets:
 * - Cache hit: < 1ms
 * - Cache miss + DB lookup: < 10ms
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PermissionCacheService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${permission.cache.ttl-minutes:5}")
    private int cacheTtlMinutes;

    @Value("${permission.cache.negative-ttl-minutes:1}")
    private int negativeCacheTtlMinutes;

    @Value("${permission.cache.scan-batch-size:1000}")
    private int scanBatchSize;

    @Value("${permission.cache.delete-batch-size:100}")
    private int deleteBatchSize;

    private static final String CACHE_PREFIX = "perm:";
    private static final String TENANT_PREFIX = "t:";
    private static final String USER_PREFIX = ":u:";
    private static final String DRIVE_PREFIX = ":d:";
    private static final String VERSION_KEY = "perm:version";

    /**
     * Get cached permission for a user+drive combination.
     * O(1) Redis lookup.
     * SECURITY FIX: Added tenantId parameter for tenant isolation.
     */
    public Optional<CachedPermission> get(String tenantId, String userId, String driveId) {
        String key = cacheKey(tenantId, userId, driveId);
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                log.debug("Cache miss for key: {}", key);
                return Optional.empty();
            }

            CachedPermission cached = objectMapper.readValue(json, CachedPermission.class);

            // Check if cached entry is still valid (not expired)
            if (!cached.isValid()) {
                log.debug("Cached entry expired for key: {}", key);
                delete(tenantId, userId, driveId);
                return Optional.empty();
            }

            log.debug("Cache hit for key: {}", key);
            return Optional.of(cached);

        } catch (JsonProcessingException e) {
            log.error("Error deserializing cached permission for key: {}", key, e);
            delete(tenantId, userId, driveId);
            return Optional.empty();
        }
    }

    /**
     * Cache a permission entry.
     * Uses different TTL for positive vs negative (no access) entries.
     * SECURITY FIX: Now uses tenantId from CachedPermission for tenant-isolated keys.
     */
    public void put(CachedPermission permission) {
        String key = cacheKey(permission.getTenantId(), permission.getUserId(), permission.getDriveId());
        try {
            String json = objectMapper.writeValueAsString(permission);

            // Use shorter TTL for "no access" entries (negative caching)
            int ttl = permission.isHasAccess() ? cacheTtlMinutes : negativeCacheTtlMinutes;

            redisTemplate.opsForValue().set(key, json, Duration.ofMinutes(ttl));
            log.debug("Cached permission for key: {} with TTL: {} minutes", key, ttl);

        } catch (JsonProcessingException e) {
            log.error("Error serializing permission for cache: {}", key, e);
        }
    }

    /**
     * Delete a cached permission entry.
     * SECURITY FIX: Added tenantId parameter for tenant isolation.
     */
    public void delete(String tenantId, String userId, String driveId) {
        String key = cacheKey(tenantId, userId, driveId);
        Boolean deleted = redisTemplate.delete(key);
        log.debug("Deleted cache entry for key: {}, success: {}", key, deleted);
    }

    /**
     * Invalidate all cached permissions for a user within a tenant.
     * Called when user's role or department changes.
     * Uses cursor-based SCAN to avoid blocking Redis.
     * SECURITY FIX: Added tenantId parameter for tenant isolation.
     */
    public void invalidateUser(String tenantId, String userId) {
        // Pattern: perm:t:{tenantId}:u:{userId}:d:* - efficient prefix scan within tenant
        String pattern = CACHE_PREFIX + TENANT_PREFIX + tenantId + USER_PREFIX + userId + DRIVE_PREFIX + "*";
        long deleted = deleteByPattern(pattern);
        if (deleted > 0) {
            log.info("Invalidated {} cache entries for user: {} in tenant: {}", deleted, userId, tenantId);
        }
    }

    /**
     * Invalidate all cached permissions for a drive within a tenant.
     * Called when drive permissions or roles change.
     * Uses cursor-based SCAN to avoid blocking Redis.
     * SECURITY FIX: Added tenantId parameter for tenant isolation.
     *
     * Note: This scans all keys matching *:d:{driveId} within the tenant which is less efficient
     * than user-based invalidation. For large-scale drive invalidations,
     * consider using invalidateUsersOnDrive() with a known user list.
     */
    public void invalidateDrive(String tenantId, String driveId) {
        // Pattern: perm:t:{tenantId}:u:*:d:{driveId} - scans within tenant only
        String pattern = CACHE_PREFIX + TENANT_PREFIX + tenantId + USER_PREFIX + "*" + DRIVE_PREFIX + driveId;
        long deleted = deleteByPattern(pattern);
        if (deleted > 0) {
            log.info("Invalidated {} cache entries for drive: {} in tenant: {}", deleted, driveId, tenantId);
        }
    }

    /**
     * Invalidate cached permissions for multiple users on a drive within a tenant.
     * Called when role permissions change.
     * Uses batched deletes for efficiency.
     * SECURITY FIX: Added tenantId parameter for tenant isolation.
     *
     * PREFERRED: This is more efficient than invalidateDrive() because
     * it doesn't require scanning - it builds keys directly.
     */
    public void invalidateUsersOnDrive(String tenantId, List<String> userIds, String driveId) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }

        List<String> keys = new ArrayList<>(userIds.size());
        for (String userId : userIds) {
            keys.add(cacheKey(tenantId, userId, driveId));
        }

        long deleted = deleteKeysBatched(keys);
        if (deleted > 0) {
            log.info("Invalidated {} cache entries for {} users on drive: {} in tenant: {}",
                    deleted, userIds.size(), driveId, tenantId);
        }
    }

    /**
     * Invalidate cached permissions for a user on multiple drives within a tenant.
     * Uses batched deletes for efficiency.
     * SECURITY FIX: Added tenantId parameter for tenant isolation.
     */
    public void invalidateUserOnDrives(String tenantId, String userId, List<String> driveIds) {
        if (driveIds == null || driveIds.isEmpty()) {
            return;
        }

        List<String> keys = new ArrayList<>(driveIds.size());
        for (String driveId : driveIds) {
            keys.add(cacheKey(tenantId, userId, driveId));
        }

        long deleted = deleteKeysBatched(keys);
        if (deleted > 0) {
            log.info("Invalidated {} cache entries for user {} on {} drives in tenant: {}",
                    deleted, userId, driveIds.size(), tenantId);
        }
    }

    /**
     * Invalidate all permission caches (emergency use only).
     * Uses cursor-based SCAN to avoid blocking Redis.
     */
    public void invalidateAll() {
        String pattern = CACHE_PREFIX + "*";
        long deleted = deleteByPattern(pattern);
        log.warn("Invalidated ALL {} permission cache entries", deleted);

        // Increment global version to force refresh
        redisTemplate.opsForValue().increment(VERSION_KEY);
    }

    /**
     * Get cache statistics.
     * Uses cursor-based SCAN to count entries without loading them all.
     */
    public CacheStats getStats() {
        String pattern = CACHE_PREFIX + "*";
        long count = countKeysByPattern(pattern);
        return CacheStats.builder()
                .totalEntries(count)
                .ttlMinutes(cacheTtlMinutes)
                .negativeTtlMinutes(negativeCacheTtlMinutes)
                .build();
    }

    /**
     * Warm the cache for a user by preloading all their drive permissions.
     * Called on login or after bulk permission changes.
     * Uses Redis pipelining for efficient bulk insertion.
     * SECURITY FIX: Now uses tenantId from each CachedPermission for tenant-isolated keys.
     */
    public void warmCache(List<CachedPermission> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return;
        }

        // Use pipelining for bulk operations
        redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            @SuppressWarnings("unchecked")
            public Object execute(RedisOperations operations) throws DataAccessException {
                for (CachedPermission perm : permissions) {
                    String key = cacheKey(perm.getTenantId(), perm.getUserId(), perm.getDriveId());
                    try {
                        String json = objectMapper.writeValueAsString(perm);
                        int ttl = perm.isHasAccess() ? cacheTtlMinutes : negativeCacheTtlMinutes;
                        operations.opsForValue().set(key, json, Duration.ofMinutes(ttl));
                    } catch (JsonProcessingException e) {
                        log.error("Error serializing permission for cache warm: {}", key, e);
                    }
                }
                return null;
            }
        });

        log.info("Warmed cache with {} permission entries using pipelining", permissions.size());
    }

    /**
     * Bulk put multiple permissions using pipelining.
     * More efficient than individual puts for batch operations.
     */
    public void putAll(List<CachedPermission> permissions) {
        warmCache(permissions); // Same implementation
    }

    /**
     * Check if a key exists in cache.
     * SECURITY FIX: Added tenantId parameter for tenant isolation.
     */
    public boolean exists(String tenantId, String userId, String driveId) {
        String key = cacheKey(tenantId, userId, driveId);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * Get remaining TTL for a cache entry.
     * SECURITY FIX: Added tenantId parameter for tenant isolation.
     */
    public Optional<Long> getTtl(String tenantId, String userId, String driveId) {
        String key = cacheKey(tenantId, userId, driveId);
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return ttl != null && ttl > 0 ? Optional.of(ttl) : Optional.empty();
    }

    // Helper methods

    /**
     * Generate cache key in format: perm:t:{tenantId}:u:{userId}:d:{driveId}
     * SECURITY FIX: Added tenantId to prevent cross-tenant cache pollution.
     * Previously the format was perm:u:{userId}:d:{driveId} which could allow
     * cache collisions between users with same ID in different tenants.
     * This format allows efficient prefix scanning by tenant and userId.
     */
    private String cacheKey(String tenantId, String userId, String driveId) {
        return CACHE_PREFIX + TENANT_PREFIX + tenantId + USER_PREFIX + userId + DRIVE_PREFIX + driveId;
    }

    /**
     * Scan keys using cursor-based iteration (non-blocking).
     * Uses SCAN command instead of KEYS to avoid blocking Redis.
     *
     * @param pattern The pattern to match (e.g., "perm:u:user123:d:*")
     * @return Set of matching keys (limited by scanBatchSize for memory safety)
     */
    private Set<String> scanKeysWithCursor(String pattern) {
        Set<String> keys = new HashSet<>();
        ScanOptions scanOptions = ScanOptions.scanOptions()
                .match(pattern)
                .count(scanBatchSize)
                .build();

        try (Cursor<String> cursor = redisTemplate.scan(scanOptions)) {
            while (cursor.hasNext() && keys.size() < scanBatchSize * 10) {
                keys.add(cursor.next());
            }
        } catch (Exception e) {
            log.error("Error scanning keys with pattern: {}", pattern, e);
        }

        return keys;
    }

    /**
     * Delete keys in batches using pipelining.
     * This is much more efficient than individual deletes.
     *
     * @param keys Collection of keys to delete
     * @return Number of keys deleted
     */
    private long deleteKeysBatched(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return 0;
        }

        AtomicLong totalDeleted = new AtomicLong(0);
        List<String> keyList = new ArrayList<>(keys);

        // Process in batches to avoid memory issues
        for (int i = 0; i < keyList.size(); i += deleteBatchSize) {
            int end = Math.min(i + deleteBatchSize, keyList.size());
            List<String> batch = keyList.subList(i, end);

            try {
                Long deleted = redisTemplate.delete(batch);
                if (deleted != null) {
                    totalDeleted.addAndGet(deleted);
                }
            } catch (Exception e) {
                log.error("Error deleting batch of {} keys", batch.size(), e);
            }
        }

        return totalDeleted.get();
    }

    /**
     * Delete keys matching a pattern using cursor-based scan and batched deletes.
     * This is the safe replacement for KEYS + DELETE pattern.
     *
     * @param pattern The pattern to match
     * @return Number of keys deleted
     */
    private long deleteByPattern(String pattern) {
        long totalDeleted = 0;
        ScanOptions scanOptions = ScanOptions.scanOptions()
                .match(pattern)
                .count(scanBatchSize)
                .build();

        List<String> batchToDelete = new ArrayList<>(deleteBatchSize);

        try (Cursor<String> cursor = redisTemplate.scan(scanOptions)) {
            while (cursor.hasNext()) {
                batchToDelete.add(cursor.next());

                // Delete in batches
                if (batchToDelete.size() >= deleteBatchSize) {
                    Long deleted = redisTemplate.delete(batchToDelete);
                    if (deleted != null) {
                        totalDeleted += deleted;
                    }
                    batchToDelete.clear();
                }
            }

            // Delete remaining keys
            if (!batchToDelete.isEmpty()) {
                Long deleted = redisTemplate.delete(batchToDelete);
                if (deleted != null) {
                    totalDeleted += deleted;
                }
            }
        } catch (Exception e) {
            log.error("Error deleting keys by pattern: {}", pattern, e);
        }

        return totalDeleted;
    }

    /**
     * Count keys matching a pattern using cursor-based scan.
     * Does not load all keys into memory - counts during iteration.
     *
     * @param pattern The pattern to match
     * @return Count of matching keys
     */
    private long countKeysByPattern(String pattern) {
        long count = 0;
        ScanOptions scanOptions = ScanOptions.scanOptions()
                .match(pattern)
                .count(scanBatchSize)
                .build();

        try (Cursor<String> cursor = redisTemplate.scan(scanOptions)) {
            while (cursor.hasNext()) {
                cursor.next();
                count++;
            }
        } catch (Exception e) {
            log.error("Error counting keys by pattern: {}", pattern, e);
        }

        return count;
    }

    @lombok.Data
    @lombok.Builder
    public static class CacheStats {
        private long totalEntries;
        private int ttlMinutes;
        private int negativeTtlMinutes;
    }
}
