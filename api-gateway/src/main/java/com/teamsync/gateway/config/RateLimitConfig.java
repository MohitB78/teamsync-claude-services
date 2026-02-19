package com.teamsync.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

/**
 * Rate limiting configuration for the API Gateway.
 *
 * <p>Provides multiple rate limiters for different use cases:
 * <ul>
 *   <li>Default rate limiter: 100 req/sec for general API endpoints</li>
 *   <li>Auth rate limiter: 10 req/sec for authentication endpoints (stricter)</li>
 *   <li>Strict rate limiter: 5 req/sec for sensitive operations</li>
 * </ul>
 *
 * <p>SECURITY: Authentication endpoints have stricter rate limits to prevent
 * brute force attacks and credential stuffing.
 */
@Configuration
public class RateLimitConfig {

    /**
     * Default rate limiter using Redis.
     * 100 requests per second, burst of 200.
     * Used for general API endpoints.
     */
    @Bean
    @Primary
    public RedisRateLimiter redisRateLimiter() {
        return new RedisRateLimiter(100, 200, 1);
    }

    /**
     * Strict rate limiter for authentication endpoints.
     * SECURITY: 10 requests per second, burst of 20.
     *
     * <p>This stricter limit helps prevent:
     * <ul>
     *   <li>Brute force password attacks</li>
     *   <li>Credential stuffing attacks</li>
     *   <li>Account enumeration via timing attacks</li>
     * </ul>
     */
    @Bean
    public RedisRateLimiter authRateLimiter() {
        return new RedisRateLimiter(10, 20, 1);
    }

    /**
     * Very strict rate limiter for sensitive operations.
     * 5 requests per second, burst of 10.
     *
     * <p>Use for:
     * <ul>
     *   <li>Password reset endpoints</li>
     *   <li>Account recovery</li>
     *   <li>Email verification resend</li>
     * </ul>
     */
    @Bean
    public RedisRateLimiter strictRateLimiter() {
        return new RedisRateLimiter(5, 10, 1);
    }

    /**
     * Key resolver based on user ID from JWT.
     * Falls back to IP address for unauthenticated requests.
     */
    @Bean
    @Primary
    public KeyResolver userKeyResolver() {
        return exchange -> {
            // Try to get user ID from JWT
            return exchange.getPrincipal()
                    .map(principal -> principal.getName())
                    .defaultIfEmpty(
                            // Fall back to IP address
                            exchange.getRequest()
                                    .getHeaders()
                                    .getFirst("X-Forwarded-For") != null
                                    ? exchange.getRequest().getHeaders().getFirst("X-Forwarded-For")
                                    : exchange.getRequest().getRemoteAddress() != null
                                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                                    : "anonymous"
                    );
        };
    }

    /**
     * IP-based key resolver for authentication endpoints.
     * SECURITY: Always use IP for auth endpoints to prevent single IP from
     * attempting many logins for different accounts.
     */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                // Get first IP in chain (original client)
                return Mono.just(xForwardedFor.split(",")[0].trim());
            }

            String xRealIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
            if (xRealIp != null && !xRealIp.isBlank()) {
                return Mono.just(xRealIp);
            }

            if (exchange.getRequest().getRemoteAddress() != null) {
                return Mono.just(exchange.getRequest().getRemoteAddress().getAddress().getHostAddress());
            }

            return Mono.just("anonymous");
        };
    }

    /**
     * Key resolver based on tenant ID header.
     */
    @Bean
    public KeyResolver tenantKeyResolver() {
        return exchange -> {
            String tenantId = exchange.getRequest().getHeaders().getFirst("X-Tenant-ID");
            return Mono.just(tenantId != null ? tenantId : "default-tenant");
        };
    }
}
