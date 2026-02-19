package com.teamsync.team.model;

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
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Team entity representing a collaboration workspace.
 * Teams contain members (internal and external), documents (via team drive),
 * tasks, roles, and activity logs.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "teams")
@CompoundIndexes({
        @CompoundIndex(name = "tenant_idx", def = "{'tenantId': 1}"),
        @CompoundIndex(name = "tenant_name_idx", def = "{'tenantId': 1, 'name': 1}", unique = true),
        @CompoundIndex(name = "tenant_owner_idx", def = "{'tenantId': 1, 'ownerId': 1}"),
        @CompoundIndex(name = "tenant_status_idx", def = "{'tenantId': 1, 'status': 1}"),
        @CompoundIndex(name = "tenant_drive_idx", def = "{'tenantId': 1, 'driveId': 1}"),
        // Index for child team queries (Phase 5 hierarchy) - critical for batch loading performance
        @CompoundIndex(name = "tenant_parent_status_idx", def = "{'tenantId': 1, 'parentTeamId': 1, 'status': 1}")
})
public class Team {

    @Id
    private String id;

    /**
     * Optimistic locking version for concurrent updates.
     */
    @Version
    private Long entityVersion;

    // Index handled by @CompoundIndex("tenant_idx") above
    private String tenantId;

    private String name;
    private String description;
    private String avatar;  // Storage key for team avatar

    // Team settings
    private TeamVisibility visibility;
    private Boolean allowMemberInvites;
    private Boolean requireApprovalToJoin;

    /**
     * NEW: Whether external (non-tenant) users can be invited to this team.
     * Requires TEAMS_EXTERNAL_USERS license feature.
     */
    private Boolean allowExternalMembers;

    // Associated drive for team files (format: team-{teamId})
    private String driveId;

    // Storage quota configuration
    /**
     * NEW: Source of storage quota for this team's drive.
     */
    private QuotaSource quotaSource;

    /**
     * NEW: Department ID or User ID depending on quotaSource.
     * - If DEPARTMENT: the department whose quota is used
     * - If PERSONAL: the user (usually owner) whose quota is used
     * - If DEDICATED: null (uses dedicatedQuotaBytes instead)
     */
    private String quotaSourceId;

    /**
     * NEW: Allocated quota in bytes if quotaSource is DEDICATED.
     */
    private Long dedicatedQuotaBytes;

    // Members (embedded for small teams, <100 members for performance)
    private List<TeamMember> members;

    /**
     * Denormalized member count for display without loading all members.
     */
    private Integer memberCount;

    // Custom roles for this team (in addition to system roles)
    private List<String> customRoleIds;

    // Metadata and tags
    private Map<String, Object> metadata;
    private List<String> tags;

    // Pinned items (document IDs and task IDs)
    private List<String> pinnedDocumentIds;
    private List<String> pinnedTaskIds;

    // Ownership
    private String ownerId;
    private String createdBy;
    private String lastModifiedBy;

    // Status
    private TeamStatus status;

    /**
     * NEW: License feature key for module access control.
     * Value: "TEAMS_MODULE"
     */
    private String licenseFeatureKey;

    // =============================================
    // Enterprise Features (Phase 5)
    // =============================================

    /**
     * Project lifecycle phase for tracking team progress.
     * Supports workflows: Planning → Active → Review → Closing → Archived
     */
    private TeamPhase phase;

    /**
     * Project code for tracking/reference (e.g., "PRJ-2024-001").
     * Used for integration with external project management systems.
     */
    private String projectCode;

    /**
     * Client name for external-facing projects.
     * Helps identify which client/customer the team is working for.
     */
    private String clientName;

    /**
     * Parent team ID for program hierarchy.
     * Enables sub-teams/workstreams within larger programs.
     */
    private String parentTeamId;

    // Audit timestamps
    private Instant createdAt;
    private Instant updatedAt;
    private Instant archivedAt;

    /**
     * Team visibility determines who can see and join the team.
     */
    public enum TeamVisibility {
        /** Anyone in tenant can see and request to join */
        PUBLIC,
        /** Only members can see the team */
        PRIVATE,
        /** Anyone can see but invite-only */
        RESTRICTED
    }

    /**
     * Team lifecycle status.
     */
    public enum TeamStatus {
        ACTIVE,
        ARCHIVED,
        DELETED
    }

    /**
     * Team lifecycle phase for project tracking.
     * Represents the current stage in the project lifecycle.
     */
    public enum TeamPhase {
        /** Team is in planning and setup stage */
        PLANNING,
        /** Team is actively working on deliverables */
        ACTIVE,
        /** Work is under review or awaiting approval */
        REVIEW,
        /** Team is wrapping up and completing handover */
        CLOSING,
        /** Team work is complete and archived for reference */
        ARCHIVED
    }

    /**
     * Source of storage quota for the team drive.
     */
    public enum QuotaSource {
        /** Uses selected department's drive quota */
        DEPARTMENT,
        /** Uses team owner's personal drive quota */
        PERSONAL,
        /** Team has its own dedicated quota allocation */
        DEDICATED
    }

    /**
     * Embedded team member with role and permissions.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamMember {
        private String userId;
        private String email;  // For display and external user identification

        /**
         * NEW: Distinguishes internal (tenant) users from external guests.
         */
        private MemberType memberType;

        /**
         * Reference to TeamRole entity ID.
         * For system roles, uses predefined IDs (e.g., "OWNER", "ADMIN", "MEMBER", "GUEST", "EXTERNAL").
         */
        private String roleId;

        /**
         * Denormalized role name for display.
         */
        private String roleName;

        /**
         * Denormalized permissions from the assigned role.
         * Updated when role permissions change.
         */
        private Set<String> permissions;

        private Instant joinedAt;
        private String invitedBy;

        /**
         * Last activity timestamp for presence tracking.
         */
        private Instant lastActiveAt;

        /**
         * Member status within the team.
         */
        private MemberStatus status;
    }

    /**
     * Type of team member.
     */
    public enum MemberType {
        /** Tenant user with Zitadel account */
        INTERNAL,
        /** External guest user (email-based, uses portal) */
        EXTERNAL
    }

    /**
     * Status of a team member.
     */
    public enum MemberStatus {
        ACTIVE,
        REMOVED,
        SUSPENDED
    }

    // Helper methods

    /**
     * Generates the standard drive ID for a team.
     * @param teamId the team ID
     * @return drive ID in format "team-{teamId}"
     */
    public static String teamDriveId(String teamId) {
        return "team-" + teamId;
    }

    /**
     * Checks if this team allows external members.
     * @return true if external members are allowed
     */
    public boolean isExternalMembersAllowed() {
        return Boolean.TRUE.equals(allowExternalMembers);
    }

    /**
     * Gets the active member count.
     * @return count of active members
     */
    public int getActiveMemberCount() {
        if (members == null) {
            return 0;
        }
        return (int) members.stream()
                .filter(m -> m.getStatus() == MemberStatus.ACTIVE)
                .count();
    }
}
