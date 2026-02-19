package com.teamsync.content.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis cache configuration for content service.
 *
 * <p>Configures Redis-backed caching with JSON serialization for document
 * metadata caching. This significantly reduces database queries for frequently
 * accessed documents.</p>
 *
 * <h2>Cache Names</h2>
 * <ul>
 *   <li>{@code documents}: Individual document metadata (5 min TTL)</li>
 *   <li>{@code folders}: Individual folder metadata (5 min TTL)</li>
 * </ul>
 *
 * <h2>Cache Key Strategy</h2>
 * <p>Keys are composed of tenantId + driveId + documentId to ensure
 * proper multi-tenant isolation.</p>
 *
 * @author TeamSync Platform Team
 * @since 1.0.0
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String DOCUMENTS_CACHE = "documents";
    public static final String FOLDERS_CACHE = "folders";

    /**
     * Configures Redis cache manager with custom TTLs per cache.
     *
     * @param connectionFactory the Redis connection factory
     * @return configured cache manager
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Default configuration for caches
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        // Per-cache configurations
        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();

        // Document cache: 5 minute TTL
        cacheConfigs.put(DOCUMENTS_CACHE, defaultConfig
                .entryTtl(Duration.ofMinutes(5))
                .prefixCacheNameWith("teamsync:content:"));

        // Folder cache: 5 minute TTL
        cacheConfigs.put(FOLDERS_CACHE, defaultConfig
                .entryTtl(Duration.ofMinutes(5))
                .prefixCacheNameWith("teamsync:content:"));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }
}
