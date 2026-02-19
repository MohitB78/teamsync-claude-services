package com.teamsync.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mobile token refresh request DTO.
 *
 * <p>Mobile apps store tokens locally (in Keychain/Keystore) and need
 * tokens returned directly, unlike the BFF pattern which stores tokens
 * server-side in Redis.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MobileRefreshRequest {

    /**
     * The refresh token stored on the mobile device.
     */
    @NotBlank(message = "Refresh token is required")
    private String refreshToken;
}
