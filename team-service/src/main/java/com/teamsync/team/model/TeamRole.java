package com.teamsync.team.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/**
 * TeamRole defines a named set of permissions that can be assigned to team members.
 *
 * Roles can be:
 * - System roles: Pre-defined, cannot be modified or deleted (OWNER, ADMIN, MEMBER, GUEST, EXTERNAL)
 * - Tenant template roles: Created at tenant level, available for all teams
 * - Team-specific roles: Created for a specific team only
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "team_roles")
@CompoundIndexes({
        @CompoundIndex(name = "tenant_team_name_idx",
                       def = "{'tenantId': 1, 'teamId': 1, 'name': 1}",
                       unique = true),
        @CompoundIndex(name = "tenant_system_idx",
                       def = "{'tenantId': 1, 'isSystemRole': 1}"),
        @CompoundIndex(name = "team_roles_idx",
                       def = "{'teamId': 1}")
})
public class TeamRole {

    // System role IDs (used as roleId in TeamMember)
    public static final String ROLE_ID_OWNER = "OWNER";
    public static final String ROLE_ID_ADMIN = "ADMIN";
    public static final String ROLE_ID_MANAGER = "MANAGER";
    public static final String ROLE_ID_MEMBER = "MEMBER";
    public static final String ROLE_ID_GUEST = "GUEST";
    public static final String ROLE_ID_EXTERNAL = "EXTERNAL";

    @Id
    private String id;

    private String tenantId;

    /**
     * Team ID this role belongs to.
     * Null for tenant-wide template roles and system roles.
     */
    private String teamId;

    private String name;
    private String description;

    /**
     * Color for UI display (hex format, e.g., "#FF5722").
     */
    private String color;

    /**
     * Display order in role lists (lower numbers appear first).
     */
    private Integer displayOrder;

    /**
     * Set of permissions granted by this role.
     */
    private Set<TeamPermission> permissions;

    /**
     * System roles cannot be modified or deleted.
     */
    private Boolean isSystemRole;

    /**
     * If true, this role is assigned to new members by default.
     */
    private Boolean isDefault;

    /**
     * For EXTERNAL role: indicates this role is only for external users.
     * External users cannot be assigned any other role.
     */
    private Boolean isExternalOnly;

    // Audit fields
    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Creates the OWNER system role with all permissions.
     */
    public static TeamRole createOwnerRole(String tenantId) {
        return TeamRole.builder()
                .id(ROLE_ID_OWNER)
                .tenantId(tenantId)
                .teamId(null)
                .name("Owner")
                .description("Full access to all team features including team deletion and ownership transfer")
                .color("#9C27B0")
                .displayOrder(100)
                .permissions(EnumSet.allOf(TeamPermission.class))
                .isSystemRole(true)
                .isDefault(false)
                .isExternalOnly(false)
                .createdAt(Instant.now())
                .build();
    }

    /**
     * Creates the ADMIN system role.
     */
    public static TeamRole createAdminRole(String tenantId) {
        Set<TeamPermission> adminPermissions = EnumSet.allOf(TeamPermission.class);
        adminPermissions.remove(TeamPermission.TEAM_DELETE);
        adminPermissions.remove(TeamPermission.TEAM_TRANSFER_OWNERSHIP);

        return TeamRole.builder()
                .id(ROLE_ID_ADMIN)
                .tenantId(tenantId)
                .teamId(null)
                .name("Admin")
                .description("Full access except team deletion and ownership transfer")
                .color("#2196F3")
                .displayOrder(80)
                .permissions(adminPermissions)
                .isSystemRole(true)
                .isDefault(false)
                .isExternalOnly(false)
                .createdAt(Instant.now())
                .build();
    }

    /**
     * Creates the MANAGER system role.
     */
    public static TeamRole createManagerRole(String tenantId) {
        Set<TeamPermission> managerPermissions = EnumSet.of(
                TeamPermission.TEAM_VIEW,
                TeamPermission.MEMBER_VIEW,
                TeamPermission.ROLE_VIEW,
                TeamPermission.CONTENT_VIEW,
                TeamPermission.CONTENT_UPLOAD,
                TeamPermission.CONTENT_VERSION_CREATE,
                TeamPermission.CONTENT_EDIT,
                TeamPermission.CONTENT_DELETE,
                TeamPermission.CONTENT_SHARE,
                TeamPermission.CONTENT_CREATE_FOLDER,
                TeamPermission.CONTENT_MOVE,
                TeamPermission.CONTENT_COMMENT,
                TeamPermission.TASK_VIEW,
                TeamPermission.TASK_CREATE,
                TeamPermission.TASK_EDIT,
                TeamPermission.TASK_DELETE,
                TeamPermission.TASK_ASSIGN,
                TeamPermission.TASK_UPDATE_STATUS,
                TeamPermission.TASK_COMMENT,
                TeamPermission.ACTIVITY_VIEW
        );

        return TeamRole.builder()
                .id(ROLE_ID_MANAGER)
                .tenantId(tenantId)
                .teamId(null)
                .name("Manager")
                .description("Can manage content and tasks, but cannot manage members or roles")
                .color("#4CAF50")
                .displayOrder(60)
                .permissions(managerPermissions)
                .isSystemRole(true)
                .isDefault(false)
                .isExternalOnly(false)
                .createdAt(Instant.now())
                .build();
    }

    /**
     * Creates the MEMBER system role.
     */
    public static TeamRole createMemberRole(String tenantId) {
        Set<TeamPermission> memberPermissions = EnumSet.of(
                TeamPermission.TEAM_VIEW,
                TeamPermission.MEMBER_VIEW,
                TeamPermission.ROLE_VIEW,
                TeamPermission.CONTENT_VIEW,
                TeamPermission.CONTENT_UPLOAD,
                TeamPermission.CONTENT_VERSION_CREATE,
                TeamPermission.CONTENT_EDIT,
                TeamPermission.CONTENT_CREATE_FOLDER,
                TeamPermission.CONTENT_COMMENT,
                TeamPermission.TASK_VIEW,
                TeamPermission.TASK_UPDATE_STATUS,
                TeamPermission.TASK_COMMENT,
                TeamPermission.ACTIVITY_VIEW
        );

        return TeamRole.builder()
                .id(ROLE_ID_MEMBER)
                .tenantId(tenantId)
                .teamId(null)
                .name("Member")
                .description("Can create and edit content, update assigned tasks")
                .color("#FF9800")
                .displayOrder(40)
                .permissions(memberPermissions)
                .isSystemRole(true)
                .isDefault(true)
                .isExternalOnly(false)
                .createdAt(Instant.now())
                .build();
    }

    /**
     * Creates the GUEST system role (read-only internal user).
     */
    public static TeamRole createGuestRole(String tenantId) {
        Set<TeamPermission> guestPermissions = EnumSet.of(
                TeamPermission.TEAM_VIEW,
                TeamPermission.MEMBER_VIEW,
                TeamPermission.CONTENT_VIEW,
                TeamPermission.TASK_VIEW,
                TeamPermission.ACTIVITY_VIEW
        );

        return TeamRole.builder()
                .id(ROLE_ID_GUEST)
                .tenantId(tenantId)
                .teamId(null)
                .name("Guest")
                .description("Read-only access to team content")
                .color("#9E9E9E")
                .displayOrder(20)
                .permissions(guestPermissions)
                .isSystemRole(true)
                .isDefault(false)
                .isExternalOnly(false)
                .createdAt(Instant.now())
                .build();
    }

    /**
     * Creates the EXTERNAL system role for external (non-tenant) users.
     * External users have restricted permissions:
     * - Can view content
     * - Can upload new documents
     * - Can create new versions (but NOT edit existing documents)
     * - Can view and comment on assigned tasks (but NOT create/edit/delete)
     */
    public static TeamRole createExternalRole(String tenantId) {
        Set<TeamPermission> externalPermissions = EnumSet.of(
                TeamPermission.TEAM_VIEW,
                TeamPermission.MEMBER_VIEW,
                TeamPermission.CONTENT_VIEW,
                TeamPermission.CONTENT_UPLOAD,
                TeamPermission.CONTENT_VERSION_CREATE,
                TeamPermission.TASK_VIEW,
                TeamPermission.TASK_COMMENT,
                TeamPermission.ACTIVITY_VIEW
        );

        return TeamRole.builder()
                .id(ROLE_ID_EXTERNAL)
                .tenantId(tenantId)
                .teamId(null)
                .name("External")
                .description("Limited access for external collaborators. Can view content, upload files, create versions, and comment on tasks.")
                .color("#795548")
                .displayOrder(10)
                .permissions(externalPermissions)
                .isSystemRole(true)
                .isDefault(false)
                .isExternalOnly(true)
                .createdAt(Instant.now())
                .build();
    }

    /**
     * Checks if this role has a specific permission.
     *
     * @param permission the permission to check
     * @return true if the role has the permission
     */
    public boolean hasPermission(TeamPermission permission) {
        return permissions != null && permissions.contains(permission);
    }

    /**
     * Checks if this is a system role (cannot be modified/deleted).
     *
     * @return true if this is a system role
     */
    public boolean isSystem() {
        return Boolean.TRUE.equals(isSystemRole);
    }

    /**
     * Checks if this role is only for external users.
     *
     * @return true if only external users can be assigned this role
     */
    public boolean isForExternalOnly() {
        return Boolean.TRUE.equals(isExternalOnly);
    }
}
