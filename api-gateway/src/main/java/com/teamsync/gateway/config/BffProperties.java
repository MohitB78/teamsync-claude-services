package com.teamsync.gateway.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * Type-safe configuration properties for BFF (Backend for Frontend) pattern.
 *
 * <p>This configuration enables the API Gateway to act as a BFF, handling:
 * <ul>
 *   <li>Session management with HttpOnly cookies</li>
 *   <li>CSRF protection for browser requests</li>
 *   <li>OAuth2 Authorization Code Flow with PKCE (Zitadel)</li>
 * </ul>
 *
 * <p>The BFF pattern keeps tokens server-side. Only session ID is sent to browser
 * via HttpOnly cookie.
 *
 * @see RedisSessionConfig
 * @see BffSecurityConfig
 */
@ConfigurationProperties(prefix = "teamsync.bff")
@Validated
public record BffProperties(
    boolean enabled,
    @NotNull @Valid SessionProperties session,
    @NotNull @Valid CsrfProperties csrf,
    @NotNull @Valid ZitadelProperties zitadel,
    @NotNull @Valid PkceProperties pkce
) {

    /**
     * Session configuration for Redis-backed sessions with HttpOnly cookies.
     *
     * <p>For cross-subdomain deployments (e.g., Railway), set cookieDomain to the
     * parent domain (e.g., ".up.railway.app") so cookies work between frontend
     * and API Gateway subdomains.
     */
    public record SessionProperties(
        @NotBlank String cookieName,
        @NotNull Duration cookieMaxAge,
        boolean cookieSecure,
        @NotBlank String cookieSameSite,
        boolean cookieHttpOnly,
        @NotBlank String redisNamespace,
        /** Cookie domain for cross-subdomain support (e.g., ".up.railway.app"). Null = use default (request host). */
        String cookieDomain
    ) {
        public SessionProperties {
            // Defaults
            if (cookieName == null || cookieName.isBlank()) {
                cookieName = "TEAMSYNC_SESSION";
            }
            if (cookieMaxAge == null) {
                cookieMaxAge = Duration.ofHours(8);
            }
            if (cookieSameSite == null || cookieSameSite.isBlank()) {
                cookieSameSite = "Lax";
            }
            if (redisNamespace == null || redisNamespace.isBlank()) {
                redisNamespace = "teamsync:bff:session";
            }
            // cookieDomain can be null (use request host) or set explicitly
        }
    }

    /**
     * CSRF protection configuration using CookieServerCsrfTokenRepository.
     *
     * <h3>SECURITY NOTE: /storage-proxy/** paths</h3>
     * <p>The /storage-proxy/** paths are NOT included in excludePatterns because:
     * <ul>
     *   <li>They use presigned URLs with cryptographic signatures (X-Amz-Signature)</li>
     *   <li>The signatures are generated server-side with MinIO credentials</li>
     *   <li>An attacker cannot forge valid presigned URLs without the credentials</li>
     *   <li>Signatures include request path, expiry time, and are time-limited</li>
     * </ul>
     * <p>This provides equivalent CSRF protection through URL signatures.</p>
     */
    public record CsrfProperties(
        boolean enabled,
        @NotBlank String cookieName,
        @NotBlank String headerName,
        List<String> excludePatterns
    ) {
        public CsrfProperties {
            // Defaults
            if (cookieName == null || cookieName.isBlank()) {
                cookieName = "XSRF-TOKEN";
            }
            if (headerName == null || headerName.isBlank()) {
                headerName = "X-XSRF-TOKEN";
            }
            if (excludePatterns == null) {
                excludePatterns = List.of("/api/**", "/wopi/**", "/bff/admin/**");
            }
        }
    }

    /**
     * Zitadel configuration for OAuth2 authentication.
     *
     * <p>Zitadel is a modern identity provider that supports PKCE-first OAuth2 flows.
     * It uses a simpler endpoint structure than Keycloak and includes roles in
     * project-specific claims.
     *
     * <p>Key endpoints:
     * <ul>
     *   <li>Authorization: /oauth/v2/authorize</li>
     *   <li>Token: /oauth/v2/token</li>
     *   <li>UserInfo: /oidc/v1/userinfo</li>
     *   <li>End Session: /oidc/v1/end_session</li>
     *   <li>JWKS: /oauth/v2/keys</li>
     * </ul>
     */
    public record ZitadelProperties(
        // Internal URLs for server-to-server communication
        String internalAuthorizationUri,
        String internalTokenUri,
        String internalUserInfoUri,
        String internalLogoutUri,
        // Public URLs for browser redirects
        @NotBlank String authorizationUri,
        @NotBlank String tokenUri,
        @NotBlank String userInfoUri,
        @NotBlank String logoutUri,
        @NotBlank String jwksUri,
        @NotBlank String clientId,
        /** Expected JWT issuer URL for validation. SECURITY: Must match Zitadel's issuer. */
        String issuer,
        @Positive int connectionTimeoutSeconds,
        @Positive int readTimeoutSeconds
    ) {
        public ZitadelProperties {
            // Defaults
            if (connectionTimeoutSeconds <= 0) {
                connectionTimeoutSeconds = 10;
            }
            if (readTimeoutSeconds <= 0) {
                readTimeoutSeconds = 30;
            }
            // If internal URLs not provided, use the public URLs
            if (internalAuthorizationUri == null || internalAuthorizationUri.isBlank()) {
                internalAuthorizationUri = authorizationUri;
            }
            if (internalTokenUri == null || internalTokenUri.isBlank()) {
                internalTokenUri = tokenUri;
            }
            if (internalUserInfoUri == null || internalUserInfoUri.isBlank()) {
                internalUserInfoUri = userInfoUri;
            }
            if (internalLogoutUri == null || internalLogoutUri.isBlank()) {
                internalLogoutUri = logoutUri;
            }
        }

        /**
         * Returns the full logout URL with post_logout_redirect_uri parameter.
         */
        public String getLogoutUrl(String postLogoutRedirectUri) {
            return logoutUri + "?client_id=" + clientId +
                   "&post_logout_redirect_uri=" + postLogoutRedirectUri;
        }

        /**
         * Returns the internal token URI for server-to-server token exchange.
         */
        public String getInternalTokenUri() {
            return internalTokenUri != null ? internalTokenUri : tokenUri;
        }

        /**
         * Returns the internal userinfo URI for server-to-server requests.
         */
        public String getInternalUserInfoUri() {
            return internalUserInfoUri != null ? internalUserInfoUri : userInfoUri;
        }
    }

    /**
     * PKCE (Proof Key for Code Exchange) configuration for Authorization Code Flow.
     *
     * <p>PKCE adds security for public clients by preventing authorization code
     * interception attacks. Zitadel uses PKCE as the default and recommended flow.
     */
    public record PkceProperties(
        boolean enabled,
        @NotBlank String redirectUri,
        @NotBlank String postLogoutRedirectUri,
        @NotBlank String frontendLoginUrl,
        @NotBlank String frontendLogoutUrl,
        @NotBlank String scope
    ) {
        public PkceProperties {
            // Defaults
            if (redirectUri == null || redirectUri.isBlank()) {
                redirectUri = "http://localhost:9080/bff/auth/callback";
            }
            if (postLogoutRedirectUri == null || postLogoutRedirectUri.isBlank()) {
                postLogoutRedirectUri = "http://localhost:3000/auth/login";
            }
            if (frontendLoginUrl == null || frontendLoginUrl.isBlank()) {
                frontendLoginUrl = "http://localhost:3000";
            }
            if (frontendLogoutUrl == null || frontendLogoutUrl.isBlank()) {
                frontendLogoutUrl = "http://localhost:3000/auth/login";
            }
            if (scope == null || scope.isBlank()) {
                scope = "openid email profile";
            }
        }
    }

    public BffProperties {
        // Default enabled to false if not specified
        if (session == null) {
            session = new SessionProperties(
                "TEAMSYNC_SESSION",
                Duration.ofHours(8),
                true,
                "Lax",
                true,
                "teamsync:bff:session",
                null  // cookieDomain - null = use request host
            );
        }
        if (csrf == null) {
            csrf = new CsrfProperties(
                true,
                "XSRF-TOKEN",
                "X-XSRF-TOKEN",
                List.of("/api/**", "/wopi/**")
            );
        }
        if (zitadel == null) {
            zitadel = new ZitadelProperties(
                // Internal URLs (server-to-server)
                "http://localhost:8085/oauth/v2/authorize",
                "http://localhost:8085/oauth/v2/token",
                "http://localhost:8085/oidc/v1/userinfo",
                "http://localhost:8085/oidc/v1/end_session",
                // Public URLs (browser)
                "http://localhost:8085/oauth/v2/authorize",
                "http://localhost:8085/oauth/v2/token",
                "http://localhost:8085/oidc/v1/userinfo",
                "http://localhost:8085/oidc/v1/end_session",
                "http://localhost:8085/oauth/v2/keys",
                "teamsync-bff",
                "http://localhost:8085",  // issuer
                10,
                30
            );
        }
        if (pkce == null) {
            pkce = new PkceProperties(
                true,
                "http://localhost:9080/bff/auth/callback",
                "http://localhost:3000/auth/login",
                "http://localhost:3000",
                "http://localhost:3000/auth/login",
                "openid email profile"
            );
        }
    }
}
