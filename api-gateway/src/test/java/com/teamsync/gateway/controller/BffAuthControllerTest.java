package com.teamsync.gateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamsync.gateway.config.BffProperties;
import com.teamsync.gateway.dto.*;
import com.teamsync.gateway.model.BffSession;
import com.teamsync.gateway.service.ZitadelAuthService;
import com.teamsync.gateway.service.LoginEventPublisher;
import com.teamsync.gateway.service.SessionInvalidationService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.web.server.WebSession;
import org.springframework.web.server.session.WebSessionIdResolver;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for BffAuthController.
 * Tests all BFF authentication endpoints: authorize, callback, logout, refresh, session.
 *
 * Note: The BFF uses PKCE-based OIDC flow with Zitadel. There is no direct login endpoint
 * in the BFF - the frontend uses Zitadel's Session API v2 for headless authentication,
 * then redirects to the callback endpoint with an authorization code.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BFF Auth Controller Tests")
class BffAuthControllerTest {

    @Mock
    private ZitadelAuthService zitadelAuthService;

    @Mock
    private LoginEventPublisher loginEventPublisher;

    @Mock
    private SessionInvalidationService sessionInvalidationService;

    @Mock
    private BffProperties bffProperties;

    @Mock
    private WebSession webSession;

    @Mock
    private WebSessionIdResolver webSessionIdResolver;

    @Mock
    private ReactiveJwtDecoder jwtDecoder;

    @Mock
    private ReactiveRedisTemplate<String, Object> bffSessionRedisTemplate;

    private BffAuthController controller;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        controller = new BffAuthController(
            zitadelAuthService,
            loginEventPublisher,
            sessionInvalidationService,
            bffProperties,
            objectMapper,
            webSessionIdResolver,
            jwtDecoder,
            bffSessionRedisTemplate
        );

        // Default mock for session attributes
        Map<String, Object> sessionAttributes = new HashMap<>();
        lenient().when(webSession.getAttributes()).thenReturn(sessionAttributes);
        lenient().when(webSession.invalidate()).thenReturn(Mono.empty());
    }

    @Nested
    @DisplayName("Logout Endpoint Tests")
    class LogoutTests {

        @Test
        @DisplayName("Should successfully logout with valid session")
        void logout_WithValidSession_ReturnsSuccess() {
            // Given
            BffSession session = createBffSession();

            when(webSession.getAttribute(BffSession.SESSION_KEY)).thenReturn(session);
            when(webSession.invalidate()).thenReturn(Mono.empty());
            when(zitadelAuthService.revokeToken(any())).thenReturn(Mono.empty());

            MockServerWebExchange exchange = MockServerWebExchange.builder(
                MockServerHttpRequest.post("/bff/auth/logout").build()
            ).session(webSession).build();

            // When
            Mono<ResponseEntity<LogoutResponse>> result = controller.logout(exchange);

            // Then
            StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody()).isNotNull();
                    assertThat(response.getBody().isSuccess()).isTrue();
                })
                .verifyComplete();

            verify(webSession).invalidate();
            verify(zitadelAuthService).revokeToken(session.getRefreshToken());
        }

        @Test
        @DisplayName("Should succeed even without active session")
        void logout_WithoutSession_ReturnsSuccess() {
            // Given
            when(webSession.getAttribute(BffSession.SESSION_KEY)).thenReturn(null);
            when(webSession.invalidate()).thenReturn(Mono.empty());

            MockServerWebExchange exchange = MockServerWebExchange.builder(
                MockServerHttpRequest.post("/bff/auth/logout").build()
            ).session(webSession).build();

            // When
            Mono<ResponseEntity<LogoutResponse>> result = controller.logout(exchange);

            // Then
            StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody().isSuccess()).isTrue();
                })
                .verifyComplete();

            verify(zitadelAuthService, never()).revokeToken(any());
        }

        @Test
        @DisplayName("Should succeed even if token revocation fails")
        void logout_WhenRevocationFails_StillSucceeds() {
            // Given
            BffSession session = createBffSession();
            when(webSession.getAttribute(BffSession.SESSION_KEY)).thenReturn(session);
            when(webSession.invalidate()).thenReturn(Mono.empty());
            when(zitadelAuthService.revokeToken(any()))
                .thenReturn(Mono.error(new RuntimeException("Revocation failed")));

            MockServerWebExchange exchange = MockServerWebExchange.builder(
                MockServerHttpRequest.post("/bff/auth/logout").build()
            ).session(webSession).build();

            // When
            Mono<ResponseEntity<LogoutResponse>> result = controller.logout(exchange);

            // Then
            StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody().isSuccess()).isTrue();
                })
                .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Refresh Endpoint Tests")
    class RefreshTests {

        @Test
        @DisplayName("Should successfully refresh token")
        void refresh_WithValidSession_ReturnsSuccess() {
            // Given
            BffSession session = createBffSession();
            TokenResponse newTokenResponse = createTokenResponse();

            when(webSession.getAttribute(BffSession.SESSION_KEY)).thenReturn(session);
            when(zitadelAuthService.refresh(session.getRefreshToken()))
                .thenReturn(Mono.just(newTokenResponse));

            MockServerWebExchange exchange = MockServerWebExchange.builder(
                MockServerHttpRequest.post("/bff/auth/refresh").build()
            ).session(webSession).build();

            // When
            Mono<ResponseEntity<RefreshResponse>> result = controller.refresh(exchange);

            // Then
            StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody()).isNotNull();
                    assertThat(response.getBody().isSuccess()).isTrue();
                    assertThat(response.getBody().getExpiresIn()).isEqualTo(3600L);
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("Should return 401 when no session exists")
        void refresh_WithoutSession_ReturnsUnauthorized() {
            // Given
            when(webSession.getAttribute(BffSession.SESSION_KEY)).thenReturn(null);

            MockServerWebExchange exchange = MockServerWebExchange.builder(
                MockServerHttpRequest.post("/bff/auth/refresh").build()
            ).session(webSession).build();

            // When
            Mono<ResponseEntity<RefreshResponse>> result = controller.refresh(exchange);

            // Then
            StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(response.getBody().getErrorCode()).isEqualTo("NO_SESSION");
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("Should return 401 when refresh token expired")
        void refresh_WithExpiredRefreshToken_ReturnsUnauthorized() {
            // Given
            BffSession session = BffSession.builder()
                .userId("user-123")
                .refreshToken("expired-token")
                .refreshTokenExpiresAt(Instant.now().minusSeconds(3600)) // Expired
                .build();

            when(webSession.getAttribute(BffSession.SESSION_KEY)).thenReturn(session);
            when(webSession.invalidate()).thenReturn(Mono.empty());

            MockServerWebExchange exchange = MockServerWebExchange.builder(
                MockServerHttpRequest.post("/bff/auth/refresh").build()
            ).session(webSession).build();

            // When
            Mono<ResponseEntity<RefreshResponse>> result = controller.refresh(exchange);

            // Then
            StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(response.getBody().getErrorCode()).isEqualTo("SESSION_EXPIRED");
                })
                .verifyComplete();

            verify(webSession).invalidate();
        }
    }

    @Nested
    @DisplayName("Session Endpoint Tests")
    class SessionTests {

        @Test
        @DisplayName("Should return authenticated session info")
        void getSession_WithValidSession_ReturnsAuthenticated() {
            // Given
            BffSession session = createBffSession();
            when(webSession.getAttribute(BffSession.SESSION_KEY)).thenReturn(session);

            MockServerWebExchange exchange = MockServerWebExchange.builder(
                MockServerHttpRequest.get("/bff/auth/session").build()
            ).session(webSession).build();

            // When
            Mono<ResponseEntity<SessionResponse>> result = controller.getSession(exchange);

            // Then
            StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody()).isNotNull();
                    assertThat(response.getBody().isAuthenticated()).isTrue();
                    assertThat(response.getBody().getUser()).isNotNull();
                    assertThat(response.getBody().getUser().getId()).isEqualTo("user-123");
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("Should return unauthenticated when no session")
        void getSession_WithoutSession_ReturnsUnauthenticated() {
            // Given
            when(webSession.getAttribute(BffSession.SESSION_KEY)).thenReturn(null);

            MockServerWebExchange exchange = MockServerWebExchange.builder(
                MockServerHttpRequest.get("/bff/auth/session").build()
            ).session(webSession).build();

            // When
            Mono<ResponseEntity<SessionResponse>> result = controller.getSession(exchange);

            // Then
            StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody()).isNotNull();
                    assertThat(response.getBody().isAuthenticated()).isFalse();
                    assertThat(response.getBody().getUser()).isNull();
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("Should return unauthenticated when session expired")
        void getSession_WithExpiredSession_ReturnsUnauthenticated() {
            // Given
            BffSession session = BffSession.builder()
                .userId("user-123")
                .accessTokenExpiresAt(Instant.now().minusSeconds(3600)) // Expired
                .refreshTokenExpiresAt(Instant.now().minusSeconds(3600)) // Also expired
                .build();

            when(webSession.getAttribute(BffSession.SESSION_KEY)).thenReturn(session);

            MockServerWebExchange exchange = MockServerWebExchange.builder(
                MockServerHttpRequest.get("/bff/auth/session").build()
            ).session(webSession).build();

            // When
            Mono<ResponseEntity<SessionResponse>> result = controller.getSession(exchange);

            // Then
            StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody().isAuthenticated()).isFalse();
                })
                .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Backchannel Logout Tests")
    class BackchannelLogoutTests {

        @Test
        @DisplayName("Should handle valid backchannel logout token")
        void backchannelLogout_WithValidToken_ReturnsOk() {
            // Given - Create a mock JWT logout token
            String logoutToken = createMockLogoutToken("user-123", "session-456");

            // Mock the JWT decoder to return a valid JWT with required claims
            Jwt mockJwt = Jwt.withTokenValue(logoutToken)
                .header("alg", "RS256")
                .subject("user-123")
                .claim("sid", "session-456")
                .claim("events", Map.of("http://schemas.openid.net/event/backchannel-logout", Map.of()))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

            when(jwtDecoder.decode(logoutToken)).thenReturn(Mono.just(mockJwt));

            // Mock the session invalidation service
            when(sessionInvalidationService.invalidateSessionsByZitadelId("session-456"))
                .thenReturn(Mono.just(1));

            // When
            Mono<ResponseEntity<Void>> result = controller.backchannelLogout(logoutToken);

            // Then
            StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                })
                .verifyComplete();

            // Verify session invalidation was called
            verify(sessionInvalidationService).invalidateSessionsByZitadelId("session-456");
        }

        @Test
        @DisplayName("Should return OK even with invalid token format")
        void backchannelLogout_WithInvalidToken_StillReturnsOk() {
            // Given - Invalid token format
            String invalidToken = "not-a-valid-jwt";

            // Mock the JWT decoder to throw an exception for invalid tokens
            when(jwtDecoder.decode(invalidToken))
                .thenReturn(Mono.error(new JwtException("Invalid token")));

            // When
            Mono<ResponseEntity<Void>> result = controller.backchannelLogout(invalidToken);

            // Then - Per OIDC spec, always return 200
            StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("Should return OK when JWT missing sid claim")
        void backchannelLogout_WithMissingSid_ReturnsOk() {
            // Given
            String logoutToken = "token-without-sid";

            // Mock the JWT decoder to return a JWT without sid claim
            Jwt mockJwt = Jwt.withTokenValue(logoutToken)
                .header("alg", "RS256")
                .subject("user-123")
                .claim("events", Map.of("http://schemas.openid.net/event/backchannel-logout", Map.of()))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

            when(jwtDecoder.decode(logoutToken)).thenReturn(Mono.just(mockJwt));

            // When
            Mono<ResponseEntity<Void>> result = controller.backchannelLogout(logoutToken);

            // Then - Per OIDC spec, always return 200
            StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                })
                .verifyComplete();

            // Session invalidation should NOT be called since sid is missing
            verify(sessionInvalidationService, never()).invalidateSessionsByZitadelId(any());
        }

        @Test
        @DisplayName("Should return OK when JWT missing logout event claim")
        void backchannelLogout_WithMissingEventsClaim_ReturnsOk() {
            // Given
            String logoutToken = "token-without-events";

            // Mock the JWT decoder to return a JWT without events claim
            Jwt mockJwt = Jwt.withTokenValue(logoutToken)
                .header("alg", "RS256")
                .subject("user-123")
                .claim("sid", "session-456")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

            when(jwtDecoder.decode(logoutToken)).thenReturn(Mono.just(mockJwt));

            // When
            Mono<ResponseEntity<Void>> result = controller.backchannelLogout(logoutToken);

            // Then - Per OIDC spec, always return 200
            StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                })
                .verifyComplete();

            // Session invalidation should NOT be called since events claim is missing
            verify(sessionInvalidationService, never()).invalidateSessionsByZitadelId(any());
        }
    }

    @Nested
    @DisplayName("PKCE Authorization Flow Tests")
    class PkceAuthorizationTests {

        @Test
        @DisplayName("Should redirect to Zitadel authorization endpoint with PKCE parameters")
        void authorize_ShouldRedirectWithPkceParameters() {
            // Given
            String expectedAuthUrl = "http://zitadel:8080/oauth/v2/authorize?client_id=test-client&response_type=code&code_challenge=abc123";

            when(zitadelAuthService.buildAuthorizationUrlViaProxy(any(), any()))
                .thenReturn(expectedAuthUrl);

            MockServerWebExchange exchange = MockServerWebExchange.builder(
                MockServerHttpRequest.get("/bff/auth/authorize").build()
            ).session(webSession).build();

            // When
            Mono<Void> result = controller.authorize(null, exchange);

            // Then
            StepVerifier.create(result)
                .verifyComplete();

            // Verify redirect was set
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FOUND);
            assertThat(exchange.getResponse().getHeaders().getLocation())
                .isNotNull()
                .hasToString(expectedAuthUrl);

            // Verify PKCE parameters were stored in session
            verify(webSession, atLeastOnce()).getAttributes();
        }

        @Test
        @DisplayName("Should store original redirect URI in session when provided")
        void authorize_WithRedirectUri_StoresInSession() {
            // Given
            String redirectUri = "http://localhost:3000/dashboard";
            String expectedAuthUrl = "http://localhost:9080/oauth/v2/authorize";

            when(zitadelAuthService.buildAuthorizationUrlViaProxy(any(), any()))
                .thenReturn(expectedAuthUrl);

            Map<String, Object> sessionAttrs = new HashMap<>();
            when(webSession.getAttributes()).thenReturn(sessionAttrs);

            MockServerWebExchange exchange = MockServerWebExchange.builder(
                MockServerHttpRequest.get("/bff/auth/authorize")
                    .queryParam("redirectUri", redirectUri)
                    .build()
            ).session(webSession).build();

            // When
            Mono<Void> result = controller.authorize(redirectUri, exchange);

            // Then
            StepVerifier.create(result)
                .verifyComplete();

            // Verify redirect URI was stored
            assertThat(sessionAttrs.get("original_uri")).isEqualTo(redirectUri);
        }
    }

    @Nested
    @DisplayName("PKCE Callback Tests")
    class PkceCallbackTests {

        @BeforeEach
        void setUpPkceProperties() {
            // Mock PKCE properties for callback tests
            BffProperties.PkceProperties pkceProps = new BffProperties.PkceProperties(
                true,
                "http://localhost:9080/bff/auth/callback",
                "http://localhost:3000/auth/login",
                "http://localhost:3000",
                "http://localhost:3000/auth/login",
                "openid email profile"
            );
            lenient().when(bffProperties.pkce()).thenReturn(pkceProps);
        }

        @Test
        @DisplayName("Should exchange authorization code and create session")
        void callback_WithValidCode_CreatesSession() {
            // Given
            String code = "authorization-code-123";
            String state = "state-parameter-456";
            String codeVerifier = "code-verifier-789";

            Map<String, Object> sessionAttrs = new HashMap<>();
            sessionAttrs.put("pkce_state", state);
            sessionAttrs.put("pkce_code_verifier", codeVerifier);

            when(webSession.getAttributes()).thenReturn(sessionAttrs);
            when(webSession.getAttribute("pkce_state")).thenReturn(state);
            when(webSession.getAttribute("pkce_code_verifier")).thenReturn(codeVerifier);
            when(webSession.getAttribute("original_uri")).thenReturn(null);

            TokenResponse tokenResponse = createTokenResponse();
            BffSession bffSession = createBffSession();

            when(zitadelAuthService.exchangeAuthorizationCode(code, codeVerifier))
                .thenReturn(Mono.just(tokenResponse));
            when(zitadelAuthService.createBffSession(any(), any(), any()))
                .thenReturn(bffSession);
            when(loginEventPublisher.publishLoginEvent(any()))
                .thenReturn(Mono.empty());

            MockServerWebExchange exchange = MockServerWebExchange.builder(
                MockServerHttpRequest.get("/bff/auth/callback")
                    .queryParam("code", code)
                    .queryParam("state", state)
                    .build()
            ).session(webSession).build();

            // When
            Mono<Void> result = controller.callback(code, state, null, null, exchange);

            // Then
            StepVerifier.create(result)
                .verifyComplete();

            // Verify token exchange was called
            verify(zitadelAuthService).exchangeAuthorizationCode(code, codeVerifier);

            // Verify session was created
            verify(zitadelAuthService).createBffSession(eq(tokenResponse), any(), any());

            // Verify login event was published
            verify(loginEventPublisher).publishLoginEvent(any());

            // Verify redirect to frontend with success
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FOUND);
            String location = exchange.getResponse().getHeaders().getLocation().toString();
            assertThat(location).contains("login=success");
        }

        @Test
        @DisplayName("Should redirect with error when state mismatch")
        void callback_WithStateMismatch_RedirectsWithError() {
            // Given
            String code = "authorization-code-123";
            String receivedState = "wrong-state";
            String savedState = "correct-state";

            when(webSession.getAttribute("pkce_state")).thenReturn(savedState);

            MockServerWebExchange exchange = MockServerWebExchange.builder(
                MockServerHttpRequest.get("/bff/auth/callback")
                    .queryParam("code", code)
                    .queryParam("state", receivedState)
                    .build()
            ).session(webSession).build();

            // When
            Mono<Void> result = controller.callback(code, receivedState, null, null, exchange);

            // Then
            StepVerifier.create(result)
                .verifyComplete();

            // Verify redirect with error
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FOUND);
            String location = exchange.getResponse().getHeaders().getLocation().toString();
            assertThat(location).contains("error=invalid_state");
        }

        @Test
        @DisplayName("Should redirect with error when authorization fails")
        void callback_WithAuthError_RedirectsWithError() {
            // Given
            String error = "access_denied";
            String errorDescription = "User denied access";

            MockServerWebExchange exchange = MockServerWebExchange.builder(
                MockServerHttpRequest.get("/bff/auth/callback")
                    .queryParam("error", error)
                    .queryParam("error_description", errorDescription)
                    .build()
            ).session(webSession).build();

            // When
            Mono<Void> result = controller.callback(null, null, error, errorDescription, exchange);

            // Then
            StepVerifier.create(result)
                .verifyComplete();

            // Verify redirect with error
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FOUND);
            String location = exchange.getResponse().getHeaders().getLocation().toString();
            assertThat(location).contains("error=access_denied");
        }

        @Test
        @DisplayName("Should redirect with error when no code provided")
        void callback_WithoutCode_RedirectsWithError() {
            // Given
            MockServerWebExchange exchange = MockServerWebExchange.builder(
                MockServerHttpRequest.get("/bff/auth/callback")
                    .queryParam("state", "some-state")
                    .build()
            ).session(webSession).build();

            // When
            Mono<Void> result = controller.callback(null, "some-state", null, null, exchange);

            // Then
            StepVerifier.create(result)
                .verifyComplete();

            // Verify redirect with error
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FOUND);
            String location = exchange.getResponse().getHeaders().getLocation().toString();
            assertThat(location).contains("error=missing_code");
        }

        @Test
        @DisplayName("Should redirect with error when code verifier missing from session")
        void callback_WithoutCodeVerifier_RedirectsWithError() {
            // Given
            String code = "authorization-code-123";
            String state = "state-parameter-456";

            // No code verifier in session
            when(webSession.getAttribute("pkce_state")).thenReturn(state);
            when(webSession.getAttribute("pkce_code_verifier")).thenReturn(null);

            MockServerWebExchange exchange = MockServerWebExchange.builder(
                MockServerHttpRequest.get("/bff/auth/callback")
                    .queryParam("code", code)
                    .queryParam("state", state)
                    .build()
            ).session(webSession).build();

            // When
            Mono<Void> result = controller.callback(code, state, null, null, exchange);

            // Then
            StepVerifier.create(result)
                .verifyComplete();

            // Verify redirect with error
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FOUND);
            String location = exchange.getResponse().getHeaders().getLocation().toString();
            assertThat(location).contains("error=missing_verifier");
        }

        @Test
        @DisplayName("Should redirect with error when token exchange fails")
        void callback_WhenExchangeFails_RedirectsWithError() {
            // Given
            String code = "authorization-code-123";
            String state = "state-parameter-456";
            String codeVerifier = "code-verifier-789";

            Map<String, Object> sessionAttrs = new HashMap<>();
            sessionAttrs.put("pkce_state", state);
            sessionAttrs.put("pkce_code_verifier", codeVerifier);

            when(webSession.getAttributes()).thenReturn(sessionAttrs);
            when(webSession.getAttribute("pkce_state")).thenReturn(state);
            when(webSession.getAttribute("pkce_code_verifier")).thenReturn(codeVerifier);

            when(zitadelAuthService.exchangeAuthorizationCode(code, codeVerifier))
                .thenReturn(Mono.error(new ZitadelAuthService.AuthenticationException(
                    "Invalid code", "CODE_EXCHANGE_FAILED")));

            MockServerWebExchange exchange = MockServerWebExchange.builder(
                MockServerHttpRequest.get("/bff/auth/callback")
                    .queryParam("code", code)
                    .queryParam("state", state)
                    .build()
            ).session(webSession).build();

            // When
            Mono<Void> result = controller.callback(code, state, null, null, exchange);

            // Then
            StepVerifier.create(result)
                .verifyComplete();

            // Verify redirect with error
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FOUND);
            String location = exchange.getResponse().getHeaders().getLocation().toString();
            assertThat(location).contains("error=CODE_EXCHANGE_FAILED");
        }

        @Test
        @DisplayName("Should use original redirect URI when provided")
        void callback_WithOriginalUri_RedirectsToOriginalUri() {
            // Given
            String code = "authorization-code-123";
            String state = "state-parameter-456";
            String codeVerifier = "code-verifier-789";
            String originalUri = "http://localhost:3000/documents/123";

            Map<String, Object> sessionAttrs = new HashMap<>();
            sessionAttrs.put("pkce_state", state);
            sessionAttrs.put("pkce_code_verifier", codeVerifier);
            sessionAttrs.put("original_uri", originalUri);

            when(webSession.getAttributes()).thenReturn(sessionAttrs);
            when(webSession.getAttribute("pkce_state")).thenReturn(state);
            when(webSession.getAttribute("pkce_code_verifier")).thenReturn(codeVerifier);
            when(webSession.getAttribute("original_uri")).thenReturn(originalUri);

            TokenResponse tokenResponse = createTokenResponse();
            BffSession bffSession = createBffSession();

            when(zitadelAuthService.exchangeAuthorizationCode(code, codeVerifier))
                .thenReturn(Mono.just(tokenResponse));
            when(zitadelAuthService.createBffSession(any(), any(), any()))
                .thenReturn(bffSession);
            when(loginEventPublisher.publishLoginEvent(any()))
                .thenReturn(Mono.empty());

            MockServerWebExchange exchange = MockServerWebExchange.builder(
                MockServerHttpRequest.get("/bff/auth/callback")
                    .queryParam("code", code)
                    .queryParam("state", state)
                    .build()
            ).session(webSession).build();

            // When
            Mono<Void> result = controller.callback(code, state, null, null, exchange);

            // Then
            StepVerifier.create(result)
                .verifyComplete();

            // Verify redirect to original URI with success
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FOUND);
            String location = exchange.getResponse().getHeaders().getLocation().toString();
            assertThat(location).startsWith(originalUri);
            assertThat(location).contains("login=success");
        }
    }

    // Helper methods

    private TokenResponse createTokenResponse() {
        return TokenResponse.builder()
            .accessToken("access-token-123")
            .refreshToken("refresh-token-456")
            .idToken("id-token-789")
            .tokenType("Bearer")
            .expiresIn(3600)
            .refreshExpiresIn(28800)
            .sessionState("session-state")
            .scope("openid email profile")
            .build();
    }

    private BffSession createBffSession() {
        return BffSession.builder()
            .userId("user-123")
            .email("user@example.com")
            .name("Test User")
            .username("testuser")
            .tenantId("default")
            .roles(List.of("user"))
            .superAdmin(false)
            .orgAdmin(false)
            .departmentAdmin(false)
            .accessToken("access-token-123")
            .refreshToken("refresh-token-456")
            .accessTokenExpiresAt(Instant.now().plusSeconds(3600))
            .refreshTokenExpiresAt(Instant.now().plusSeconds(28800))
            .createdAt(Instant.now())
            .lastActivityAt(Instant.now())
            .clientIp("127.0.0.1")
            .build();
    }

    private String createMockLogoutToken(String userId, String sessionId) {
        // Create a minimal JWT structure for testing
        String header = java.util.Base64.getUrlEncoder().encodeToString(
            "{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes());
        String payload = java.util.Base64.getUrlEncoder().encodeToString(
            String.format("{\"sub\":\"%s\",\"sid\":\"%s\"}", userId, sessionId).getBytes());
        String signature = java.util.Base64.getUrlEncoder().encodeToString("signature".getBytes());

        return header + "." + payload + "." + signature;
    }
}
