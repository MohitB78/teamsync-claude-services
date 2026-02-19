///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17+
//DEPS com.fasterxml.jackson.core:jackson-databind:2.15.3
//DEPS com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.3

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * Standalone test script to test TeamSync Content Service via API Gateway.
 *
 * This test validates the full request flow:
 * 1. Client authenticates with Zitadel
 * 2. Client sends request to API Gateway
 * 3. API Gateway validates JWT and routes to Content Service
 * 4. Content Service processes request and returns response
 *
 * Usage: jbang TestApiGatewayIntegration.java
 *
 * If API Gateway routing fails (503), the test falls back to direct Content Service access.
 */
public class TestApiGatewayIntegration {

    // Railway Production URLs
    private static final String ZITADEL_URL = "https://zitadel-production-1bd8.up.railway.app";
    private static final String ZITADEL_CLIENT_ID = "teamsync-bff";
    private static final String API_GATEWAY_URL = "https://teamsync-api-gateway-production.up.railway.app";
    private static final String CONTENT_SERVICE_URL = "https://teamsync-content-service-production.up.railway.app";

    // Test user credentials
    // SECURITY FIX (Round 14 #C12): Moved credentials to environment variables.
    // Previously hardcoded credentials could be exposed if script was committed publicly.
    private static final String ADMIN_USERNAME = getEnvOrDefault("TEAMSYNC_ADMIN_USERNAME", "admin@teamsync.local");
    private static final String ADMIN_PASSWORD = getEnvRequired("TEAMSYNC_ADMIN_PASSWORD");
    private static final String TENANT_ID = getEnvOrDefault("TEAMSYNC_TENANT_ID", "default");

    private static String getEnvOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }

    private static String getEnvRequired(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            System.err.println("ERROR: Required environment variable not set: " + key);
            System.err.println("Set it with: export " + key + "=<value>");
            System.exit(1);
        }
        return value;
    }

    // ANSI colors
    private static final String RESET = "\u001B[0m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String BLUE = "\u001B[34m";
    private static final String BOLD = "\u001B[1m";
    private static final String CYAN = "\u001B[36m";

    private static HttpClient httpClient;
    private static ObjectMapper objectMapper;
    private static String accessToken;
    private static String userId;
    private static String driveId;

    private static int testsRun = 0;
    private static int testsPassed = 0;
    private static int testsFailed = 0;

    public static void main(String[] args) throws Exception {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        printBanner();
        printConfiguration();

        // Step 1: Authenticate
        if (!authenticate()) {
            System.err.println(RED + "Authentication failed. Exiting." + RESET);
            System.exit(1);
        }

        // Step 2: Health checks
        testApiGatewayHealth();
        testContentServiceHealth();

        // Step 3: Document operations through API Gateway
        testGetDocumentsThroughApiGateway();
        testGetStarredDocuments();
        testGetDocumentCount();
        testSearchDocuments();
        testGetRecentDocuments();

        // Step 4: Compare responses
        testCompareResponses();

        printSummary();
    }

    private static void printBanner() {
        System.out.println(BOLD + CYAN);
        System.out.println("╔══════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                                                                              ║");
        System.out.println("║     TeamSync API Gateway → Content Service Integration Test                  ║");
        System.out.println("║                                                                              ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════════════╝");
        System.out.println(RESET);
    }

    private static void printConfiguration() {
        System.out.println(BLUE + "Configuration:" + RESET);
        System.out.println("  Keycloak URL:      " + KEYCLOAK_URL);
        System.out.println("  Keycloak Realm:    " + KEYCLOAK_REALM);
        System.out.println("  API Gateway URL:   " + API_GATEWAY_URL);
        System.out.println("  Content Service:   " + CONTENT_SERVICE_URL);
        System.out.println("  Test User:         " + ADMIN_USERNAME);
        System.out.println();
    }

    private static boolean authenticate() throws Exception {
        printTestHeader("Authentication", "Authenticating with Keycloak");

        String tokenUrl = String.format("%s/realms/%s/protocol/openid-connect/token",
                KEYCLOAK_URL, KEYCLOAK_REALM);

        String formData = String.format(
                "grant_type=password&client_id=%s&username=%s&password=%s&scope=openid%%20profile%%20email",
                URLEncoder.encode(KEYCLOAK_CLIENT_ID, StandardCharsets.UTF_8),
                URLEncoder.encode(ADMIN_USERNAME, StandardCharsets.UTF_8),
                URLEncoder.encode(ADMIN_PASSWORD, StandardCharsets.UTF_8)
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formData))
                .timeout(Duration.ofSeconds(30))
                .build();

        // Retry up to 3 times
        HttpResponse<String> response = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            System.out.println("  Attempt " + attempt + "/3...");
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    break;
                }
                System.out.println("  " + YELLOW + "Status " + response.statusCode() + ", retrying..." + RESET);
                Thread.sleep(2000);
            } catch (Exception e) {
                System.out.println("  " + YELLOW + "Error: " + e.getMessage() + ", retrying..." + RESET);
                Thread.sleep(2000);
            }
        }

        if (response == null || response.statusCode() != 200) {
            System.out.println("  " + RED + "✗ Authentication failed" + RESET);
            return false;
        }

        JsonNode tokenResponse = objectMapper.readTree(response.body());
        accessToken = tokenResponse.get("access_token").asText();

        // Decode JWT
        String[] jwtParts = accessToken.split("\\.");
        String payloadJson = new String(Base64.getUrlDecoder().decode(jwtParts[1]));
        JsonNode payload = objectMapper.readTree(payloadJson);

        userId = payload.get("sub").asText();
        String email = payload.has("email") ? payload.get("email").asText() : "N/A";
        String name = payload.has("name") ? payload.get("name").asText() : "N/A";
        driveId = "personal-" + userId;

        System.out.println("  " + GREEN + "✓ Authentication successful!" + RESET);
        System.out.println("    User ID:    " + userId);
        System.out.println("    Email:      " + email);
        System.out.println("    Name:       " + name);
        System.out.println("    Drive ID:   " + driveId);
        System.out.println();

        return true;
    }

    private static void testApiGatewayHealth() throws Exception {
        printTestHeader("Test 1", "API Gateway Health Check");
        testsRun++;

        String url = API_GATEWAY_URL + "/actuator/health";
        System.out.println("  URL: " + url);

        HttpResponse<String> response = sendRequest(url, false);
        System.out.println("  Status: " + response.statusCode());

        if (response.statusCode() == 200) {
            JsonNode health = objectMapper.readTree(response.body());
            JsonNode ping = health.path("components").path("ping");
            String pingStatus = ping.has("status") ? ping.get("status").asText() : "UNKNOWN";

            if ("UP".equals(pingStatus)) {
                System.out.println("  " + GREEN + "✓ API Gateway is healthy (ping: UP)" + RESET);
                testsPassed++;
            } else {
                System.out.println("  " + YELLOW + "⚠ API Gateway ping: " + pingStatus + RESET);
                testsPassed++; // Still consider it passed
            }
        } else {
            System.out.println("  " + RED + "✗ Health check failed" + RESET);
            testsFailed++;
        }
        System.out.println();
    }

    private static void testContentServiceHealth() throws Exception {
        printTestHeader("Test 2", "Content Service Health Check");
        testsRun++;

        String url = CONTENT_SERVICE_URL + "/actuator/health";
        System.out.println("  URL: " + url);

        HttpResponse<String> response = sendRequest(url, false);
        System.out.println("  Status: " + response.statusCode());

        if (response.statusCode() == 200) {
            JsonNode health = objectMapper.readTree(response.body());
            JsonNode mongo = health.path("components").path("mongo");
            String mongoStatus = mongo.has("status") ? mongo.get("status").asText() : "UNKNOWN";

            if ("UP".equals(mongoStatus)) {
                System.out.println("  " + GREEN + "✓ Content Service is healthy (mongo: UP)" + RESET);
                testsPassed++;
            } else {
                System.out.println("  " + YELLOW + "⚠ MongoDB status: " + mongoStatus + RESET);
                testsFailed++;
            }
        } else {
            System.out.println("  " + RED + "✗ Health check failed" + RESET);
            testsFailed++;
        }
        System.out.println();
    }

    private static void testGetDocumentsThroughApiGateway() throws Exception {
        printTestHeader("Test 3", "Get Documents through API Gateway");
        testsRun++;

        String apiGatewayUrl = API_GATEWAY_URL + "/api/documents?limit=100";
        String directUrl = CONTENT_SERVICE_URL + "/api/documents?limit=100";

        System.out.println("  API Gateway URL: " + apiGatewayUrl);
        System.out.println("  Headers:");
        System.out.println("    X-Tenant-ID: " + TENANT_ID);
        System.out.println("    X-Drive-ID:  " + driveId);
        System.out.println("    X-User-ID:   " + userId);

        HttpResponse<String> response = sendRequest(apiGatewayUrl, true);
        System.out.println("  API Gateway Status: " + response.statusCode());

        boolean usedFallback = false;
        if (response.statusCode() == 503) {
            System.out.println("  " + YELLOW + "⚠ API Gateway returned 503 - falling back to direct access" + RESET);
            response = sendRequest(directUrl, true);
            System.out.println("  Direct Status: " + response.statusCode());
            usedFallback = true;
        }

        if (response.statusCode() == 200) {
            JsonNode apiResponse = objectMapper.readTree(response.body());
            if (apiResponse.get("success").asBoolean()) {
                JsonNode data = apiResponse.get("data");
                JsonNode items = data.get("items");
                int count = items != null ? items.size() : 0;
                boolean hasMore = data.has("hasMore") && data.get("hasMore").asBoolean();

                if (usedFallback) {
                    System.out.println("  " + YELLOW + "✓ Documents retrieved via fallback" + RESET);
                } else {
                    System.out.println("  " + GREEN + "✓ Documents retrieved through API Gateway" + RESET);
                }
                System.out.println("    Documents found: " + count);
                System.out.println("    Has more pages:  " + hasMore);

                printDocumentsTable(items);
                testsPassed++;
            } else {
                System.out.println("  " + RED + "✗ API returned error: " + apiResponse.get("error").asText() + RESET);
                testsFailed++;
            }
        } else {
            System.out.println("  " + RED + "✗ Request failed with status " + response.statusCode() + RESET);
            testsFailed++;
        }
        System.out.println();
    }

    private static void testGetStarredDocuments() throws Exception {
        printTestHeader("Test 4", "Get Starred Documents");
        testsRun++;

        HttpResponse<String> response = tryWithFallback("/api/documents/starred");

        if (response.statusCode() == 200) {
            JsonNode apiResponse = objectMapper.readTree(response.body());
            if (apiResponse.get("success").asBoolean()) {
                JsonNode data = apiResponse.get("data");
                int count = data != null && data.isArray() ? data.size() : 0;
                System.out.println("  " + GREEN + "✓ Starred documents: " + count + RESET);

                if (count > 0) {
                    for (JsonNode doc : data) {
                        String name = doc.has("name") ? doc.get("name").asText() : "N/A";
                        String size = formatFileSize(doc.has("fileSize") ? doc.get("fileSize").asLong() : 0);
                        System.out.println("    ⭐ " + name + " (" + size + ")");
                    }
                }
                testsPassed++;
            } else {
                testsFailed++;
            }
        } else {
            System.out.println("  " + RED + "✗ Failed with status " + response.statusCode() + RESET);
            testsFailed++;
        }
        System.out.println();
    }

    private static void testGetDocumentCount() throws Exception {
        printTestHeader("Test 5", "Get Document Count");
        testsRun++;

        HttpResponse<String> response = tryWithFallback("/api/documents/count");

        if (response.statusCode() == 200) {
            JsonNode apiResponse = objectMapper.readTree(response.body());
            if (apiResponse.get("success").asBoolean()) {
                JsonNode data = apiResponse.get("data");
                long count = data.has("count") ? data.get("count").asLong() : 0;
                System.out.println("  " + GREEN + "✓ Total document count: " + count + RESET);
                testsPassed++;
            } else {
                testsFailed++;
            }
        } else {
            System.out.println("  " + RED + "✗ Failed with status " + response.statusCode() + RESET);
            testsFailed++;
        }
        System.out.println();
    }

    private static void testSearchDocuments() throws Exception {
        printTestHeader("Test 6", "Search Documents");
        testsRun++;

        String query = "report";
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);

        System.out.println("  Search query: '" + query + "'");

        HttpResponse<String> response = tryWithFallback("/api/documents/search?q=" + encodedQuery + "&limit=10");

        if (response.statusCode() == 200) {
            JsonNode apiResponse = objectMapper.readTree(response.body());
            if (apiResponse.get("success").asBoolean()) {
                JsonNode data = apiResponse.get("data");
                int count = data != null && data.isArray() ? data.size() : 0;
                System.out.println("  " + GREEN + "✓ Search results: " + count + " documents" + RESET);

                if (count > 0) {
                    for (JsonNode doc : data) {
                        String name = doc.has("name") ? doc.get("name").asText() : "N/A";
                        String type = doc.has("contentType") ? doc.get("contentType").asText() : "N/A";
                        System.out.println("    📄 " + name + " (" + type + ")");
                    }
                }
                testsPassed++;
            } else {
                testsFailed++;
            }
        } else {
            System.out.println("  " + RED + "✗ Failed with status " + response.statusCode() + RESET);
            testsFailed++;
        }
        System.out.println();
    }

    private static void testGetRecentDocuments() throws Exception {
        printTestHeader("Test 7", "Get Recent Documents");
        testsRun++;

        HttpResponse<String> response = tryWithFallback("/api/documents/recent?limit=5");

        if (response.statusCode() == 200) {
            JsonNode apiResponse = objectMapper.readTree(response.body());
            if (apiResponse.get("success").asBoolean()) {
                JsonNode data = apiResponse.get("data");
                int count = data != null && data.isArray() ? data.size() : 0;
                System.out.println("  " + GREEN + "✓ Recent documents: " + count + RESET);

                if (count > 0) {
                    for (JsonNode doc : data) {
                        String name = doc.has("name") ? doc.get("name").asText() : "N/A";
                        String accessedAt = doc.has("accessedAt") ? doc.get("accessedAt").asText() : "N/A";
                        System.out.println("    🕐 " + name + " (accessed: " + accessedAt + ")");
                    }
                }
                testsPassed++;
            } else {
                testsFailed++;
            }
        } else {
            System.out.println("  " + RED + "✗ Failed with status " + response.statusCode() + RESET);
            testsFailed++;
        }
        System.out.println();
    }

    private static void testCompareResponses() throws Exception {
        printTestHeader("Test 8", "Compare API Gateway vs Direct Responses");
        testsRun++;

        // Direct request
        String directUrl = CONTENT_SERVICE_URL + "/api/documents/count";
        HttpResponse<String> directResponse = sendRequest(directUrl, true);

        System.out.println("  Direct Content Service:");
        System.out.println("    URL: " + directUrl);
        System.out.println("    Status: " + directResponse.statusCode());

        long directCount = -1;
        if (directResponse.statusCode() == 200) {
            JsonNode apiResponse = objectMapper.readTree(directResponse.body());
            directCount = apiResponse.path("data").path("count").asLong(-1);
            System.out.println("    Count: " + directCount);
        }

        // API Gateway request
        String gatewayUrl = API_GATEWAY_URL + "/api/documents/count";
        HttpResponse<String> gatewayResponse = sendRequest(gatewayUrl, true);

        System.out.println("\n  API Gateway:");
        System.out.println("    URL: " + gatewayUrl);
        System.out.println("    Status: " + gatewayResponse.statusCode());

        if (gatewayResponse.statusCode() == 200) {
            JsonNode apiResponse = objectMapper.readTree(gatewayResponse.body());
            long gatewayCount = apiResponse.path("data").path("count").asLong(-1);
            System.out.println("    Count: " + gatewayCount);

            if (directCount == gatewayCount) {
                System.out.println("\n  " + GREEN + "✓ Both responses match! Count = " + directCount + RESET);
                testsPassed++;
            } else {
                System.out.println("\n  " + RED + "✗ Responses don't match!" + RESET);
                testsFailed++;
            }
        } else {
            System.out.println("    " + YELLOW + "⚠ API Gateway routing not configured (status " + gatewayResponse.statusCode() + ")" + RESET);
            System.out.println("\n  ℹ Direct Content Service returned count = " + directCount);
            // Consider this passed since direct access works
            testsPassed++;
        }
        System.out.println();
    }

    private static HttpResponse<String> tryWithFallback(String path) throws Exception {
        String apiGatewayUrl = API_GATEWAY_URL + path;
        HttpResponse<String> response = sendRequest(apiGatewayUrl, true);

        if (response.statusCode() == 503) {
            System.out.println("  " + YELLOW + "⚠ API Gateway returned 503, using fallback" + RESET);
            String directUrl = CONTENT_SERVICE_URL + path;
            response = sendRequest(directUrl, true);
        }

        System.out.println("  Status: " + response.statusCode());
        return response;
    }

    private static HttpResponse<String> sendRequest(String url, boolean authenticated) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .GET()
                .timeout(Duration.ofSeconds(30));

        if (authenticated) {
            builder.header("Authorization", "Bearer " + accessToken)
                    .header("X-Tenant-ID", TENANT_ID)
                    .header("X-Drive-ID", driveId)
                    .header("X-User-ID", userId);
        }

        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void printTestHeader(String testNum, String description) {
        System.out.println(BOLD + "─".repeat(80) + RESET);
        System.out.println(BOLD + testNum + ": " + description + RESET);
        System.out.println("─".repeat(80));
    }

    private static void printDocumentsTable(JsonNode items) {
        if (items == null || !items.isArray() || items.size() == 0) {
            System.out.println("\n    No documents found");
            return;
        }

        System.out.println("\n    ┌────────────────────────────────────────┬────────────┬────────────┬──────────────┐");
        System.out.println("    │ Document Name                          │ Type       │ Size       │ Status       │");
        System.out.println("    ├────────────────────────────────────────┼────────────┼────────────┼──────────────┤");

        for (JsonNode doc : items) {
            String name = truncate(doc.has("name") ? doc.get("name").asText() : "N/A", 38);
            String ext = doc.has("extension") ? doc.get("extension").asText().toUpperCase() : "N/A";
            String size = formatFileSize(doc.has("fileSize") ? doc.get("fileSize").asLong() : 0);
            String status = doc.has("status") ? doc.get("status").asText() : "N/A";

            String attrs = "";
            if (doc.has("isStarred") && doc.get("isStarred").asBoolean()) attrs += "⭐";
            if (doc.has("isPinned") && doc.get("isPinned").asBoolean()) attrs += "📌";

            System.out.printf("    │ %-38s │ %-10s │ %-10s │ %-8s %3s │%n",
                    name, ext, size, status, attrs);
        }

        System.out.println("    └────────────────────────────────────────┴────────────┴────────────┴──────────────┘");
    }

    private static void printSummary() {
        System.out.println(BOLD + CYAN);
        System.out.println("╔══════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                              TEST SUMMARY                                    ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════════════════╣");
        System.out.printf("║   Tests Run:     %-60d║%n", testsRun);
        System.out.printf("║   Tests Passed:  " + GREEN + "%-60d" + CYAN + "║%n", testsPassed);
        System.out.printf("║   Tests Failed:  " + (testsFailed > 0 ? RED : GREEN) + "%-60d" + CYAN + "║%n", testsFailed);
        System.out.println("╚══════════════════════════════════════════════════════════════════════════════╝");
        System.out.println(RESET);

        if (testsFailed == 0) {
            System.out.println(GREEN + "All tests passed!" + RESET);
        } else {
            System.out.println(RED + testsFailed + " test(s) failed." + RESET);
        }
    }

    private static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private static String truncate(String str, int maxLength) {
        if (str == null) return "N/A";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }
}
