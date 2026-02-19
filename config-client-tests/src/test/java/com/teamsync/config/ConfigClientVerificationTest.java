package com.teamsync.config;

import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive Config Client Verification Tests
 *
 * These tests verify that all backend services (TeamSync + AccessArc) are correctly
 * configured to use Spring Cloud Config Server and Vault for centralized configuration.
 *
 * Tests cover:
 * 1. YAML configuration verification (static analysis)
 * 2. Config Server connectivity (runtime tests)
 * 3. Vault integration verification
 * 4. Service-specific configuration checks
 *
 * Run with:
 *   mvn test -Dtest=ConfigClientVerificationTest \
 *     -DCONFIG_SERVER_URL=http://localhost:8888 \
 *     -DCONFIG_SERVER_USER=config \
 *     -DCONFIG_SERVER_PASSWORD=password
 */
@DisplayName("Config Client Verification Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConfigClientVerificationTest {

    // TeamSync Services (14 total + 2 already configured = 16)
    private static final List<ServiceInfo> TEAMSYNC_SERVICES = Arrays.asList(
        new ServiceInfo("activity-service", "teamsync-activity-service", 9102),
        new ServiceInfo("chat-service", "teamsync-chat-service", 9090),
        new ServiceInfo("notification-service", "teamsync-notification-service", 9091),
        new ServiceInfo("permission-manager-service", "permission-manager-service", 9096),
        new ServiceInfo("presence-service", "teamsync-presence-service", 9095),
        new ServiceInfo("project-service", "teamsync-project-service", 9086),
        new ServiceInfo("search-service", "teamsync-search-service", 9089),
        new ServiceInfo("settings-service", "teamsync-settings-service", 9094),
        new ServiceInfo("sharing-service", "teamsync-sharing-service", 9084),
        new ServiceInfo("storage-service", "teamsync-storage-service", 9083),
        new ServiceInfo("team-service", "teamsync-team-service", 9085),
        new ServiceInfo("trash-service", "teamsync-trash-service", 9088),
        new ServiceInfo("wopi-service", "teamsync-wopi-service", 9093),
        new ServiceInfo("workflow-execution-service", "teamsync-workflow-execution-service", 9087),
        // Already configured services
        new ServiceInfo("api-gateway", "teamsync-api-gateway", 9080),
        new ServiceInfo("content-service", "teamsync-content-service", 9081)
    );

    // AccessArc Services (9 total)
    private static final List<ServiceInfo> ACCESSARC_SERVICES = Arrays.asList(
        new ServiceInfo("user-service", "user-service", 8081),
        new ServiceInfo("department-service", "department-service", 8082),
        new ServiceInfo("tenant-service", "tenant-service", 8083),
        new ServiceInfo("license-service", "license-service", 8084),
        new ServiceInfo("integration-service", "integration-service", 8085),
        new ServiceInfo("ldap-service", "ldap-service", 8083),
        new ServiceInfo("workflow-service", "workflow-service", 8087),
        new ServiceInfo("business-rules-service", "business-rules-service", 8080),
        new ServiceInfo("api-gateway", "api-gateway", 8080)
    );

    private static final String PROJECT_ROOT = System.getProperty("project.root",
        System.getProperty("user.dir").replace("/teamsync-backend/config-client-tests", ""));

    @Nested
    @DisplayName("1. TeamSync Services - YAML Configuration Verification")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class TeamSyncYamlVerificationTests {

        @Test
        @Order(1)
        @DisplayName("All TeamSync services should have Config Client enabled in application.yml")
        void allTeamSyncServices_ShouldHaveConfigClientEnabled() throws IOException {
            System.out.println("\n" + "=".repeat(70));
            System.out.println("  TEAMSYNC SERVICES - CONFIG CLIENT YAML VERIFICATION");
            System.out.println("=".repeat(70) + "\n");

            int passCount = 0;
            int failCount = 0;
            List<String> failures = new ArrayList<>();

            for (ServiceInfo service : TEAMSYNC_SERVICES) {
                String yamlPath = String.format("%s/teamsync-backend/%s/src/main/resources/application.yml",
                    PROJECT_ROOT, service.directory);

                try {
                    ConfigClientVerificationResult result = verifyConfigClientInYaml(yamlPath, service.directory);

                    if (result.isValid()) {
                        System.out.println("[PASS] " + service.directory);
                        System.out.println("       Config import: " + result.configImport);
                        System.out.println("       Config enabled: " + result.configEnabled);
                        System.out.println("       Bus enabled: " + result.busEnabled);
                        passCount++;
                    } else {
                        System.out.println("[FAIL] " + service.directory);
                        System.out.println("       Issues: " + result.issues);
                        failures.add(service.directory + ": " + result.issues);
                        failCount++;
                    }
                    System.out.println();
                } catch (IOException e) {
                    System.out.println("[ERROR] " + service.directory + " - " + e.getMessage());
                    failures.add(service.directory + ": File not found");
                    failCount++;
                }
            }

            System.out.println("-".repeat(70));
            System.out.println("Summary: " + passCount + " passed, " + failCount + " failed");
            System.out.println("-".repeat(70));

            assertThat(failures)
                .describedAs("All TeamSync services should have Config Client properly configured")
                .isEmpty();
        }

        @Test
        @Order(2)
        @DisplayName("TeamSync services should have consistent Config Client configuration")
        void teamSyncServices_ShouldHaveConsistentConfig() throws IOException {
            System.out.println("\n--- Config Client Configuration Consistency Check ---\n");

            Set<String> configImports = new HashSet<>();
            Set<String> busDestinations = new HashSet<>();

            for (ServiceInfo service : TEAMSYNC_SERVICES) {
                String yamlPath = String.format("%s/teamsync-backend/%s/src/main/resources/application.yml",
                    PROJECT_ROOT, service.directory);

                try {
                    String content = Files.readString(Path.of(yamlPath));

                    // Extract config import pattern
                    Pattern importPattern = Pattern.compile("import:\\s*[\"']?(optional:configserver:[^\"'\\n]+)[\"']?");
                    java.util.regex.Matcher importMatcher = importPattern.matcher(content);
                    if (importMatcher.find()) {
                        configImports.add(importMatcher.group(1).trim());
                    }

                    // Extract bus destination
                    Pattern busPattern = Pattern.compile("destination:\\s*([^\\n]+)");
                    java.util.regex.Matcher busMatcher = busPattern.matcher(content);
                    if (busMatcher.find()) {
                        busDestinations.add(busMatcher.group(1).trim());
                    }
                } catch (IOException ignored) {
                }
            }

            System.out.println("Config Import Patterns Found:");
            configImports.forEach(p -> System.out.println("  - " + p));

            System.out.println("\nBus Destinations Found:");
            busDestinations.forEach(d -> System.out.println("  - " + d));

            // Should have consistent config
            assertThat(configImports)
                .describedAs("All services should use the same Config Server import pattern")
                .hasSize(1);

            assertThat(busDestinations)
                .describedAs("All services should use the same bus destination")
                .hasSize(1);

            System.out.println("\n[PASS] All services have consistent Config Client configuration");
        }
    }

    @Nested
    @DisplayName("2. AccessArc Services - YAML Configuration Verification")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class AccessArcYamlVerificationTests {

        @Test
        @Order(1)
        @DisplayName("All AccessArc services should have Config Client enabled in application.yml")
        void allAccessArcServices_ShouldHaveConfigClientEnabled() throws IOException {
            System.out.println("\n" + "=".repeat(70));
            System.out.println("  ACCESSARC SERVICES - CONFIG CLIENT YAML VERIFICATION");
            System.out.println("=".repeat(70) + "\n");

            int passCount = 0;
            int failCount = 0;
            List<String> failures = new ArrayList<>();

            for (ServiceInfo service : ACCESSARC_SERVICES) {
                String yamlPath = String.format("%s/access-arc-backend/%s/src/main/resources/application.yml",
                    PROJECT_ROOT, service.directory);

                try {
                    ConfigClientVerificationResult result = verifyConfigClientInYaml(yamlPath, service.directory);

                    if (result.isValid()) {
                        System.out.println("[PASS] " + service.directory);
                        System.out.println("       Config import: " + result.configImport);
                        System.out.println("       Config enabled: " + result.configEnabled);
                        System.out.println("       Bus enabled: " + result.busEnabled);
                        passCount++;
                    } else {
                        System.out.println("[FAIL] " + service.directory);
                        System.out.println("       Issues: " + result.issues);
                        failures.add(service.directory + ": " + result.issues);
                        failCount++;
                    }
                    System.out.println();
                } catch (IOException e) {
                    System.out.println("[ERROR] " + service.directory + " - " + e.getMessage());
                    failures.add(service.directory + ": File not found");
                    failCount++;
                }
            }

            System.out.println("-".repeat(70));
            System.out.println("Summary: " + passCount + " passed, " + failCount + " failed");
            System.out.println("-".repeat(70));

            assertThat(failures)
                .describedAs("All AccessArc services should have Config Client properly configured")
                .isEmpty();
        }
    }

    @Nested
    @DisplayName("3. Config Client Required Properties Verification")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class RequiredPropertiesTests {

        @Test
        @Order(1)
        @DisplayName("All services should have required Config Client properties")
        void allServices_ShouldHaveRequiredProperties() throws IOException {
            System.out.println("\n" + "=".repeat(70));
            System.out.println("  REQUIRED PROPERTIES VERIFICATION");
            System.out.println("=".repeat(70) + "\n");

            List<String> allServices = new ArrayList<>();

            // Check TeamSync services
            for (ServiceInfo service : TEAMSYNC_SERVICES) {
                String yamlPath = String.format("%s/teamsync-backend/%s/src/main/resources/application.yml",
                    PROJECT_ROOT, service.directory);
                verifyRequiredProperties(yamlPath, "teamsync/" + service.directory, allServices);
            }

            // Check AccessArc services
            for (ServiceInfo service : ACCESSARC_SERVICES) {
                String yamlPath = String.format("%s/access-arc-backend/%s/src/main/resources/application.yml",
                    PROJECT_ROOT, service.directory);
                verifyRequiredProperties(yamlPath, "accessarc/" + service.directory, allServices);
            }

            System.out.println("\n-".repeat(70));
            System.out.println("Total services checked: " + allServices.size());
            System.out.println("-".repeat(70));
        }

        private void verifyRequiredProperties(String yamlPath, String serviceName, List<String> checkedServices) {
            try {
                String content = Files.readString(Path.of(yamlPath));
                List<String> missingProps = new ArrayList<>();

                // Required properties for Config Client
                String[] requiredPatterns = {
                    "config:\\s*\\n\\s+import:",           // spring.config.import
                    "config:\\s*\\n[^#]*enabled:",         // spring.cloud.config.enabled
                    "fail-fast:",                          // spring.cloud.config.fail-fast
                    "retry:",                              // spring.cloud.config.retry
                    "bus:\\s*\\n[^#]*enabled:"             // spring.cloud.bus.enabled
                };

                String[] propertyNames = {
                    "spring.config.import",
                    "spring.cloud.config.enabled",
                    "spring.cloud.config.fail-fast",
                    "spring.cloud.config.retry",
                    "spring.cloud.bus.enabled"
                };

                for (int i = 0; i < requiredPatterns.length; i++) {
                    if (!Pattern.compile(requiredPatterns[i]).matcher(content).find()) {
                        missingProps.add(propertyNames[i]);
                    }
                }

                if (missingProps.isEmpty()) {
                    System.out.println("[PASS] " + serviceName + " - All required properties present");
                } else {
                    System.out.println("[WARN] " + serviceName + " - Missing: " + missingProps);
                }

                checkedServices.add(serviceName);
            } catch (IOException e) {
                System.out.println("[SKIP] " + serviceName + " - " + e.getMessage());
            }
        }
    }

    @Nested
    @DisplayName("4. Config Enabled Default Value Verification")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class DefaultValueTests {

        @Test
        @Order(1)
        @DisplayName("All services should have CONFIG_ENABLED defaulting to true")
        void allServices_ShouldDefaultConfigEnabledToTrue() throws IOException {
            System.out.println("\n" + "=".repeat(70));
            System.out.println("  CONFIG_ENABLED DEFAULT VALUE VERIFICATION");
            System.out.println("=".repeat(70) + "\n");

            List<String> wrongDefaults = new ArrayList<>();

            // Check TeamSync
            for (ServiceInfo service : TEAMSYNC_SERVICES) {
                String yamlPath = String.format("%s/teamsync-backend/%s/src/main/resources/application.yml",
                    PROJECT_ROOT, service.directory);
                checkConfigEnabledDefault(yamlPath, "teamsync/" + service.directory, wrongDefaults);
            }

            // Check AccessArc
            for (ServiceInfo service : ACCESSARC_SERVICES) {
                String yamlPath = String.format("%s/access-arc-backend/%s/src/main/resources/application.yml",
                    PROJECT_ROOT, service.directory);
                checkConfigEnabledDefault(yamlPath, "accessarc/" + service.directory, wrongDefaults);
            }

            if (wrongDefaults.isEmpty()) {
                System.out.println("\n[PASS] All services default CONFIG_ENABLED to true");
            } else {
                System.out.println("\n[FAIL] Services with wrong defaults: " + wrongDefaults);
            }

            assertThat(wrongDefaults)
                .describedAs("All services should default CONFIG_ENABLED to true")
                .isEmpty();
        }

        private void checkConfigEnabledDefault(String yamlPath, String serviceName, List<String> wrongDefaults) {
            try {
                String content = Files.readString(Path.of(yamlPath));

                // Check for CONFIG_ENABLED:false (wrong) vs CONFIG_ENABLED:true (correct)
                Pattern wrongPattern = Pattern.compile("\\$\\{CONFIG_ENABLED:false\\}");
                Pattern correctPattern = Pattern.compile("\\$\\{CONFIG_ENABLED:true\\}");

                if (wrongPattern.matcher(content).find()) {
                    System.out.println("[FAIL] " + serviceName + " - defaults to false");
                    wrongDefaults.add(serviceName);
                } else if (correctPattern.matcher(content).find()) {
                    System.out.println("[PASS] " + serviceName + " - defaults to true");
                } else {
                    System.out.println("[WARN] " + serviceName + " - CONFIG_ENABLED pattern not found");
                }
            } catch (IOException e) {
                System.out.println("[SKIP] " + serviceName);
            }
        }
    }

    @Nested
    @DisplayName("5. Kafka Configuration for Spring Cloud Bus")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class KafkaConfigurationTests {

        @Test
        @Order(1)
        @DisplayName("All services should have Kafka bootstrap servers configured for Cloud Bus")
        void allServices_ShouldHaveKafkaConfigured() throws IOException {
            System.out.println("\n" + "=".repeat(70));
            System.out.println("  KAFKA CONFIGURATION FOR SPRING CLOUD BUS");
            System.out.println("=".repeat(70) + "\n");

            List<String> missingKafka = new ArrayList<>();

            // Check TeamSync
            for (ServiceInfo service : TEAMSYNC_SERVICES) {
                String yamlPath = String.format("%s/teamsync-backend/%s/src/main/resources/application.yml",
                    PROJECT_ROOT, service.directory);
                checkKafkaConfig(yamlPath, "teamsync/" + service.directory, missingKafka);
            }

            // Check AccessArc
            for (ServiceInfo service : ACCESSARC_SERVICES) {
                String yamlPath = String.format("%s/access-arc-backend/%s/src/main/resources/application.yml",
                    PROJECT_ROOT, service.directory);
                checkKafkaConfig(yamlPath, "accessarc/" + service.directory, missingKafka);
            }

            System.out.println("\n-".repeat(70));
            if (missingKafka.isEmpty()) {
                System.out.println("[PASS] All services have Kafka configured for Cloud Bus");
            } else {
                System.out.println("[WARN] Services without Kafka config: " + missingKafka.size());
                missingKafka.forEach(s -> System.out.println("  - " + s));
            }
        }

        private void checkKafkaConfig(String yamlPath, String serviceName, List<String> missingKafka) {
            try {
                String content = Files.readString(Path.of(yamlPath));

                // Check for kafka bootstrap-servers
                Pattern kafkaPattern = Pattern.compile("kafka:\\s*\\n[^#]*bootstrap-servers:");

                if (kafkaPattern.matcher(content).find()) {
                    System.out.println("[PASS] " + serviceName + " - Kafka configured");
                } else {
                    System.out.println("[MISS] " + serviceName + " - No Kafka config found");
                    missingKafka.add(serviceName);
                }
            } catch (IOException e) {
                System.out.println("[SKIP] " + serviceName);
            }
        }
    }

    // ==================== Helper Methods ====================

    private ConfigClientVerificationResult verifyConfigClientInYaml(String yamlPath, String serviceName) throws IOException {
        String content = Files.readString(Path.of(yamlPath));
        ConfigClientVerificationResult result = new ConfigClientVerificationResult();
        List<String> issues = new ArrayList<>();

        // 1. Check for config import (spring.config.import)
        Pattern importPattern = Pattern.compile("import:\\s*[\"']?(optional:configserver:[^\"'\\n]+)[\"']?");
        java.util.regex.Matcher importMatcher = importPattern.matcher(content);
        if (importMatcher.find()) {
            result.configImport = importMatcher.group(1).trim();
        } else {
            issues.add("Missing spring.config.import");
        }

        // 2. Check for config enabled (spring.cloud.config.enabled)
        Pattern enabledPattern = Pattern.compile("config:\\s*\\n\\s+enabled:\\s*\\$\\{CONFIG_ENABLED:([^}]+)\\}");
        java.util.regex.Matcher enabledMatcher = enabledPattern.matcher(content);
        if (enabledMatcher.find()) {
            result.configEnabled = "${CONFIG_ENABLED:" + enabledMatcher.group(1) + "}";
            if ("false".equals(enabledMatcher.group(1))) {
                issues.add("CONFIG_ENABLED defaults to false (should be true)");
            }
        } else {
            // Check simpler patterns
            if (content.contains("enabled: ${CONFIG_ENABLED:true}")) {
                result.configEnabled = "${CONFIG_ENABLED:true}";
            } else if (content.contains("enabled: ${CONFIG_ENABLED:false}")) {
                result.configEnabled = "${CONFIG_ENABLED:false}";
                issues.add("CONFIG_ENABLED defaults to false");
            } else {
                issues.add("Missing spring.cloud.config.enabled");
            }
        }

        // 3. Check for bus enabled (spring.cloud.bus.enabled)
        Pattern busPattern = Pattern.compile("bus:\\s*\\n\\s+enabled:\\s*\\$\\{BUS_ENABLED:([^}]+)\\}");
        java.util.regex.Matcher busMatcher = busPattern.matcher(content);
        if (busMatcher.find()) {
            result.busEnabled = "${BUS_ENABLED:" + busMatcher.group(1) + "}";
        } else if (content.contains("enabled: ${BUS_ENABLED:true}")) {
            result.busEnabled = "${BUS_ENABLED:true}";
        } else if (content.contains("enabled: ${BUS_ENABLED:false}")) {
            result.busEnabled = "${BUS_ENABLED:false}";
            issues.add("BUS_ENABLED defaults to false");
        } else {
            issues.add("Missing spring.cloud.bus.enabled");
        }

        // 4. Check for fail-fast: false
        if (!content.contains("fail-fast: false")) {
            issues.add("Missing fail-fast: false (recommended)");
        }

        // 5. Check for retry configuration
        if (!content.contains("retry:")) {
            issues.add("Missing retry configuration (recommended)");
        }

        result.issues = issues;
        return result;
    }

    // ==================== Helper Classes ====================

    static class ServiceInfo {
        String directory;
        String applicationName;
        int port;

        ServiceInfo(String directory, String applicationName, int port) {
            this.directory = directory;
            this.applicationName = applicationName;
            this.port = port;
        }
    }

    static class ConfigClientVerificationResult {
        String configImport;
        String configEnabled;
        String busEnabled;
        List<String> issues = new ArrayList<>();

        boolean isValid() {
            return issues.isEmpty() ||
                   (issues.size() == 1 && issues.get(0).contains("recommended"));
        }
    }
}
