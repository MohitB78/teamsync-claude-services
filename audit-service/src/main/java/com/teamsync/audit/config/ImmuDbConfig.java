package com.teamsync.audit.config;

import io.codenotary.immudb4j.FileImmuStateHolder;
import io.codenotary.immudb4j.ImmuClient;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
/*        Path stateFolder = Path.of(immudbProps.getStateFolder());
        if (!Files.exists(stateFolder)) {
            Files.createDirectories(stateFolder);
            log.info("Created ImmuDB state folder: {}", stateFolder);
        }

        // FileImmuStateHolder persists cryptographic state for verification across restarts
        FileImmuStateHolder stateHolder = FileImmuStateHolder.newBuilder()
                .withStatesFolder(immudbProps.getStateFolder())
                .build();*/

        // Build ImmuDB client
        ImmuClient client = ImmuClient.newBuilder()
                .withServerUrl(immudbProps.getHost())
                .withServerPort(immudbProps.getPort())
                .build();
               // .withStateHolder(stateHolder);

        // Enable TLS if configured
        // Note: In production, always enable TLS
        // The TLS configuration should use secure cipher suites per ImmuDB v1.9.6+ defaults


        // Open session to the audit database
        try {
            client.openSession(
                    immudbProps.getDatabase(),
                    immudbProps.getUsername(),
                    immudbProps.getPassword()
            );
            log.info("Connected to ImmuDB database: {}", immudbProps.getDatabase());

            // Initialize schema if needed
            initializeSchema();
            this.immuClient=client;
            return client;

        } catch (Exception e) {
            log.error("Failed to connect to ImmuDB: {}", e.getMessage());
            throw new RuntimeException("ImmuDB connection failed", e);
        }

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
                        event_id       VARCHAR[100],
                        user_name       VARCHAR[256],
                        action          VARCHAR[64] NOT NULL,
                        resource_type   VARCHAR[64] NOT NULL,
                        resource_id     VARCHAR[64] NOT NULL,
                        event_type       VARCHAR[100],
                        result          VARCHAR[50],
                        details         JSON,
                        context         JSON,
                        event_time      TIMESTAMP NOT NULL,
                        hash_chain      VARCHAR[128],
                        PRIMARY KEY (id)
                    )
                    """;
            immuClient.sqlExec(createAuditEventsTable);
            log.info("audit_events table ready");

            // Create signature_audit_events table
            String createSignatureEventsTable = """
                    CREATE TABLE IF NOT EXISTS signature_audit_events (
                        id              VARCHAR[64] NOT NULL,
                        tenant_id       VARCHAR[64] NOT NULL,
                        event_id        VARCHAR[64] NOT NULL,
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
            log.info("signature_audit_events table ready");

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

            // Create DLQ processed table
            String createDlqProcessedTable = """
                    CREATE TABLE IF NOT EXISTS dlq_processed (
                        id              VARCHAR[36] NOT NULL,
                        topic           VARCHAR[200],
                        dlq_offset      INTEGER,
                        partition_no    INTEGER,
                        processed_at    INTEGER,
                        status          VARCHAR[50],
                        method_name     VARCHAR[50],
                        error_message   VARCHAR[1000],
                        message_value   VARCHAR[5000],
                        processed_by    VARCHAR[100],
                        attempt_number  INTEGER,
                        PRIMARY KEY (id)
                    )
                    """;
            immuClient.sqlExec(createDlqProcessedTable);
            log.info("dlq_processed table ready");

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
