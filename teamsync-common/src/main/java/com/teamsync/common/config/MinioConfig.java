package com.teamsync.common.config;

import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * MinIO client configuration with connection pooling for high-concurrency workloads.
 *
 * <p>This configuration is only loaded when {@code teamsync.storage.minio.enabled=true}
 * (default: true for storage services, can be disabled for services that don't need storage).</p>
 *
 * Connection pool settings are configurable via properties:
 * - teamsync.storage.minio.connection-pool-size (default: 50)
 * - teamsync.storage.minio.connection-pool-keep-alive-minutes (default: 5)
 * - teamsync.storage.minio.connect-timeout-seconds (default: 10)
 * - teamsync.storage.minio.read-timeout-seconds (default: 60)
 */
@Configuration
@ConditionalOnProperty(name = "teamsync.storage.minio.enabled", havingValue = "true", matchIfMissing = false)
@Slf4j
public class MinioConfig {

    @Value("${teamsync.storage.minio.endpoint:http://localhost:9000}")
    private String endpoint;

    /**
     * SECURITY FIX (Round 11): Removed default credentials fallback.
     * MinIO credentials MUST be explicitly configured via environment variables
     * or HashiCorp Vault. Using default credentials is a critical security risk.
     */
    @Value("${teamsync.storage.minio.access-key:}")
    private String accessKey;

    @Value("${teamsync.storage.minio.secret-key:}")
    private String secretKey;

    @Value("${teamsync.storage.minio.connection-pool-size:50}")
    private int connectionPoolSize;

    @Value("${teamsync.storage.minio.connection-pool-keep-alive-minutes:5}")
    private int keepAliveMinutes;

    @Value("${teamsync.storage.minio.connect-timeout-seconds:10}")
    private int connectTimeoutSeconds;

    @Value("${teamsync.storage.minio.read-timeout-seconds:60}")
    private int readTimeoutSeconds;

    @Bean
    public MinioClient minioClient() {
        log.info("Configuring MinIO client with endpoint: {} and connection pool size: {}",
                endpoint, connectionPoolSize);

        // SECURITY FIX (Round 11): Validate that credentials are explicitly configured
        if (accessKey == null || accessKey.isBlank()) {
            throw new IllegalStateException(
                    "SECURITY: MinIO access key not configured. Set teamsync.storage.minio.access-key " +
                    "via environment variable or HashiCorp Vault. Default credentials are not allowed.");
        }
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException(
                    "SECURITY: MinIO secret key not configured. Set teamsync.storage.minio.secret-key " +
                    "via environment variable or HashiCorp Vault. Default credentials are not allowed.");
        }

        try {
            // Configure OkHttp connection pool for better performance under load
            // Default OkHttp pool is only 5 connections which causes queuing
            OkHttpClient httpClient = new OkHttpClient.Builder()
                    .connectionPool(new ConnectionPool(
                            connectionPoolSize,
                            keepAliveMinutes,
                            TimeUnit.MINUTES))
                    .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
                    .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
                    .writeTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
                    .build();

            MinioClient client = MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(accessKey, secretKey)
                    .httpClient(httpClient)
                    .build();

            log.info("MinIO client configured successfully with {} pooled connections", connectionPoolSize);
            return client;
        } catch (Exception e) {
            log.error("Failed to configure MinIO client for endpoint: {}. Error: {}", endpoint, e.getMessage(), e);
            throw new IllegalStateException("Failed to create MinIO client. Ensure MINIO_ENDPOINT, MINIO_ACCESS_KEY, and MINIO_SECRET_KEY are set correctly.", e);
        }
    }
}
