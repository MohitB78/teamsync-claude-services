package com.teamsync.team.dto.portal;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to verify a magic link token.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyMagicLinkRequest {

    @NotBlank(message = "Token is required")
    private String token;
}
