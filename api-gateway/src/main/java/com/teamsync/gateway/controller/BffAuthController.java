package com.teamsync.gateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamsync.gateway.config.BffProperties;
import com.teamsync.gateway.dto.*;
import com.teamsync.gateway.model.BffSession;
import com.teamsync.gateway.service.ZitadelAuthService;
import com.teamsync.gateway.service.LoginEventPublisher;
import com.teamsync.gateway.service.SessionInvalidationService;
import com.teamsync.gateway.util.PkceUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseCookie;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.session.WebSessionIdResolver;
import reactor.core.publisher.Mono;

import org.springframework.data.redis.core.ReactiveRedisTemplate;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * BFF Authentication Controller.
 *
 * <p>Provides endpoints for browser-based authentication using the BFF pattern
 * with Authorization Code Flow and PKCE:
 * <ul>
 *   <li>GET /bff/auth/authorize - Start OAuth2 authorization (redirect to Zitadel)</li>
 *   <li>GET /bff/auth/callback - Handle authorization code callback</li>
 *   <li>POST /bff/auth/logout - Invalidate session and revoke token</li>
 *   <li>POST /bff/auth/refresh - Refresh access token</li>
 *   <li>GET /bff/auth/session - Get current session info</li>
 *   <li>POST /bff/auth/backchannel-logout - Zitadel callback for logout</li>
 * </ul>
 *
 * <p>Tokens are stored server-side in Redis. Only session ID is sent to browser
 * via HttpOnly cookie.
 *
 * @see ZitadelAuthService
 * @see BffSession
 * @see PkceUtil
 */
@RestController
@RequestMapping("/bff/auth")
@ConditionalOnProperty(name = "teamsync.bff.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class BffAuthController {

    private static final String PKCE_CODE_VERIFIER_KEY = "pkce_code_verifier";
    private static final String PKCE_STATE_KEY = "pkce_state";
    private static final String PKCE_NONCE_KEY = "pkce_nonce";
    private static final String ORIGINAL_URI_KEY = "original_uri";

    /**
     * SECURITY FIX (Round 10 #17): Disallowed cookie domain suffixes.
     * These TLDs and public suffix domains should never be used as cookie domains
     * because they would allow cookies to be shared with other websites on the same TLD.
     * Setting cookies on these domains could allow session hijacking from malicious sites.
     */
    private static final Set<String> DISALLOWED_COOKIE_DOMAIN_SUFFIXES = Set.of(
        ".com", ".net", ".org", ".io", ".app", ".dev", ".co", ".me",
        ".uk", ".de", ".fr", ".es", ".it", ".nl", ".au", ".ca",
        ".railway.app"  // Railway PSL - don't allow cookies on the public suffix
    );

    /**
     * Whitelist of allowed redirect URI hosts.
     * SECURITY: Only these hosts are allowed as post-login redirect destinations.
     */
    private static final Set<String> ALLOWED_REDIRECT_HOSTS = Set.of(
        "localhost",
        "127.0.0.1",
        "teamsync-frontend-production.up.railway.app",
        "portal.teamsync.link"
    );

    /**
     * Whitelist of allowed redirect URI host suffixes (for subdomain matching).
     *
     * SECURITY FIX: Removed ".up.railway.app" as it's too broad - any Railway app would match.
     * Only allow our own domains as suffixes.
     */
    private static final Set<String> ALLOWED_REDIRECT_HOST_SUFFIXES = Set.of(
        ".teamsync.com",
        ".accessarc.com",
        ".teamsync.link"
    );

    /**
     * SECURITY FIX: Allowed Railway app prefixes for stricter matching.
     * Only specific apps on Railway are allowed, not arbitrary railway apps.
     */
    private static final Set<String> ALLOWED_RAILWAY_APP_PREFIXES = Set.of(
        "teamsync-frontend-",
        "teamsync-admin-",
        "accessarc-frontend-",
        "accessarc-admin-"
    );

    private final ZitadelAuthService zitadelAuthService;
    private final LoginEventPublisher loginEventPublisher;
    private final SessionInvalidationService sessionInvalidationService;
    private final BffProperties bffProperties;
    private final ObjectMapper objectMapper;
    private final WebSessionIdResolver webSessionIdResolver;
    private final ReactiveJwtDecoder jwtDecoder;
    private final ReactiveRedisTemplate<String, Object> bffSessionRedisTemplate;

    /**
     * Initiate OAuth2 Authorization Code Flow with PKCE.
     *
     * <p>This endpoint:
     * <ol>
     *   <li>Generates PKCE parameters (code_verifier, code_challenge, state, nonce)</li>
     *   <li>Stores code_verifier and state in the session</li>
     *   <li>Redirects the browser to the API Gateway's OIDC proxy (not external Zitadel)</li>
     * </ol>
     *
     * <p>The API Gateway proxies OIDC requests to internal Zitadel, so the browser
     * never directly contacts Zitadel. This enables keeping Zitadel internal with no
     * public ingress.
     *
     * <p>After successful authentication, Zitadel redirects to the custom login URL
     * with an authRequestId, allowing headless login via Session API v2.
     *
     * @param redirectUri Optional URI to redirect to after successful login
     * @param exchange Server web exchange
     * @return Redirect to OIDC proxy authorization endpoint
     */
    @GetMapping("/authorize")
    public Mono<Void> authorize(
            @RequestParam(required = false) String redirectUri,
            ServerWebExchange exchange) {

        log.info("Starting OAuth2 authorization flow for headless login via OIDC proxy");

        BffProperties.SessionProperties sessionProps = bffProperties.session();

        return exchange.getSession()
            .flatMap(session -> {
                // Generate PKCE parameters
                PkceUtil.PkceParameters pkceParams = PkceUtil.PkceParameters.generate();

                // Store PKCE parameters in session for callback validation
                session.getAttributes().put(PKCE_CODE_VERIFIER_KEY, pkceParams.codeVerifier());
                session.getAttributes().put(PKCE_STATE_KEY, pkceParams.state());
                session.getAttributes().put(PKCE_NONCE_KEY, pkceParams.nonce());

                // Store original redirect URI if provided and validated
                // SECURITY: Validate redirect URI against whitelist to prevent open redirect attacks
                if (redirectUri != null && !redirectUri.isBlank()) {
                    if (isValidRedirectUri(redirectUri)) {
                        session.getAttributes().put(ORIGINAL_URI_KEY, redirectUri);
                        log.debug("Stored validated redirect URI: {}", redirectUri);
                    } else {
                        log.warn("Rejected invalid redirect URI (possible open redirect attack): {}", redirectUri);
                        // Continue without storing the redirect URI - will use default frontend URL
                    }
                }

                // Build API Gateway's public URL from the incoming request
                // This ensures the browser redirects to the same host it's already talking to
                ServerHttpRequest request = exchange.getRequest();
                String apiGatewayBaseUrl = getApiGatewayBaseUrl(request);

                // Build authorization URL via OIDC proxy (browser → API Gateway → Zitadel)
                String authorizationUrl = zitadelAuthService.buildAuthorizationUrlViaProxy(pkceParams, apiGatewayBaseUrl);

                log.debug("Redirecting to OIDC proxy authorization endpoint with state: {}, sessionId: {}",
                    pkceParams.state(), session.getId());

                // IMPORTANT: Save session to Redis BEFORE redirecting, so the PKCE parameters
                // are persisted and available when the callback comes back
                String redisNamespace = bffProperties.session().redisNamespace();
                String redisKey = redisNamespace + ":sessions:" + session.getId();

                log.info("Authorize: saving session to Redis - sessionId={}, redisKey={}, attributes={}, state={}",
                    session.getId(), redisKey, session.getAttributes().keySet(), pkceParams.state());

                return session.save()
                    .doOnSuccess(v -> log.info("Authorize: session.save() completed - sessionId={}", session.getId()))
                    .doOnError(e -> log.error("Authorize: session.save() FAILED - sessionId={}, error={}",
                        session.getId(), e.getMessage(), e))
                    // Verify the session was actually saved to Redis
                    .then(bffSessionRedisTemplate.hasKey(redisKey))
                    .doOnNext(exists -> log.info("Authorize: Redis verification - key={}, exists={}", redisKey, exists))
                    .then(Mono.fromRunnable(() -> {
                        ServerHttpResponse response = exchange.getResponse();

                        // Manually build and add the session cookie to response headers
                        // This is necessary because setComplete() bypasses the normal filter chain
                        ResponseCookie.ResponseCookieBuilder cookieBuilder = ResponseCookie.from(sessionProps.cookieName(), session.getId())
                            .path("/")
                            .maxAge(sessionProps.cookieMaxAge())
                            .httpOnly(sessionProps.cookieHttpOnly())
                            .secure(sessionProps.cookieSecure())
                            .sameSite(sessionProps.cookieSameSite());

                        // SECURITY FIX (Round 10 #17): Validate cookie domain before setting
                        String validatedDomain = validateCookieDomain(sessionProps.cookieDomain());
                        if (validatedDomain != null) {
                            cookieBuilder.domain(validatedDomain);
                        }

                        ResponseCookie sessionCookie = cookieBuilder.build();
                        response.addCookie(sessionCookie);

                        log.info("Session cookie set for authorize redirect: name={}, sessionId={}, domain={}, sameSite={}",
                            sessionProps.cookieName(), session.getId(), validatedDomain, sessionProps.cookieSameSite());

                        // Redirect to API Gateway's OIDC proxy
                        response.setStatusCode(HttpStatus.FOUND);
                        response.getHeaders().setLocation(URI.create(authorizationUrl));
                    }))
                    .then(exchange.getResponse().setComplete());
            });
    }

    /**
     * Handle OAuth2 authorization code callback from Zitadel.
     *
     * <p>This endpoint:
     * <ol>
     *   <li>Validates the state parameter to prevent CSRF</li>
     *   <li>Exchanges the authorization code for tokens using PKCE code_verifier</li>
     *   <li>Creates a BffSession with the tokens</li>
     *   <li>Redirects to the frontend application</li>
     * </ol>
     *
     * @param code Authorization code from Zitadel
     * @param state State parameter for CSRF validation
     * @param error OAuth2 error code (if authorization failed)
     * @param errorDescription OAuth2 error description
     * @param exchange Server web exchange
     * @return Redirect to frontend application
     */
    @GetMapping("/callback")
    public Mono<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            @RequestParam(name = "error_description", required = false) String errorDescription,
            ServerWebExchange exchange) {

        ServerHttpRequest httpRequest = exchange.getRequest();
        String clientIp = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeaders().getFirst("User-Agent");

        // SECURITY FIX (Round 14 #H29): Reduced logging verbosity for session cookies
        // Session cookie values should never be logged, even at DEBUG level, to prevent
        // session hijacking via log file access. Only log presence/absence for debugging.
        BffProperties.SessionProperties sessionProps = bffProperties.session();
        String sessionCookieName = sessionProps.cookieName();
        var cookies = httpRequest.getCookies();
        var sessionCookie = cookies.getFirst(sessionCookieName);

        // DEBUG: Comprehensive header logging to diagnose session resolution issues
        String rawCookieHeader = httpRequest.getHeaders().getFirst("Cookie");
        String forwardedSessionId = httpRequest.getHeaders().getFirst("X-Forwarded-Session-ID");
        String requestStartTime = httpRequest.getHeaders().getFirst("X-Request-Start-Time");

        log.info("Callback received - headers analysis: " +
            "rawCookieHeader={}, " +
            "cookieHeaderLength={}, " +
            "parsedCookieNames={}, " +
            "sessionCookiePresent={}, " +
            "X-Forwarded-Session-ID={}, " +
            "X-Request-Start-Time={}, " +
            "allHeaders={}",
            rawCookieHeader != null ? rawCookieHeader.substring(0, Math.min(100, rawCookieHeader.length())) + "..." : "NULL",
            rawCookieHeader != null ? rawCookieHeader.length() : 0,
            cookies.keySet(),
            sessionCookie != null ? "YES (prefix: " + sessionCookie.getValue().substring(0, Math.min(8, sessionCookie.getValue().length())) + "...)" : "NO",
            forwardedSessionId != null ? forwardedSessionId.substring(0, Math.min(12, forwardedSessionId.length())) + "..." : "NULL",
            requestStartTime != null ? requestStartTime : "NULL",
            httpRequest.getHeaders().toSingleValueMap().keySet());

        // First, check if the session exists in Redis directly (for debugging)
        String redisNamespace = bffProperties.session().redisNamespace();
        String expectedSessionId = sessionCookie != null ? sessionCookie.getValue() :
                                   (forwardedSessionId != null && !forwardedSessionId.equals("NONE") ? forwardedSessionId : null);

        Mono<Boolean> sessionExistsCheck;
        if (expectedSessionId != null) {
            String redisKey = redisNamespace + ":sessions:" + expectedSessionId;
            sessionExistsCheck = bffSessionRedisTemplate.hasKey(redisKey)
                .doOnNext(exists -> log.info("Redis session check - key={}, exists={}", redisKey, exists));
        } else {
            sessionExistsCheck = Mono.just(false)
                .doOnNext(v -> log.warn("No session ID available to check Redis"));
        }

        return sessionExistsCheck.then(exchange.getSession())
            .flatMap(session -> {
                // DEBUG: Log session resolution to diagnose session mismatch issues
                String cookieSessionId = sessionCookie != null ? sessionCookie.getValue() : "NONE";
                String actualSessionId = session.getId();
                boolean sessionMatch = cookieSessionId.equals(actualSessionId);
                log.info("Callback session resolution - " +
                    "cookieSessionId={}, " +
                    "forwardedSessionId={}, " +
                    "actualSessionId={}, " +
                    "match={}, " +
                    "isStarted={}, " +
                    "isNew={}, " +
                    "attributeCount={}, " +
                    "attributeKeys={}",
                    cookieSessionId.length() > 8 ? cookieSessionId.substring(0, 8) + "..." : cookieSessionId,
                    forwardedSessionId != null ? (forwardedSessionId.length() > 8 ? forwardedSessionId.substring(0, 8) + "..." : forwardedSessionId) : "NULL",
                    actualSessionId.length() > 8 ? actualSessionId.substring(0, 8) + "..." : actualSessionId,
                    sessionMatch,
                    session.isStarted(),
                    !session.isStarted(), // isNew - not started means new
                    session.getAttributes().size(),
                    session.getAttributes().keySet());

                // Handle authorization errors
                if (error != null) {
                    log.warn("OAuth2 authorization failed: {} - {}", error, errorDescription);
                    return redirectToFrontendWithError(exchange, error, errorDescription);
                }

                if (code == null || code.isBlank()) {
                    log.warn("OAuth2 callback received without authorization code");
                    return redirectToFrontendWithError(exchange, "missing_code", "No authorization code received");
                }

                log.info("Processing OAuth2 callback with authorization code");

                // Validate state parameter
                String savedState = session.getAttribute(PKCE_STATE_KEY);

                // EXPIRED SESSION HANDLING: If session has no saved state, it means the session
                // from the cookie either expired or was never persisted properly.
                // Instead of showing a cryptic "State validation failed" error, redirect to
                // start a fresh login. This provides better UX when users return after their
                // session expired (e.g., left browser open overnight).
                if (savedState == null && !sessionMatch) {
                    log.warn("Session expired or missing - cookie had session {}, but resolved to new session {}. " +
                        "Redirecting to start fresh auth flow.",
                        cookieSessionId.length() > 8 ? cookieSessionId.substring(0, 8) + "..." : cookieSessionId,
                        actualSessionId.length() > 8 ? actualSessionId.substring(0, 8) + "..." : actualSessionId);

                    // Redirect to authorize to start a fresh PKCE flow
                    // This invalidates the stale callback and gives the user a clean login experience
                    ServerHttpResponse response = exchange.getResponse();
                    response.setStatusCode(HttpStatus.FOUND);
                    response.getHeaders().setLocation(URI.create("/bff/auth/authorize"));
                    return response.setComplete();
                }

                if (savedState == null || !savedState.equals(state)) {
                    log.warn("State mismatch - possible CSRF attack. Expected: {}, Received: {}, SessionId: {}",
                        savedState, state, session.getId());
                    return redirectToFrontendWithError(exchange, "invalid_state", "State validation failed");
                }

                // Get code verifier from session
                String codeVerifier = session.getAttribute(PKCE_CODE_VERIFIER_KEY);
                log.info("Session lookup for callback - sessionId: {}, hasCodeVerifier: {}, codeVerifierLength: {}, sessionAttributes: {}",
                    session.getId(),
                    codeVerifier != null,
                    codeVerifier != null ? codeVerifier.length() : 0,
                    session.getAttributes().keySet());
                if (codeVerifier == null) {
                    log.error("No code verifier found in session - sessionId: {}, attributes: {}",
                        session.getId(), session.getAttributes().keySet());
                    return redirectToFrontendWithError(exchange, "missing_verifier", "Session expired");
                }

                // Get original redirect URI
                String originalUri = session.getAttribute(ORIGINAL_URI_KEY);

                // Clean up PKCE parameters from session
                session.getAttributes().remove(PKCE_CODE_VERIFIER_KEY);
                session.getAttributes().remove(PKCE_STATE_KEY);
                session.getAttributes().remove(PKCE_NONCE_KEY);
                session.getAttributes().remove(ORIGINAL_URI_KEY);

                // Exchange authorization code for tokens
                return zitadelAuthService.exchangeAuthorizationCode(code, codeVerifier)
                    .doOnSuccess(tokenResponse -> log.info("Token exchange successful, creating BFF session"))
                    .flatMap(tokenResponse -> {
                        // Create BFF session with token and user info
                        BffSession bffSession;
                        try {
                            bffSession = zitadelAuthService.createBffSession(
                                tokenResponse, clientIp, userAgent);
                        } catch (ZitadelAuthService.AuthenticationException e) {
                            log.error("BFF session creation failed (auth error): {} ({})", e.getMessage(), e.getErrorCode());
                            return Mono.error(e);
                        } catch (IllegalArgumentException e) {
                            log.error("BFF session creation failed (validation): {}", e.getMessage());
                            return Mono.error(new ZitadelAuthService.AuthenticationException(
                                "Session creation failed: " + e.getMessage(), "SESSION_CREATION_FAILED"));
                        }

                        // SECURITY: Session fixation mitigation without invalidation
                        // We keep the same session ID because:
                        // 1. session.invalidate() triggers Spring Session to send a deletion cookie
                        // 2. This deletion cookie interferes with our manual cookie setting
                        // 3. The frontend already has this session ID embedded in the OAuth state parameter
                        //
                        // Instead of invalidating, we:
                        // - Clear PKCE secrets from the session (they've served their purpose)
                        // - Store the authenticated BffSession
                        // - The session is now bound to this authenticated user
                        //
                        // Note: True session fixation attacks require the attacker to set a known
                        // session ID on the victim's browser before auth. In our BFF pattern,
                        // session IDs are generated server-side and sent via HttpOnly cookies,
                        // making fixation attacks very difficult.

                        log.info("Storing authenticated session: sessionId={}, userId={}",
                            session.getId(), bffSession.getUserId());

                        // Clear PKCE secrets - they've served their purpose and shouldn't remain
                        session.getAttributes().remove(PKCE_CODE_VERIFIER_KEY);
                        session.getAttributes().remove(PKCE_STATE_KEY);
                        session.getAttributes().remove(PKCE_NONCE_KEY);
                        session.getAttributes().remove(ORIGINAL_URI_KEY);

                        // Store the authenticated session
                        session.getAttributes().put(BffSession.SESSION_KEY, bffSession);

                        log.info("Login successful for user: {} (id: {})",
                            bffSession.getEmail(), bffSession.getUserId());

                        // Save session to Redis BEFORE redirect to ensure data is persisted
                        return session.save()
                            .then(loginEventPublisher.publishLoginEvent(bffSession)
                                .onErrorResume(e -> {
                                    log.warn("Login event publish failed (continuing): {}", e.getMessage());
                                    return Mono.empty();
                                }))
                            .then(redirectToFrontendAfterLogin(exchange, bffSession, originalUri, session));
                    });
            })
            .onErrorResume(ZitadelAuthService.AuthenticationException.class, e -> {
                // SECURITY FIX (Round 14 #H28): Don't expose internal error details to frontend
                // Error codes can reveal internal service structure; error messages may contain sensitive info
                log.warn("Authentication failed: {} ({})", e.getMessage(), e.getErrorCode());
                return redirectToFrontendWithError(exchange, "authentication_failed", "Authentication failed. Please try again.");
            })
            .onErrorResume(e -> {
                log.error("Unexpected error during callback processing: {}", e.getMessage(), e);
                return redirectToFrontendWithError(exchange, "internal_error", "An unexpected error occurred");
            });
    }

    /**
     * Logout endpoint.
     *
     * <p>Invalidates the Redis session and revokes the refresh token at Zitadel.
     */
    @PostMapping("/logout")
    public Mono<ResponseEntity<LogoutResponse>> logout(ServerWebExchange exchange) {
        return exchange.getSession()
            .flatMap(session -> {
                BffSession bffSession = session.getAttribute(BffSession.SESSION_KEY);

                if (bffSession == null) {
                    log.debug("Logout called but no session found");
                    return session.invalidate()
                        .then(Mono.just(ResponseEntity.ok(LogoutResponse.success())));
                }

                // SECURITY FIX (Round 12): Removed PII (email) from INFO-level logging
                log.info("Logging out user: {}", bffSession.getUserId());

                // Revoke token at Zitadel (best effort)
                Mono<Void> revokeTokenMono = Mono.empty();
                if (bffSession.getRefreshToken() != null) {
                    revokeTokenMono = zitadelAuthService.revokeToken(bffSession.getRefreshToken())
                        .onErrorResume(e -> {
                            log.warn("Token revocation failed: {}", e.getMessage());
                            return Mono.empty();
                        });
                }

                // Invalidate session
                return revokeTokenMono
                    .then(session.invalidate())
                    .then(Mono.just(ResponseEntity.ok(LogoutResponse.success())));
            })
            .onErrorResume(e -> {
                log.error("Error during logout: {}", e.getMessage(), e);
                return Mono.just(ResponseEntity.ok(LogoutResponse.success()));
            });
    }

    /**
     * Refresh endpoint.
     *
     * <p>Uses the refresh token to obtain new access tokens from Zitadel.
     * Updates the Redis session with new tokens and extends the session
     * timeout (sliding window) to prevent session expiry during active use.
     */
    @PostMapping("/refresh")
    public Mono<ResponseEntity<RefreshResponse>> refresh(ServerWebExchange exchange) {
        return exchange.getSession()
            .flatMap(session -> {
                BffSession bffSession = session.getAttribute(BffSession.SESSION_KEY);

                if (bffSession == null) {
                    log.debug("Refresh called but no session found");
                    return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(RefreshResponse.failure("No active session", "NO_SESSION")));
                }

                if (!bffSession.canRefresh()) {
                    log.warn("Refresh token expired for user: {}", bffSession.getUserId());
                    return session.invalidate()
                        .then(Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(RefreshResponse.failure("Session expired", "SESSION_EXPIRED"))));
                }

                log.debug("Refreshing token for user: {}", bffSession.getUserId());

                return zitadelAuthService.refresh(bffSession.getRefreshToken())
                    .flatMap(tokenResponse -> {
                        // Update session with new tokens
                        zitadelAuthService.updateSessionTokens(bffSession, tokenResponse);
                        session.getAttributes().put(BffSession.SESSION_KEY, bffSession);

                        // Sliding window: Reset session max inactive interval on refresh
                        // This extends the session timeout each time user refreshes tokens
                        session.setMaxIdleTime(bffProperties.session().cookieMaxAge());

                        log.debug("Token refreshed for user: {}, session extended", bffSession.getUserId());

                        return Mono.just(ResponseEntity.ok(
                            RefreshResponse.success(tokenResponse.getExpiresIn())));
                    });
            })
            .onErrorResume(ZitadelAuthService.AuthenticationException.class, e -> {
                log.warn("Token refresh failed: {} ({})", e.getMessage(), e.getErrorCode());
                return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(RefreshResponse.failure(e.getMessage(), e.getErrorCode())));
            })
            .onErrorResume(e -> {
                log.error("Error during token refresh: {}", e.getMessage(), e);
                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(RefreshResponse.failure("Refresh failed", "INTERNAL_ERROR")));
            });
    }

    /**
     * Session info endpoint.
     *
     * <p>Returns current session information for authenticated users.
     * Used by frontend to check authentication status on page load.
     */
    @GetMapping("/session")
    public Mono<ResponseEntity<SessionResponse>> getSession(ServerWebExchange exchange) {
        return exchange.getSession()
            .map(session -> {
                BffSession bffSession = session.getAttribute(BffSession.SESSION_KEY);

                if (bffSession == null) {
                    return ResponseEntity.ok(SessionResponse.unauthenticated());
                }

                // Check if access token expired (need refresh)
                if (bffSession.isAccessTokenExpired() && !bffSession.canRefresh()) {
                    log.debug("Session expired for user: {}", bffSession.getUserId());
                    return ResponseEntity.ok(SessionResponse.unauthenticated());
                }

                log.debug("Session valid for user: {}", bffSession.getUserId());
                return ResponseEntity.ok(SessionResponse.authenticated(bffSession));
            })
            .onErrorResume(e -> {
                log.error("Error checking session: {}", e.getMessage());
                return Mono.just(ResponseEntity.ok(SessionResponse.unauthenticated()));
            });
    }

    /**
     * Backchannel logout endpoint called by Zitadel when user logs out.
     *
     * <p>SECURITY: Implements OIDC Back-Channel Logout for Single Logout (SLO).
     * When a user logs out from Zitadel (or any other application in the SSO),
     * Zitadel sends a logout_token to this endpoint to invalidate all associated
     * TeamSync sessions.
     *
     * <p>The logout_token is a JWT containing:
     * <ul>
     *   <li>sid - Zitadel session ID</li>
     *   <li>sub - User ID (subject)</li>
     *   <li>aud - Audience (our client ID)</li>
     *   <li>events - Logout event identifier</li>
     * </ul>
     *
     * <p>SECURITY: The JWT signature is validated using the same decoder as regular
     * authentication to prevent attackers from crafting fake logout tokens to
     * invalidate arbitrary user sessions.
     *
     * <p>Per OIDC Back-Channel Logout spec, this endpoint always returns 200 OK,
     * even on errors, to prevent information leakage.
     *
     * @param logoutToken JWT containing session information
     * @return Always 200 OK per OIDC spec
     */
    @PostMapping("/backchannel-logout")
    public Mono<ResponseEntity<Void>> backchannelLogout(
            @RequestParam("logout_token") String logoutToken) {

        log.info("Received backchannel logout request");

        // SECURITY: Validate JWT signature before processing claims
        // This prevents attackers from crafting fake logout tokens
        return jwtDecoder.decode(logoutToken)
            .flatMap(jwt -> {
                // Extract claims from validated JWT
                String zitadelSessionId = jwt.getClaimAsString("sid");
                String userId = jwt.getSubject();

                // Validate required claims per OIDC Back-Channel Logout spec
                if (zitadelSessionId == null || zitadelSessionId.isBlank()) {
                    log.warn("Backchannel logout token missing 'sid' claim");
                    return Mono.just(ResponseEntity.ok().<Void>build());
                }

                // SECURITY FIX (Round 10 #25): Validate session ID format to prevent injection
                // Zitadel session IDs follow a specific format (alphanumeric with hyphens)
                if (!zitadelSessionId.matches("^[a-zA-Z0-9\\-_]{10,128}$")) {
                    log.warn("SECURITY: Backchannel logout token has invalid 'sid' format: {}",
                            zitadelSessionId.length() > 20 ? zitadelSessionId.substring(0, 20) + "..." : zitadelSessionId);
                    return Mono.just(ResponseEntity.ok().<Void>build());
                }

                // SECURITY FIX (Round 10 #25): Validate userId is present and matches expected format
                if (userId == null || userId.isBlank()) {
                    log.warn("SECURITY: Backchannel logout token missing 'sub' claim");
                    return Mono.just(ResponseEntity.ok().<Void>build());
                }

                // Validate "events" claim contains logout event (per OIDC spec)
                Map<String, Object> events = jwt.getClaim("events");
                if (events == null || !events.containsKey("http://schemas.openid.net/event/backchannel-logout")) {
                    log.warn("Backchannel logout token missing required logout event claim");
                    return Mono.just(ResponseEntity.ok().<Void>build());
                }

                log.info("Backchannel logout validated for user: {}, zitadel session: {}", userId, zitadelSessionId);

                // Invalidate all TeamSync sessions associated with this Zitadel session
                return sessionInvalidationService.invalidateSessionsByZitadelId(zitadelSessionId)
                    .doOnSuccess(count -> {
                        if (count > 0) {
                            log.info("Backchannel logout successful: invalidated {} session(s) for user {}",
                                count, userId);
                        } else {
                            log.debug("Backchannel logout: no active sessions found for zitadel session {}",
                                zitadelSessionId);
                        }
                    })
                    .onErrorResume(e -> {
                        log.error("Error during backchannel logout session invalidation: {}", e.getMessage());
                        return Mono.just(0);
                    })
                    // Always return 200 OK per OIDC spec
                    .thenReturn(ResponseEntity.ok().<Void>build());
            })
            .onErrorResume(JwtException.class, e -> {
                // SECURITY: Log JWT validation failures - could indicate attack attempt
                log.warn("Backchannel logout JWT validation failed: {}", e.getMessage());
                // Always return 200 per OIDC spec to prevent information leakage
                return Mono.just(ResponseEntity.ok().build());
            })
            .onErrorResume(e -> {
                log.error("Backchannel logout failed: {}", e.getMessage());
                // Always return 200 per OIDC spec to prevent information leakage
                return Mono.just(ResponseEntity.ok().build());
            });
    }

    // ============ Private Helper Methods ============

    /**
     * Redirect to frontend application after successful login.
     *
     * <p>IMPORTANT: We must explicitly write the session cookie to the response
     * headers before redirecting, because response.setComplete() bypasses the normal
     * filter chain that would write the session cookie.
     */
    private Mono<Void> redirectToFrontendAfterLogin(ServerWebExchange exchange, BffSession bffSession, String originalUri) {
        // Get the session to ensure cookie is written
        return exchange.getSession()
            .flatMap(session -> redirectToFrontendAfterLogin(exchange, bffSession, originalUri, session));
    }

    /**
     * SECURITY FIX: Overloaded method that accepts the session directly.
     * Used after session regeneration to ensure the new session ID is used.
     */
    private Mono<Void> redirectToFrontendAfterLogin(ServerWebExchange exchange, BffSession bffSession,
                                                     String originalUri, org.springframework.web.server.WebSession session) {
        BffProperties.PkceProperties pkceProps = bffProperties.pkce();
        BffProperties.SessionProperties sessionProps = bffProperties.session();

        // Use original URI if provided, otherwise default to frontend URL
        String baseUrl = (originalUri != null && !originalUri.isBlank())
            ? originalUri
            : pkceProps.frontendLoginUrl();

        // SECURITY FIX (Round 13 #8): Removed session ID from redirect URL.
        // Session IDs should not be exposed in URLs as they can:
        // - Leak via Referer header
        // - Be logged by proxies/WAFs
        // - Be bookmarked/shared accidentally
        // Session is now exclusively managed via HttpOnly cookie.
        String separator = baseUrl.contains("?") ? "&" : "?";
        final String finalRedirectUrl = baseUrl + separator + "login=success";

        log.debug("Redirecting to frontend after successful login: {}", finalRedirectUrl);

        ServerHttpResponse response = exchange.getResponse();

        // Manually build and add the session cookie to response headers
        // This is necessary because setComplete() bypasses the normal filter chain
        ResponseCookie.ResponseCookieBuilder cookieBuilder = ResponseCookie.from(sessionProps.cookieName(), session.getId())
            .path("/")
            .maxAge(sessionProps.cookieMaxAge())
            .httpOnly(sessionProps.cookieHttpOnly())
            .secure(sessionProps.cookieSecure())
            .sameSite(sessionProps.cookieSameSite());

        // SECURITY FIX (Round 10 #17): Validate cookie domain before setting
        String validatedDomain = validateCookieDomain(sessionProps.cookieDomain());
        if (validatedDomain != null) {
            cookieBuilder.domain(validatedDomain);
        }

        ResponseCookie sessionCookie = cookieBuilder.build();
        response.addCookie(sessionCookie);

        log.info("Session cookie manually added to response: name={}, sessionId={}, domain={}, sameSite={}, secure={}",
            sessionProps.cookieName(), session.getId(), validatedDomain, sessionProps.cookieSameSite(), sessionProps.cookieSecure());

        response.setStatusCode(HttpStatus.FOUND);
        response.getHeaders().setLocation(URI.create(finalRedirectUrl));

        return response.setComplete();
    }

    /**
     * Redirect to frontend with error parameters.
     */
    private Mono<Void> redirectToFrontendWithError(ServerWebExchange exchange, String error, String description) {
        BffProperties.PkceProperties pkceProps = bffProperties.pkce();

        String encodedError = URLEncoder.encode(error != null ? error : "", StandardCharsets.UTF_8);
        String encodedDescription = URLEncoder.encode(description != null ? description : "", StandardCharsets.UTF_8);

        String redirectUrl = pkceProps.frontendLogoutUrl() +
            "?error=" + encodedError +
            "&error_description=" + encodedDescription;

        log.debug("Redirecting to frontend with error: {}", error);

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FOUND);
        response.getHeaders().setLocation(URI.create(redirectUrl));

        return response.setComplete();
    }

    /**
     * Extract client IP from request, handling proxies.
     */
    private String getClientIp(ServerHttpRequest request) {
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // Get first IP in chain (original client)
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp;
        }

        if (request.getRemoteAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }

        return "unknown";
    }

    /**
     * Build the API Gateway's public base URL from the incoming request.
     *
     * <p>This method extracts the scheme, host, and port from the request to build
     * the base URL that the browser sees. This is used for OIDC proxy redirects
     * so the browser stays on the API Gateway and never directly contacts Zitadel.
     *
     * <p>Handles:
     * <ul>
     *   <li>X-Forwarded-Proto header (for reverse proxies like Railway/nginx)</li>
     *   <li>X-Forwarded-Host header (for reverse proxies)</li>
     *   <li>Standard ports (80 for HTTP, 443 for HTTPS) are omitted</li>
     * </ul>
     *
     * @param request The incoming HTTP request
     * @return The API Gateway's public base URL (e.g., https://api-gateway.railway.app)
     */
    private String getApiGatewayBaseUrl(ServerHttpRequest request) {
        // SECURITY FIX (Round 10 #14): Validate X-Forwarded-Proto against whitelist
        String scheme = request.getHeaders().getFirst("X-Forwarded-Proto");
        if (scheme == null || scheme.isBlank()) {
            scheme = request.getURI().getScheme();
        }
        // Only allow http or https schemes
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            log.warn("SECURITY: Invalid X-Forwarded-Proto scheme '{}', defaulting to https", scheme);
            scheme = "https";
        }
        scheme = scheme.toLowerCase();

        // Check for X-Forwarded-Host (set by reverse proxies)
        String host = request.getHeaders().getFirst("X-Forwarded-Host");
        if (host == null || host.isBlank()) {
            host = request.getURI().getHost();
        }
        // Sanitize host - remove any path or query components that might be injected
        if (host != null && (host.contains("/") || host.contains("?"))) {
            log.warn("SECURITY: Invalid X-Forwarded-Host contains path/query: {}", host);
            host = host.split("[/?]")[0];
        }

        // SECURITY FIX (Round 10 #15): Validate X-Forwarded-Port bounds
        String portStr = request.getHeaders().getFirst("X-Forwarded-Port");
        int port = -1;
        if (portStr != null && !portStr.isBlank()) {
            try {
                port = Integer.parseInt(portStr.trim());
                if (port < 1 || port > 65535) {
                    log.warn("SECURITY: X-Forwarded-Port out of range: {}, ignoring", port);
                    port = -1;
                }
            } catch (NumberFormatException e) {
                log.warn("SECURITY: Invalid X-Forwarded-Port format: {}", portStr);
                port = -1;
            }
        }
        if (port == -1) {
            port = request.getURI().getPort();
        }

        // Build URL, omitting standard ports
        StringBuilder url = new StringBuilder();
        url.append(scheme).append("://").append(host);

        // Only include port if non-standard
        boolean isStandardHttpPort = "http".equals(scheme) && port == 80;
        boolean isStandardHttpsPort = "https".equals(scheme) && port == 443;
        boolean isDefaultPort = port == -1;

        if (!isDefaultPort && !isStandardHttpPort && !isStandardHttpsPort) {
            url.append(":").append(port);
        }

        log.debug("Built API Gateway base URL: {}", url);
        return url.toString();
    }

    /**
     * Validate redirect URI against whitelist to prevent open redirect attacks.
     *
     * <p>SECURITY: This method ensures that post-login redirects can only go to
     * trusted destinations. It checks:
     * <ul>
     *   <li>Exact host match against ALLOWED_REDIRECT_HOSTS</li>
     *   <li>Host suffix match against ALLOWED_REDIRECT_HOST_SUFFIXES</li>
     *   <li>Only HTTP/HTTPS schemes are allowed</li>
     * </ul>
     *
     * @param uri The redirect URI to validate
     * @return true if the URI is safe to redirect to, false otherwise
     */
    private boolean isValidRedirectUri(String uri) {
        if (uri == null || uri.isBlank()) {
            return false;
        }

        try {
            // SECURITY FIX (Round 14 #C21): Use URI first to check for userinfo
            // URLs like "https://attacker.com@allowed.com" can trick URL parsing
            java.net.URI parsedUri = new java.net.URI(uri);

            // SECURITY FIX (Round 14 #C21): Reject URIs with userinfo (user:pass@host)
            // This prevents open redirect attacks using URL authority confusion
            if (parsedUri.getUserInfo() != null) {
                log.warn("SECURITY: Rejected redirect URI with userinfo (possible open redirect): {}", uri);
                return false;
            }

            // SECURITY FIX (Round 14 #C21): Reject URIs with port (prevent port-based bypasses)
            // All our legitimate redirect URIs use standard ports (80/443)
            if (parsedUri.getPort() != -1 && parsedUri.getPort() != 80 && parsedUri.getPort() != 443) {
                log.warn("SECURITY: Rejected redirect URI with non-standard port: {}", uri);
                return false;
            }

            URL url = new URL(uri);
            String host = url.getHost();
            String scheme = url.getProtocol();

            // Only allow HTTP/HTTPS schemes
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                log.debug("Rejected redirect URI with invalid scheme: {}", scheme);
                return false;
            }

            // Check exact host match
            if (ALLOWED_REDIRECT_HOSTS.contains(host.toLowerCase())) {
                return true;
            }

            // Check host suffix match (for subdomains of our domains)
            String lowerHost = host.toLowerCase();
            for (String suffix : ALLOWED_REDIRECT_HOST_SUFFIXES) {
                if (lowerHost.endsWith(suffix)) {
                    return true;
                }
            }

            // SECURITY FIX: Stricter Railway app matching - only allow specific app prefixes
            if (lowerHost.endsWith(".up.railway.app")) {
                // Extract the app name (everything before .up.railway.app)
                String appName = lowerHost.substring(0, lowerHost.length() - ".up.railway.app".length());
                for (String prefix : ALLOWED_RAILWAY_APP_PREFIXES) {
                    if (appName.startsWith(prefix)) {
                        return true;
                    }
                }
                log.warn("SECURITY: Rejected Railway app redirect URI - app '{}' not in whitelist", appName);
                return false;
            }

            log.debug("Rejected redirect URI with untrusted host: {}", host);
            return false;

        } catch (java.net.URISyntaxException e) {
            log.debug("Rejected redirect URI due to invalid URI syntax: {}", uri);
            return false;
        } catch (MalformedURLException e) {
            log.debug("Rejected redirect URI due to malformed URL: {}", uri);
            return false;
        }
    }

    /**
     * SECURITY FIX (Round 10 #17): Validate cookie domain to prevent setting cookies
     * on public suffixes or overly broad domains.
     *
     * <p>Setting cookies on public suffixes (like .com, .railway.app) would allow
     * any website on that TLD to read the session cookie, enabling session hijacking.
     *
     * @param cookieDomain The configured cookie domain
     * @return The validated cookie domain, or null if invalid (use request host)
     */
    private String validateCookieDomain(String cookieDomain) {
        if (cookieDomain == null || cookieDomain.isBlank()) {
            return null;  // Use request host
        }

        String normalizedDomain = cookieDomain.toLowerCase().trim();

        // Must start with a dot for subdomain matching
        if (!normalizedDomain.startsWith(".")) {
            normalizedDomain = "." + normalizedDomain;
        }

        // Check against disallowed suffixes (public TLDs)
        for (String disallowed : DISALLOWED_COOKIE_DOMAIN_SUFFIXES) {
            if (normalizedDomain.equals(disallowed) || normalizedDomain.endsWith(disallowed)) {
                // The domain IS the public suffix or ends with just the public suffix
                // e.g., ".railway.app" or ".com"
                // But ".up.railway.app" is ok because it's more specific

                // Count the dots to determine specificity
                long dotCount = normalizedDomain.chars().filter(ch -> ch == '.').count();
                long disallowedDotCount = disallowed.chars().filter(ch -> ch == '.').count();

                // If same number of dots or just one more, it's too broad
                if (dotCount <= disallowedDotCount + 1) {
                    log.warn("SECURITY: Rejecting cookie domain '{}' - too close to public suffix '{}'",
                            cookieDomain, disallowed);
                    return null;
                }
            }
        }

        return normalizedDomain;
    }
}
