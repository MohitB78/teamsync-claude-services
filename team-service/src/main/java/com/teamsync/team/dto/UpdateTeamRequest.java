package com.teamsync.team.dto;

import com.teamsync.team.model.Team;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for updating an existing team.
 * All fields are optional - only non-null fields are updated.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTeamRequest {

    @Size(min = 2, max = 100, message = "Team name must be between 2 and 100 characters")
    private String name;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;

    private Team.TeamVisibility visibility;

    private Boolean allowMemberInvites;

    private Boolean allowExternalMembers;

    private Boolean requireApprovalToJoin;

    /**
     * New status for the team.
     * Only owner can change to ARCHIVED.
     */
    private Team.TeamStatus status;

    /**
     * Tags for the team.
     */
    private List<String> tags;

    // =============================================
    // Enterprise Features (Phase 5)
    // =============================================

    /**
     * Project lifecycle phase.
     */
    private Team.TeamPhase phase;

    /**
     * Project code for tracking/reference.
     */
    @Size(max = 50, message = "Project code cannot exceed 50 characters")
    private String projectCode;

    /**
     * Client name for external-facing projects.
     */
    @Size(max = 100, message = "Client name cannot exceed 100 characters")
    private String clientName;

    /**
     * Parent team ID for program hierarchy.
     * Set to null to remove from hierarchy.
     */
    private String parentTeamId;
}
