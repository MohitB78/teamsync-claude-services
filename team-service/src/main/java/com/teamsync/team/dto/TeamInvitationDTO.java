package com.teamsync.team.dto;

import com.teamsync.team.model.TeamInvitation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Data Transfer Object for TeamInvitation entity.
 * Used for API responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamInvitationDTO {

    private String id;
    private String tenantId;
    private String teamId;
    private String teamName;

    private String email;
    private TeamInvitation.InviteeType inviteeType;

    private String roleId;
    private String roleName;

    private TeamInvitation.InvitationStatus status;

    private String invitedById;
    private String invitedByName;

    private Instant expiresAt;
    private Integer resendCount;

    private Instant createdAt;
    private Instant respondedAt;
}
