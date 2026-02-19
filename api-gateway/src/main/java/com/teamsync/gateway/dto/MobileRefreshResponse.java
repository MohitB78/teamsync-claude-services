package com.teamsync.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mobile token refresh response DTO.
 *
 * <p>Returns tokens directly to mobile apps which store them locally
 * in the device's secure storage (Keychain/Keystore).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MobileRefreshResponse {

    /**
     * Whether refresh was successful.
     */
    private boolean success;

    /**
     * Nested data object containing tokens (for mobile app compatibility).
     */
    private TokenData data;

    /**
     * Error message (returned on failed refresh).
     */
    private String error;

    /**
     * Error code for programmatic handling.
     */
    private String errorCode;

    /**
     * Token data nested inside 'data' field for mobile app compatibility.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenData {
        /**
         * New access token (JWT).
         */
        private String accessToken;

        /**
         * New refresh token (rotated for security).
         */
        private String refreshToken;

        /**
         * Access token expiration time in seconds.
         */
        private Long expiresIn;
    }

    /**
     * Create a successful refresh response with tokens.
     */
    public static MobileRefreshResponse success(String accessToken, String refreshToken, long expiresIn) {
        return MobileRefreshResponse.builder()
            .success(true)
            .data(TokenData.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(expiresIn)
                .build())
            .build();
    }

    /**
     * Create a failed refresh response.
     */
    public static MobileRefreshResponse failure(String error, String errorCode) {
        return MobileRefreshResponse.builder()
            .success(false)
            .error(error)
            .errorCode(errorCode)
            .build();
    }
}
