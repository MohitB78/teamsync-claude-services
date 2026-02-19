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
 * Config Server Secrets Verification Test
 *
 * This test verifies that Config Server is correctly serving secrets from Vault
 * for all services. It prints all secrets available for each service.
 *
 * Run with:
 *   CONFIG_SERVER_URL=https://config-server-production-xxx.up.railway.app \
 *   CONFIG_SERVER_USER=config \
 *   CONFIG_SERVER_PASSWORD=<password> \
 *   VAULT_URL=https://vault-production-xxx.up.railway.app \
 *   VAULT_TOKEN=<token> \
 *   java ConfigServerSecretsVerificationTest
 */
@DisplayName("Config Server Secrets Verification")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ConfigServerSecretsVerificationTest {

    private static final String CONFIG_SERVER_URL = getEnvOrProperty("CONFIG_SERVER_URL", "http://localhost:8888");
    private static final String CONFIG_SERVER_USER = getEnvOrProperty("CONFIG_SERVER_USER", "config");
    // SECURITY FIX: Removed insecure default password. Must be set via environment variable.
    private static final String CONFIG_SERVER_PASSWORD = getEnvRequired("CONFIG_SERVER_PASSWORD");
    private static final String VAULT_URL = getEnvOrProperty("VAULT_URL", "http://localhost:8200");
    private static final String VAULT_TOKEN = getEnvOrProperty("VAULT_TOKEN", "");

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
        "accessarc-user-service",
        "accessarc-department-service",
        "accessarc-tenant-service",
        "accessarc-license-service",
        "accessarc-integration-service",
        "accessarc-ldap-service",
        "accessarc-workflow-service",
        "accessarc-business-rules-service",
        "accessarc-api-gateway"
    );

    // All Vault secret paths
    private static final List<String> VAULT_SECRET_PATHS = Arrays.asList(
        "mongodb",
        "redis",
        "jwt",
        "zitadel",
        "zitadel-db",
        "minio",
        "kafka",
        "elasticsearch",
        "config-server",
        "teamsync",
        "wopi",
        "session",
        "sendgrid",
        "openai",
        "anthropic",
        "firebase",
        "langsmith",
        "viewer",
        "ldap"
    );

    private static RestTemplate restTemplate;
    private static ObjectMapper objectMapper;

    private static String getEnvOrProperty(String name, String defaultValue) {
        String value = System.getenv(name);
        if (value != null && !value.isEmpty()) return value;
        value = System.getProperty(name);
        if (value != null && !value.isEmpty()) return value;
        return defaultValue;
    }

    private static String getEnvRequired(String name) {
        String value = System.getenv(name);
        if (value != null && !value.isEmpty()) return value;
        value = System.getProperty(name);
        if (value != null && !value.isEmpty()) return value;
        throw new IllegalStateException(
            "Required environment variable not set: " + name +
            ". Set with: export " + name + "=<value>");
    }

    @BeforeAll
    static void setUp() {
        restTemplate = new RestTemplate();
        objectMapper = new ObjectMapper();

        System.out.println("\n" + "=".repeat(100));
        System.out.println("    CONFIG SERVER & VAULT SECRETS VERIFICATION");
        System.out.println("=".repeat(100));
        System.out.println("\nConfiguration:");
        System.out.println("  Config Server URL: " + CONFIG_SERVER_URL);
        System.out.println("  Config Server User: " + CONFIG_SERVER_USER);
        System.out.println("  Vault URL: " + VAULT_URL);
        System.out.println("  Vault Token: " + (VAULT_TOKEN.isEmpty() ? "(not set)" : VAULT_TOKEN.substring(0, Math.min(10, VAULT_TOKEN.length())) + "..."));
        System.out.println("=".repeat(100) + "\n");
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

    // ==================== Vault Direct Secrets Test ====================

    @Test
    @Order(1)
    @DisplayName("1. List all secrets directly from Vault")
    void listAllVaultSecrets() {
        Assumptions.assumeTrue(!VAULT_TOKEN.isEmpty(), "VAULT_TOKEN not provided - skipping Vault direct test");

        System.out.println("\n" + "=".repeat(100));
        System.out.println("    VAULT SECRETS - DIRECT ACCESS");
        System.out.println("=".repeat(100) + "\n");

        HttpEntity<String> entity = new HttpEntity<>(createVaultHeaders());
        Map<String, Map<String, String>> allSecrets = new LinkedHashMap<>();

        for (String secretPath : VAULT_SECRET_PATHS) {
            String secretUrl = VAULT_URL + "/v1/secret/data/platform/" + secretPath;

            try {
                ResponseEntity<String> response = restTemplate.exchange(
                    secretUrl, HttpMethod.GET, entity, String.class);

                if (response.getStatusCode().is2xxSuccessful()) {
                    JsonNode root = objectMapper.readTree(response.getBody());
                    JsonNode data = root.path("data").path("data");

                    Map<String, String> secrets = new LinkedHashMap<>();
                    data.fieldNames().forEachRemaining(key -> {
                        String value = data.path(key).asText();
                        secrets.put(key, redactSecret(key, value));
                    });

                    allSecrets.put(secretPath, secrets);
                }
            } catch (HttpClientErrorException.NotFound e) {
                System.out.println("[NOT FOUND] secret/platform/" + secretPath);
            } catch (Exception e) {
                System.out.println("[ERROR] secret/platform/" + secretPath + " - " + e.getMessage());
            }
        }

        // Print all secrets
        System.out.println("Vault Secrets (secret/platform/*):\n");

        for (Map.Entry<String, Map<String, String>> entry : allSecrets.entrySet()) {
            System.out.println("┌─ " + entry.getKey());
            Map<String, String> secrets = entry.getValue();
            int i = 0;
            for (Map.Entry<String, String> secret : secrets.entrySet()) {
                String prefix = (i == secrets.size() - 1) ? "└── " : "├── ";
                System.out.println("│  " + prefix + secret.getKey() + ": " + secret.getValue());
                i++;
            }
            System.out.println();
        }

        System.out.println("-".repeat(100));
        System.out.println("Total secret paths found: " + allSecrets.size() + "/" + VAULT_SECRET_PATHS.size());
        System.out.println("-".repeat(100));
    }

    // ==================== Config Server Secrets for Services ====================

    @Test
    @Order(2)
    @DisplayName("2. List secrets served by Config Server for TeamSync services")
    void listConfigServerSecretsForTeamSync() {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("    CONFIG SERVER SECRETS - TEAMSYNC SERVICES");
        System.out.println("=".repeat(100) + "\n");

        HttpEntity<String> entity = new HttpEntity<>(createConfigServerHeaders());

        for (String service : TEAMSYNC_SERVICES) {
            printServiceSecrets(service, "railway", entity);
        }
    }

    @Test
    @Order(3)
    @DisplayName("3. List secrets served by Config Server for AccessArc services")
    void listConfigServerSecretsForAccessArc() {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("    CONFIG SERVER SECRETS - ACCESSARC SERVICES");
        System.out.println("=".repeat(100) + "\n");

        HttpEntity<String> entity = new HttpEntity<>(createConfigServerHeaders());

        for (String service : ACCESSARC_SERVICES) {
            printServiceSecrets(service, "railway", entity);
        }
    }

    @Test
    @Order(4)
    @DisplayName("4. Verify shared configuration (application-railway.yml)")
    void verifySharedConfiguration() {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("    SHARED CONFIGURATION (application-railway.yml)");
        System.out.println("=".repeat(100) + "\n");

        HttpEntity<String> entity = new HttpEntity<>(createConfigServerHeaders());
        String configUrl = CONFIG_SERVER_URL + "/application/railway";

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                configUrl, HttpMethod.GET, entity, String.class);

            JsonNode config = objectMapper.readTree(response.getBody());
            JsonNode propertySources = config.path("propertySources");

            System.out.println("Property Sources:");
            for (JsonNode source : propertySources) {
                String sourceName = source.path("name").asText();
                System.out.println("\n┌─ " + sourceName);

                JsonNode sourceData = source.path("source");
                if (!sourceData.isMissingNode()) {
                    // Group properties by prefix
                    Map<String, List<String>> grouped = new TreeMap<>();
                    sourceData.fieldNames().forEachRemaining(key -> {
                        String prefix = key.contains(".") ? key.substring(0, key.indexOf(".")) : key;
                        grouped.computeIfAbsent(prefix, k -> new ArrayList<>()).add(key);
                    });

                    for (Map.Entry<String, List<String>> group : grouped.entrySet()) {
                        System.out.println("│  ├─ " + group.getKey() + ".*");
                        for (String key : group.getValue()) {
                            String value = sourceData.path(key).asText();
                            System.out.println("│  │  └── " + key + ": " + redactSecret(key, value));
                        }
                    }
                }
            }

            System.out.println("\n[PASS] Shared configuration retrieved successfully");
        } catch (Exception e) {
            System.out.println("[FAIL] Could not retrieve shared configuration: " + e.getMessage());
        }
    }

    @Test
    @Order(5)
    @DisplayName("5. Generate comprehensive secrets report")
    void generateSecretsReport() {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("    COMPREHENSIVE SECRETS REPORT");
        System.out.println("=".repeat(100) + "\n");

        HttpEntity<String> configEntity = new HttpEntity<>(createConfigServerHeaders());

        // Collect all unique property keys across services
        Set<String> allPropertyKeys = new TreeSet<>();
        Map<String, Set<String>> serviceProperties = new LinkedHashMap<>();

        List<String> allServices = new ArrayList<>();
        allServices.addAll(TEAMSYNC_SERVICES);
        allServices.addAll(ACCESSARC_SERVICES);

        for (String service : allServices) {
            String configUrl = CONFIG_SERVER_URL + "/" + service + "/railway";
            Set<String> properties = new TreeSet<>();

            try {
                ResponseEntity<String> response = restTemplate.exchange(
                    configUrl, HttpMethod.GET, configEntity, String.class);

                JsonNode config = objectMapper.readTree(response.getBody());
                JsonNode propertySources = config.path("propertySources");

                for (JsonNode source : propertySources) {
                    JsonNode sourceData = source.path("source");
                    sourceData.fieldNames().forEachRemaining(key -> {
                        properties.add(key);
                        allPropertyKeys.add(key);
                    });
                }

                serviceProperties.put(service, properties);
            } catch (Exception e) {
                serviceProperties.put(service, Set.of("ERROR: " + e.getMessage()));
            }
        }

        // Print summary by category
        System.out.println("1. INFRASTRUCTURE SECRETS (available to all services):\n");

        List<String> infrastructureKeys = Arrays.asList(
            "infrastructure.mongodb.uri",
            "infrastructure.redis.host",
            "infrastructure.redis.port",
            "infrastructure.redis.password",
            "infrastructure.zitadel.url",
            "infrastructure.zitadel.issuer",
            "infrastructure.zitadel.project-id",
            "infrastructure.zitadel.client-id",
            "infrastructure.kafka.bootstrap-servers",
            "infrastructure.elasticsearch.uri",
            "storage.minio.endpoint",
            "storage.minio.access-key",
            "storage.minio.secret-key"
        );

        for (String key : infrastructureKeys) {
            boolean found = allPropertyKeys.contains(key);
            System.out.println("   " + (found ? "[✓]" : "[ ]") + " " + key);
        }

        System.out.println("\n2. SERVICE-SPECIFIC SECRETS:\n");

        List<String> serviceSpecificKeys = Arrays.asList(
            "ai.openai.api-key",
            "ai.anthropic.api-key",
            "ai.langsmith.api-key",
            "wopi.discovery-url",
            "sendgrid.api-key",
            "firebase.credentials",
            "viewer.license-key"
        );

        for (String key : serviceSpecificKeys) {
            boolean found = allPropertyKeys.contains(key);
            System.out.println("   " + (found ? "[✓]" : "[ ]") + " " + key);
        }

        System.out.println("\n3. SERVICES RECEIVING CONFIGURATION:\n");

        int configuredServices = 0;
        for (Map.Entry<String, Set<String>> entry : serviceProperties.entrySet()) {
            String service = entry.getKey();
            Set<String> props = entry.getValue();
            boolean hasInfra = props.stream().anyMatch(p -> p.startsWith("infrastructure."));

            if (hasInfra) {
                System.out.println("   [✓] " + service + " (" + props.size() + " properties)");
                configuredServices++;
            } else if (props.iterator().next().startsWith("ERROR:")) {
                System.out.println("   [!] " + service + " - " + props.iterator().next());
            } else {
                System.out.println("   [?] " + service + " (" + props.size() + " properties, no infrastructure)");
            }
        }

        System.out.println("\n" + "-".repeat(100));
        System.out.println("Summary:");
        System.out.println("  - Total services: " + allServices.size());
        System.out.println("  - Services with infrastructure config: " + configuredServices);
        System.out.println("  - Total unique property keys: " + allPropertyKeys.size());
        System.out.println("-".repeat(100));
    }

    // ==================== Helper Methods ====================

    private void printServiceSecrets(String serviceName, String profile, HttpEntity<String> entity) {
        String configUrl = CONFIG_SERVER_URL + "/" + serviceName + "/" + profile;

        System.out.println("┌" + "─".repeat(98) + "┐");
        System.out.println("│ " + serviceName + " ".repeat(Math.max(1, 97 - serviceName.length())) + "│");
        System.out.println("└" + "─".repeat(98) + "┘");

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                configUrl, HttpMethod.GET, entity, String.class);

            JsonNode config = objectMapper.readTree(response.getBody());
            JsonNode propertySources = config.path("propertySources");

            int sourceCount = 0;
            Map<String, String> secrets = new TreeMap<>();

            for (JsonNode source : propertySources) {
                sourceCount++;
                String sourceName = source.path("name").asText();
                JsonNode sourceData = source.path("source");

                // Check if this is a Vault source
                boolean isVaultSource = sourceName.toLowerCase().contains("vault");

                sourceData.fieldNames().forEachRemaining(key -> {
                    String value = sourceData.path(key).asText();
                    // Only show secrets (sensitive values)
                    if (isSensitiveKey(key)) {
                        secrets.put(key, redactSecret(key, value));
                    }
                });

                if (isVaultSource) {
                    System.out.println("  [VAULT] " + sourceName);
                }
            }

            System.out.println("  Property sources: " + sourceCount);
            System.out.println("  Secrets available:");

            if (secrets.isEmpty()) {
                System.out.println("    (No sensitive properties found in this configuration)");
            } else {
                for (Map.Entry<String, String> secret : secrets.entrySet()) {
                    System.out.println("    • " + secret.getKey() + ": " + secret.getValue());
                }
            }

            // Also show non-secret infrastructure config
            System.out.println("  Infrastructure config:");
            for (JsonNode source : propertySources) {
                JsonNode sourceData = source.path("source");
                sourceData.fieldNames().forEachRemaining(key -> {
                    if (key.startsWith("infrastructure.") && !isSensitiveKey(key)) {
                        String value = sourceData.path(key).asText();
                        System.out.println("    • " + key + ": " + value);
                    }
                });
            }

        } catch (HttpClientErrorException.Unauthorized e) {
            System.out.println("  [ERROR] Unauthorized - check credentials");
        } catch (HttpClientErrorException.NotFound e) {
            System.out.println("  [INFO] No service-specific config (will use shared application.yml)");
        } catch (Exception e) {
            System.out.println("  [ERROR] " + e.getMessage());
        }

        System.out.println();
    }

    private boolean isSensitiveKey(String key) {
        String lowerKey = key.toLowerCase();
        return lowerKey.contains("password") ||
               lowerKey.contains("secret") ||
               lowerKey.contains("key") ||
               lowerKey.contains("token") ||
               lowerKey.contains("credential") ||
               lowerKey.contains("uri") && (lowerKey.contains("mongo") || lowerKey.contains("redis")) ||
               lowerKey.contains("api-key") ||
               lowerKey.contains("apikey");
    }

    private String redactSecret(String key, String value) {
        if (value == null || value.isEmpty()) {
            return "(empty)";
        }

        String lowerKey = key.toLowerCase();

        // Don't redact non-sensitive values
        if (!isSensitiveKey(key)) {
            return value;
        }

        // Special handling for URIs with credentials
        if (value.contains("@") && (lowerKey.contains("uri") || lowerKey.contains("url"))) {
            // mongodb://user:password@host -> mongodb://user:***@host
            return value.replaceAll(":[^:@/]+@", ":***@");
        }

        // For short values, show length only
        if (value.length() <= 10) {
            return "***(" + value.length() + " chars)";
        }

        // Show first 4 and last 2 characters
        return value.substring(0, 4) + "***" + value.substring(value.length() - 2) +
               " (" + value.length() + " chars)";
    }

    // ==================== Main method for standalone execution ====================

    public static void main(String[] args) {
        setUp();

        ConfigServerSecretsVerificationTest test = new ConfigServerSecretsVerificationTest();

        try {
            test.listAllVaultSecrets();
        } catch (Exception e) {
            System.out.println("Vault test skipped: " + e.getMessage());
        }

        test.listConfigServerSecretsForTeamSync();
        test.listConfigServerSecretsForAccessArc();
        test.verifySharedConfiguration();
        test.generateSecretsReport();
    }
}
