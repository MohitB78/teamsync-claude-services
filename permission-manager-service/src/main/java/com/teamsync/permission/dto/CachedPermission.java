package com.teamsync.permission.dto;

import com.teamsync.common.model.Permission;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.Set;

/**
 * Cached permission data stored in Redis.
 * Contains all information needed for O(1) permission checks.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CachedPermission implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * The user ID
     */
    private String userId;

    /**
     * The drive ID
     */
    private String driveId;

    /**
     * The tenant ID
     */
    private String tenantId;

    /**
     * Whether the user has access to this drive
     */
    private boolean hasAccess;

    /**
     * The set of permissions the user has
     */
    private Set<Permission> permissions;

    /**
     * The role name (for display purposes)
     */
    private String roleName;

    /**
     * The role ID
     */
    private String roleId;

    /**
     * Whether this is the drive owner
     */
    private boolean isOwner;

    /**
     * When the cache entry was created
     */
    private Instant cachedAt;

    /**
     * When the assignment expires (null = never)
     */
    private Instant expiresAt;

    /**
     * Version for cache invalidation
     */
    private Long version;

    /**
     * Create a "no access" cache entry
     */
    public static CachedPermission noAccess(String userId, String driveId, String tenantId) {
        return CachedPermission.builder()
                .userId(userId)
                .driveId(driveId)
                .tenantId(tenantId)
                .hasAccess(false)
                .permissions(Set.of())
                .cachedAt(Instant.now())
                .version(1L)
                .build();
    }

    /**
     * Create from a DriveAssignment
     */
    public static CachedPermission fromAssignment(
            String tenantId,
            String userId,
            String driveId,
            String roleId,
            String roleName,
            Set<Permission> permissions,
            boolean isOwner,
            Instant expiresAt) {
        return CachedPermission.builder()
                .tenantId(tenantId)
                .userId(userId)
                .driveId(driveId)
                .hasAccess(true)
                .permissions(permissions)
                .roleId(roleId)
                .roleName(roleName)
                .isOwner(isOwner)
                .expiresAt(expiresAt)
                .cachedAt(Instant.now())
                .version(1L)
                .build();
    }

    /**
     * Check if the user has a specific permission
     */
    public boolean hasPermission(Permission permission) {
        return hasAccess && permissions != null && permissions.contains(permission);
    }

    /**
     * Check if the cache entry is still valid
     */
    public boolean isValid() {
        if (!hasAccess) {
            return true; // "no access" entries are always valid
        }
        if (expiresAt != null && Instant.now().isAfter(expiresAt)) {
            return false;
        }
        return true;
    }

    // NOTE: Cache key generation is centralized in PermissionCacheService.
    // Key format: perm:t:{tenantId}:u:{userId}:d:{driveId}
    // SECURITY: tenantId is included to prevent cross-tenant cache pollution.
    // Do NOT add key generation methods here to avoid format mismatches.
}
