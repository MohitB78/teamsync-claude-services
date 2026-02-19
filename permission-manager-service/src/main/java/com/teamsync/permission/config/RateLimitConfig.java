package com.teamsync.permission.config;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * SECURITY: Rate limiting configuration for Permission Manager Service.
 *
 * This prevents:
 * 1. Permission enumeration attacks (trying many user/drive combinations)
 * 2. DoS attacks on the permission check endpoint
 * 3. Brute-force attempts to discover access patterns
 */
@Configuration
public class RateLimitConfig {

    /**
     * Rate limiter for permission check operations.
     * Allows 100 requests per second per caller.
     */
    @Bean
    public RateLimiter permissionCheckLimiter(RateLimiterRegistry registry) {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .limitForPeriod(100)
                .timeoutDuration(Duration.ofMillis(500))
                .build();

        return registry.rateLimiter("permissionCheck", config);
    }

    /**
     * Rate limiter for role assignment operations.
     * More restrictive: 10 requests per second.
     */
    @Bean
    public RateLimiter roleAssignmentLimiter(RateLimiterRegistry registry) {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .limitForPeriod(10)
                .timeoutDuration(Duration.ofMillis(1000))
                .build();

        return registry.rateLimiter("roleAssignment", config);
    }

    /**
     * Rate limiter for drive creation operations.
     * Very restrictive: 5 requests per second.
     */
    @Bean
    public RateLimiter driveCreationLimiter(RateLimiterRegistry registry) {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .limitForPeriod(5)
                .timeoutDuration(Duration.ofMillis(1000))
                .build();

        return registry.rateLimiter("driveCreation", config);
    }

    @Bean
    public RateLimiterRegistry rateLimiterRegistry() {
        return RateLimiterRegistry.ofDefaults();
    }
}
