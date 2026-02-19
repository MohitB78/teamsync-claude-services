package com.teamsync.team.dto;

import com.teamsync.team.model.TeamInvitation;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a team invitation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateInvitationRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    /**
     * Type of invitee.
     * INTERNAL: Existing tenant user
     * EXTERNAL: External guest (uses portal)
     * Default: INTERNAL
     */
    @Builder.Default
    private TeamInvitation.InviteeType inviteeType = TeamInvitation.InviteeType.INTERNAL;

    /**
     * Role to assign on acceptance.
     * If not specified, uses team's default role.
     * External users are always assigned EXTERNAL role regardless of this setting.
     */
    private String roleId;

    /**
     * Optional message to include in invitation email.
     */
    private String message;
}
