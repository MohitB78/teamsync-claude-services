package com.teamsync.chat.config;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * SECURITY FIX (Round 12): Rate limiting configuration for Chat Service.
 *
 * Chat endpoints are particularly vulnerable to abuse because:
 * 1. Message sending can be used for spam/harassment
 * 2. AI chat (DocuTalk) can consume expensive compute resources
 * 3. Comment flooding can create DoS conditions
 *
 * This configuration provides global rate limiters. Per-user rate limiting
 * is handled by the RateLimitAspect using Redis for distributed tracking.
 */
@Configuration
public class RateLimitConfig {

    /**
     * Global rate limiter for message sending operations.
     * Allows 100 requests per second globally (across all users).
     */
    @Bean
    public RateLimiter messageSendLimiter(RateLimiterRegistry registry) {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .limitForPeriod(100)  // 100 requests per second globally
                .timeoutDuration(Duration.ofMillis(100))  // Fast fail
                .build();

        return registry.rateLimiter("messageSend", config);
    }

    /**
     * Global rate limiter for comment operations.
     * Allows 50 requests per second globally.
     */
    @Bean
    public RateLimiter commentLimiter(RateLimiterRegistry registry) {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .limitForPeriod(50)  // 50 requests per second globally
                .timeoutDuration(Duration.ofMillis(100))
                .build();

        return registry.rateLimiter("comment", config);
    }

    /**
     * Global rate limiter for AI chat (DocuTalk) operations.
     * Much stricter due to high compute cost of AI inference.
     * Allows 10 requests per second globally.
     */
    @Bean
    public RateLimiter aiChatLimiter(RateLimiterRegistry registry) {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .limitForPeriod(10)  // Only 10 AI requests per second globally
                .timeoutDuration(Duration.ofMillis(500))  // Slightly longer timeout for AI
                .build();

        return registry.rateLimiter("aiChat", config);
    }

    /**
     * Global rate limiter for message read operations.
     * More lenient as reads are cheaper than writes.
     */
    @Bean
    public RateLimiter messageReadLimiter(RateLimiterRegistry registry) {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .limitForPeriod(200)  // 200 reads per second globally
                .timeoutDuration(Duration.ofMillis(100))
                .build();

        return registry.rateLimiter("messageRead", config);
    }

    @Bean
    public RateLimiterRegistry rateLimiterRegistry() {
        return RateLimiterRegistry.ofDefaults();
    }
}
