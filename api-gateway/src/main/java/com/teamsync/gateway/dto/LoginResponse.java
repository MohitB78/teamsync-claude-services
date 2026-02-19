package com.teamsync.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Login response DTO for BFF authentication.
 *
 * <p>Note: The actual tokens are stored server-side in Redis.
 * Only the session ID is sent via HttpOnly cookie.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    /**
     * Whether login was successful.
     */
    private boolean success;

    /**
     * User information (returned on successful login).
     */
    private UserInfo user;

    /**
     * Session expiration time in seconds.
     */
    private Long expiresIn;

    /**
     * Error message (returned on failed login).
     */
    private String error;

    /**
     * Error code for programmatic handling.
     */
    private String errorCode;

    /**
     * Create a successful login response.
     */
    public static LoginResponse success(UserInfo user, long expiresIn) {
        return LoginResponse.builder()
            .success(true)
            .user(user)
            .expiresIn(expiresIn)
            .build();
    }

    /**
     * Create a failed login response.
     */
    public static LoginResponse failure(String error, String errorCode) {
        return LoginResponse.builder()
            .success(false)
            .error(error)
            .errorCode(errorCode)
            .build();
    }
}
