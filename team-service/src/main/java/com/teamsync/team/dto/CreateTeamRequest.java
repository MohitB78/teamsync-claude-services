package com.teamsync.team.dto;

import com.teamsync.team.model.Team;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Request DTO for creating a new team.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTeamRequest {

    @NotBlank(message = "Team name is required")
    @Size(min = 2, max = 100, message = "Team name must be between 2 and 100 characters")
    private String name;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;

    /**
     * Team visibility setting.
     * Default: PRIVATE
     */
    @Builder.Default
    private Team.TeamVisibility visibility = Team.TeamVisibility.PRIVATE;

    /**
     * Whether members can invite other users.
     * Default: true
     */
    @Builder.Default
    private Boolean allowMemberInvites = true;

    /**
     * Whether external (non-tenant) users can be invited.
     * Default: false
     */
    @Builder.Default
    private Boolean allowExternalMembers = false;

    /**
     * Whether join requests require owner approval.
     * Only relevant for PUBLIC teams.
     * Default: false
     */
    @Builder.Default
    private Boolean requireApprovalToJoin = false;

    /**
     * Source of storage quota for the team drive.
     * Default: PERSONAL (uses team owner's quota)
     */
    @Builder.Default
    private Team.QuotaSource quotaSource = Team.QuotaSource.PERSONAL;

    /**
     * ID of the department or user providing quota.
     * Required if quotaSource is DEPARTMENT.
     * Ignored for PERSONAL (uses owner's drive) or DEDICATED.
     */
    private String quotaSourceId;

    /**
     * Dedicated quota allocation in bytes.
     * Only used when quotaSource is DEDICATED.
     */
    private Long dedicatedQuotaBytes;

    /**
     * Email of the team owner (for display/notifications).
     */
    private String ownerEmail;

    /**
     * Optional tags for the team.
     */
    private List<String> tags;

    /**
     * Optional metadata for the team.
     */
    private Map<String, Object> metadata;

    // =============================================
    // Enterprise Features (Phase 5)
    // =============================================

    /**
     * Initial project lifecycle phase.
     * Default: PLANNING
     */
    @Builder.Default
    private Team.TeamPhase phase = Team.TeamPhase.PLANNING;

    /**
     * Project code for tracking/reference (e.g., "PRJ-2024-001").
     */
    @Size(max = 50, message = "Project code cannot exceed 50 characters")
    private String projectCode;

    /**
     * Client name for external-facing projects.
     */
    @Size(max = 100, message = "Client name cannot exceed 100 characters")
    private String clientName;

    /**
     * Parent team ID for creating a sub-team/workstream.
     * The current user must have access to the parent team.
     */
    private String parentTeamId;
}
