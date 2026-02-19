package com.teamsync.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * TeamSync Audit Service (TS-18)
 *
 * Provides tamper-proof audit management using ImmuDB with:
 * - Cryptographic verification of audit events
 * - Hash chain linking for tamper evidence
 * - High-value event filtering (deletions, permission changes, shares)
 * - MongoDB mirror for fast queries
 * - Admin API for search and purge operations
 *
 * Port: 9097
 */
@SpringBootApplication(scanBasePackages = {
    "com.teamsync.audit",
    "com.teamsync.common"
})
@ConfigurationPropertiesScan
@EnableScheduling
public class AuditServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuditServiceApplication.class, args);
    }
}
