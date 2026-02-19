package com.teamsync.audit.config;

import io.codenotary.immudb4j.FileImmuStateHolder;
import io.codenotary.immudb4j.ImmuClient;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * ImmuDB client configuration.
 *
 * Uses FileImmuStateHolder to persist cryptographic state locally,
 * ensuring verification can detect tampering across restarts.
 *
 * Best Practices (from ImmuDB documentation):
 * 1. Always use FileImmuStateHolder for production
 * 2. Use verifiedSQLExec() for cryptographic proof on writes
 * 3. Enable TLS in production with secure cipher suites
 * 4. Keep state folder persistent across deployments
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class ImmuDbConfig {

    private final AuditServiceProperties properties;
    private ImmuClient immuClient;

    @Bean
    public ImmuClient immuClient() throws IOException {
        AuditServiceProperties.ImmuDbProperties immudbProps = properties.getImmudb();

        log.info("Connecting to ImmuDB at {}:{}", immudbProps.getHost(), immudbProps.getPort());

        // Ensure state folder exists
        Path stateFolder = Path.of(immudbProps.getStateFolder());
        if (!Files.exists(stateFolder)) {
            Files.createDirectories(stateFolder);
            log.info("Created ImmuDB state folder: {}", stateFolder);
        }

        // FileImmuStateHolder persists cryptographic state for verification across restarts
        FileImmuStateHolder stateHolder = FileImmuStateHolder.newBuilder()
                .withStatesFolder(immudbProps.getStateFolder())
                .build();

        // Build ImmuDB client
        ImmuClient.Builder clientBuilder = ImmuClient.newBuilder()
                .withServerUrl(immudbProps.getHost())
                .withServerPort(immudbProps.getPort())
                .withStateHolder(stateHolder);

        // Enable TLS if configured
        // Note: In production, always enable TLS
        // The TLS configuration should use secure cipher suites per ImmuDB v1.9.6+ defaults

        this.immuClient = clientBuilder.build();

        // Open session to the audit database
        try {
            immuClient.openSession(
                immudbProps.getDatabase(),
                immudbProps.getUsername(),
                immudbProps.getPassword()
            );
            log.info("Connected to ImmuDB database: {}", immudbProps.getDatabase());

            // Initialize schema if needed
            initializeSchema();

        } catch (Exception e) {
            log.error("Failed to connect to ImmuDB: {}", e.getMessage());
            throw new RuntimeException("ImmuDB connection failed", e);
        }

        return immuClient;
    }

    /**
     * Initialize ImmuDB schema if tables don't exist.
     */
    private void initializeSchema() {
        try {
            // Create audit_events table
            String createAuditEventsTable = """
                CREATE TABLE IF NOT EXISTS audit_events (
                    id              VARCHAR[64] NOT NULL,
                    tenant_id       VARCHAR[64] NOT NULL,
                    user_id         VARCHAR[64] NOT NULL,
                    user_name       VARCHAR[256],
                    action          VARCHAR[64] NOT NULL,
                    resource_type   VARCHAR[64] NOT NULL,
                    resource_id     VARCHAR[64] NOT NULL,
                    resource_name   VARCHAR[512],
                    drive_id        VARCHAR[64],
                    before_state    VARCHAR[8192],
                    after_state     VARCHAR[8192],
                    ip_address      VARCHAR[64],
                    user_agent      VARCHAR[512],
                    session_id      VARCHAR[128],
                    request_id      VARCHAR[128],
                    outcome         VARCHAR[32] NOT NULL,
                    failure_reason  VARCHAR[1024],
                    pii_accessed    BOOLEAN,
                    sensitive_data  BOOLEAN,
                    classification  VARCHAR[64],
                    event_time      TIMESTAMP NOT NULL,
                    hash_chain      VARCHAR[128],
                    PRIMARY KEY (id)
                )
                """;
            immuClient.sqlExec(createAuditEventsTable);
            log.debug("audit_events table ready");

            // Create signature_audit_events table
            String createSignatureEventsTable = """
                CREATE TABLE IF NOT EXISTS signature_audit_events (
                    id              VARCHAR[64] NOT NULL,
                    tenant_id       VARCHAR[64] NOT NULL,
                    request_id      VARCHAR[64] NOT NULL,
                    document_id     VARCHAR[64],
                    actor_id        VARCHAR[64] NOT NULL,
                    actor_email     VARCHAR[256] NOT NULL,
                    actor_name      VARCHAR[256],
                    actor_type      VARCHAR[32] NOT NULL,
                    event_type      VARCHAR[64] NOT NULL,
                    description     VARCHAR[1024],
                    metadata        VARCHAR[8192],
                    ip_address      VARCHAR[64],
                    user_agent      VARCHAR[512],
                    session_id      VARCHAR[128],
                    event_time      TIMESTAMP NOT NULL,
                    hash_chain      VARCHAR[128],
                    PRIMARY KEY (id)
                )
                """;
            immuClient.sqlExec(createSignatureEventsTable);
            log.debug("signature_audit_events table ready");

            // Create purge_records table (tracks purge operations for compliance)
            String createPurgeRecordsTable = """
                CREATE TABLE IF NOT EXISTS purge_records (
                    id                  VARCHAR[64] NOT NULL,
                    tenant_id           VARCHAR[64] NOT NULL,
                    purged_by           VARCHAR[64] NOT NULL,
                    purge_reason        VARCHAR[512] NOT NULL,
                    retention_period    VARCHAR[32] NOT NULL,
                    events_purged       INTEGER NOT NULL,
                    purge_time          TIMESTAMP NOT NULL,
                    hash_before         VARCHAR[128],
                    hash_after          VARCHAR[128],
                    PRIMARY KEY (id)
                )
                """;
            immuClient.sqlExec(createPurgeRecordsTable);
            log.debug("purge_records table ready");

            // Create indexes for common query patterns
            createIndexSafely("CREATE INDEX IF NOT EXISTS idx_audit_tenant ON audit_events(tenant_id)");
            createIndexSafely("CREATE INDEX IF NOT EXISTS idx_audit_tenant_user ON audit_events(tenant_id, user_id)");
            createIndexSafely("CREATE INDEX IF NOT EXISTS idx_audit_tenant_resource ON audit_events(tenant_id, resource_type, resource_id)");
            createIndexSafely("CREATE INDEX IF NOT EXISTS idx_audit_event_time ON audit_events(event_time)");
            createIndexSafely("CREATE INDEX IF NOT EXISTS idx_sig_tenant_request ON signature_audit_events(tenant_id, request_id)");
            createIndexSafely("CREATE INDEX IF NOT EXISTS idx_sig_request_time ON signature_audit_events(request_id, event_time)");
            createIndexSafely("CREATE INDEX IF NOT EXISTS idx_sig_actor ON signature_audit_events(actor_email)");

            log.info("ImmuDB schema initialized successfully");

        } catch (Exception e) {
            log.warn("Schema initialization warning (tables may already exist): {}", e.getMessage());
        }
    }

    private void createIndexSafely(String indexSql) {
        try {
            immuClient.sqlExec(indexSql);
        } catch (Exception e) {
            // Index may already exist, which is fine
            log.trace("Index creation: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        if (immuClient != null) {
            try {
                immuClient.closeSession();
                log.info("ImmuDB session closed");
            } catch (Exception e) {
                log.warn("Error closing ImmuDB session: {}", e.getMessage());
            }
        }
    }
}
