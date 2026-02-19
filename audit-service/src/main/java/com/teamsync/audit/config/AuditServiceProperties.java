package com.teamsync.audit.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * Configuration properties for the Audit Service.
 * Uses Java records for immutable configuration following Spring Boot 4 best practices.
 */
@ConfigurationProperties(prefix = "teamsync.audit")
@Validated
@Data
public class AuditServiceProperties {

    @Valid
    @NotNull
    private ImmuDbProperties immudb = new ImmuDbProperties();

    @Valid
    @NotNull
    private MongoDbSyncProperties mongodbSync = new MongoDbSyncProperties();

    @Valid
    @NotNull
    private VerificationProperties verification = new VerificationProperties();

    @Valid
    @NotNull
    private RetentionProperties retention = new RetentionProperties();

    @Valid
    @NotNull
    private HighValueEventsProperties highValueEvents = new HighValueEventsProperties();

    @Valid
    @NotNull
    private DeduplicationProperties deduplication = new DeduplicationProperties();

    /**
     * ImmuDB connection and authentication settings.
     */
    @Data
    public static class ImmuDbProperties {

        /**
         * Whether ImmuDB integration is enabled.
         * When disabled, audit events are only stored in MongoDB.
         */
        private boolean enabled = true;

        private String host = "localhost";

        @Positive
        private int port = 3322;

        private String database = "teamsync_audit";

        private String username = "immudb";

        private String password = "immudb";

        @NotBlank
        private String stateFolder = "/var/lib/teamsync/immudb-state";

        @Valid
        @NotNull
        private TlsProperties tls = new TlsProperties();

        @Data
        public static class TlsProperties {
            private boolean enabled = false;
            private String certPath;
            private String keyPath;
            private String caPath;
        }
    }

    /**
     * MongoDB sync settings for fast query mirror.
     */
    @Data
    public static class MongoDbSyncProperties {
        private boolean enabled = true;
        private String collection = "audit_logs";
    }

    /**
     * Verification and hash chain settings.
     */
    @Data
    public static class VerificationProperties {
        private boolean hashChainEnabled = true;
        private Duration checkpointInterval = Duration.ofHours(24);
    }

    /**
     * Data retention and purge settings.
     */
    @Data
    public static class RetentionProperties {
        private boolean enabled = true;
        private Duration defaultPeriod = Duration.ofDays(365);
        private Duration truncationFrequency = Duration.ofHours(24);
        private Duration minRetentionPeriod = Duration.ofDays(30);
        private Duration maxRetentionPeriod = Duration.ofDays(2555); // ~7 years
    }

    /**
     * High-value event filtering configuration.
     * Only these events are stored in ImmuDB for tamper-proof storage.
     */
    @Data
    public static class HighValueEventsProperties {
        /**
         * Actions that are always considered high-value.
         * DELETE, PERMISSION_CHANGE, SHARE are defaults.
         */
        private Set<String> actions = Set.of("DELETE", "PERMISSION_CHANGE", "SHARE");

        /**
         * Whether to include all events with FAILURE outcome.
         */
        private boolean includeFailures = true;

        /**
         * Whether to include all events with DENIED outcome.
         */
        private boolean includeDenied = true;

        /**
         * Additional resource types that should always be tracked.
         * Signature events are always tracked regardless of this setting.
         */
        private Set<String> alwaysTrackResourceTypes = Set.of();
    }

    /**
     * Event deduplication settings using Redis.
     */
    @Data
    public static class DeduplicationProperties {
        private boolean enabled = true;
        private int ttlHours = 24;
        private String redisPrefix = "teamsync:audit:dedup:";
    }
}
