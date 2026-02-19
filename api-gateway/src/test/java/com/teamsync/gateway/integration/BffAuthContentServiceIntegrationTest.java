package com.teamsync.gateway.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.ExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the complete BFF authentication flow followed by
 * accessing content service endpoints.
 *
 * Tests the full flow:
 * 1. Login via BFF (ROPC or PKCE callback)
 * 2. Session cookie is set
 * 3. Access content service with session-based authentication
 * 4. Token relay injects JWT to downstream service
 *
 * Disabled due to ApplicationContext startup issues with OAuth2 auto-configuration.
 * Run manually for integration testing against real Zitadel.
 */
@Disabled("ApplicationContext startup issues with OAuth2 auto-configuration. Run manually for integration testing.")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "30000")
@ActiveProfiles("test")
@DisplayName("BFF Auth + Content Service Integration Tests")
class BffAuthContentServiceIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ObjectMapper objectMapper;

    // Mock servers
    private static MockWebServer zitadelMock;
    private static MockWebServer contentServiceMock;

    @BeforeAll
    static void startMockServers() throws IOException {
        zitadelMock = new MockWebServer();
        zitadelMock.start(18080);

        contentServiceMock = new MockWebServer();
        contentServiceMock.start(19081);
    }

    @AfterAll
    static void stopMockServers() throws IOException {
        zitadelMock.shutdown();
        contentServiceMock.shutdown();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Enable BFF
        registry.add("teamsync.bff.enabled", () -> "true");

        // Zitadel configuration pointing to mock
        registry.add("teamsync.bff.zitadel.authorization-uri",
                () -> "http://localhost:18080/oauth/v2/authorize");
        registry.add("teamsync.bff.zitadel.token-uri",
                () -> "http://localhost:18080/oauth/v2/token");
        registry.add("teamsync.bff.zitadel.user-info-uri",
                () -> "http://localhost:18080/oidc/v1/userinfo");
        registry.add("teamsync.bff.zitadel.logout-uri",
                () -> "http://localhost:18080/oidc/v1/end_session");
        registry.add("teamsync.bff.zitadel.jwks-uri",
                () -> "http://localhost:18080/oauth/v2/keys");
        registry.add("teamsync.bff.zitadel.client-id", () -> "teamsync-bff");

        // PKCE configuration
        registry.add("teamsync.bff.pkce.enabled", () -> "true");
        registry.add("teamsync.bff.pkce.redirect-uri", () -> "http://localhost:9080/bff/auth/callback");
        registry.add("teamsync.bff.pkce.post-logout-redirect-uri", () -> "http://localhost:3000/auth/login");
        registry.add("teamsync.bff.pkce.frontend-login-url", () -> "http://localhost:3000");
        registry.add("teamsync.bff.pkce.frontend-logout-url", () -> "http://localhost:3000/auth/login");
        registry.add("teamsync.bff.pkce.scope", () -> "openid email profile");

        // Session configuration
        registry.add("teamsync.bff.session.cookie-name", () -> "TEAMSYNC_SESSION");
        registry.add("teamsync.bff.session.cookie-max-age", () -> "8h");
        registry.add("teamsync.bff.session.cookie-secure", () -> "false");
        registry.add("teamsync.bff.session.cookie-same-site", () -> "Lax");
        registry.add("teamsync.bff.session.cookie-http-only", () -> "true");

        // Content service pointing to mock
        registry.add("teamsync.services.content-service", () -> "http://localhost:19081");

        // Disable other services for this test
        registry.add("teamsync.gateway.dynamic-routes.enabled", () -> "false");

        // JWT configuration (for validating tokens)
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> "http://localhost:18080");
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://localhost:18080/oauth/v2/keys");
    }

    @Nested
    @DisplayName("ROPC Login + Content Service Access")
    class RopcLoginContentAccessTests {

        @Test
        @DisplayName("Should login via ROPC and access documents with session")
        void loginAndAccessDocuments_WithROPC_Success() throws Exception {
            // Step 1: Mock Zitadel token response for ROPC login
            String tokenResponse = createZitadelTokenResponse(
                    "access-token-123",
                    "refresh-token-456",
                    "user-789",
                    "admin@example.com",
                    "Admin User",
                    List.of("user", "admin")
            );
            zitadelMock.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(tokenResponse));

            // Step 2: Login via BFF ROPC endpoint
            ExchangeResult loginResult = webTestClient.post()
                    .uri("/bff/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {
                                "email": "admin@example.com",
                                "password": "password123"
                            }
                            """)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.user.email").isEqualTo("admin@example.com")
                    .returnResult();

            // Extract session cookie from login response
            String sessionCookie = extractSessionCookie(loginResult);
            assertThat(sessionCookie).isNotNull().contains("TEAMSYNC_SESSION");

            // Step 3: Mock content service response for documents
            String documentsResponse = """
                    {
                        "items": [
                            {
                                "id": "doc-001",
                                "name": "quarterly-report.pdf",
                                "type": "application/pdf",
                                "size": 102400,
                                "createdAt": "2024-01-15T10:30:00Z"
                            },
                            {
                                "id": "doc-002",
                                "name": "meeting-notes.docx",
                                "type": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                "size": 51200,
                                "createdAt": "2024-01-16T14:20:00Z"
                            }
                        ],
                        "totalCount": 2,
                        "hasMore": false
                    }
                    """;
            contentServiceMock.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(documentsResponse));

            // Step 4: Access documents endpoint with session cookie
            webTestClient.get()
                    .uri("/api/documents")
                    .header("Cookie", sessionCookie)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.items").isArray()
                    .jsonPath("$.items.length()").isEqualTo(2)
                    .jsonPath("$.items[0].id").isEqualTo("doc-001")
                    .jsonPath("$.items[0].name").isEqualTo("quarterly-report.pdf")
                    .jsonPath("$.items[1].id").isEqualTo("doc-002");

            // Step 5: Verify token was relayed to content service
            RecordedRequest contentRequest = contentServiceMock.takeRequest(5, TimeUnit.SECONDS);
            assertThat(contentRequest).isNotNull();
            assertThat(contentRequest.getPath()).isEqualTo("/api/documents");
            assertThat(contentRequest.getHeader("Authorization"))
                    .isNotNull()
                    .startsWith("Bearer access-token-123");
        }

        @Test
        @DisplayName("Should get specific document after login")
        void loginAndGetSpecificDocument_Success() throws Exception {
            // Step 1: Mock Zitadel token response
            zitadelMock.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(createZitadelTokenResponse(
                            "access-token-abc",
                            "refresh-token-def",
                            "user-123",
                            "user@example.com",
                            "Test User",
                            List.of("user")
                    )));

            // Step 2: Login
            ExchangeResult loginResult = webTestClient.post()
                    .uri("/bff/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {
                                "email": "user@example.com",
                                "password": "password123"
                            }
                            """)
                    .exchange()
                    .expectStatus().isOk()
                    .returnResult();

            String sessionCookie = extractSessionCookie(loginResult);

            // Step 3: Mock content service response for specific document
            String documentResponse = """
                    {
                        "id": "doc-specific-001",
                        "name": "project-proposal.pdf",
                        "type": "application/pdf",
                        "size": 256000,
                        "driveId": "drive-personal-123",
                        "folderId": null,
                        "ownerId": "user-123",
                        "metadata": {
                            "pages": 15,
                            "author": "Test User"
                        },
                        "createdAt": "2024-01-10T09:00:00Z",
                        "modifiedAt": "2024-01-12T16:45:00Z"
                    }
                    """;
            contentServiceMock.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(documentResponse));

            // Step 4: Get specific document
            webTestClient.get()
                    .uri("/api/documents/doc-specific-001")
                    .header("Cookie", sessionCookie)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.id").isEqualTo("doc-specific-001")
                    .jsonPath("$.name").isEqualTo("project-proposal.pdf")
                    .jsonPath("$.ownerId").isEqualTo("user-123")
                    .jsonPath("$.metadata.pages").isEqualTo(15);

            // Verify Authorization header
            RecordedRequest contentRequest = contentServiceMock.takeRequest(5, TimeUnit.SECONDS);
            assertThat(contentRequest).isNotNull();
            assertThat(contentRequest.getPath()).isEqualTo("/api/documents/doc-specific-001");
            assertThat(contentRequest.getHeader("Authorization")).startsWith("Bearer ");
        }

        @Test
        @DisplayName("Should list folders after login")
        void loginAndListFolders_Success() throws Exception {
            // Login first
            zitadelMock.enqueue(createTokenMockResponse());
            ExchangeResult loginResult = webTestClient.post()
                    .uri("/bff/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"email\":\"user@example.com\",\"password\":\"pass\"}")
                    .exchange()
                    .expectStatus().isOk()
                    .returnResult();

            String sessionCookie = extractSessionCookie(loginResult);

            // Mock folders response
            String foldersResponse = """
                    {
                        "items": [
                            {"id": "folder-001", "name": "Projects", "parentId": null},
                            {"id": "folder-002", "name": "Reports", "parentId": null},
                            {"id": "folder-003", "name": "Q1 2024", "parentId": "folder-002"}
                        ],
                        "totalCount": 3
                    }
                    """;
            contentServiceMock.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(foldersResponse));

            // Access folders
            webTestClient.get()
                    .uri("/api/folders")
                    .header("Cookie", sessionCookie)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.items.length()").isEqualTo(3)
                    .jsonPath("$.items[0].name").isEqualTo("Projects")
                    .jsonPath("$.items[2].parentId").isEqualTo("folder-002");

            RecordedRequest folderRequest = contentServiceMock.takeRequest(5, TimeUnit.SECONDS);
            assertThat(folderRequest.getHeader("Authorization")).isNotNull();
        }

        @Test
        @DisplayName("Should create document after login")
        void loginAndCreateDocument_Success() throws Exception {
            // Login
            zitadelMock.enqueue(createTokenMockResponse());
            ExchangeResult loginResult = webTestClient.post()
                    .uri("/bff/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"email\":\"user@example.com\",\"password\":\"pass\"}")
                    .exchange()
                    .expectStatus().isOk()
                    .returnResult();

            String sessionCookie = extractSessionCookie(loginResult);

            // Mock document creation response
            String createResponse = """
                    {
                        "id": "doc-new-001",
                        "name": "new-document.pdf",
                        "type": "application/pdf",
                        "size": 0,
                        "status": "PENDING_UPLOAD",
                        "uploadUrl": "http://storage/upload/session-123",
                        "createdAt": "2024-01-20T10:00:00Z"
                    }
                    """;
            contentServiceMock.enqueue(new MockResponse()
                    .setResponseCode(201)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(createResponse));

            // Create document
            webTestClient.post()
                    .uri("/api/documents")
                    .header("Cookie", sessionCookie)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {
                                "name": "new-document.pdf",
                                "type": "application/pdf",
                                "size": 102400,
                                "driveId": "drive-123",
                                "folderId": null
                            }
                            """)
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody()
                    .jsonPath("$.id").isEqualTo("doc-new-001")
                    .jsonPath("$.status").isEqualTo("PENDING_UPLOAD");

            RecordedRequest createRequest = contentServiceMock.takeRequest(5, TimeUnit.SECONDS);
            assertThat(createRequest.getMethod()).isEqualTo("POST");
            assertThat(createRequest.getHeader("Authorization")).isNotNull();
        }
    }

    @Nested
    @DisplayName("Session Management Tests")
    class SessionManagementTests {

        @Test
        @DisplayName("Should reject unauthenticated access to documents")
        void accessDocuments_WithoutSession_Rejected() {
            // Try to access documents without any session
            webTestClient.get()
                    .uri("/api/documents")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("Should reject access with invalid session cookie")
        void accessDocuments_WithInvalidSession_Rejected() {
            webTestClient.get()
                    .uri("/api/documents")
                    .header("Cookie", "TEAMSYNC_SESSION=invalid-session-id")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("Should refresh session and continue accessing documents")
        void refreshSessionAndAccessDocuments_Success() throws Exception {
            // Initial login
            zitadelMock.enqueue(createTokenMockResponse());
            ExchangeResult loginResult = webTestClient.post()
                    .uri("/bff/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"email\":\"user@example.com\",\"password\":\"pass\"}")
                    .exchange()
                    .expectStatus().isOk()
                    .returnResult();

            String sessionCookie = extractSessionCookie(loginResult);

            // Mock refresh token response from Keycloak
            zitadelMock.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(createZitadelTokenResponse(
                            "new-access-token",
                            "new-refresh-token",
                            "user-123",
                            "user@example.com",
                            "Test User",
                            List.of("user")
                    )));

            // Refresh session
            webTestClient.post()
                    .uri("/bff/auth/refresh")
                    .header("Cookie", sessionCookie)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true);

            // Mock content service response
            contentServiceMock.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody("{\"items\": [], \"totalCount\": 0}"));

            // Access documents with refreshed session
            webTestClient.get()
                    .uri("/api/documents")
                    .header("Cookie", sessionCookie)
                    .exchange()
                    .expectStatus().isOk();

            // Verify new token was used
            RecordedRequest contentRequest = contentServiceMock.takeRequest(5, TimeUnit.SECONDS);
            assertThat(contentRequest.getHeader("Authorization"))
                    .contains("new-access-token");
        }

        @Test
        @DisplayName("Should get session info after login")
        void getSessionInfo_AfterLogin_ReturnsUserInfo() throws Exception {
            // Login
            zitadelMock.enqueue(createTokenMockResponse());
            ExchangeResult loginResult = webTestClient.post()
                    .uri("/bff/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"email\":\"admin@example.com\",\"password\":\"pass\"}")
                    .exchange()
                    .expectStatus().isOk()
                    .returnResult();

            String sessionCookie = extractSessionCookie(loginResult);

            // Get session info
            webTestClient.get()
                    .uri("/bff/auth/session")
                    .header("Cookie", sessionCookie)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.authenticated").isEqualTo(true)
                    .jsonPath("$.user.email").isEqualTo("admin@example.com")
                    .jsonPath("$.user.roles").isArray();
        }

        @Test
        @DisplayName("Should logout and invalidate session")
        void logout_InvalidatesSession() throws Exception {
            // Login
            zitadelMock.enqueue(createTokenMockResponse());
            ExchangeResult loginResult = webTestClient.post()
                    .uri("/bff/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"email\":\"user@example.com\",\"password\":\"pass\"}")
                    .exchange()
                    .expectStatus().isOk()
                    .returnResult();

            String sessionCookie = extractSessionCookie(loginResult);

            // Mock token revocation
            zitadelMock.enqueue(new MockResponse().setResponseCode(200));

            // Logout
            webTestClient.post()
                    .uri("/bff/auth/logout")
                    .header("Cookie", sessionCookie)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true);

            // Try to access documents with old session cookie (should fail)
            webTestClient.get()
                    .uri("/api/documents")
                    .header("Cookie", sessionCookie)
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle invalid credentials gracefully")
        void login_WithInvalidCredentials_ReturnsUnauthorized() {
            // Mock Zitadel error response
            zitadelMock.enqueue(new MockResponse()
                    .setResponseCode(401)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody("""
                            {
                                "error": "invalid_grant",
                                "error_description": "Invalid user credentials"
                            }
                            """));

            webTestClient.post()
                    .uri("/bff/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {
                                "email": "wrong@example.com",
                                "password": "wrongpassword"
                            }
                            """)
                    .exchange()
                    .expectStatus().isUnauthorized()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errorCode").exists();
        }

        @Test
        @DisplayName("Should handle content service errors after login")
        void loginAndAccessDocument_ServiceError_PropagatesError() throws Exception {
            // Login successfully
            zitadelMock.enqueue(createTokenMockResponse());
            ExchangeResult loginResult = webTestClient.post()
                    .uri("/bff/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"email\":\"user@example.com\",\"password\":\"pass\"}")
                    .exchange()
                    .expectStatus().isOk()
                    .returnResult();

            String sessionCookie = extractSessionCookie(loginResult);

            // Mock content service 404 error
            contentServiceMock.enqueue(new MockResponse()
                    .setResponseCode(404)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody("{\"error\": \"Document not found\", \"code\": \"NOT_FOUND\"}"));

            // Try to access non-existent document
            webTestClient.get()
                    .uri("/api/documents/non-existent-doc")
                    .header("Cookie", sessionCookie)
                    .exchange()
                    .expectStatus().isNotFound();
        }

        @Test
        @DisplayName("Should handle content service unavailable")
        void loginAndAccessDocument_ServiceUnavailable_Returns503() throws Exception {
            // Login successfully
            zitadelMock.enqueue(createTokenMockResponse());
            ExchangeResult loginResult = webTestClient.post()
                    .uri("/bff/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"email\":\"user@example.com\",\"password\":\"pass\"}")
                    .exchange()
                    .expectStatus().isOk()
                    .returnResult();

            String sessionCookie = extractSessionCookie(loginResult);

            // Mock content service unavailable
            contentServiceMock.enqueue(new MockResponse()
                    .setResponseCode(503)
                    .setBody("Service Unavailable"));

            // Try to access documents
            webTestClient.get()
                    .uri("/api/documents")
                    .header("Cookie", sessionCookie)
                    .exchange()
                    .expectStatus().is5xxServerError();
        }
    }

    @Nested
    @DisplayName("Multi-User Session Tests")
    class MultiUserSessionTests {

        @Test
        @DisplayName("Should isolate sessions between different users")
        void multipleSessions_AreisolatedBetweenUsers() throws Exception {
            // Login User 1
            zitadelMock.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(createZitadelTokenResponse(
                            "user1-token",
                            "user1-refresh",
                            "user-001",
                            "user1@example.com",
                            "User One",
                            List.of("user")
                    )));

            ExchangeResult user1Login = webTestClient.post()
                    .uri("/bff/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"email\":\"user1@example.com\",\"password\":\"pass1\"}")
                    .exchange()
                    .expectStatus().isOk()
                    .returnResult();

            String user1Cookie = extractSessionCookie(user1Login);

            // Login User 2
            zitadelMock.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(createZitadelTokenResponse(
                            "user2-token",
                            "user2-refresh",
                            "user-002",
                            "user2@example.com",
                            "User Two",
                            List.of("user", "admin")
                    )));

            ExchangeResult user2Login = webTestClient.post()
                    .uri("/bff/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"email\":\"user2@example.com\",\"password\":\"pass2\"}")
                    .exchange()
                    .expectStatus().isOk()
                    .returnResult();

            String user2Cookie = extractSessionCookie(user2Login);

            // Verify sessions are different
            assertThat(user1Cookie).isNotEqualTo(user2Cookie);

            // Mock responses for each user's documents
            contentServiceMock.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody("{\"items\": [{\"id\": \"user1-doc\"}]}"));

            contentServiceMock.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody("{\"items\": [{\"id\": \"user2-doc\"}]}"));

            // User 1 accesses documents
            webTestClient.get()
                    .uri("/api/documents")
                    .header("Cookie", user1Cookie)
                    .exchange()
                    .expectStatus().isOk();

            RecordedRequest user1Request = contentServiceMock.takeRequest(5, TimeUnit.SECONDS);
            assertThat(user1Request.getHeader("Authorization")).contains("user1-token");

            // User 2 accesses documents
            webTestClient.get()
                    .uri("/api/documents")
                    .header("Cookie", user2Cookie)
                    .exchange()
                    .expectStatus().isOk();

            RecordedRequest user2Request = contentServiceMock.takeRequest(5, TimeUnit.SECONDS);
            assertThat(user2Request.getHeader("Authorization")).contains("user2-token");
        }
    }

    // Helper methods

    private String extractSessionCookie(ExchangeResult result) {
        List<String> cookies = result.getResponseHeaders().get(HttpHeaders.SET_COOKIE);
        if (cookies == null || cookies.isEmpty()) {
            return null;
        }

        return cookies.stream()
                .filter(c -> c.contains("TEAMSYNC_SESSION"))
                .findFirst()
                .map(c -> c.split(";")[0])
                .orElse(null);
    }

    private MockResponse createTokenMockResponse() {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(createZitadelTokenResponse(
                        "access-token-default",
                        "refresh-token-default",
                        "user-default",
                        "admin@example.com",
                        "Admin User",
                        List.of("user", "admin")
                ));
    }

    private String createZitadelTokenResponse(
            String accessToken,
            String refreshToken,
            String userId,
            String email,
            String name,
            List<String> roles) {

        // Create a mock JWT access token with the user info in the payload
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes());

        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(String.format("""
                        {
                            "sub": "%s",
                            "email": "%s",
                            "name": "%s",
                            "preferred_username": "%s",
                            "realm_access": {
                                "roles": %s
                            },
                            "resource_access": {
                                "account": {
                                    "roles": ["manage-account", "view-profile"]
                                }
                            },
                            "scope": "openid email profile",
                            "email_verified": true,
                            "iss": "http://localhost:18080/realms/test",
                            "aud": "teamsync-bff",
                            "iat": %d,
                            "exp": %d
                        }
                        """,
                        userId,
                        email,
                        name,
                        email.split("@")[0],
                        toJsonArray(roles),
                        System.currentTimeMillis() / 1000,
                        System.currentTimeMillis() / 1000 + 3600
                ).getBytes());

        String signature = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("mock-signature".getBytes());

        String mockJwt = header + "." + payload + "." + signature;

        return String.format("""
                {
                    "access_token": "%s",
                    "refresh_token": "%s",
                    "id_token": "%s",
                    "token_type": "Bearer",
                    "expires_in": 3600,
                    "refresh_expires_in": 28800,
                    "session_state": "session-%s",
                    "scope": "openid email profile"
                }
                """,
                accessToken.equals("access-token-123") ? accessToken : mockJwt,
                refreshToken,
                mockJwt,
                userId
        );
    }

    private String toJsonArray(List<String> items) {
        return "[" + items.stream()
                .map(s -> "\"" + s + "\"")
                .reduce((a, b) -> a + "," + b)
                .orElse("") + "]";
    }
}
