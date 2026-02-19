package com.teamsync.gateway.dto;

import com.teamsync.gateway.model.BffSession;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * User information DTO returned in auth responses.
 *
 * <p>Contains essential user details extracted from Zitadel JWT claims.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfo {

    /**
     * Zitadel user ID (sub claim).
     */
    private String id;

    /**
     * User's email address.
     */
    private String email;

    /**
     * User's display name.
     */
    private String name;

    /**
     * User's username (preferred_username claim).
     */
    private String username;

    /**
     * Tenant ID for multi-tenant context.
     */
    private String tenantId;

    /**
     * User's roles from Zitadel project roles claim.
     */
    private List<String> roles;

    /**
     * Whether user has super-admin role.
     */
    private boolean isSuperAdmin;

    /**
     * Whether user has org-admin role.
     */
    private boolean isOrgAdmin;

    /**
     * Whether user has department-admin role.
     */
    private boolean isDepartmentAdmin;

    /**
     * Create UserInfo from BffSession.
     */
    public static UserInfo fromSession(BffSession session) {
        return UserInfo.builder()
            .id(session.getUserId())
            .email(session.getEmail())
            .name(session.getName())
            .username(session.getUsername())
            .tenantId(session.getTenantId())
            .roles(session.getRoles())
            .isSuperAdmin(session.isSuperAdmin())
            .isOrgAdmin(session.isOrgAdmin())
            .isDepartmentAdmin(session.isDepartmentAdmin())
            .build();
    }
}
