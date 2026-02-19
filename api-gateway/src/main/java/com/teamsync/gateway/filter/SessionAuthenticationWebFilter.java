package com.teamsync.gateway.filter;

import com.teamsync.gateway.model.BffSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * WebFilter that adds Authorization header from Redis session BEFORE Spring Security runs.
 *
 * <p>This is different from SessionTokenRelayFilter (which is a Gateway GlobalFilter).
 * This WebFilter runs BEFORE the security filter chain, allowing session-based
 * authentication to work seamlessly with JWT validation.
 *
 * <p>Flow:
 * <ol>
 *   <li>Browser sends request with session cookie</li>
 *   <li>This filter retrieves JWT from Redis session</li>
 *   <li>Caches the BffSession in exchange attributes for downstream filters</li>
 *   <li>Adds Authorization: Bearer {token} header to request</li>
 *   <li>Spring Security validates the JWT (sees Authorization header)</li>
 *   <li>Request is authenticated and proceeds to gateway routing</li>
 * </ol>
 *
 * <p>PERFORMANCE: This filter caches the BffSession in exchange attributes after
 * the first Redis lookup. The SessionTokenRelayFilter can then use this cached
 * session instead of performing a duplicate Redis lookup, saving ~2ms per request.
 *
 * @see SessionTokenRelayFilter for the Gateway filter that relays to downstream services
 */
@Component
@ConditionalOnProperty(name = "teamsync.bff.enabled", havingValue = "true")
@Slf4j
public class SessionAuthenticationWebFilter implements WebFilter, Ordered {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * Exchange attribute key for cached BffSession.
     * Used by SessionTokenRelayFilter to avoid duplicate Redis lookups.
     */
    public static final String CACHED_BFF_SESSION_ATTR = "cachedBffSession";

    /**
     * Exchange attribute key indicating session lookup was performed.
     * Even if no session was found, this flag prevents duplicate lookups.
     */
    public static final String SESSION_LOOKUP_PERFORMED_ATTR = "sessionLookupPerformed";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        boolean isApiPath = path.startsWith("/api/");

        // Skip if request already has Authorization header (API client with JWT)
        String existingAuth = exchange.getRequest().getHeaders().getFirst(AUTHORIZATION_HEADER);
        if (existingAuth != null && existingAuth.startsWith(BEARER_PREFIX)) {
            log.trace("Request already has Authorization header, skipping session auth");
            return chain.filter(exchange);
        }

        // Skip for BFF auth endpoints (they don't need authentication)
        if (path.startsWith("/bff/auth/")) {
            log.trace("Skipping session auth for BFF auth endpoint: {}", path);
            return chain.filter(exchange);
        }

        // Skip for public endpoints
        if (isPublicPath(path)) {
            log.trace("Skipping session auth for public endpoint: {}", path);
            return chain.filter(exchange);
        }

        // Log for API paths to debug authentication issues
        if (isApiPath) {
            String cookieHeader = exchange.getRequest().getHeaders().getFirst("Cookie");
            log.info("[SessionAuthWebFilter] Processing API request: path={}, hasCookie={}, cookieLength={}",
                path, cookieHeader != null, cookieHeader != null ? cookieHeader.length() : 0);
        }

        // Get token from Redis session and add Authorization header
        // IMPORTANT: Use cache() to prevent multiple subscriptions which can cause race conditions
        // The session Mono must be subscribed only once, and the result shared across the reactive chain
        // IMPORTANT: Wrap getSession() in a try-catch Mono to handle corrupt/incomplete sessions
        // Spring Session Redis throws IllegalStateException when session data is corrupt
        // (e.g., missing creationTime key after Redis restart or partial data loss)
        return Mono.defer(() -> exchange.getSession())
            .cache() // Prevent multiple subscriptions to the session Mono
            .flatMap(session -> {
                BffSession bffSession = session.getAttribute(BffSession.SESSION_KEY);

                // Log session details for API paths
                if (isApiPath) {
                    log.info("[SessionAuthWebFilter] Session lookup: path={}, sessionId={}, hasBffSession={}, sessionAttributes={}",
                        path, session.getId(),
                        bffSession != null,
                        session.getAttributes().keySet());
                }

                // PERFORMANCE: Cache session lookup result in exchange attributes
                // This prevents duplicate Redis lookups in SessionTokenRelayFilter
                exchange.getAttributes().put(SESSION_LOOKUP_PERFORMED_ATTR, true);
                if (bffSession != null) {
                    exchange.getAttributes().put(CACHED_BFF_SESSION_ATTR, bffSession);
                }

                if (bffSession == null) {
                    if (isApiPath) {
                        log.warn("[SessionAuthWebFilter] No BFF session found for API path: {}, sessionId={}", path, session.getId());
                    } else {
                        log.trace("No BFF session found for path: {}", path);
                    }
                    return chain.filter(exchange);
                }

                String accessToken = bffSession.getAccessToken();
                if (accessToken == null || accessToken.isBlank()) {
                    log.warn("BFF session exists but has no access token for user: {}",
                        bffSession.getUserId());
                    return chain.filter(exchange);
                }

                // Check if token is expired (but still add it - let security chain decide)
                if (bffSession.isAccessTokenExpired()) {
                    log.debug("Access token expired for user: {}, security may reject",
                        bffSession.getUserId());
                }

                // Mutate request to add Authorization header
                ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header(AUTHORIZATION_HEADER, BEARER_PREFIX + accessToken)
                    .build();

                ServerWebExchange mutatedExchange = exchange.mutate()
                    .request(mutatedRequest)
                    .build();

                // Copy cached session to mutated exchange
                mutatedExchange.getAttributes().put(SESSION_LOOKUP_PERFORMED_ATTR, true);
                mutatedExchange.getAttributes().put(CACHED_BFF_SESSION_ATTR, bffSession);

                if (isApiPath) {
                    // Verify the Authorization header is actually in the mutated request
                    String verifyAuth = mutatedExchange.getRequest().getHeaders().getFirst(AUTHORIZATION_HEADER);
                    log.info("[SessionAuthWebFilter] Added Authorization header for user: {} to API path: {}, headerVerified={}",
                        bffSession.getUserId(), path, verifyAuth != null && verifyAuth.startsWith(BEARER_PREFIX));
                } else {
                    log.debug("Added Authorization header from session for user: {} to path: {}",
                        bffSession.getUserId(), path);
                }

                return chain.filter(mutatedExchange);
            })
            .switchIfEmpty(Mono.defer(() -> {
                if (isApiPath) {
                    log.warn("[SessionAuthWebFilter] No session available for API path: {}", path);
                } else {
                    log.trace("No session available for path: {}", path);
                }
                // Mark that we checked even though no session was found
                exchange.getAttributes().put(SESSION_LOOKUP_PERFORMED_ATTR, true);
                return chain.filter(exchange);
            }))
            .onErrorResume(e -> {
                // Log session lookup errors - these could indicate Redis issues
                String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();

                // Special handling for corrupt/incomplete Redis sessions
                // Spring Session Redis throws IllegalStateException when session data is incomplete
                // (e.g., "creationTime key must not be null" after Redis restart or partial data loss)
                boolean isCorruptSession = e instanceof IllegalStateException &&
                    errorMsg != null && errorMsg.contains("key must not be null");

                if (isCorruptSession) {
                    log.warn("[SessionAuthWebFilter] Corrupt Redis session detected for path: {}, error: {}. " +
                        "Clearing session cookie and continuing without authentication.", path, errorMsg);
                    // The corrupt session will be invalidated by Spring Session when the user re-authenticates
                } else if (isApiPath) {
                    log.error("[SessionAuthWebFilter] Session lookup error for API path: {}, error: {}, type: {}",
                        path, errorMsg, e.getClass().getName());
                } else {
                    log.debug("Session lookup error for path: {}, error: {}", path, errorMsg);
                }

                // Check if response is already committed (e.g., redirect already sent)
                // If so, we can't continue the filter chain - just complete
                if (exchange.getResponse().isCommitted()) {
                    log.debug("[SessionAuthWebFilter] Response already committed, skipping filter chain for: {}", path);
                    return Mono.empty();
                }

                // Continue without authentication - let Spring Security handle it
                // For corrupt sessions, the user will be prompted to re-authenticate
                exchange.getAttributes().put(SESSION_LOOKUP_PERFORMED_ATTR, true);
                return chain.filter(exchange);
            });
    }

    /**
     * Check if the path is a public endpoint that doesn't require authentication.
     *
     * <p>NOTE: Includes /realms/** and /resources/** because Spring Cloud Gateway's
     * rewritePath filter transforms /auth/realms/... to /realms/... BEFORE this filter runs.
     */
    private boolean isPublicPath(String path) {
        return path.startsWith("/actuator/") ||
               path.equals("/health") ||
               path.startsWith("/fallback/") ||
               path.startsWith("/swagger-ui/") ||
               path.startsWith("/v3/api-docs") ||
               path.startsWith("/wopi/") ||
               path.startsWith("/storage-proxy/") ||
               path.startsWith("/auth/") ||  // Keycloak reverse proxy (before rewrite)
               path.startsWith("/realms/") ||  // Keycloak paths (after rewrite)
               path.startsWith("/resources/");  // Keycloak static resources (after rewrite)
    }

    @Override
    public int getOrder() {
        // Run BEFORE Spring Security WebFilter chain (which is at -100)
        // SecurityWebFilterChain default order is -100
        return -200;
    }
}
