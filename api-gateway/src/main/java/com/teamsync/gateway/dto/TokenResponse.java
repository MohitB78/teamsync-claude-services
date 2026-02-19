package com.teamsync.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OAuth2 token endpoint response DTO.
 *
 * <p>Represents the response from an OAuth2/OIDC token endpoint.
 * This is a generic response that works with both Zitadel and other
 * OAuth2 providers.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenResponse {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("expires_in")
    private Integer expiresIn;

    @JsonProperty("refresh_expires_in")
    private Integer refreshExpiresIn;

    @JsonProperty("refresh_token")
    private String refreshToken;

    @JsonProperty("token_type")
    private String tokenType;

    @JsonProperty("id_token")
    private String idToken;

    @JsonProperty("session_state")
    private String sessionState;

    @JsonProperty("scope")
    private String scope;

    @JsonProperty("not-before-policy")
    private Integer notBeforePolicy;
}
