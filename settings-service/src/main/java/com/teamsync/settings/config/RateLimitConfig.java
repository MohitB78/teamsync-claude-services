package com.teamsync.settings.config;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Rate limiting configuration for the Settings Service.
 * Protects against DoS attacks and excessive API usage.
 */
@Configuration
public class RateLimitConfig {

    public static final String SETTINGS_READ_LIMITER = "settingsReadLimiter";
    public static final String SETTINGS_WRITE_LIMITER = "settingsWriteLimiter";
    public static final String TENANT_SETTINGS_LIMITER = "tenantSettingsLimiter";

    @Bean
    public RateLimiterRegistry rateLimiterRegistry() {
        return RateLimiterRegistry.of(
                RateLimiterConfig.custom()
                        .limitRefreshPeriod(Duration.ofSeconds(1))
                        .limitForPeriod(100) // Default: 100 requests per second
                        .timeoutDuration(Duration.ofMillis(500))
                        .build()
        );
    }

    /**
     * Rate limiter for read operations (GET requests).
     * Higher limit since reads are typically cached.
     */
    @Bean(SETTINGS_READ_LIMITER)
    public RateLimiter settingsReadLimiter(RateLimiterRegistry registry) {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .limitForPeriod(200) // 200 reads per second per instance
                .timeoutDuration(Duration.ofMillis(100))
                .build();
        return registry.rateLimiter(SETTINGS_READ_LIMITER, config);
    }

    /**
     * Rate limiter for write operations (PATCH/POST/DELETE requests).
     * Lower limit to protect database and cache invalidation.
     */
    @Bean(SETTINGS_WRITE_LIMITER)
    public RateLimiter settingsWriteLimiter(RateLimiterRegistry registry) {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .limitForPeriod(50) // 50 writes per second per instance
                .timeoutDuration(Duration.ofMillis(500))
                .build();
        return registry.rateLimiter(SETTINGS_WRITE_LIMITER, config);
    }

    /**
     * Rate limiter for tenant settings operations.
     * Most restrictive since these affect all users in a tenant.
     */
    @Bean(TENANT_SETTINGS_LIMITER)
    public RateLimiter tenantSettingsLimiter(RateLimiterRegistry registry) {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .limitForPeriod(30) // 30 tenant settings changes per minute
                .timeoutDuration(Duration.ofSeconds(1))
                .build();
        return registry.rateLimiter(TENANT_SETTINGS_LIMITER, config);
    }
}
