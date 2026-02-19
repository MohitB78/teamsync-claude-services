package com.teamsync.team.dto.portal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Authentication response for portal users.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortalAuthResponse {

    private String accessToken;
    private String refreshToken;
    private long expiresIn;
    private PortalUserDTO user;
}
