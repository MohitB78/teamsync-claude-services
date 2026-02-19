package com.teamsync.permission.dto;

import com.teamsync.common.model.Permission;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Response for permission check requests.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionCheckResponse {

    /**
     * Whether the user has access to the drive
     */
    private boolean hasAccess;

    /**
     * Whether the specific requested permission is granted
     */
    private boolean hasPermission;

    /**
     * All permissions the user has on this drive
     */
    private Set<Permission> permissions;

    /**
     * The role name (if any)
     */
    private String roleName;

    /**
     * Whether the user is the drive owner
     */
    private boolean isOwner;

    /**
     * Source of the access (CACHE or DATABASE)
     */
    private String source;

    /**
     * Time to lookup in milliseconds
     */
    private long lookupTimeMs;

    /**
     * Create a "no access" response
     */
    public static PermissionCheckResponse noAccess(String source, long lookupTimeMs) {
        return PermissionCheckResponse.builder()
                .hasAccess(false)
                .hasPermission(false)
                .permissions(Set.of())
                .source(source)
                .lookupTimeMs(lookupTimeMs)
                .build();
    }

    /**
     * Create an "access granted" response
     */
    public static PermissionCheckResponse granted(
            Set<Permission> permissions,
            Permission requiredPermission,
            String roleName,
            boolean isOwner,
            String source,
            long lookupTimeMs) {
        boolean hasPermission = requiredPermission == null || permissions.contains(requiredPermission);
        return PermissionCheckResponse.builder()
                .hasAccess(true)
                .hasPermission(hasPermission)
                .permissions(permissions)
                .roleName(roleName)
                .isOwner(isOwner)
                .source(source)
                .lookupTimeMs(lookupTimeMs)
                .build();
    }
}
