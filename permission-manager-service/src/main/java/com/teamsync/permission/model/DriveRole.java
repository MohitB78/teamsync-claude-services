package com.teamsync.permission.model;

import com.teamsync.common.model.Permission;
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
 * DriveRole defines a set of permissions that can be assigned to users for a drive.
 * Roles can be system-defined (shared across tenants) or custom (per-tenant/drive).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "drive_roles")
@CompoundIndexes({
        @CompoundIndex(name = "tenant_drive_name_idx", def = "{'tenantId': 1, 'driveId': 1, 'name': 1}", unique = true),
        @CompoundIndex(name = "tenant_system_idx", def = "{'tenantId': 1, 'isSystemRole': 1}")
})
public class DriveRole {

    @Id
    private String id;

    private String tenantId;

    /**
     * If null, this is a tenant-wide role template.
     * If set, this role is specific to a drive.
     */
    private String driveId;

    private String name;
    private String description;

    /**
     * The permissions granted by this role
     */
    private Set<Permission> permissions;

    /**
     * System roles are predefined and cannot be deleted
     */
    @Builder.Default
    private Boolean isSystemRole = false;

    /**
     * Priority for permission inheritance (higher = more priority)
     */
    @Builder.Default
    private Integer priority = 0;

    // Audit fields
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;

    /**
     * Predefined system roles
     */
    public static final String ROLE_OWNER = "OWNER";
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_EDITOR = "EDITOR";
    public static final String ROLE_COMMENTER = "COMMENTER";
    public static final String ROLE_VIEWER = "VIEWER";

    /**
     * Create the default system roles for a tenant
     */
    public static DriveRole createOwnerRole(String tenantId) {
        return DriveRole.builder()
                .tenantId(tenantId)
                .name(ROLE_OWNER)
                .description("Full control over the drive including user and role management")
                .permissions(EnumSet.allOf(Permission.class))
                .isSystemRole(true)
                .priority(100)
                .createdAt(Instant.now())
                .build();
    }

    public static DriveRole createAdminRole(String tenantId) {
        return DriveRole.builder()
                .tenantId(tenantId)
                .name(ROLE_ADMIN)
                .description("Can manage users and content but not roles")
                .permissions(EnumSet.of(
                        Permission.READ,
                        Permission.WRITE,
                        Permission.DELETE,
                        Permission.SHARE,
                        Permission.MANAGE_USERS
                ))
                .isSystemRole(true)
                .priority(80)
                .createdAt(Instant.now())
                .build();
    }

    public static DriveRole createEditorRole(String tenantId) {
        return DriveRole.builder()
                .tenantId(tenantId)
                .name(ROLE_EDITOR)
                .description("Can view, create, edit, and delete content")
                .permissions(EnumSet.of(
                        Permission.READ,
                        Permission.WRITE,
                        Permission.DELETE,
                        Permission.SHARE
                ))
                .isSystemRole(true)
                .priority(60)
                .createdAt(Instant.now())
                .build();
    }

    public static DriveRole createCommenterRole(String tenantId) {
        return DriveRole.builder()
                .tenantId(tenantId)
                .name(ROLE_COMMENTER)
                .description("Can view content and add comments")
                .permissions(EnumSet.of(
                        Permission.READ,
                        Permission.WRITE  // Limited to comments only - enforced at document level
                ))
                .isSystemRole(true)
                .priority(40)
                .createdAt(Instant.now())
                .build();
    }

    public static DriveRole createViewerRole(String tenantId) {
        return DriveRole.builder()
                .tenantId(tenantId)
                .name(ROLE_VIEWER)
                .description("Can only view content")
                .permissions(EnumSet.of(Permission.READ))
                .isSystemRole(true)
                .priority(20)
                .createdAt(Instant.now())
                .build();
    }

    /**
     * Check if this role has a specific permission
     */
    public boolean hasPermission(Permission permission) {
        return permissions != null && permissions.contains(permission);
    }
}
