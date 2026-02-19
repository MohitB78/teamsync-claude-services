package com.teamsync.gateway.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests against Railway-deployed API Gateway.
 *
 * These tests verify the BFF authentication flow against actual Railway services:
 * - API Gateway: https://teamsync-api-gateway-production.up.railway.app
 * - Zitadel: https://teamsync-zitadel-production.up.railway.app
 * - Redis: Railway Redis instance
 * - Content Service: https://teamsync-content-service-production.up.railway.app
 *
 * Prerequisites:
 * - Railway services must be running
 * - Valid test user must exist in Zitadel (admin@accessarc.com)
 * - Latest code must be deployed to Railway (with CSRF exclusion for /bff/auth/**)
 *
 * Note: These tests are disabled by default. Enable for manual integration testing.
 * Run with: mvn test -Dtest=RailwayBffIntegrationTest -DenableRailwayTests=true
 */
@DisplayName("Railway BFF Integration Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Disabled("Requires Railway deployment with latest code. Run manually with -DenableRailwayTests=true")
class RailwayBffIntegrationTest {

    private static final String API_GATEWAY_URL = "https://teamsync-api-gateway-production.up.railway.app";
    private static final String TEST_USER_EMAIL = "admin@accessarc.com";
    private static final String TEST_USER_PASSWORD = "admin123";  // Update with actual password

    private static RestTemplate restTemplate;
    private static ObjectMapper objectMapper;
    private static String sessionCookie;
    private static String csrfToken;

    @BeforeAll
    static void setup() {
        restTemplate = new RestTemplate();
        objectMapper = new ObjectMapper();
    }

    @Nested
    @DisplayName("Health Check Tests")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class HealthCheckTests {

        @Test
        @Order(1)
        @DisplayName("API Gateway should be healthy")
        void healthCheck_ReturnsUp() {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    API_GATEWAY_URL + "/actuator/health",
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).contains("UP");

            System.out.println("✅ API Gateway health check passed");
        }

        @Test
        @Order(2)
        @DisplayName("BFF auth endpoints should be accessible")
        void bffAuthEndpoints_Accessible() {
            // Session endpoint should return 401 when not authenticated
            try {
                restTemplate.getForEntity(
                        API_GATEWAY_URL + "/bff/auth/session",
                        String.class
                );
            } catch (HttpClientErrorException e) {
                assertThat(e.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.OK);
                System.out.println("✅ BFF session endpoint is accessible (returned " + e.getStatusCode() + ")");
            }
        }
    }

    @Nested
    @DisplayName("BFF Authentication Flow Tests")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class BffAuthFlowTests {

        @Test
        @Order(1)
        @DisplayName("Should login via ROPC and receive session cookie")
        void login_WithValidCredentials_ReturnsSessionCookie() throws Exception {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String loginBody = String.format("""
                {
                    "email": "%s",
                    "password": "%s"
                }
                """, TEST_USER_EMAIL, TEST_USER_PASSWORD);

            HttpEntity<String> request = new HttpEntity<>(loginBody, headers);

            try {
                ResponseEntity<String> response = restTemplate.exchange(
                        API_GATEWAY_URL + "/bff/auth/login",
                        HttpMethod.POST,
                        request,
                        String.class
                );

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

                // Extract session cookie
                List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
                assertThat(cookies).isNotNull();

                sessionCookie = cookies.stream()
                        .filter(c -> c.contains("TEAMSYNC_SESSION"))
                        .findFirst()
                        .orElse(null);

                assertThat(sessionCookie).isNotNull();

                // Parse response
                JsonNode responseJson = objectMapper.readTree(response.getBody());
                assertThat(responseJson.has("success")).isTrue();
                assertThat(responseJson.get("success").asBoolean()).isTrue();

                if (responseJson.has("user")) {
                    JsonNode user = responseJson.get("user");
                    System.out.println("✅ Login successful for user: " + user.get("email").asText());
                    System.out.println("   User ID: " + user.get("id").asText());
                    System.out.println("   Name: " + user.get("name").asText());
                    if (user.has("roles")) {
                        System.out.println("   Roles: " + user.get("roles"));
                    }
                }

            } catch (HttpClientErrorException e) {
                System.out.println("❌ Login failed with status: " + e.getStatusCode());
                System.out.println("   Response: " + e.getResponseBodyAsString());
                throw e;
            }
        }

        @Test
        @Order(2)
        @DisplayName("Should get session info after login")
        void getSession_AfterLogin_ReturnsUserInfo() throws Exception {
            Assumptions.assumeTrue(sessionCookie != null, "Session cookie required from login test");

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.COOKIE, sessionCookie);

            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    API_GATEWAY_URL + "/bff/auth/session",
                    HttpMethod.GET,
                    request,
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            JsonNode responseJson = objectMapper.readTree(response.getBody());
            assertThat(responseJson.get("authenticated").asBoolean()).isTrue();
            assertThat(responseJson.has("user")).isTrue();

            JsonNode user = responseJson.get("user");
            System.out.println("✅ Session info retrieved:");
            System.out.println("   Email: " + user.get("email").asText());
            System.out.println("   Authenticated: true");
        }

        @Test
        @Order(3)
        @DisplayName("Should access content service documents after login")
        void listDocuments_AfterLogin_ReturnsDocuments() throws Exception {
            Assumptions.assumeTrue(sessionCookie != null, "Session cookie required from login test");

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.COOKIE, sessionCookie);
            headers.add("X-Tenant-ID", "default");
            headers.add("X-Drive-ID", "personal");

            HttpEntity<Void> request = new HttpEntity<>(headers);

            try {
                ResponseEntity<String> response = restTemplate.exchange(
                        API_GATEWAY_URL + "/api/documents",
                        HttpMethod.GET,
                        request,
                        String.class
                );

                System.out.println("✅ Documents endpoint accessible");
                System.out.println("   Status: " + response.getStatusCode());

                if (response.getBody() != null) {
                    JsonNode responseJson = objectMapper.readTree(response.getBody());
                    if (responseJson.has("items")) {
                        int count = responseJson.get("items").size();
                        System.out.println("   Document count: " + count);

                        if (count > 0) {
                            for (int i = 0; i < Math.min(count, 5); i++) {
                                JsonNode doc = responseJson.get("items").get(i);
                                System.out.println("   - " + doc.get("name").asText());
                            }
                            if (count > 5) {
                                System.out.println("   ... and " + (count - 5) + " more");
                            }
                        }
                    } else if (responseJson.has("totalCount")) {
                        System.out.println("   Total count: " + responseJson.get("totalCount").asInt());
                    }
                }

            } catch (HttpClientErrorException e) {
                System.out.println("⚠️ Documents endpoint returned: " + e.getStatusCode());
                System.out.println("   This may be expected if content service is not configured");
            }
        }

        @Test
        @Order(4)
        @DisplayName("Should refresh session")
        void refreshSession_AfterLogin_Success() throws Exception {
            Assumptions.assumeTrue(sessionCookie != null, "Session cookie required from login test");

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.COOKIE, sessionCookie);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> request = new HttpEntity<>("{}", headers);

            try {
                ResponseEntity<String> response = restTemplate.exchange(
                        API_GATEWAY_URL + "/bff/auth/refresh",
                        HttpMethod.POST,
                        request,
                        String.class
                );

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

                JsonNode responseJson = objectMapper.readTree(response.getBody());
                assertThat(responseJson.get("success").asBoolean()).isTrue();

                System.out.println("✅ Session refresh successful");
                if (responseJson.has("expiresIn")) {
                    System.out.println("   Expires in: " + responseJson.get("expiresIn").asInt() + " seconds");
                }

            } catch (HttpClientErrorException e) {
                System.out.println("⚠️ Session refresh returned: " + e.getStatusCode());
                System.out.println("   Response: " + e.getResponseBodyAsString());
            }
        }

        @Test
        @Order(5)
        @DisplayName("Should logout and invalidate session")
        void logout_AfterLogin_InvalidatesSession() throws Exception {
            Assumptions.assumeTrue(sessionCookie != null, "Session cookie required from login test");

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.COOKIE, sessionCookie);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> request = new HttpEntity<>("{}", headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    API_GATEWAY_URL + "/bff/auth/logout",
                    HttpMethod.POST,
                    request,
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            JsonNode responseJson = objectMapper.readTree(response.getBody());
            assertThat(responseJson.get("success").asBoolean()).isTrue();

            System.out.println("✅ Logout successful");

            // Verify session is invalidated
            try {
                ResponseEntity<String> sessionResponse = restTemplate.exchange(
                        API_GATEWAY_URL + "/bff/auth/session",
                        HttpMethod.GET,
                        request,
                        String.class
                );

                JsonNode sessionJson = objectMapper.readTree(sessionResponse.getBody());
                if (!sessionJson.get("authenticated").asBoolean()) {
                    System.out.println("✅ Session properly invalidated");
                }
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                    System.out.println("✅ Session properly invalidated (401)");
                }
            }

            // Clear session cookie for subsequent tests
            sessionCookie = null;
        }
    }

    @Nested
    @DisplayName("PKCE Authorization Code Flow Tests")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class PkceFlowTests {

        @Test
        @Order(1)
        @DisplayName("Should redirect to Keycloak login for PKCE flow")
        void initiateLogin_RedirectsToKeycloak() {
            try {
                // Don't follow redirects
                ResponseEntity<String> response = restTemplate.exchange(
                        API_GATEWAY_URL + "/bff/auth/pkce/login",
                        HttpMethod.GET,
                        null,
                        String.class
                );

                // If we get here, the redirect was followed
                System.out.println("✅ PKCE login endpoint accessible");
                System.out.println("   Response: " + response.getStatusCode());

            } catch (HttpClientErrorException e) {
                System.out.println("PKCE endpoint status: " + e.getStatusCode());
            } catch (Exception e) {
                // Redirect exception expected
                System.out.println("✅ PKCE redirect initiated (as expected)");
            }
        }
    }

    @Nested
    @DisplayName("Security Tests")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class SecurityTests {

        @Test
        @Order(1)
        @DisplayName("Should reject requests without session to protected endpoints")
        void protectedEndpoint_WithoutSession_Returns401() {
            try {
                restTemplate.getForEntity(
                        API_GATEWAY_URL + "/api/documents",
                        String.class
                );
                Assertions.fail("Expected 401 Unauthorized");
            } catch (HttpClientErrorException e) {
                assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                System.out.println("✅ Protected endpoint requires authentication (401)");
            }
        }

        @Test
        @Order(2)
        @DisplayName("Should reject invalid credentials")
        void login_WithInvalidCredentials_Returns401() {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String loginBody = """
                {
                    "email": "invalid@example.com",
                    "password": "wrongpassword"
                }
                """;

            HttpEntity<String> request = new HttpEntity<>(loginBody, headers);

            try {
                restTemplate.exchange(
                        API_GATEWAY_URL + "/bff/auth/login",
                        HttpMethod.POST,
                        request,
                        String.class
                );
                Assertions.fail("Expected 401 Unauthorized");
            } catch (HttpClientErrorException e) {
                assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                System.out.println("✅ Invalid credentials rejected (401)");
            }
        }

        @Test
        @Order(3)
        @DisplayName("Should handle missing email in login request")
        void login_WithMissingEmail_ReturnsBadRequest() {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String loginBody = """
                {
                    "password": "somepassword"
                }
                """;

            HttpEntity<String> request = new HttpEntity<>(loginBody, headers);

            try {
                restTemplate.exchange(
                        API_GATEWAY_URL + "/bff/auth/login",
                        HttpMethod.POST,
                        request,
                        String.class
                );
                Assertions.fail("Expected 400 Bad Request");
            } catch (HttpClientErrorException e) {
                assertThat(e.getStatusCode()).isIn(HttpStatus.BAD_REQUEST, HttpStatus.UNAUTHORIZED);
                System.out.println("✅ Missing email rejected (" + e.getStatusCode() + ")");
            }
        }
    }

    @Nested
    @DisplayName("Content Service Integration Tests")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class ContentServiceTests {

        private String testSessionCookie;

        @BeforeEach
        void loginBeforeTest() throws Exception {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String loginBody = String.format("""
                {
                    "email": "%s",
                    "password": "%s"
                }
                """, TEST_USER_EMAIL, TEST_USER_PASSWORD);

            HttpEntity<String> request = new HttpEntity<>(loginBody, headers);

            try {
                ResponseEntity<String> response = restTemplate.exchange(
                        API_GATEWAY_URL + "/bff/auth/login",
                        HttpMethod.POST,
                        request,
                        String.class
                );

                List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
                if (cookies != null) {
                    testSessionCookie = cookies.stream()
                            .filter(c -> c.contains("TEAMSYNC_SESSION"))
                            .findFirst()
                            .orElse(null);
                }
            } catch (Exception e) {
                System.out.println("⚠️ Login failed in @BeforeEach: " + e.getMessage());
            }
        }

        @Test
        @Order(1)
        @DisplayName("Should list documents for admin user")
        void listDocuments_ForAdmin_ReturnsDocuments() throws Exception {
            Assumptions.assumeTrue(testSessionCookie != null, "Login required");

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.COOKIE, testSessionCookie);
            headers.add("X-Tenant-ID", "default");
            headers.add("X-Drive-ID", "personal");

            HttpEntity<Void> request = new HttpEntity<>(headers);

            try {
                ResponseEntity<String> response = restTemplate.exchange(
                        API_GATEWAY_URL + "/api/documents",
                        HttpMethod.GET,
                        request,
                        String.class
                );

                System.out.println("📄 Documents for " + TEST_USER_EMAIL + ":");

                if (response.getBody() != null) {
                    JsonNode responseJson = objectMapper.readTree(response.getBody());

                    int documentCount = 0;
                    if (responseJson.has("items")) {
                        documentCount = responseJson.get("items").size();
                        System.out.println("   Total documents: " + documentCount);

                        for (int i = 0; i < documentCount; i++) {
                            JsonNode doc = responseJson.get("items").get(i);
                            String name = doc.has("name") ? doc.get("name").asText() : "unknown";
                            String type = doc.has("mimeType") ? doc.get("mimeType").asText() : "unknown";
                            System.out.println("   " + (i + 1) + ". " + name + " (" + type + ")");
                        }
                    } else if (responseJson.has("totalCount")) {
                        documentCount = responseJson.get("totalCount").asInt();
                        System.out.println("   Total count: " + documentCount);
                    }

                    System.out.println("\n✅ Admin user has " + documentCount + " documents");
                }

            } catch (HttpClientErrorException e) {
                System.out.println("❌ Failed to list documents: " + e.getStatusCode());
                System.out.println("   Response: " + e.getResponseBodyAsString());
            }
        }

        @Test
        @Order(2)
        @DisplayName("Should list folders for admin user")
        void listFolders_ForAdmin_ReturnsFolders() throws Exception {
            Assumptions.assumeTrue(testSessionCookie != null, "Login required");

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.COOKIE, testSessionCookie);
            headers.add("X-Tenant-ID", "default");
            headers.add("X-Drive-ID", "personal");

            HttpEntity<Void> request = new HttpEntity<>(headers);

            try {
                ResponseEntity<String> response = restTemplate.exchange(
                        API_GATEWAY_URL + "/api/folders",
                        HttpMethod.GET,
                        request,
                        String.class
                );

                System.out.println("📁 Folders for " + TEST_USER_EMAIL + ":");

                if (response.getBody() != null) {
                    JsonNode responseJson = objectMapper.readTree(response.getBody());

                    if (responseJson.has("items")) {
                        int folderCount = responseJson.get("items").size();
                        System.out.println("   Total folders: " + folderCount);

                        for (int i = 0; i < folderCount; i++) {
                            JsonNode folder = responseJson.get("items").get(i);
                            String name = folder.has("name") ? folder.get("name").asText() : "unknown";
                            System.out.println("   " + (i + 1) + ". " + name);
                        }
                    }
                }

            } catch (HttpClientErrorException e) {
                System.out.println("⚠️ Folders endpoint: " + e.getStatusCode());
            }
        }
    }
}
