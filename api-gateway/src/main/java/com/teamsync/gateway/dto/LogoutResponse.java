package com.teamsync.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Logout response DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogoutResponse {

    /**
     * Whether logout was successful.
     */
    private boolean success;

    /**
     * Message describing the result.
     */
    private String message;

    /**
     * Optional redirect URL (for Keycloak logout flow).
     */
    private String redirectUrl;

    /**
     * Create a successful logout response.
     */
    public static LogoutResponse success() {
        return LogoutResponse.builder()
            .success(true)
            .message("Successfully logged out")
            .build();
    }

    /**
     * Create a successful logout response with redirect.
     */
    public static LogoutResponse successWithRedirect(String redirectUrl) {
        return LogoutResponse.builder()
            .success(true)
            .message("Successfully logged out")
            .redirectUrl(redirectUrl)
            .build();
    }

    /**
     * Create a failed logout response.
     */
    public static LogoutResponse failure(String message) {
        return LogoutResponse.builder()
            .success(false)
            .message(message)
            .build();
    }
}
