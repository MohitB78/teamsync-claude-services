package com.teamsync.permission.model;

import com.teamsync.common.model.Permission;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Set;

/**
 * DriveAssignment represents a user's access to a drive with a specific role.
 * This is the core table for O(1) permission lookups.
 *
 * Key design decisions:
 * 1. Permissions are denormalized from DriveRole for O(1) lookup
 * 2. Compound index on (userId, driveId) ensures fast lookups
 * 3. Cached in Redis with TTL for sub-millisecond access
 *
 * SECURITY FIX (Round 15 #M35): Added @Version for optimistic locking to prevent
 * race conditions in concurrent permission updates.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "drive_assignments")
@CompoundIndexes({
        // Primary lookup: O(1) check if user has access to drive (includes isActive for filtered queries)
        @CompoundIndex(name = "user_drive_active_idx", def = "{'userId': 1, 'driveId': 1, 'isActive': 1}"),
        // Unique constraint on user+drive (regardless of active status)
        @CompoundIndex(name = "user_drive_idx", def = "{'userId': 1, 'driveId': 1}", unique = true),
        // List all active drives a user has access to (most common query)
        @CompoundIndex(name = "tenant_user_active_idx", def = "{'tenantId': 1, 'userId': 1, 'isActive': 1}"),
        // List all active users with access to a drive
        @CompoundIndex(name = "tenant_drive_active_idx", def = "{'tenantId': 1, 'driveId': 1, 'isActive': 1}"),
        // Find assignments by role (for role updates) - doesn't need isActive as we update all
        @CompoundIndex(name = "tenant_role_idx", def = "{'tenantId': 1, 'roleId': 1}"),
        // Find active assignments by department (for bulk operations)
        @CompoundIndex(name = "tenant_department_active_idx", def = "{'tenantId': 1, 'assignedViaDepartment': 1, 'isActive': 1}"),
        // Find expired assignments for cleanup job
        @CompoundIndex(name = "expires_active_idx", def = "{'expiresAt': 1, 'isActive': 1}"),
        // Optimized isTenantAdmin() check: find users with MANAGE_ROLES permission
        @CompoundIndex(name = "tenant_user_permission_active_idx", def = "{'tenantId': 1, 'userId': 1, 'permissions': 1, 'isActive': 1}")
})
public class DriveAssignment {

    @Id
    private String id;

    /**
     * Version field for optimistic locking.
     * Prevents concurrent role/permission updates from overwriting each other.
     */
    @Version
    private Long version;

    private String tenantId;

    /**
     * The drive being accessed
     */
    private String driveId;

    /**
     * The user who has access
     */
    private String userId;

    /**
     * The role assigned to this user for this drive
     */
    private String roleId;

    /**
     * Role name (denormalized for display without join)
     */
    private String roleName;

    /**
     * Permissions from the role (DENORMALIZED for O(1) lookup).
     * Updated when role permissions change.
     */
    private Set<Permission> permissions;

    /**
     * How this assignment was created
     */
    private AssignmentSource source;

    /**
     * If source is DEPARTMENT, this is the department that granted access
     */
    private String assignedViaDepartment;

    /**
     * If source is TEAM, this is the team that granted access
     */
    private String assignedViaTeam;

    /**
     * Who granted this access
     */
    private String grantedBy;

    /**
     * When the access was granted
     */
    private Instant grantedAt;

    /**
     * Optional expiration (null = never expires)
     */
    private Instant expiresAt;

    /**
     * Whether this assignment is currently active
     */
    @Builder.Default
    private Boolean isActive = true;

    /**
     * When was this record last updated
     */
    private Instant updatedAt;

    public enum AssignmentSource {
        DIRECT,         // Explicitly assigned to user
        DEPARTMENT,     // Inherited from department membership
        TEAM,           // Inherited from team membership
        OWNER           // User is the drive owner (personal drive)
    }

    /**
     * Check if this assignment grants a specific permission
     */
    public boolean hasPermission(Permission permission) {
        return permissions != null && permissions.contains(permission);
    }

    /**
     * Check if this assignment is still valid (not expired)
     */
    public boolean isValid() {
        if (!Boolean.TRUE.equals(isActive)) {
            return false;
        }
        if (expiresAt != null && Instant.now().isAfter(expiresAt)) {
            return false;
        }
        return true;
    }

    /**
     * Create an owner assignment for a personal drive
     */
    public static DriveAssignment createOwnerAssignment(
            String tenantId,
            String driveId,
            String userId,
            String ownerRoleId,
            Set<Permission> ownerPermissions) {
        return DriveAssignment.builder()
                .tenantId(tenantId)
                .driveId(driveId)
                .userId(userId)
                .roleId(ownerRoleId)
                .roleName(DriveRole.ROLE_OWNER)
                .permissions(ownerPermissions)
                .source(AssignmentSource.OWNER)
                .grantedBy("system")
                .grantedAt(Instant.now())
                .isActive(true)
                .build();
    }
}
