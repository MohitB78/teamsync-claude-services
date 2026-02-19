package com.teamsync.storage.service;

import com.teamsync.common.context.TenantContext;
import com.teamsync.common.model.Permission;
import com.teamsync.common.permission.PermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Drive access service for SpEL expressions in @PreAuthorize annotations.
 *
 * This service provides a simple interface for checking drive access permissions
 * that can be used in SpEL expressions like:
 * {@code @PreAuthorize("@driveAccessService.hasAccess(#driveId, 'READ')")}
 *
 * It delegates to the PermissionService from teamsync-common which handles
 * the actual permission checking via the Permission Manager Service.
 */
@Service("driveAccessService")
@Slf4j
@RequiredArgsConstructor
public class DriveAccessService {

    private final PermissionService permissionService;

    /**
     * Check if the current authenticated user has the specified permission on a drive.
     *
     * @param driveId The drive ID to check access for
     * @param permissionName The permission name (e.g., "READ", "WRITE", "MANAGE_ROLES")
     * @return true if the user has the permission, false otherwise
     */
    public boolean hasAccess(String driveId, String permissionName) {
        String userId = TenantContext.getUserId();

        if (userId == null || userId.isBlank()) {
            log.warn("DriveAccessService.hasAccess called without authenticated user");
            return false;
        }

        if (driveId == null || driveId.isBlank()) {
            log.warn("DriveAccessService.hasAccess called with null/empty driveId");
            return false;
        }

        try {
            Permission permission = Permission.valueOf(permissionName);
            boolean hasAccess = permissionService.hasPermission(userId, driveId, permission);

            log.debug("DriveAccessService.hasAccess: user={}, driveId={}, permission={}, result={}",
                    userId, driveId, permissionName, hasAccess);

            return hasAccess;
        } catch (IllegalArgumentException e) {
            log.error("Invalid permission name: {}", permissionName);
            return false;
        }
    }

    /**
     * Check if the current authenticated user has any access to a drive.
     *
     * @param driveId The drive ID to check access for
     * @return true if the user has any access, false otherwise
     */
    public boolean hasAccess(String driveId) {
        String userId = TenantContext.getUserId();

        if (userId == null || userId.isBlank()) {
            log.warn("DriveAccessService.hasAccess called without authenticated user");
            return false;
        }

        if (driveId == null || driveId.isBlank()) {
            log.warn("DriveAccessService.hasAccess called with null/empty driveId");
            return false;
        }

        boolean hasAccess = permissionService.hasAccess(userId, driveId);

        log.debug("DriveAccessService.hasAccess: user={}, driveId={}, result={}",
                userId, driveId, hasAccess);

        return hasAccess;
    }
}
