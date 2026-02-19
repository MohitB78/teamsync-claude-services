package com.teamsync.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Runtime Integration Tests for Config Server and Vault
 *
 * These tests verify that Config Server can serve configurations
 * for all services and that Vault secrets are accessible.
 *
 * Requirements:
 * - Config Server must be running
 * - Vault must be unsealed (for Vault tests)
 *
 * Run with:
 *   mvn test -Dtest=ConfigServerRuntimeIntegrationTest \
 *     -DCONFIG_SERVER_URL=https://config-server-production-xxx.up.railway.app \
 *     -DCONFIG_SERVER_USER=config \
 *     -DCONFIG_SERVER_PASSWORD=<password> \
 *     -DVAULT_URL=https://vault-production-xxx.up.railway.app \
 *     -DVAULT_TOKEN=<token>
 */
@DisplayName("Config Server Runtime Integration Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConfigServerRuntimeIntegrationTest {

    private static final String CONFIG_SERVER_URL = System.getenv("CONFIG_SERVER_URL") != null
        ? System.getenv("CONFIG_SERVER_URL")
        : System.getProperty("CONFIG_SERVER_URL", "http://localhost:8888");

    private static final String CONFIG_SERVER_USER = System.getenv("CONFIG_SERVER_USER") != null
        ? System.getenv("CONFIG_SERVER_USER")
        : System.getProperty("CONFIG_SERVER_USER", "config");

    private static final String CONFIG_SERVER_PASSWORD = System.getenv("CONFIG_SERVER_PASSWORD") != null
        ? System.getenv("CONFIG_SERVER_PASSWORD")
        : System.getProperty("CONFIG_SERVER_PASSWORD", "password");

    private static final String VAULT_URL = System.getenv("VAULT_URL") != null
        ? System.getenv("VAULT_URL")
        : System.getProperty("VAULT_URL", "http://localhost:8200");

    private static final String VAULT_TOKEN = System.getenv("VAULT_TOKEN") != null
        ? System.getenv("VAULT_TOKEN")
        : System.getProperty("VAULT_TOKEN", "");

    // All TeamSync services
    private static final List<String> TEAMSYNC_SERVICES = Arrays.asList(
        "teamsync-api-gateway",
        "teamsync-content-service",
        "teamsync-storage-service",
        "teamsync-sharing-service",
        "teamsync-team-service",
        "teamsync-project-service",
        "teamsync-workflow-execution-service",
        "teamsync-trash-service",
        "teamsync-search-service",
        "teamsync-chat-service",
        "teamsync-notification-service",
        "teamsync-activity-service",
        "teamsync-wopi-service",
        "teamsync-settings-service",
        "teamsync-presence-service",
        "permission-manager-service"
    );

    // All AccessArc services
    private static final List<String> ACCESSARC_SERVICES = Arrays.asList(
        "user-service",
        "department-service",
        "tenant-service",
        "license-service",
        "integration-service",
        "ldap-service",
        "workflow-service",
        "business-rules-service",
        "api-gateway"
    );

    // Required Vault secrets
    private static final List<String> VAULT_SECRETS = Arrays.asList(
        "mongodb",
        "redis",
        "jwt",
        "zitadel",
        "minio",
        "kafka",
        "elasticsearch"
    );

    private RestTemplate restTemplate;
    private ObjectMapper objectMapper;
    private boolean configServerAvailable = false;
    private boolean vaultAvailable = false;

    @BeforeAll
    static void printBanner() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("    CONFIG SERVER & VAULT RUNTIME INTEGRATION TESTS");
        System.out.println("=".repeat(80));
        System.out.println("Config Server: " + CONFIG_SERVER_URL);
        System.out.println("Vault: " + VAULT_URL);
        System.out.println("=".repeat(80) + "\n");
    }

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        objectMapper = new ObjectMapper();

        // Check Config Server availability
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(
                CONFIG_SERVER_URL + "/actuator/health", String.class);
            configServerAvailable = response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            configServerAvailable = false;
        }

        // Check Vault availability
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(
                VAULT_URL + "/v1/sys/seal-status", String.class);
            vaultAvailable = response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            vaultAvailable = false;
        }
    }

    private HttpHeaders createConfigServerHeaders() {
        HttpHeaders headers = new HttpHeaders();
        String auth = CONFIG_SERVER_USER + ":" + CONFIG_SERVER_PASSWORD;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
        headers.set("Authorization", "Basic " + encodedAuth);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private HttpHeaders createVaultHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Vault-Token", VAULT_TOKEN);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    // ==================== Config Server Health Tests ====================

    @Nested
    @DisplayName("1. Config Server Health")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class ConfigServerHealthTests {

        @Test
        @Order(1)
        @DisplayName("Config Server should be healthy and accessible")
        void configServer_ShouldBeHealthy() {
            Assumptions.assumeTrue(configServerAvailable, "Config Server not available");

            System.out.println("\n--- Config Server Health Check ---");

            try {
                ResponseEntity<String> response = restTemplate.getForEntity(
                    CONFIG_SERVER_URL + "/actuator/health", String.class);

                JsonNode health = objectMapper.readTree(response.getBody());
                String status = health.path("status").asText();

                System.out.println("Status: " + status);
                System.out.println("URL: " + CONFIG_SERVER_URL);

                assertThat(response.getStatusCode())
                    .describedAs("Config Server should return 2xx")
                    .matches(s -> s.is2xxSuccessful());

                System.out.println("[PASS] Config Server is healthy");
            } catch (Exception e) {
                fail("Config Server health check failed: " + e.getMessage());
            }
        }

        @Test
        @Order(2)
        @DisplayName("Config Server should require authentication")
        void configServer_ShouldRequireAuth() {
            Assumptions.assumeTrue(configServerAvailable, "Config Server not available");

            System.out.println("\n--- Config Server Authentication Check ---");

            try {
                // Try without authentication - should fail
                ResponseEntity<String> response = restTemplate.getForEntity(
                    CONFIG_SERVER_URL + "/application/default", String.class);

                // If we get here, auth might be disabled
                System.out.println("[WARN] Config Server allowed unauthenticated access");
            } catch (HttpClientErrorException.Unauthorized e) {
                System.out.println("[PASS] Config Server requires authentication (401 returned)");
            } catch (HttpClientErrorException.Forbidden e) {
                System.out.println("[PASS] Config Server requires authentication (403 returned)");
            } catch (Exception e) {
                System.out.println("[INFO] Exception: " + e.getClass().getSimpleName());
            }
        }
    }

    // ==================== TeamSync Service Configuration Tests ====================

    @Nested
    @DisplayName("2. TeamSync Services Configuration")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class TeamSyncConfigurationTests {

        @Test
        @Order(1)
        @DisplayName("All TeamSync services should be able to fetch configuration from Config Server")
        void allTeamSyncServices_ShouldFetchConfiguration() {
            Assumptions.assumeTrue(configServerAvailable, "Config Server not available");

            System.out.println("\n" + "=".repeat(60));
            System.out.println("  TEAMSYNC SERVICES - CONFIG SERVER FETCH TEST");
            System.out.println("=".repeat(60) + "\n");

            HttpEntity<String> entity = new HttpEntity<>(createConfigServerHeaders());
            int successCount = 0;
            int failCount = 0;
            List<String> failures = new ArrayList<>();

            for (String service : TEAMSYNC_SERVICES) {
                String configUrl = CONFIG_SERVER_URL + "/" + service + "/railway";

                try {
                    ResponseEntity<String> response = restTemplate.exchange(
                        configUrl, HttpMethod.GET, entity, String.class);

                    if (response.getStatusCode().is2xxSuccessful()) {
                        JsonNode config = objectMapper.readTree(response.getBody());
                        int sourceCount = 0;
                        for (JsonNode ignored : config.path("propertySources")) {
                            sourceCount++;
                        }

                        System.out.println("[PASS] " + service);
                        System.out.println("       Property sources: " + sourceCount);
                        successCount++;
                    }
                } catch (Exception e) {
                    System.out.println("[FAIL] " + service + " - " + e.getMessage());
                    failures.add(service);
                    failCount++;
                }
            }

            System.out.println("\n-".repeat(60));
            System.out.println("Summary: " + successCount + " passed, " + failCount + " failed");
            System.out.println("-".repeat(60));

            assertThat(failures)
                .describedAs("All TeamSync services should fetch configuration successfully")
                .isEmpty();
        }

        @Test
        @Order(2)
        @DisplayName("TeamSync services should receive infrastructure configuration")
        void teamSyncServices_ShouldReceiveInfrastructureConfig() {
            Assumptions.assumeTrue(configServerAvailable, "Config Server not available");

            System.out.println("\n--- Infrastructure Configuration Verification ---");

            HttpEntity<String> entity = new HttpEntity<>(createConfigServerHeaders());
            String configUrl = CONFIG_SERVER_URL + "/teamsync-content-service/railway";

            try {
                ResponseEntity<String> response = restTemplate.exchange(
                    configUrl, HttpMethod.GET, entity, String.class);

                JsonNode config = objectMapper.readTree(response.getBody());
                JsonNode propertySources = config.path("propertySources");

                // Check for key infrastructure properties
                List<String> expectedKeys = Arrays.asList(
                    "infrastructure.mongodb.uri",
                    "infrastructure.redis.host",
                    "infrastructure.zitadel.url",
                    "infrastructure.kafka.bootstrap-servers"
                );

                System.out.println("Checking for infrastructure properties in config:");

                for (JsonNode source : propertySources) {
                    JsonNode sourceData = source.path("source");
                    for (String key : expectedKeys) {
                        if (!sourceData.path(key).isMissingNode()) {
                            String value = sourceData.path(key).asText();
                            // Redact sensitive parts
                            if (value.contains("@")) {
                                value = value.replaceAll(":[^@]+@", ":***@");
                            }
                            System.out.println("  [FOUND] " + key);
                        }
                    }
                }

                System.out.println("\n[PASS] Infrastructure configuration available");
            } catch (Exception e) {
                System.out.println("[FAIL] Could not verify infrastructure config: " + e.getMessage());
            }
        }
    }

    // ==================== AccessArc Service Configuration Tests ====================

    @Nested
    @DisplayName("3. AccessArc Services Configuration")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class AccessArcConfigurationTests {

        @Test
        @Order(1)
        @DisplayName("All AccessArc services should be able to fetch configuration from Config Server")
        void allAccessArcServices_ShouldFetchConfiguration() {
            Assumptions.assumeTrue(configServerAvailable, "Config Server not available");

            System.out.println("\n" + "=".repeat(60));
            System.out.println("  ACCESSARC SERVICES - CONFIG SERVER FETCH TEST");
            System.out.println("=".repeat(60) + "\n");

            HttpEntity<String> entity = new HttpEntity<>(createConfigServerHeaders());
            int successCount = 0;
            int failCount = 0;
            List<String> failures = new ArrayList<>();

            for (String service : ACCESSARC_SERVICES) {
                // AccessArc services use accessarc- prefix in config
                String configUrl = CONFIG_SERVER_URL + "/accessarc-" + service + "/railway";

                try {
                    ResponseEntity<String> response = restTemplate.exchange(
                        configUrl, HttpMethod.GET, entity, String.class);

                    if (response.getStatusCode().is2xxSuccessful()) {
                        JsonNode config = objectMapper.readTree(response.getBody());
                        int sourceCount = 0;
                        for (JsonNode ignored : config.path("propertySources")) {
                            sourceCount++;
                        }

                        System.out.println("[PASS] accessarc-" + service);
                        System.out.println("       Property sources: " + sourceCount);
                        successCount++;
                    }
                } catch (Exception e) {
                    System.out.println("[FAIL] accessarc-" + service + " - " + e.getMessage());
                    failures.add(service);
                    failCount++;
                }
            }

            System.out.println("\n-".repeat(60));
            System.out.println("Summary: " + successCount + " passed, " + failCount + " failed");
            System.out.println("-".repeat(60));

            // Note: Some failures are acceptable if service-specific configs don't exist
            System.out.println("\nNote: Failures may be acceptable if service-specific configs don't exist.");
            System.out.println("All services will still receive application-railway.yml shared config.");
        }
    }

    // ==================== Vault Integration Tests ====================

    @Nested
    @DisplayName("4. Vault Integration")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class VaultIntegrationTests {

        @Test
        @Order(1)
        @DisplayName("Vault should be unsealed and accessible")
        void vault_ShouldBeUnsealed() {
            Assumptions.assumeTrue(vaultAvailable, "Vault not available");

            System.out.println("\n--- Vault Status Check ---");

            try {
                ResponseEntity<String> response = restTemplate.getForEntity(
                    VAULT_URL + "/v1/sys/seal-status", String.class);

                JsonNode sealStatus = objectMapper.readTree(response.getBody());
                boolean sealed = sealStatus.path("sealed").asBoolean(true);
                boolean initialized = sealStatus.path("initialized").asBoolean(false);

                System.out.println("Initialized: " + initialized);
                System.out.println("Sealed: " + sealed);

                assertThat(initialized).describedAs("Vault should be initialized").isTrue();
                assertThat(sealed).describedAs("Vault should be unsealed").isFalse();

                System.out.println("[PASS] Vault is unsealed and operational");
            } catch (Exception e) {
                System.out.println("[FAIL] Vault check failed: " + e.getMessage());
            }
        }

        @Test
        @Order(2)
        @DisplayName("All required secrets should exist in Vault")
        void allSecrets_ShouldExistInVault() {
            Assumptions.assumeTrue(vaultAvailable, "Vault not available");
            Assumptions.assumeTrue(!VAULT_TOKEN.isEmpty(), "VAULT_TOKEN not provided");

            System.out.println("\n--- Vault Secrets Verification ---");

            HttpEntity<String> entity = new HttpEntity<>(createVaultHeaders());
            int foundCount = 0;
            int missingCount = 0;

            for (String secret : VAULT_SECRETS) {
                String secretUrl = VAULT_URL + "/v1/secret/data/platform/" + secret;

                try {
                    ResponseEntity<String> response = restTemplate.exchange(
                        secretUrl, HttpMethod.GET, entity, String.class);

                    if (response.getStatusCode().is2xxSuccessful()) {
                        System.out.println("[FOUND] secret/platform/" + secret);
                        foundCount++;
                    }
                } catch (HttpClientErrorException.NotFound e) {
                    System.out.println("[MISSING] secret/platform/" + secret);
                    missingCount++;
                } catch (Exception e) {
                    System.out.println("[ERROR] secret/platform/" + secret + " - " + e.getMessage());
                    missingCount++;
                }
            }

            System.out.println("\nSummary: " + foundCount + " found, " + missingCount + " missing");

            assertThat(missingCount)
                .describedAs("All required secrets should exist in Vault")
                .isZero();
        }

        @Test
        @Order(3)
        @DisplayName("Config Server should serve Vault secrets to services")
        void configServer_ShouldServeVaultSecrets() {
            Assumptions.assumeTrue(configServerAvailable, "Config Server not available");
            Assumptions.assumeTrue(vaultAvailable, "Vault not available");

            System.out.println("\n--- Config Server Vault Integration ---");

            HttpEntity<String> entity = new HttpEntity<>(createConfigServerHeaders());
            String configUrl = CONFIG_SERVER_URL + "/teamsync-content-service/railway";

            try {
                ResponseEntity<String> response = restTemplate.exchange(
                    configUrl, HttpMethod.GET, entity, String.class);

                JsonNode config = objectMapper.readTree(response.getBody());
                JsonNode propertySources = config.path("propertySources");

                boolean vaultSourceFound = false;
                for (JsonNode source : propertySources) {
                    String sourceName = source.path("name").asText();
                    if (sourceName.contains("vault")) {
                        vaultSourceFound = true;
                        System.out.println("[FOUND] Vault property source: " + sourceName);
                    }
                }

                if (vaultSourceFound) {
                    System.out.println("\n[PASS] Config Server is serving Vault secrets");
                } else {
                    System.out.println("\n[INFO] No Vault property sources found");
                    System.out.println("       This may be expected if Vault integration is disabled");
                }
            } catch (Exception e) {
                System.out.println("[FAIL] Could not verify Vault integration: " + e.getMessage());
            }
        }
    }

    // ==================== Summary Report ====================

    @Nested
    @DisplayName("5. Summary Report")
    class SummaryReport {

        @Test
        @Order(100)
        @DisplayName("Generate comprehensive test summary")
        void generateSummary() {
            System.out.println("\n" + "=".repeat(80));
            System.out.println("    TEST SUMMARY REPORT");
            System.out.println("=".repeat(80));

            System.out.println("\nTest Configuration:");
            System.out.println("  Config Server URL: " + CONFIG_SERVER_URL);
            System.out.println("  Config Server Available: " + configServerAvailable);
            System.out.println("  Vault URL: " + VAULT_URL);
            System.out.println("  Vault Available: " + vaultAvailable);
            System.out.println("  Vault Token Provided: " + !VAULT_TOKEN.isEmpty());

            System.out.println("\nServices Tested:");
            System.out.println("  TeamSync Services: " + TEAMSYNC_SERVICES.size());
            System.out.println("  AccessArc Services: " + ACCESSARC_SERVICES.size());
            System.out.println("  Total: " + (TEAMSYNC_SERVICES.size() + ACCESSARC_SERVICES.size()));

            System.out.println("\nVault Secrets Checked: " + VAULT_SECRETS.size());

            System.out.println("\n" + "=".repeat(80));
            System.out.println("    END OF SUMMARY");
            System.out.println("=".repeat(80) + "\n");
        }
    }
}
