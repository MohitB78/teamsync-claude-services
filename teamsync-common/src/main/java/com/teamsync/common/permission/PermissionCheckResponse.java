package com.teamsync.common.permission;

import com.teamsync.common.model.Permission;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Response DTO for permission checks.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionCheckResponse {

    private boolean hasAccess;
    private boolean hasPermission;
    private Set<Permission> permissions;
    private String roleName;
    private boolean isOwner;
    private String source;
    private long lookupTimeMs;

    /**
     * Create a "no access" response for fallback scenarios
     */
    public static PermissionCheckResponse noAccess() {
        return PermissionCheckResponse.builder()
                .hasAccess(false)
                .hasPermission(false)
                .permissions(Set.of())
                .source("FALLBACK")
                .lookupTimeMs(0)
                .build();
    }

    /**
     * Create an "owner" response for personal drive access
     */
    public static PermissionCheckResponse ownerAccess() {
        return PermissionCheckResponse.builder()
                .hasAccess(true)
                .hasPermission(true)
                .permissions(Set.of(Permission.values()))
                .roleName("OWNER")
                .isOwner(true)
                .source("LOCAL")
                .lookupTimeMs(0)
                .build();
    }
}
