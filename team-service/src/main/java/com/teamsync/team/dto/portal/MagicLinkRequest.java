package com.teamsync.team.dto.portal;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to send a magic link email for portal authentication.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MagicLinkRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    /**
     * Optional invitation token if the user is coming from an invitation link.
     */
    private String invitationToken;
}
