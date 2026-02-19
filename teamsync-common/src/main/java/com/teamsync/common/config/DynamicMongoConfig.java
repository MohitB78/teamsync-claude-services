package com.teamsync.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.vault.core.lease.SecretLeaseContainer;
import org.springframework.vault.core.lease.event.SecretLeaseCreatedEvent;
import org.springframework.vault.core.lease.event.SecretLeaseExpiredEvent;
import org.springframework.vault.core.lease.event.SecretLeaseRotatedEvent;

/**
 * Configuration for handling Vault dynamic MongoDB credentials.
 * <p>
 * This component listens for Vault lease lifecycle events and logs
 * credential rotation events. MongoDB drivers automatically handle
 * reconnection with new credentials when using separate host/port/username/password
 * properties (instead of a connection URI with embedded credentials).
 * </p>
 *
 * <h3>Credential Rotation Strategy</h3>
 * <p>
 * With a 1-hour TTL on Vault database credentials:
 * <ul>
 *   <li>Vault renews the lease automatically before expiry</li>
 *   <li>If renewal fails, a new credential is generated</li>
 *   <li>MongoDB connection pools gracefully reconnect with new credentials</li>
 *   <li>No application restart required</li>
 * </ul>
 * </p>
 *
 * <h3>Spring Cloud Vault Integration</h3>
 * <p>
 * This configuration works with Spring Cloud Vault's database secrets backend.
 * The Config Server fetches credentials and injects them as:
 * <ul>
 *   <li>spring.data.mongodb.username</li>
 *   <li>spring.data.mongodb.password</li>
 * </ul>
 * </p>
 *
 * @see org.springframework.cloud.vault.config.databases.VaultDatabaseProperties
 */
@Configuration
@ConditionalOnClass(SecretLeaseContainer.class)
@Slf4j
public class DynamicMongoConfig {

    /**
     * Handles the event when a new database credential is created.
     *
     * @param event the secret lease created event
     */
    @EventListener
    public void onSecretLeaseCreated(SecretLeaseCreatedEvent event) {
        String path = event.getSource().getPath();
        if (path != null && path.contains("database/creds/")) {
            log.info("MongoDB credential created - Path: {}, TTL: {}s",
                    path,
                    event.getLease() != null ? event.getLease().getLeaseDuration().getSeconds() : "unknown");
        }
    }

    /**
     * Handles the event when a database credential is rotated.
     * <p>
     * This occurs when Vault generates a new credential before the old one expires.
     * MongoDB connections will automatically use the new credentials on next operation.
     * </p>
     *
     * @param event the secret lease rotated event
     */
    @EventListener
    public void onSecretLeaseRotated(SecretLeaseRotatedEvent event) {
        String path = event.getSource().getPath();
        if (path != null && path.contains("database/creds/")) {
            log.info("MongoDB credential rotated - Path: {}, New TTL: {}s",
                    path,
                    event.getLease() != null ? event.getLease().getLeaseDuration().getSeconds() : "unknown");

            // MongoDB Java driver handles reconnection automatically
            // No manual intervention needed for credential refresh
        }
    }

    /**
     * Handles the event when a database credential lease expires.
     * <p>
     * This should rarely occur in normal operation since Vault renews leases.
     * If it does occur, a new credential will be requested automatically.
     * </p>
     *
     * @param event the secret lease expired event
     */
    @EventListener
    public void onSecretLeaseExpired(SecretLeaseExpiredEvent event) {
        String path = event.getSource().getPath();
        if (path != null && path.contains("database/creds/")) {
            log.warn("MongoDB credential lease expired - Path: {}. A new credential will be requested.", path);
        }
    }
}
