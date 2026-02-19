package com.teamsync.gateway.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/**
 * BFF Session data stored in Redis.
 *
 * <p>This model contains the Zitadel tokens and user information that are
 * stored server-side in Redis. Only the session ID (in the HttpOnly cookie)
 * is sent to the browser.
 *
 * <p>The session is created on successful PKCE login and used by the
 * SessionTokenRelayFilter to inject JWT tokens into downstream requests.
 *
 * @see com.teamsync.gateway.filter.SessionTokenRelayFilter
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BffSession implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Session attribute key used in WebSession.
     */
    public static final String SESSION_KEY = "bff_session";

    // ===== Token Data =====

    /**
     * Zitadel access token (JWT).
     * This is relayed to downstream services via the Authorization header.
     */
    private String accessToken;

    /**
     * Zitadel refresh token.
     * Used to obtain new access tokens without re-authentication.
     */
    private String refreshToken;

    /**
     * Zitadel ID token (for OpenID Connect).
     */
    private String idToken;

    /**
     * Token type (typically "Bearer").
     */
    private String tokenType;

    /**
     * Access token expiration time.
     */
    private Instant accessTokenExpiresAt;

    /**
     * Refresh token expiration time.
     */
    private Instant refreshTokenExpiresAt;

    // ===== User Info =====

    /**
     * Zitadel user ID (subject claim from JWT).
     */
    private String userId;

    /**
     * User's email address.
     */
    private String email;

    /**
     * User's display name.
     */
    private String name;

    /**
     * User's username (preferred_username claim).
     */
    private String username;

    /**
     * Tenant ID for multi-tenant isolation.
     */
    private String tenantId;

    /**
     * User's roles from Zitadel project roles claim.
     */
    private List<String> roles;

    // ===== Role Flags =====

    /**
     * Whether user has super-admin role.
     */
    private boolean superAdmin;

    /**
     * Whether user has org-admin role.
     */
    private boolean orgAdmin;

    /**
     * Whether user has department-admin role.
     */
    private boolean departmentAdmin;

    // ===== Session Metadata =====

    /**
     * Zitadel session ID (for backchannel logout).
     */
    private String zitadelSessionId;

    /**
     * When the session was created.
     */
    private Instant createdAt;

    /**
     * Last activity timestamp.
     */
    private Instant lastActivityAt;

    /**
     * Client IP address (for security auditing).
     */
    private String clientIp;

    /**
     * User agent string (for security auditing).
     */
    private String userAgent;

    /**
     * Check if the access token has expired.
     */
    public boolean isAccessTokenExpired() {
        return accessTokenExpiresAt != null && Instant.now().isAfter(accessTokenExpiresAt);
    }

    /**
     * Check if the refresh token has expired.
     */
    public boolean isRefreshTokenExpired() {
        return refreshTokenExpiresAt != null && Instant.now().isAfter(refreshTokenExpiresAt);
    }

    /**
     * Check if the session can be refreshed.
     */
    public boolean canRefresh() {
        return refreshToken != null && !isRefreshTokenExpired();
    }

    /**
     * Update the session with new tokens after refresh.
     */
    public void updateTokens(String accessToken, String refreshToken, String idToken,
                             Instant accessTokenExpiresAt, Instant refreshTokenExpiresAt) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.idToken = idToken;
        this.accessTokenExpiresAt = accessTokenExpiresAt;
        this.refreshTokenExpiresAt = refreshTokenExpiresAt;
        this.lastActivityAt = Instant.now();
    }
}
