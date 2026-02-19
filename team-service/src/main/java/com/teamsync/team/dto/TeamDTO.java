package com.teamsync.team.dto;

import com.teamsync.team.model.Team;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Data Transfer Object for Team entity.
 * Used for API responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamDTO {

    private String id;
    private String tenantId;
    private String name;
    private String description;
    private String avatar;

    private Team.TeamVisibility visibility;
    private Team.TeamStatus status;

    // Settings
    private Boolean allowMemberInvites;
    private Boolean allowExternalMembers;
    private Boolean requireApprovalToJoin;

    // Storage
    private String driveId;
    private Team.QuotaSource quotaSource;
    private String quotaSourceId;
    private Long dedicatedQuotaBytes;

    // Counts
    private Integer memberCount;
    private Integer taskCount;

    // Owner info
    private String ownerId;
    private String ownerName;

    // Current user's context (populated per-request)
    private String currentUserRoleId;
    private String currentUserRoleName;
    private List<String> currentUserPermissions;
    private Boolean isCurrentUserOwner;

    // Denormalized lists for display
    private List<String> customRoleIds;
    private List<String> pinnedDocumentIds;
    private List<String> tags;

    // =============================================
    // Enterprise Features (Phase 5)
    // =============================================

    /** Project lifecycle phase */
    private Team.TeamPhase phase;

    /** Project code for tracking */
    private String projectCode;

    /** Client name for external projects */
    private String clientName;

    /** Parent team ID for program hierarchy */
    private String parentTeamId;

    /** Parent team name (resolved for display) */
    private String parentTeamName;

    /** Child teams for hierarchy navigation */
    private List<ChildTeamDTO> childTeams;

    // Audit
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;

    /**
     * Minimal DTO for child team references in hierarchy.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChildTeamDTO {
        private String id;
        private String name;
    }
}
