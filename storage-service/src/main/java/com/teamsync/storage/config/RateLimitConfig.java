package com.teamsync.storage.config;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * SECURITY FIX (Round 13 #35): Rate limiting configuration for Storage Service.
 *
 * Prevents:
 * - DoS attacks on upload endpoints
 * - Storage quota exhaustion through rapid uploads
 * - Resource exhaustion from presigned URL generation
 */
@Configuration
public class RateLimitConfig {

    /**
     * Rate limiter for upload initialization.
     * Allows 10 uploads per minute globally.
     */
    @Bean
    public RateLimiter uploadInitLimiter() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .limitForPeriod(100)  // 100 uploads/min globally
                .timeoutDuration(Duration.ofMillis(500))
                .build();

        return RateLimiterRegistry.of(config).rateLimiter("upload-init");
    }

    /**
     * Rate limiter for presigned URL generation.
     * Allows 200 presigned URLs per minute globally.
     */
    @Bean
    public RateLimiter presignedUrlLimiter() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .limitForPeriod(200)  // 200 presigned URLs/min globally
                .timeoutDuration(Duration.ofMillis(500))
                .build();

        return RateLimiterRegistry.of(config).rateLimiter("presigned-url");
    }

    /**
     * Rate limiter for direct upload operations.
     * Stricter limit since these stream through the backend.
     */
    @Bean
    public RateLimiter directUploadLimiter() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .limitForPeriod(50)  // 50 direct uploads/min globally
                .timeoutDuration(Duration.ofMillis(500))
                .build();

        return RateLimiterRegistry.of(config).rateLimiter("direct-upload");
    }

    /**
     * Rate limiter for download operations.
     * More permissive since downloads are less resource-intensive.
     */
    @Bean
    public RateLimiter downloadLimiter() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .limitForPeriod(500)  // 500 downloads/min globally
                .timeoutDuration(Duration.ofMillis(500))
                .build();

        return RateLimiterRegistry.of(config).rateLimiter("download");
    }
}
