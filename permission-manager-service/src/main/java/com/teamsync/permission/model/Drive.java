package com.teamsync.permission.model;

import com.teamsync.common.model.DriveType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Drive represents a storage container (personal, department, or team drive).
 * Personal drives are owned by users, department/team drives are shared with RBAC.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "drives")
@CompoundIndexes({
        @CompoundIndex(name = "tenant_owner_idx", def = "{'tenantId': 1, 'ownerId': 1}"),
        @CompoundIndex(name = "tenant_type_idx", def = "{'tenantId': 1, 'type': 1}"),
        @CompoundIndex(name = "tenant_department_idx", def = "{'tenantId': 1, 'departmentId': 1}"),
        @CompoundIndex(name = "tenant_team_idx", def = "{'tenantId': 1, 'teamId': 1}")
})
public class Drive {

    @Id
    private String id;

    @Indexed
    private String tenantId;

    private String name;
    private String description;

    private DriveType type;

    /**
     * For PERSONAL drives: the user who owns the drive
     * For DEPARTMENT drives: null (use departmentId instead)
     */
    private String ownerId;

    /**
     * For DEPARTMENT drives: the department that owns the drive
     * For PERSONAL and TEAM drives: null
     */
    private String departmentId;

    /**
     * For TEAM drives: the team that owns the drive
     * For PERSONAL and DEPARTMENT drives: null
     */
    private String teamId;

    /**
     * Storage quota in bytes (null = unlimited)
     */
    private Long quotaBytes;

    /**
     * Current storage usage in bytes
     */
    @Builder.Default
    private Long usedBytes = 0L;

    /**
     * Default role assigned to new department members
     */
    private String defaultRoleId;

    /**
     * Drive status
     */
    @Builder.Default
    private DriveStatus status = DriveStatus.ACTIVE;

    /**
     * Settings
     */
    @Builder.Default
    private DriveSettings settings = new DriveSettings();

    // Audit fields
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;

    public enum DriveStatus {
        ACTIVE,
        SUSPENDED,      // Quota exceeded or admin action
        ARCHIVED,       // Department deleted, drive preserved
        DELETED         // Marked for permanent deletion
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DriveSettings {
        @Builder.Default
        private Boolean allowPublicLinks = true;

        @Builder.Default
        private Boolean allowExternalSharing = false;

        @Builder.Default
        private Integer maxVersions = 100;

        @Builder.Default
        private Integer trashRetentionDays = 30;

        @Builder.Default
        private Boolean requireApprovalForDelete = false;
    }

    /**
     * Check if this is a personal drive for the given user
     */
    public boolean isPersonalDriveOf(String userId) {
        return type == DriveType.PERSONAL && userId.equals(ownerId);
    }

    /**
     * Generate the standard personal drive ID for a user
     */
    public static String personalDriveId(String userId) {
        return "personal-" + userId;
    }

    /**
     * Generate the standard department drive ID
     */
    public static String departmentDriveId(String departmentId) {
        return "dept-" + departmentId;
    }

    /**
     * Generate the standard team drive ID
     */
    public static String teamDriveId(String teamId) {
        return "team-" + teamId;
    }

    /**
     * Check if this is a team drive for the given team
     */
    public boolean isTeamDriveOf(String teamIdToCheck) {
        return type == DriveType.TEAM && teamIdToCheck.equals(teamId);
    }
}
