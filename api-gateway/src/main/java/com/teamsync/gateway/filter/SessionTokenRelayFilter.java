package com.teamsync.gateway.filter;

import com.teamsync.gateway.model.BffSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global filter that relays JWT tokens from Redis session to downstream services.
 *
 * <p>This filter implements the TokenRelay pattern for the BFF architecture:
 * <ul>
 *   <li>Checks if request already has Authorization header (API client with JWT)</li>
 *   <li>If not, retrieves JWT from BffSession stored in Redis</li>
 *   <li>Adds Authorization: Bearer {token} header to downstream request</li>
 * </ul>
 *
 * <p>This enables dual authentication mode:
 * <ul>
 *   <li>Browser clients: Use session cookie → token retrieved from Redis</li>
 *   <li>API clients: Send JWT directly in Authorization header</li>
 * </ul>
 *
 * <p>PERFORMANCE: This filter first checks for a cached BffSession in exchange
 * attributes (set by SessionAuthenticationWebFilter). If found, it uses the
 * cached session instead of performing a duplicate Redis lookup, saving ~2ms
 * per request.
 *
 * @see BffSession
 * @see SessionAuthenticationWebFilter
 * @see com.teamsync.gateway.config.BffSecurityConfig
 */
@Component
@ConditionalOnProperty(name = "teamsync.bff.enabled", havingValue = "true")
@Slf4j
public class SessionTokenRelayFilter implements GlobalFilter, Ordered {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Skip if request already has Authorization header (API client with JWT)
        String existingAuth = exchange.getRequest().getHeaders().getFirst(AUTHORIZATION_HEADER);
        if (existingAuth != null && existingAuth.startsWith(BEARER_PREFIX)) {
            log.trace("Request already has Authorization header, skipping token relay");
            return chain.filter(exchange);
        }

        // Skip for public/auth endpoints (they don't need token relay)
        // NOTE: Includes /realms/** and /resources/** because Spring Cloud Gateway's
        // rewritePath filter transforms /auth/realms/... to /realms/... BEFORE this filter runs.
        String path = exchange.getRequest().getPath().value();
        if (path.startsWith("/bff/auth/") ||
            path.startsWith("/auth/") ||
            path.startsWith("/realms/") ||
            path.startsWith("/resources/")) {
            log.trace("Skipping token relay for auth endpoint: {}", path);
            return chain.filter(exchange);
        }

        // PERFORMANCE: Check for cached session from SessionAuthenticationWebFilter
        // This avoids a duplicate Redis lookup, saving ~2ms per request
        Boolean sessionLookupPerformed = exchange.getAttribute(
            SessionAuthenticationWebFilter.SESSION_LOOKUP_PERFORMED_ATTR);

        if (Boolean.TRUE.equals(sessionLookupPerformed)) {
            // Use cached session - no Redis lookup needed
            BffSession cachedSession = exchange.getAttribute(
                SessionAuthenticationWebFilter.CACHED_BFF_SESSION_ATTR);
            log.trace("Using cached BFF session (no Redis lookup)");
            return processSession(exchange, chain, cachedSession, path);
        }

        // Fallback: Load from Redis (for requests that bypassed SessionAuthenticationWebFilter,
        // e.g., API clients that send direct Bearer tokens that were stripped somewhere)
        log.trace("No cached session found, performing Redis lookup");
        return exchange.getSession()
            .flatMap(session -> {
                BffSession bffSession = session.getAttribute(BffSession.SESSION_KEY);
                return processSession(exchange, chain, bffSession, path);
            })
            .switchIfEmpty(Mono.defer(() -> {
                log.trace("No session available, proceeding without token relay");
                return chain.filter(exchange);
            }));
    }

    /**
     * Process the BFF session and relay the token to downstream services.
     *
     * @param exchange The server web exchange
     * @param chain The gateway filter chain
     * @param bffSession The BFF session (may be null)
     * @param path The request path (for logging)
     * @return Mono<Void> completing when the filter chain completes
     */
    private Mono<Void> processSession(ServerWebExchange exchange, GatewayFilterChain chain,
                                       BffSession bffSession, String path) {
        if (bffSession == null) {
            log.trace("No BFF session found, proceeding without token relay");
            return chain.filter(exchange);
        }

        String accessToken = bffSession.getAccessToken();
        if (accessToken == null || accessToken.isBlank()) {
            log.warn("BFF session exists but has no access token for user: {}",
                bffSession.getUserId());
            return chain.filter(exchange);
        }

        // SECURITY FIX: Reject expired tokens immediately instead of relaying
        // This provides immediate feedback to frontend and prevents unnecessary
        // downstream requests with known-bad tokens
        if (bffSession.isAccessTokenExpired()) {
            log.info("Access token expired for user: {}, returning 401", bffSession.getUserId());
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            // SECURITY FIX (Round 6): Removed X-Token-Expired header
            // This header leaked session state information that could be used by attackers to:
            // 1. Fingerprint session state for timing attacks
            // 2. Determine when tokens expire to optimize brute-force attacks
            // 3. Distinguish expired sessions from invalid sessions
            // Frontend should handle 401 uniformly by redirecting to /bff/auth/refresh
            return exchange.getResponse().setComplete();
        }

        // Mutate request to add Authorization header
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
            .header(AUTHORIZATION_HEADER, BEARER_PREFIX + accessToken)
            .build();

        ServerWebExchange mutatedExchange = exchange.mutate()
            .request(mutatedRequest)
            .build();

        log.debug("Relayed token for user: {} to path: {}",
            bffSession.getUserId(), path);

        return chain.filter(mutatedExchange);
    }

    @Override
    public int getOrder() {
        // Run after TenantContextFilter but before routing
        // TenantContextFilter is at HIGHEST_PRECEDENCE + 1
        return Ordered.HIGHEST_PRECEDENCE + 2;
    }
}
