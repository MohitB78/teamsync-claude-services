package com.teamsync.content.config;

import com.mongodb.connection.ConnectionPoolSettings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * MongoDB connection pool configuration for high-throughput operations.
 *
 * <p>PERFORMANCE: Configures MongoDB driver connection pooling for:
 * <ul>
 *   <li>Connection reuse - reduces TCP handshake overhead (~50-100ms per connection)</li>
 *   <li>Keep-alive - maintains idle connections ready for requests</li>
 *   <li>Elastic scaling - grows pool under load, shrinks when idle</li>
 * </ul>
 *
 * <p>These settings are optimized for Railway's internal networking where
 * services communicate over fast internal links but may still benefit from
 * connection reuse to reduce latency spikes.
 *
 * @author TeamSync Platform Team
 * @since 1.0.0
 */
@Configuration
@Slf4j
public class MongoConfig {

    /**
     * Customizes MongoDB client connection pool settings.
     *
     * <p>Connection pool settings:
     * <ul>
     *   <li>maxSize: 100 - Maximum connections in pool (default: 100)</li>
     *   <li>minSize: 10 - Minimum connections kept ready (default: 0)</li>
     *   <li>maxWaitTime: 10s - Max time to wait for connection from pool</li>
     *   <li>maxConnectionLifeTime: 60min - Max lifetime before connection recycled</li>
     *   <li>maxConnectionIdleTime: 10min - Max idle time before connection closed</li>
     *   <li>maintenanceFrequency: 60s - How often to check for idle connections</li>
     * </ul>
     *
     * @return MongoClientSettingsBuilderCustomizer with connection pool configuration
     */
    @Bean
    public MongoClientSettingsBuilderCustomizer mongoConnectionPoolCustomizer() {
        return settings -> {
            log.info("Configuring MongoDB connection pool for high-throughput operations");

            settings.applyToConnectionPoolSettings(pool -> {
                pool.applySettings(ConnectionPoolSettings.builder()
                    // Pool size settings
                    .maxSize(100)                    // Max connections (default: 100)
                    .minSize(10)                     // Keep 10 connections ready (default: 0)

                    // Timeout settings
                    .maxWaitTime(10, TimeUnit.SECONDS)  // Max wait for connection from pool

                    // Connection lifecycle
                    .maxConnectionLifeTime(60, TimeUnit.MINUTES)   // Recycle after 60 min
                    .maxConnectionIdleTime(10, TimeUnit.MINUTES)   // Close idle after 10 min

                    // Maintenance
                    .maintenanceFrequency(60, TimeUnit.SECONDS)    // Check every 60s

                    .build());
            });

            // Socket settings for keep-alive
            settings.applyToSocketSettings(socket -> {
                socket.connectTimeout(10, TimeUnit.SECONDS)  // Connection timeout
                      .readTimeout(30, TimeUnit.SECONDS);    // Read timeout
            });

            log.info("MongoDB connection pool configured: maxSize=100, minSize=10, " +
                     "maxWaitTime=10s, maxIdleTime=10min, maxLifeTime=60min");
        };
    }
}
