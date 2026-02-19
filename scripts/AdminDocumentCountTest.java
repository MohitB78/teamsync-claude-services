import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * JUnit-style integration test to verify document count for admin@teamsync.local via API Gateway.
 *
 * This test authenticates with Zitadel and queries the Content Service to verify
 * the number of documents in the admin user's personal drive.
 *
 * Usage:
 *   cd teamsync-backend/scripts
 *   javac AdminDocumentCountTest.java && java AdminDocumentCountTest
 *
 * Expected Result: admin@teamsync.local has 5 documents in their personal drive
 */
public class AdminDocumentCountTest {

    // Railway Production URLs
    private static final String ZITADEL_URL = "https://zitadel-production-1bd8.up.railway.app";
    private static final String ZITADEL_CLIENT_ID = "teamsync-bff";
    private static final String API_GATEWAY_URL = "https://teamsync-api-gateway-production.up.railway.app";

    // Test user credentials
    private static final String ADMIN_USERNAME = "admin@teamsync.local";
    private static final String ADMIN_PASSWORD = "Admin@Teamsync2024!";
    private static final String TENANT_ID = "default";

    // Expected document count for admin@accessarc.com
    private static final int EXPECTED_DOCUMENT_COUNT = 5;

    // ANSI colors
    private static final String RESET = "\u001B[0m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String BLUE = "\u001B[34m";
    private static final String BOLD = "\u001B[1m";

    private static HttpClient httpClient;
    private static String accessToken;
    private static String userId;
    private static String driveId;

    public static void main(String[] args) throws Exception {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        printBanner();

        // Step 1: Authenticate
        boolean authenticated = authenticate();
        if (!authenticated) {
            System.out.println(RED + "FAIL: Authentication failed" + RESET);
            System.exit(1);
        }

        // Step 2: Test document count
        boolean passed = testDocumentCount();

        // Step 3: Print result
        System.out.println();
        System.out.println(BOLD + "=" .repeat(80) + RESET);
        if (passed) {
            System.out.println(GREEN + BOLD + "TEST PASSED: admin@accessarc.com has " + EXPECTED_DOCUMENT_COUNT + " documents" + RESET);
        } else {
            System.out.println(RED + BOLD + "TEST FAILED" + RESET);
            System.exit(1);
        }
        System.out.println(BOLD + "=" .repeat(80) + RESET);
    }

    private static void printBanner() {
        System.out.println(BOLD + BLUE);
        System.out.println("=".repeat(80));
        System.out.println("  Admin Document Count Integration Test");
        System.out.println("  Testing: admin@accessarc.com via Content Service");
        System.out.println("=".repeat(80));
        System.out.println(RESET);
    }

    private static boolean authenticate() throws Exception {
        System.out.println(YELLOW + "Step 1: Authenticating with Keycloak..." + RESET);
        System.out.println("  URL: " + KEYCLOAK_URL);
        System.out.println("  User: " + ADMIN_USERNAME);

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
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    break;
                }
                System.out.println("  Retry " + attempt + "/3...");
                Thread.sleep(2000);
            } catch (Exception e) {
                System.out.println("  Retry " + attempt + "/3 - " + e.getMessage());
                Thread.sleep(2000);
            }
        }

        if (response == null || response.statusCode() != 200) {
            return false;
        }

        // Parse access token (simple JSON parsing without external library)
        String body = response.body();
        accessToken = extractJsonValue(body, "access_token");

        // Decode JWT to get user ID
        String[] jwtParts = accessToken.split("\\.");
        String payloadJson = new String(Base64.getUrlDecoder().decode(jwtParts[1]));
        userId = extractJsonValue(payloadJson, "sub");
        String email = extractJsonValue(payloadJson, "email");
        String name = extractJsonValue(payloadJson, "name");

        driveId = "personal-" + userId;

        System.out.println(GREEN + "  Authentication successful!" + RESET);
        System.out.println("  User ID: " + userId);
        System.out.println("  Email: " + email);
        System.out.println("  Name: " + name);
        System.out.println("  Drive ID: " + driveId);
        System.out.println();

        return true;
    }

    private static boolean testDocumentCount() throws Exception {
        System.out.println(YELLOW + "Step 2: Testing document count for admin@accessarc.com via API Gateway..." + RESET);

        String url = API_GATEWAY_URL + "/api/documents/count";
        System.out.println("  URL: " + url);
        System.out.println("  Headers:");
        System.out.println("    X-Tenant-ID: " + TENANT_ID);
        System.out.println("    X-Drive-ID: " + driveId);
        System.out.println("    X-User-ID: " + userId);

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
            System.out.println(RED + "  Error: HTTP " + response.statusCode() + RESET);
            System.out.println("  Body: " + response.body());
            return false;
        }

        // Parse response
        String body = response.body();
        String successStr = extractJsonValue(body, "success");
        boolean success = "true".equalsIgnoreCase(successStr);

        if (!success) {
            System.out.println(RED + "  Error: API returned success=false" + RESET);
            return false;
        }

        // Extract count from nested data object
        int dataStart = body.indexOf("\"data\"");
        int countStart = body.indexOf("\"count\"", dataStart);
        int colonPos = body.indexOf(":", countStart);
        int endPos = body.indexOf("}", colonPos);
        String countStr = body.substring(colonPos + 1, endPos).trim();
        int actualCount = Integer.parseInt(countStr);

        System.out.println();
        System.out.println(BOLD + "  Assertion: Document count for " + ADMIN_USERNAME + RESET);
        System.out.println("    Expected: " + EXPECTED_DOCUMENT_COUNT);
        System.out.println("    Actual:   " + actualCount);

        if (actualCount == EXPECTED_DOCUMENT_COUNT) {
            System.out.println(GREEN + "    PASSED" + RESET);
            return true;
        } else {
            System.out.println(RED + "    FAILED: Expected " + EXPECTED_DOCUMENT_COUNT + " but got " + actualCount + RESET);
            return false;
        }
    }

    /**
     * Simple JSON value extractor (without external JSON library)
     */
    private static String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\"";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex == -1) return null;

        int colonIndex = json.indexOf(":", keyIndex);
        if (colonIndex == -1) return null;

        int valueStart = colonIndex + 1;
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }

        if (valueStart >= json.length()) return null;

        char startChar = json.charAt(valueStart);
        if (startChar == '"') {
            // String value
            int valueEnd = json.indexOf("\"", valueStart + 1);
            return json.substring(valueStart + 1, valueEnd);
        } else {
            // Number or boolean
            int valueEnd = valueStart;
            while (valueEnd < json.length() && !",}]".contains(String.valueOf(json.charAt(valueEnd)))) {
                valueEnd++;
            }
            return json.substring(valueStart, valueEnd).trim();
        }
    }
}
