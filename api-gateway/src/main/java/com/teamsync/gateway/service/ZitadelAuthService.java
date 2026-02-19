package com.teamsync.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamsync.gateway.config.BffProperties;
import com.teamsync.gateway.dto.TokenResponse;
import com.teamsync.gateway.model.BffSession;
import com.teamsync.gateway.util.PkceUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Service for authenticating with Zitadel using Authorization Code Flow with PKCE.
 *
 * <p>Zitadel is a modern identity provider that supports PKCE-first OAuth2 flows.
 * Unlike Keycloak, Zitadel uses a simpler role claim structure and doesn't require
 * a client secret for public clients using PKCE.
 *
 * <p>Key differences from Keycloak:
 * <ul>
 *   <li>Roles are in: urn:zitadel:iam:org:project:{projectId}:roles</li>
 *   <li>PKCE is the default and recommended flow</li>
 *   <li>No client secret needed for PKCE flows</li>
 *   <li>Simpler endpoint structure</li>
 * </ul>
 *
 * @see BffProperties.ZitadelProperties
 * @see PkceUtil
 */
@Service
@ConditionalOnProperty(name = "teamsync.bff.enabled", havingValue = "true")
@Slf4j
public class ZitadelAuthService {

    private final WebClient webClient;
    private final BffProperties bffProperties;
    private final ObjectMapper objectMapper;

    @Value("${teamsync.zitadel.project-id:}")
    private String zitadelProjectId;

    public ZitadelAuthService(WebClient.Builder webClientBuilder,
                              BffProperties bffProperties,
                              ObjectMapper objectMapper) {
        this.bffProperties = bffProperties;
        this.objectMapper = objectMapper;

        this.webClient = webClientBuilder
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
            .build();

        log.info("ZitadelAuthService initialized for Authorization Code Flow with PKCE");
    }

    /**
     * Build the Zitadel authorization URL for the Authorization Code Flow with PKCE.
     *
     * <p>The browser is redirected to this URL (Zitadel login page).
     * Zitadel's login UI can be customized or replaced with a custom login UI.
     *
     * @param pkceParams PKCE parameters (code_challenge, state, nonce)
     * @return The full authorization URL
     */
    public String buildAuthorizationUrl(PkceUtil.PkceParameters pkceParams) {
        BffProperties.ZitadelProperties zitadelProps = bffProperties.zitadel();
        BffProperties.PkceProperties pkceProps = bffProperties.pkce();

        // Build scope with Zitadel project role assertion
        String scope = pkceProps.scope();
        if (zitadelProjectId != null && !zitadelProjectId.isBlank()) {
            // Add project-specific scope for role assertion
            scope = scope + " urn:zitadel:iam:org:project:id:" + zitadelProjectId + ":aud";
        }

        String authUrl = UriComponentsBuilder.fromUriString(zitadelProps.authorizationUri())
            .queryParam("client_id", zitadelProps.clientId())
            .queryParam("response_type", "code")
            .queryParam("scope", scope)
            .queryParam("redirect_uri", pkceProps.redirectUri())
            .queryParam("state", pkceParams.state())
            .queryParam("nonce", pkceParams.nonce())
            .queryParam("code_challenge", pkceParams.codeChallenge())
            .queryParam("code_challenge_method", "S256")
            .encode()
            .build()
            .toUriString();

        log.debug("Built Zitadel authorization URL with state: {}", pkceParams.state());
        return authUrl;
    }

    /**
     * Build the authorization URL using the API Gateway's OIDC proxy.
     *
     * <p>This method is used when Zitadel is kept internal and all OIDC traffic
     * is proxied through the API Gateway. The browser never directly contacts Zitadel.
     *
     * @param pkceParams PKCE parameters (code_challenge, state, nonce)
     * @param apiGatewayBaseUrl The public URL of the API Gateway (e.g., https://api-gateway.railway.app)
     * @return The full authorization URL via the OIDC proxy
     */
    public String buildAuthorizationUrlViaProxy(PkceUtil.PkceParameters pkceParams, String apiGatewayBaseUrl) {
        BffProperties.ZitadelProperties zitadelProps = bffProperties.zitadel();
        BffProperties.PkceProperties pkceProps = bffProperties.pkce();

        // Build scope with Zitadel project role assertion
        String scope = pkceProps.scope();
        if (zitadelProjectId != null && !zitadelProjectId.isBlank()) {
            // Add project-specific scope for role assertion
            scope = scope + " urn:zitadel:iam:org:project:id:" + zitadelProjectId + ":aud";
        }

        // Use the API Gateway's OIDC proxy path instead of external Zitadel URL
        String authUrl = UriComponentsBuilder.fromUriString(apiGatewayBaseUrl + "/oauth/v2/authorize")
            .queryParam("client_id", zitadelProps.clientId())
            .queryParam("response_type", "code")
            .queryParam("scope", scope)
            .queryParam("redirect_uri", pkceProps.redirectUri())
            .queryParam("state", pkceParams.state())
            .queryParam("nonce", pkceParams.nonce())
            .queryParam("code_challenge", pkceParams.codeChallenge())
            .queryParam("code_challenge_method", "S256")
            .encode()
            .build()
            .toUriString();

        log.debug("Built authorization URL via OIDC proxy with state: {}", pkceParams.state());
        return authUrl;
    }

    /**
     * Exchange authorization code for tokens using PKCE.
     *
     * <p>This is called after Zitadel redirects back to the callback URL with the code.
     * Uses the internal token URI for server-to-server communication.
     *
     * @param authorizationCode The authorization code from Zitadel callback
     * @param codeVerifier The original PKCE code verifier (stored in session)
     * @return Mono containing token response
     */
    public Mono<TokenResponse> exchangeAuthorizationCode(String authorizationCode, String codeVerifier) {
        BffProperties.ZitadelProperties zitadelProps = bffProperties.zitadel();
        BffProperties.PkceProperties pkceProps = bffProperties.pkce();

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "authorization_code");
        formData.add("client_id", zitadelProps.clientId());
        formData.add("code", authorizationCode);
        formData.add("redirect_uri", pkceProps.redirectUri());
        formData.add("code_verifier", codeVerifier);

        // Note: PKCE flows in Zitadel don't require client_secret

        String tokenUri = zitadelProps.getInternalTokenUri();
        log.info("Token exchange - URI: {}, client_id: {}, redirect_uri: {}, code_verifier_length: {}",
            tokenUri, zitadelProps.clientId(), pkceProps.redirectUri(), codeVerifier != null ? codeVerifier.length() : 0);

        return webClient.post()
            .uri(tokenUri)
            .body(BodyInserters.fromFormData(formData))
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                response.bodyToMono(String.class)
                    .flatMap(body -> {
                        log.error("Token exchange FAILED (4xx) - Response: {}, URI: {}, client_id: {}, redirect_uri: {}",
                            body, tokenUri, zitadelProps.clientId(), pkceProps.redirectUri());
                        return Mono.error(new AuthenticationException(
                            "Authorization code exchange failed", "CODE_EXCHANGE_FAILED"));
                    }))
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                response.bodyToMono(String.class)
                    .flatMap(body -> {
                        log.error("Zitadel server error (5xx): {}", body);
                        return Mono.error(new AuthenticationException(
                            "Authentication service unavailable", "SERVICE_UNAVAILABLE"));
                    }))
            .bodyToMono(TokenResponse.class)
            .doOnSuccess(token -> log.info("Successfully exchanged authorization code for tokens"))
            .doOnError(e -> log.error("Authorization code exchange failed: {}", e.getMessage()));
    }

    /**
     * Refresh access token using refresh token.
     *
     * @param refreshToken The refresh token from previous authentication
     * @return Mono containing new token response
     */
    public Mono<TokenResponse> refresh(String refreshToken) {
        BffProperties.ZitadelProperties zitadelProps = bffProperties.zitadel();

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "refresh_token");
        formData.add("client_id", zitadelProps.clientId());
        formData.add("refresh_token", refreshToken);

        String tokenUri = zitadelProps.getInternalTokenUri();
        log.debug("Refreshing token with client: {} at: {}", zitadelProps.clientId(), tokenUri);

        return webClient.post()
            .uri(tokenUri)
            .body(BodyInserters.fromFormData(formData))
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                response.bodyToMono(String.class)
                    .flatMap(body -> {
                        log.warn("Token refresh failed (4xx): {}", body);
                        return Mono.error(new AuthenticationException(
                            "Refresh token expired or invalid", "REFRESH_FAILED"));
                    }))
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                response.bodyToMono(String.class)
                    .flatMap(body -> {
                        log.error("Zitadel server error during refresh (5xx): {}", body);
                        return Mono.error(new AuthenticationException(
                            "Authentication service unavailable", "SERVICE_UNAVAILABLE"));
                    }))
            .bodyToMono(TokenResponse.class)
            .doOnSuccess(token -> log.debug("Successfully refreshed token"))
            .doOnError(e -> log.error("Token refresh failed: {}", e.getMessage()));
    }

    /**
     * Revoke tokens (logout) at Zitadel.
     *
     * @param refreshToken The refresh token to revoke
     * @return Mono completing when revocation is done
     */
    public Mono<Void> revokeToken(String refreshToken) {
        BffProperties.ZitadelProperties zitadelProps = bffProperties.zitadel();

        // Zitadel revocation endpoint
        String revokeUri = zitadelProps.getInternalTokenUri().replace("/token", "/revoke");

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("client_id", zitadelProps.clientId());
        formData.add("token", refreshToken);
        formData.add("token_type_hint", "refresh_token");

        log.debug("Revoking token at: {}", revokeUri);

        return webClient.post()
            .uri(revokeUri)
            .body(BodyInserters.fromFormData(formData))
            .retrieve()
            .bodyToMono(Void.class)
            .doOnSuccess(v -> log.debug("Successfully revoked token"))
            .onErrorResume(e -> {
                log.warn("Token revocation failed (continuing with session cleanup): {}", e.getMessage());
                return Mono.empty();
            });
    }

    /**
     * Create BffSession from Zitadel token response.
     *
     * @param tokenResponse Token response from Zitadel
     * @param clientIp Client IP address for audit
     * @param userAgent User agent string for audit
     * @return BffSession populated with token and user info
     */
    public BffSession createBffSession(TokenResponse tokenResponse, String clientIp, String userAgent) {
        Instant now = Instant.now();

        // Parse access token for roles (Zitadel puts roles in access token)
        Map<String, Object> accessTokenClaims = parseJwtClaims(tokenResponse.getAccessToken());

        // Parse ID token for user info (email, name, preferred_username are in ID token)
        // ID token contains the identity claims as per OIDC spec
        Map<String, Object> idTokenClaims = tokenResponse.getIdToken() != null
            ? parseJwtClaims(tokenResponse.getIdToken())
            : accessTokenClaims;

        log.debug("Access token claims: {}", accessTokenClaims.keySet());
        log.debug("ID token claims: {}", idTokenClaims.keySet());

        // SECURITY FIX (Round 10 #10): Validate audience before extracting roles
        // Role claims should only be trusted if the token's audience matches our client
        String expectedClientId = bffProperties.zitadel().clientId();
        List<String> roles = extractRolesWithAudienceValidation(accessTokenClaims, expectedClientId);

        // SECURITY FIX (Round 10 #1, #6, #7): Validate required claims from ID token
        // Extract user info from ID token (OIDC standard)
        String userId = (String) idTokenClaims.get("sub");
        if (userId == null || userId.isBlank()) {
            log.error("SECURITY: ID token missing required 'sub' claim");
            throw new IllegalArgumentException("ID token missing required 'sub' claim");
        }

        String email = (String) idTokenClaims.get("email");
        String name = (String) idTokenClaims.get("name");

        // SECURITY FIX (Round 10 #1): Use userId as fallback if email is null
        String username = (String) idTokenClaims.get("preferred_username");
        if (username == null || username.isBlank()) {
            username = email != null ? email : userId;
        }

        // Extract tenant ID from Zitadel's standard claims or custom claim
        // Zitadel provides organization context via urn:zitadel:iam:user:resourceowner:id
        // Fallback to custom tenant_id claim if present
        String tenantId = (String) idTokenClaims.get("tenant_id");
        if (tenantId == null || tenantId.isBlank()) {
            // Try Zitadel's standard resource owner claim (organization ID)
            tenantId = (String) idTokenClaims.get("urn:zitadel:iam:user:resourceowner:id");
        }
        if (tenantId == null || tenantId.isBlank()) {
            // Log all available claims for debugging
            log.warn("SECURITY: No tenant_id or resourceowner claim found for user: {}, available claims: {}",
                userId, idTokenClaims.keySet());
            // Use organization ID from project roles claim as fallback
            tenantId = extractOrgIdFromRoles(accessTokenClaims);
        }
        if (tenantId == null || tenantId.isBlank()) {
            // Final fallback: use a default tenant (for development/single-tenant deployments)
            log.warn("Using default tenant 'teamsync' for user: {} - configure tenant_id claim in Zitadel for multi-tenant", userId);
            tenantId = "teamsync";
        }

        String zitadelSessionId = (String) idTokenClaims.get("sid");

        // SECURITY FIX (Round 12): Removed PII (email, name) from INFO-level logging
        // PII should only be logged at DEBUG level to comply with GDPR/privacy regulations
        log.info("Creating BffSession for user: {}", userId);
        log.debug("BffSession details - email: {}, name: {}", email, name);

        // Determine role flags
        boolean isSuperAdmin = roles.contains("super-admin") || roles.contains("admin");
        boolean isOrgAdmin = roles.contains("org-admin");
        boolean isDepartmentAdmin = roles.contains("department-admin");

        // Calculate refresh token expiry (Zitadel may not provide refresh_expires_in)
        int refreshExpiresIn = tokenResponse.getRefreshExpiresIn() != null
            ? tokenResponse.getRefreshExpiresIn()
            : 86400; // Default 24 hours

        return BffSession.builder()
            .accessToken(tokenResponse.getAccessToken())
            .refreshToken(tokenResponse.getRefreshToken())
            .idToken(tokenResponse.getIdToken())
            .tokenType(tokenResponse.getTokenType())
            .accessTokenExpiresAt(now.plusSeconds(tokenResponse.getExpiresIn()))
            .refreshTokenExpiresAt(now.plusSeconds(refreshExpiresIn))
            .userId(userId)
            .email(email)
            .name(name)
            .username(username)
            .tenantId(tenantId)
            .roles(roles)
            .superAdmin(isSuperAdmin)
            .orgAdmin(isOrgAdmin)
            .departmentAdmin(isDepartmentAdmin)
            .zitadelSessionId(zitadelSessionId)
            .createdAt(now)
            .lastActivityAt(now)
            .clientIp(clientIp)
            .userAgent(userAgent)
            .build();
    }

    /**
     * Update existing session with new tokens after refresh.
     */
    public void updateSessionTokens(BffSession session, TokenResponse tokenResponse) {
        Instant now = Instant.now();

        int refreshExpiresIn = tokenResponse.getRefreshExpiresIn() != null
            ? tokenResponse.getRefreshExpiresIn()
            : 86400;

        session.updateTokens(
            tokenResponse.getAccessToken(),
            tokenResponse.getRefreshToken(),
            tokenResponse.getIdToken(),
            now.plusSeconds(tokenResponse.getExpiresIn()),
            now.plusSeconds(refreshExpiresIn)
        );
    }

    /**
     * Parse JWT claims from access token (without verification - already validated by Zitadel).
     *
     * SECURITY FIX (Round 10 #8, #18): Properly validate JWT format and throw on errors
     * instead of returning empty map which could lead to privilege escalation.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJwtClaims(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("JWT token cannot be null or empty");
        }

        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            log.error("SECURITY: Invalid JWT format - expected 3 parts, got {}", parts.length);
            throw new IllegalArgumentException("Invalid JWT format - not a valid JWS");
        }

        try {
            // SECURITY FIX (Round 10 #18): Validate header to ensure it's JWS not JWE
            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]));
            Map<String, Object> header = objectMapper.readValue(headerJson, Map.class);
            String typ = (String) header.get("typ");
            String alg = (String) header.get("alg");

            // JWE would have "enc" header, JWS should have "alg" for signing
            if (header.containsKey("enc")) {
                log.error("SECURITY: Received JWE token instead of JWS - rejecting");
                throw new IllegalArgumentException("JWE tokens are not supported, expected JWS");
            }

            if (alg == null || alg.isBlank()) {
                log.error("SECURITY: JWT missing 'alg' header claim");
                throw new IllegalArgumentException("JWT missing required 'alg' header");
            }

            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            return objectMapper.readValue(payload, Map.class);

        } catch (IllegalArgumentException e) {
            // Re-throw IllegalArgumentException (our validation errors)
            throw e;
        } catch (Exception e) {
            // SECURITY FIX (Round 14 #H26): Don't expose internal parsing details
            // Exception messages can reveal JWT library internals, parsing errors, or structure details
            log.error("SECURITY: Failed to parse JWT claims: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid token format");
        }
    }

    /**
     * SECURITY FIX (Round 10 #10): Extract roles with audience validation.
     * Role claims should only be trusted if the token's audience includes our client ID.
     * This prevents cross-client token reuse where a token from another application
     * could be used to gain elevated privileges.
     *
     * @param claims the JWT claims
     * @param expectedClientId our client ID (audience must include this)
     * @return list of roles, empty if audience validation fails
     */
    @SuppressWarnings("unchecked")
    private List<String> extractRolesWithAudienceValidation(Map<String, Object> claims, String expectedClientId) {
        // Validate audience before trusting any role claims
        Object audClaim = claims.get("aud");
        boolean audienceValid = false;

        if (audClaim instanceof String audString) {
            audienceValid = audString.equals(expectedClientId);
        } else if (audClaim instanceof Collection<?> audList) {
            audienceValid = audList.contains(expectedClientId);
        }

        if (!audienceValid) {
            // SECURITY FIX (Round 15 #M20): Token audience mismatch is a security concern
            // Could indicate cross-client token reuse attempt
            log.warn("SECURITY: Token audience mismatch - potential cross-client token reuse. " +
                    "Expected client '{}', got audience: {}", expectedClientId, audClaim);
            throw new AuthenticationException(
                "Authentication failed. Please try logging in again.",
                "INVALID_TOKEN_AUDIENCE");
        }

        return extractRoles(claims);
    }

    /**
     * Extract roles from Zitadel JWT claims.
     *
     * <p>Zitadel uses a different claim structure than Keycloak:
     * <pre>
     * {
     *   "urn:zitadel:iam:org:project:{projectId}:roles": {
     *     "admin": { "orgId": "orgDomain" },
     *     "user": { "orgId": "orgDomain" }
     *   }
     * }
     * </pre>
     *
     * SECURITY FIX (Round 10 #3, #16): Validate claim types before casting and
     * remove permissive fallback to arbitrary "roles" claim.
     */
    @SuppressWarnings("unchecked")
    private List<String> extractRoles(Map<String, Object> claims) {
        List<String> roles = new ArrayList<>();

        try {
            // Try Zitadel's project-specific role claim
            if (zitadelProjectId != null && !zitadelProjectId.isBlank()) {
                String rolesKey = "urn:zitadel:iam:org:project:" + zitadelProjectId + ":roles";
                Object rolesValue = claims.get(rolesKey);

                // SECURITY FIX (Round 10 #3): Validate type before casting
                if (rolesValue != null) {
                    if (!(rolesValue instanceof Map)) {
                        log.warn("SECURITY: Unexpected type for roles claim '{}': {} (expected Map)",
                                rolesKey, rolesValue.getClass().getSimpleName());
                        return roles; // Return empty roles instead of throwing
                    }
                    Map<String, Object> projectRoles = (Map<String, Object>) rolesValue;
                    roles.addAll(projectRoles.keySet());
                    return roles;
                }
            }

            // Fallback: Try to find any Zitadel role claim (still validate types)
            for (String key : claims.keySet()) {
                if (key.startsWith("urn:zitadel:iam:org:project:") && key.endsWith(":roles")) {
                    Object rolesValue = claims.get(key);

                    // SECURITY FIX (Round 10 #3): Validate type before casting
                    if (rolesValue != null && rolesValue instanceof Map) {
                        Map<String, Object> projectRoles = (Map<String, Object>) rolesValue;
                        roles.addAll(projectRoles.keySet());
                        break;
                    } else if (rolesValue != null) {
                        log.warn("SECURITY: Unexpected type for Zitadel role claim '{}': {}",
                                key, rolesValue.getClass().getSimpleName());
                    }
                }
            }

            // SECURITY FIX (Round 10 #16): REMOVED permissive fallback to "roles" claim
            // The standard "roles" claim could be spoofed. Only trust Zitadel's project-specific claims.
            // NOTE: For initial deployment, allow users without roles but log a warning
            // In production multi-tenant environments, uncomment the exception below
            if (roles.isEmpty()) {
                log.warn("SECURITY: User has no roles assigned in Zitadel project. " +
                        "User should be assigned at least one role in Zitadel for proper authorization.");
                // Assign default "user" role for basic access
                roles.add("user");
                // Uncomment below to enforce strict role requirements:
                // throw new AuthenticationException(
                //     "User account is not configured. Please contact your administrator.",
                //     "NO_ROLES_ASSIGNED");
            }

        } catch (AuthenticationException e) {
            // Re-throw authentication exceptions (security denials)
            throw e;
        } catch (Exception e) {
            log.warn("Failed to extract roles from JWT: {}", e.getMessage());
        }

        return roles;
    }

    /**
     * Extract organization ID from Zitadel role claims.
     * Zitadel role claims have the format: "roleName": {"orgId": "orgDomain"}
     */
    @SuppressWarnings("unchecked")
    private String extractOrgIdFromRoles(Map<String, Object> claims) {
        try {
            // Look for project role claims
            for (String key : claims.keySet()) {
                if (key.startsWith("urn:zitadel:iam:org:project:") && key.endsWith(":roles")) {
                    Object rolesValue = claims.get(key);
                    if (rolesValue instanceof Map) {
                        Map<String, Object> projectRoles = (Map<String, Object>) rolesValue;
                        // Get first role's org ID
                        for (Object roleValue : projectRoles.values()) {
                            if (roleValue instanceof Map) {
                                Map<String, String> orgMap = (Map<String, String>) roleValue;
                                // Return first org ID found
                                if (!orgMap.isEmpty()) {
                                    return orgMap.keySet().iterator().next();
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract org ID from role claims: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Custom authentication exception with error code.
     */
    public static class AuthenticationException extends RuntimeException {
        private final String errorCode;

        public AuthenticationException(String message, String errorCode) {
            super(message);
            this.errorCode = errorCode;
        }

        public String getErrorCode() {
            return errorCode;
        }
    }
}
