package com.teamsync.common.permission;

import com.teamsync.common.context.TenantContext;
import com.teamsync.common.dto.ApiResponse;
import com.teamsync.common.exception.AccessDeniedException;
import com.teamsync.common.model.Permission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Service wrapper for permission checks with local caching.
 *
 * This service provides:
 * 1. Quick local check for personal drive ownership (no network call)
 * 2. Request-scoped caching to avoid duplicate calls within same request
 * 3. Circuit breaker fallback when permission service is unavailable
 * 4. Convenient methods for common permission checks
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionClient permissionClient;

    // Request-scoped cache using ThreadLocal
    private static final ThreadLocal<ConcurrentHashMap<String, PermissionCheckResponse>> REQUEST_CACHE =
            ThreadLocal.withInitial(ConcurrentHashMap::new);

    /**
     * Check if current user has a specific permission on the current drive.
     * Uses TenantContext to get userId and driveId.
     */
    public boolean hasPermission(Permission permission) {
        String userId = TenantContext.getUserId();
        String driveId = TenantContext.getDriveId();
        return hasPermission(userId, driveId, permission);
    }

    /**
     * Check if a user has a specific permission on a drive.
     */
    public boolean hasPermission(String userId, String driveId, Permission permission) {
        PermissionCheckResponse response = checkPermission(userId, driveId, permission);
        return response.isHasPermission();
    }

    /**
     * Check if current user has access to the current drive.
     */
    public boolean hasAccess() {
        String userId = TenantContext.getUserId();
        String driveId = TenantContext.getDriveId();
        return hasAccess(userId, driveId);
    }

    /**
     * Check if a user has any access to a drive.
     */
    public boolean hasAccess(String userId, String driveId) {
        PermissionCheckResponse response = checkPermission(userId, driveId, null);
        return response.isHasAccess();
    }

    /**
     * Require a permission, throwing AccessDeniedException if not granted.
     */
    public void requirePermission(Permission permission) {
        String userId = TenantContext.getUserId();
        String driveId = TenantContext.getDriveId();
        requirePermission(userId, driveId, permission);
    }

    /**
     * Require a permission, throwing AccessDeniedException if not granted.
     */
    public void requirePermission(String userId, String driveId, Permission permission) {
        if (!hasPermission(userId, driveId, permission)) {
            throw new AccessDeniedException(
                    String.format("User %s does not have %s permission on drive %s",
                            userId, permission, driveId));
        }
    }

    /**
     * Get all permissions for current user on current drive.
     */
    public Set<Permission> getPermissions() {
        String userId = TenantContext.getUserId();
        String driveId = TenantContext.getDriveId();
        return getPermissions(userId, driveId);
    }

    /**
     * Get all permissions for a user on a drive.
     */
    public Set<Permission> getPermissions(String userId, String driveId) {
        PermissionCheckResponse response = checkPermission(userId, driveId, null);
        return response.getPermissions();
    }

    /**
     * Check if current user is the owner of the current drive.
     */
    public boolean isOwner() {
        String userId = TenantContext.getUserId();
        String driveId = TenantContext.getDriveId();
        return isOwner(userId, driveId);
    }

    /**
     * Check if a user is the owner of a drive.
     */
    public boolean isOwner(String userId, String driveId) {
        PermissionCheckResponse response = checkPermission(userId, driveId, null);
        return response.isOwner();
    }

    /**
     * Full permission check with caching.
     */
    public PermissionCheckResponse checkPermission(String userId, String driveId, Permission permission) {
        // Quick local check for personal drive ownership
        if (isPersonalDriveOwner(userId, driveId)) {
            return PermissionCheckResponse.ownerAccess();
        }

        // Check request-scoped cache
        String cacheKey = buildCacheKey(userId, driveId, permission);
        ConcurrentHashMap<String, PermissionCheckResponse> cache = REQUEST_CACHE.get();

        PermissionCheckResponse cached = cache.get(cacheKey);
        if (cached != null) {
            log.debug("Permission cache hit for key: {}", cacheKey);
            return cached;
        }

        // Call permission manager service
        try {
            PermissionCheckRequest request = PermissionCheckRequest.builder()
                    .userId(userId)
                    .driveId(driveId)
                    .requiredPermission(permission)
                    .build();

            ApiResponse<PermissionCheckResponse> response = permissionClient.checkPermission(request);

            if (response != null && response.isSuccess() && response.getData() != null) {
                PermissionCheckResponse result = response.getData();
                cache.put(cacheKey, result);
                log.debug("Permission check result for {}/{}: hasAccess={}, hasPermission={}",
                        userId, driveId, result.isHasAccess(), result.isHasPermission());
                return result;
            }

            log.warn("Permission check returned null or unsuccessful for {}/{}", userId, driveId);
            return PermissionCheckResponse.noAccess();

        } catch (Exception e) {
            log.error("Error checking permission for {}/{}: {}", userId, driveId, e.getMessage());
            // Fail closed - deny access on error
            return PermissionCheckResponse.noAccess();
        }
    }

    /**
     * Clear the request-scoped cache.
     * Should be called at the end of each request (e.g., via filter).
     */
    public static void clearRequestCache() {
        REQUEST_CACHE.remove();
    }

    /**
     * Warm the cache for a user (call on login).
     */
    public void warmCache(String userId) {
        try {
            permissionClient.warmCache(userId);
            log.debug("Warmed permission cache for user: {}", userId);
        } catch (Exception e) {
            log.warn("Failed to warm permission cache for user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Invalidate cache for a user.
     */
    public void invalidateUserCache(String userId) {
        try {
            permissionClient.invalidateUserCache(userId);
            log.debug("Invalidated permission cache for user: {}", userId);
        } catch (Exception e) {
            log.warn("Failed to invalidate permission cache for user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Invalidate cache for a drive.
     */
    public void invalidateDriveCache(String driveId) {
        try {
            permissionClient.invalidateDriveCache(driveId);
            log.debug("Invalidated permission cache for drive: {}", driveId);
        } catch (Exception e) {
            log.warn("Failed to invalidate permission cache for drive {}: {}", driveId, e.getMessage());
        }
    }

    // ============== Private Helper Methods ==============

    /**
     * Quick check if user owns a personal drive (no network call needed).
     */
    private boolean isPersonalDriveOwner(String userId, String driveId) {
        return driveId != null && driveId.equals("personal-" + userId);
    }

    /**
     * Build a cache key for the request-scoped cache.
     */
    private String buildCacheKey(String userId, String driveId, Permission permission) {
        return userId + ":" + driveId + ":" + (permission != null ? permission.name() : "ALL");
    }
}
