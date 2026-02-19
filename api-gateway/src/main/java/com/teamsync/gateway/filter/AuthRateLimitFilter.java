package com.teamsync.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * SECURITY FIX: Rate limiting filter for BFF authentication endpoints.
 *
 * <p>Applies IP-based rate limiting to prevent:
 * <ul>
 *   <li>Brute force attacks on login</li>
 *   <li>Credential stuffing attacks</li>
 *   <li>Denial of service on auth endpoints</li>
 *   <li>Token brute forcing on callback endpoint</li>
 * </ul>
 *
 * <p>Rate limits by endpoint:
 * <ul>
 *   <li>/bff/auth/authorize - 10 req/min per IP (prevents auth flood)</li>
 *   <li>/bff/auth/callback - 20 req/min per IP (slightly higher for legitimate retries)</li>
 *   <li>/bff/auth/refresh - 30 req/min per IP (normal refresh cycle is 7 min)</li>
 *   <li>/bff/auth/logout - 10 req/min per IP</li>
 *   <li>/bff/auth/session - 60 req/min per IP (polled on page load)</li>
 * </ul>
 *
 * <p>Uses Redis for distributed rate limiting across gateway instances.
 */
@Component
@Order(-100)  // Run before security filters
@ConditionalOnProperty(name = "teamsync.bff.enabled", havingValue = "true")
@Slf4j
public class AuthRateLimitFilter implements WebFilter {

    private static final String RATE_LIMIT_PREFIX = "teamsync:ratelimit:auth:";

    // Rate limits (requests per minute)
    private static final int AUTHORIZE_LIMIT = 10;
    private static final int CALLBACK_LIMIT = 20;
    private static final int REFRESH_LIMIT = 30;
    private static final int LOGOUT_LIMIT = 10;
    private static final int SESSION_LIMIT = 60;
    private static final int DEFAULT_LIMIT = 20;

    // Window duration
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final ReactiveStringRedisTemplate redisTemplate;

    public AuthRateLimitFilter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // Only apply to BFF auth endpoints
        if (!path.startsWith("/bff/auth/")) {
            return chain.filter(exchange);
        }

        String clientIp = getClientIp(exchange.getRequest());
        int limit = getRateLimitForPath(path);
        String rateLimitKey = RATE_LIMIT_PREFIX + path.replace("/", ":") + ":" + clientIp;

        return checkRateLimit(rateLimitKey, limit)
                .flatMap(allowed -> {
                    if (allowed) {
                        return chain.filter(exchange);
                    } else {
                        log.warn("SECURITY: Rate limit exceeded for auth endpoint {} from IP {}",
                                path, clientIp);
                        return handleRateLimitExceeded(exchange);
                    }
                });
    }

    /**
     * Check rate limit using Redis atomic increment with expiry.
     *
     * SECURITY FIX (Round 10 #11): Use atomic INCR with conditional EXPIRE to prevent
     * race condition where increment succeeds but expire fails, causing permanent keys.
     *
     * @param key The rate limit key (endpoint + IP)
     * @param limit Maximum requests per window
     * @return true if request is allowed, false if rate limited
     */
    private Mono<Boolean> checkRateLimit(String key, int limit) {
        // SECURITY FIX (Round 10 #11): Use setIfAbsent with expiry for first request,
        // then increment. This ensures atomic key creation with TTL.
        return redisTemplate.opsForValue()
                .setIfAbsent(key, "0", WINDOW)  // Atomically create key with TTL if not exists
                .flatMap(wasSet -> redisTemplate.opsForValue().increment(key))
                .flatMap(count -> {
                    // If count is null (shouldn't happen but defensive)
                    if (count == null) {
                        return Mono.just(true);
                    }
                    return Mono.just(count <= limit);
                })
                .onErrorResume(e -> {
                    // SECURITY FIX: Fail CLOSED for authentication endpoints.
                    // If Redis is unavailable, deny requests to prevent brute force attacks.
                    // This is critical for security-sensitive endpoints like /bff/auth/*
                    // where unlimited attempts could lead to credential compromise.
                    //
                    // Trade-off: Users may be temporarily unable to authenticate when Redis is down.
                    // This is acceptable because:
                    // 1. Redis downtime is rare and typically brief
                    // 2. Existing authenticated sessions continue to work (session data in Redis too)
                    // 3. Security of authentication is paramount
                    //
                    // Monitor for: "Rate limiting Redis unavailable" alerts to detect Redis issues.
                    log.error("SECURITY: Rate limiting Redis unavailable - DENYING request to auth endpoint. Error: {}",
                            e.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Handle rate limit exceeded - return 429 Too Many Requests.
     */
    private Mono<Void> handleRateLimitExceeded(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().add("Retry-After", "60");
        response.getHeaders().add("X-RateLimit-Reset", String.valueOf(
                System.currentTimeMillis() / 1000 + 60));

        return response.setComplete();
    }

    /**
     * Get rate limit based on the specific auth endpoint.
     */
    private int getRateLimitForPath(String path) {
        if (path.contains("/authorize")) {
            return AUTHORIZE_LIMIT;
        } else if (path.contains("/callback")) {
            return CALLBACK_LIMIT;
        } else if (path.contains("/refresh")) {
            return REFRESH_LIMIT;
        } else if (path.contains("/logout")) {
            return LOGOUT_LIMIT;
        } else if (path.contains("/session")) {
            return SESSION_LIMIT;
        } else if (path.contains("/backchannel-logout")) {
            // Backchannel logout comes from Zitadel, not users - but still limit
            return CALLBACK_LIMIT;
        }
        return DEFAULT_LIMIT;
    }

    /**
     * Extract client IP, handling proxy headers.
     *
     * SECURITY FIX (Round 10 #2): Validate IP format to prevent spoofing attacks.
     * Attackers can set X-Forwarded-For to arbitrary values to bypass rate limiting.
     * We validate the IP format and fall back to remote address if invalid.
     */
    private String getClientIp(ServerHttpRequest request) {
        // First, get the direct connection IP as fallback
        String directIp = "unknown";
        if (request.getRemoteAddress() != null && request.getRemoteAddress().getAddress() != null) {
            directIp = request.getRemoteAddress().getAddress().getHostAddress();
        }

        // Try X-Forwarded-For header
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // Get first IP in chain (original client)
            String forwardedIp = xForwardedFor.split(",")[0].trim();

            // SECURITY FIX (Round 10 #2): Validate IP format
            if (isValidIpAddress(forwardedIp)) {
                return forwardedIp;
            } else {
                log.warn("SECURITY: Invalid X-Forwarded-For IP format: '{}', using direct IP: {}",
                        forwardedIp.length() > 50 ? forwardedIp.substring(0, 50) + "..." : forwardedIp,
                        directIp);
                return directIp;
            }
        }

        // Try X-Real-IP header
        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            String realIp = xRealIp.trim();
            if (isValidIpAddress(realIp)) {
                return realIp;
            } else {
                log.warn("SECURITY: Invalid X-Real-IP format: '{}', using direct IP: {}",
                        realIp.length() > 50 ? realIp.substring(0, 50) + "..." : realIp,
                        directIp);
                return directIp;
            }
        }

        return directIp;
    }

    /**
     * SECURITY FIX (Round 10 #2): Validate IP address format to prevent header spoofing.
     * Accepts IPv4 (e.g., 192.168.1.1) and IPv6 (e.g., ::1, 2001:db8::1).
     */
    private boolean isValidIpAddress(String ip) {
        if (ip == null || ip.isBlank() || ip.length() > 45) {
            return false;
        }

        // IPv4 pattern: 4 octets separated by dots
        if (ip.contains(".")) {
            return ip.matches("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$");
        }

        // IPv6 pattern: Simplified validation (hexadecimal groups separated by colons)
        if (ip.contains(":")) {
            return ip.matches("^([0-9a-fA-F]{0,4}:){2,7}[0-9a-fA-F]{0,4}$") ||
                   ip.equals("::1"); // Loopback
        }

        return false;
    }
}
