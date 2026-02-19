package com.teamsync.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Token refresh response DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshResponse {

    /**
     * Whether refresh was successful.
     */
    private boolean success;

    /**
     * New session expiration time in seconds.
     */
    private Long expiresIn;

    /**
     * Error message (returned on failed refresh).
     */
    private String error;

    /**
     * Error code for programmatic handling.
     */
    private String errorCode;

    /**
     * Create a successful refresh response.
     */
    public static RefreshResponse success(long expiresIn) {
        return RefreshResponse.builder()
            .success(true)
            .expiresIn(expiresIn)
            .build();
    }

    /**
     * Create a failed refresh response.
     */
    public static RefreshResponse failure(String error, String errorCode) {
        return RefreshResponse.builder()
            .success(false)
            .error(error)
            .errorCode(errorCode)
            .build();
    }
}
