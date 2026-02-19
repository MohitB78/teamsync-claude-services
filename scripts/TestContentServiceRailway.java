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
 * Standalone test script to test TeamSync Content Service on Railway.
 *
 * Usage: java TestContentServiceRailway.java
 *
 * This script:
 * 1. Authenticates with Zitadel as admin@teamsync.local
 * 2. Fetches documents from the user's personal drive (mydrive)
 * 3. Lists all documents found
 */
public class TestContentServiceRailway {

    // Railway Production URLs
    private static final String ZITADEL_URL = "https://zitadel-production-1bd8.up.railway.app";
    private static final String ZITADEL_CLIENT_ID = "teamsync-bff";
    private static final String API_GATEWAY_URL = "https://teamsync-content-service-production.up.railway.app";

    // Test user credentials
    // SECURITY FIX (Round 14 #C11): Moved credentials to environment variables.
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

    private static HttpClient httpClient;
    private static ObjectMapper objectMapper;

    public static void main(String[] args) throws Exception {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        System.out.println("=".repeat(80));
        System.out.println("TeamSync Content Service Integration Test - Railway Deployment");
        System.out.println("=".repeat(80));
        System.out.println();
        System.out.println("Configuration:");
        System.out.println("  Zitadel URL: " + ZITADEL_URL);
        System.out.println("  API Gateway URL: " + API_GATEWAY_URL);
        System.out.println("  Test User: " + ADMIN_USERNAME);
        System.out.println();

        // Step 1: Authenticate with Zitadel
        System.out.println("-".repeat(80));
        System.out.println("Step 1: Authenticating with Zitadel...");
        System.out.println("-".repeat(80));

        String accessToken = authenticateWithZitadel();
        if (accessToken == null) {
            System.err.println("ERROR: Failed to authenticate with Zitadel");
            System.exit(1);
        }

        // Decode JWT to get user info
        String[] jwtParts = accessToken.split("\\.");
        String payloadJson = new String(Base64.getUrlDecoder().decode(jwtParts[1]));
        JsonNode payload = objectMapper.readTree(payloadJson);

        String userId = payload.get("sub").asText();
        String email = payload.has("email") ? payload.get("email").asText() : "N/A";
        String name = payload.has("name") ? payload.get("name").asText() : "N/A";
        String driveId = "personal-" + userId;

        System.out.println("Authentication successful!");
        System.out.println("  User ID: " + userId);
        System.out.println("  Email: " + email);
        System.out.println("  Name: " + name);
        System.out.println("  Personal Drive ID: " + driveId);
        System.out.println("  Access Token: " + accessToken.substring(0, Math.min(50, accessToken.length())) + "...");
        System.out.println();

        // Step 2: Get documents from mydrive
        System.out.println("-".repeat(80));
        System.out.println("Step 2: Fetching documents from MyDrive...");
        System.out.println("-".repeat(80));

        getDocumentsFromMyDrive(accessToken, userId, driveId);

        // Step 3: Get starred documents
        System.out.println();
        System.out.println("-".repeat(80));
        System.out.println("Step 3: Fetching starred documents...");
        System.out.println("-".repeat(80));

        getStarredDocuments(accessToken, userId, driveId);

        // Step 4: Get document count
        System.out.println();
        System.out.println("-".repeat(80));
        System.out.println("Step 4: Getting document count...");
        System.out.println("-".repeat(80));

        getDocumentCount(accessToken, userId, driveId);

        System.out.println();
        System.out.println("=".repeat(80));
        System.out.println("Test completed successfully!");
        System.out.println("=".repeat(80));
    }

    private static String authenticateWithZitadel() throws Exception {
        String tokenUrl = String.format("%s/oauth/v2/token", ZITADEL_URL);

        String formData = String.format(
                "grant_type=password&client_id=%s&username=%s&password=%s&scope=openid%%20profile%%20email",
                URLEncoder.encode(ZITADEL_CLIENT_ID, StandardCharsets.UTF_8),
                URLEncoder.encode(ADMIN_USERNAME, StandardCharsets.UTF_8),
                URLEncoder.encode(ADMIN_PASSWORD, StandardCharsets.UTF_8)
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formData))
                .timeout(Duration.ofSeconds(30))
                .build();

        System.out.println("  Token URL: " + tokenUrl);

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("  Response Status: " + response.statusCode());

        if (response.statusCode() != 200) {
            System.err.println("  ERROR: " + response.body());
            return null;
        }

        JsonNode tokenResponse = objectMapper.readTree(response.body());
        return tokenResponse.get("access_token").asText();
    }

    private static void getDocumentsFromMyDrive(String accessToken, String userId, String driveId) throws Exception {
        String url = String.format("%s/api/documents?limit=100", API_GATEWAY_URL);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Tenant-ID", TENANT_ID)
                .header("X-Drive-ID", driveId)
                .header("X-User-ID", userId)
                .header("Accept", "application/json")
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build();

        System.out.println("  Request URL: " + url);
        System.out.println("  Headers: X-Tenant-ID=" + TENANT_ID + ", X-Drive-ID=" + driveId + ", X-User-ID=" + userId);

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("  Response Status: " + response.statusCode());

        if (response.statusCode() != 200) {
            System.err.println("  ERROR: " + response.body());
            return;
        }

        JsonNode apiResponse = objectMapper.readTree(response.body());

        if (!apiResponse.get("success").asBoolean()) {
            System.err.println("  API Error: " + apiResponse.get("error").asText());
            return;
        }

        JsonNode data = apiResponse.get("data");
        JsonNode items = data.get("items");
        boolean hasMore = data.has("hasMore") && data.get("hasMore").asBoolean();

        System.out.println();
        System.out.println("=== Documents in MyDrive for admin@accessarc.com ===");
        System.out.println("Total documents found: " + (items != null ? items.size() : 0));
        System.out.println("Has more pages: " + hasMore);
        System.out.println();

        if (items != null && items.size() > 0) {
            System.out.printf("%-40s %-12s %-12s %-15s%n", "Document Name", "Type", "Size", "Status");
            System.out.println("-".repeat(82));

            for (JsonNode doc : items) {
                String docName = doc.has("name") ? doc.get("name").asText() : "N/A";
                String extension = doc.has("extension") ? doc.get("extension").asText().toUpperCase() : "N/A";
                String size = formatFileSize(doc.has("fileSize") ? doc.get("fileSize").asLong() : 0);
                String status = doc.has("status") ? doc.get("status").asText() : "N/A";

                System.out.printf("%-40s %-12s %-12s %-15s%n",
                        truncate(docName, 38),
                        extension,
                        size,
                        status);

                // Print additional details
                if (doc.has("id")) {
                    System.out.println("    ID: " + doc.get("id").asText());
                }
                if (doc.has("description") && !doc.get("description").isNull()) {
                    System.out.println("    Description: " + doc.get("description").asText());
                }
                if (doc.has("tags") && doc.get("tags").isArray() && doc.get("tags").size() > 0) {
                    System.out.println("    Tags: " + doc.get("tags").toString());
                }
                if (doc.has("isStarred")) {
                    System.out.println("    Starred: " + doc.get("isStarred").asBoolean() +
                                      ", Pinned: " + (doc.has("isPinned") ? doc.get("isPinned").asBoolean() : false));
                }
                System.out.println();
            }

            System.out.println("-".repeat(82));
        } else {
            System.out.println("No documents found in mydrive.");
            System.out.println("To seed sample documents, run: ./scripts/seed-sample-documents.sh");
        }
    }

    private static void getStarredDocuments(String accessToken, String userId, String driveId) throws Exception {
        String url = String.format("%s/api/documents/starred", API_GATEWAY_URL);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Tenant-ID", TENANT_ID)
                .header("X-Drive-ID", driveId)
                .header("X-User-ID", userId)
                .header("Accept", "application/json")
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("  Response Status: " + response.statusCode());

        if (response.statusCode() != 200) {
            System.err.println("  ERROR: " + response.body());
            return;
        }

        JsonNode apiResponse = objectMapper.readTree(response.body());

        if (!apiResponse.get("success").asBoolean()) {
            System.err.println("  API Error: " + apiResponse.get("error").asText());
            return;
        }

        JsonNode data = apiResponse.get("data");
        int count = data != null && data.isArray() ? data.size() : 0;

        System.out.println("  Starred documents found: " + count);

        if (count > 0) {
            for (JsonNode doc : data) {
                String name = doc.has("name") ? doc.get("name").asText() : "N/A";
                String size = formatFileSize(doc.has("fileSize") ? doc.get("fileSize").asLong() : 0);
                System.out.println("    - " + name + " (" + size + ")");
            }
        }
    }

    private static void getDocumentCount(String accessToken, String userId, String driveId) throws Exception {
        String url = String.format("%s/api/documents/count", API_GATEWAY_URL);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Tenant-ID", TENANT_ID)
                .header("X-Drive-ID", driveId)
                .header("X-User-ID", userId)
                .header("Accept", "application/json")
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("  Response Status: " + response.statusCode());

        if (response.statusCode() != 200) {
            System.err.println("  ERROR: " + response.body());
            return;
        }

        JsonNode apiResponse = objectMapper.readTree(response.body());

        if (!apiResponse.get("success").asBoolean()) {
            System.err.println("  API Error: " + apiResponse.get("error").asText());
            return;
        }

        JsonNode data = apiResponse.get("data");
        long count = data.has("count") ? data.get("count").asLong() : 0;

        System.out.println("  Total document count in drive: " + count);
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
