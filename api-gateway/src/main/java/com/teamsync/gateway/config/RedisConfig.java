package com.teamsync.gateway.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.api.StatefulConnection;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import java.net.URI;
import java.time.Duration;

/**
 * Custom Redis configuration for Railway deployment.
 *
 * <p>Railway Redis uses a proxy that requires special connection settings.
 * This configuration handles URL parsing and connection options.
 *
 * <p>Note: Not conditional - Redis is always required for rate limiting
 * and BFF session management.
 *
 * <p><b>IMPORTANT:</b> This configuration provides a SINGLE connection factory
 * marked as {@code @Primary} that is shared by:
 * <ul>
 *   <li>Spring Session (via {@code @EnableRedisIndexedWebSession})</li>
 *   <li>Rate limiting (via {@code ReactiveStringRedisTemplate})</li>
 *   <li>BFF session management (via {@code bffSessionRedisTemplate})</li>
 * </ul>
 *
 * <p>This ensures all Redis operations use the same connection pool, preventing
 * pool exhaustion that occurs when multiple connection factories compete for resources.
 *
 * <p>PERFORMANCE: Uses connection pooling for high throughput. The pool is
 * configured with:
 * <ul>
 *   <li>max-active: Maximum concurrent connections (default: 100)</li>
 *   <li>min-idle: Pre-warmed connections for quick access (default: 20)</li>
 *   <li>max-idle: Limit idle connections to prevent resource waste (default: 50)</li>
 *   <li>max-wait: Fail fast if pool exhausted (default: 2000ms)</li>
 * </ul>
 */
@Configuration
@Slf4j
public class RedisConfig {

    @Value("${spring.data.redis.url:redis://localhost:6379}")
    private String redisUrl;

    // Connection pool configuration - tune based on expected load
    // INCREASED DEFAULTS: Previous values (50/10/20) caused pool exhaustion
    // when Spring Session, rate limiting, and BFF sessions competed for connections
    @Value("${spring.data.redis.lettuce.pool.max-active:100}")
    private int maxActive;

    @Value("${spring.data.redis.lettuce.pool.min-idle:20}")
    private int minIdle;

    @Value("${spring.data.redis.lettuce.pool.max-idle:50}")
    private int maxIdle;

    @Value("${spring.data.redis.lettuce.pool.max-wait:2000}")
    private long maxWaitMillis;

    @Value("${spring.data.redis.timeout:5s}")
    private Duration commandTimeout;

    /**
     * Creates a LettuceConnectionFactory with connection pooling for high throughput.
     *
     * <p>This bean is marked as {@code @Primary} to ensure ALL Redis operations in the
     * application use this single connection pool, including:
     * <ul>
     *   <li>Spring Session's {@code ReactiveRedisIndexedSessionRepository}</li>
     *   <li>Rate limiting via {@code ReactiveStringRedisTemplate}</li>
     *   <li>BFF session management via custom templates</li>
     * </ul>
     *
     * <p>PERFORMANCE: Connection pooling ensures we have pre-warmed connections
     * ready to serve requests, avoiding connection setup latency. The pool also
     * limits the maximum connections to prevent resource exhaustion.
     *
     * <p>FIX: Pool exhaustion issue - This factory is now properly exposed as the
     * primary {@code ReactiveRedisConnectionFactory} to prevent Spring from creating
     * multiple connection pools.
     */
    @Bean
    @Primary
    public LettuceConnectionFactory lettuceConnectionFactory() {
        log.info("Configuring Redis connection from URL: {}", maskPassword(redisUrl));

        URI uri = URI.create(redisUrl);

        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(uri.getHost());
        config.setPort(uri.getPort() > 0 ? uri.getPort() : 6379);

        // Extract password from URL (format: redis://user:password@host:port)
        String userInfo = uri.getUserInfo();
        if (userInfo != null && userInfo.contains(":")) {
            String password = userInfo.split(":", 2)[1];
            config.setPassword(password);
        }

        // Configure connection pool for high throughput
        // Use proper generic type for Lettuce compatibility
        GenericObjectPoolConfig<StatefulConnection<?, ?>> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(maxActive);           // Max concurrent connections
        poolConfig.setMinIdle(minIdle);              // Pre-warmed connections
        poolConfig.setMaxIdle(maxIdle);              // Limit idle connections
        poolConfig.setMaxWait(Duration.ofMillis(maxWaitMillis)); // Fail fast if exhausted
        poolConfig.setTestOnBorrow(false);           // Skip validation for speed
        poolConfig.setTestOnReturn(false);
        poolConfig.setTestWhileIdle(true);           // Background health checks
        poolConfig.setTimeBetweenEvictionRuns(Duration.ofSeconds(30));

        // Configure Lettuce client with pooling and connection options
        LettucePoolingClientConfiguration clientConfig = LettucePoolingClientConfiguration.builder()
                .poolConfig(poolConfig)
                .commandTimeout(commandTimeout)
                .shutdownTimeout(Duration.ofMillis(200))
                .clientOptions(ClientOptions.builder()
                        .socketOptions(SocketOptions.builder()
                                .connectTimeout(Duration.ofSeconds(5))
                                .keepAlive(true)
                                .tcpNoDelay(true)    // Disable Nagle's algorithm for lower latency
                                .build())
                        .autoReconnect(true)
                        .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                        .build())
                .build();

        log.info("Redis pool configured: host={}, port={}, maxActive={}, minIdle={}, maxIdle={}, commandTimeout={}",
                config.getHostName(), config.getPort(), maxActive, minIdle, maxIdle, commandTimeout);

        LettuceConnectionFactory factory = new LettuceConnectionFactory(config, clientConfig);
        factory.setShareNativeConnection(false);  // Use pool, not shared connection
        factory.afterPropertiesSet();             // Initialize the factory
        return factory;
    }

    private String maskPassword(String url) {
        if (url == null) return null;
        return url.replaceAll("(://[^:]+:)[^@]+(@)", "$1****$2");
    }

    // NOTE: LettuceConnectionFactory already implements both ReactiveRedisConnectionFactory
    // and RedisConnectionFactory interfaces. The @Primary annotation on lettuceConnectionFactory()
    // is sufficient - no need for separate beans that would cause "more than one primary" conflicts.

    /**
     * ReactiveStringRedisTemplate for rate limiting and other reactive Redis operations.
     */
    @Bean
    @Primary
    public ReactiveStringRedisTemplate reactiveStringRedisTemplate(LettuceConnectionFactory factory) {
        return new ReactiveStringRedisTemplate(factory);
    }
}
