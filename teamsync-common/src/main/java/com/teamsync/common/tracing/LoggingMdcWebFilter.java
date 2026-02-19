package com.teamsync.common.tracing;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Reactive WebFilter that populates MDC with request context for log correlation.
 *
 * <p>This filter is for WebFlux-based services (like API Gateway) and adds:</p>
 * <ul>
 *   <li>{@code tenantId} - Tenant ID from X-Tenant-ID header</li>
 *   <li>{@code userId} - User ID from JWT subject</li>
 *   <li>{@code driveId} - Drive ID from X-Drive-ID header</li>
 *   <li>{@code requestId} - Request ID from X-Request-ID header</li>
 * </ul>
 *
 * <p>Uses Reactor Context to propagate MDC values across async boundaries.</p>
 *
 * @see LoggingMdcFilter for Servlet-based services
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@Slf4j
public class LoggingMdcWebFilter implements WebFilter {

    private static final String MDC_TENANT_ID = "tenantId";
    private static final String MDC_USER_ID = "userId";
    private static final String MDC_DRIVE_ID = "driveId";
    private static final String MDC_REQUEST_ID = "requestId";

    private static final String HEADER_TENANT_ID = "X-Tenant-ID";
    private static final String HEADER_DRIVE_ID = "X-Drive-ID";
    private static final String HEADER_REQUEST_ID = "X-Request-ID";

    // Reactor Context key for MDC values
    private static final String MDC_CONTEXT_KEY = "mdc-context";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // Skip MDC for actuator and static resources
        if (shouldSkip(path)) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        Map<String, String> mdcContext = new HashMap<>();

        // Extract from headers
        extractHeader(request, HEADER_TENANT_ID, MDC_TENANT_ID, mdcContext);
        extractHeader(request, HEADER_DRIVE_ID, MDC_DRIVE_ID, mdcContext);
        extractHeader(request, HEADER_REQUEST_ID, MDC_REQUEST_ID, mdcContext);

        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .filter(Authentication::isAuthenticated)
            .map(this::extractUserContext)
            .defaultIfEmpty(Map.of())
            .flatMap(userContext -> {
                // Merge user context into MDC
                mdcContext.putAll(userContext);

                // Set MDC for current thread (for initial logging)
                mdcContext.forEach(MDC::put);

                return chain.filter(exchange)
                    .contextWrite(Context.of(MDC_CONTEXT_KEY, mdcContext))
                    .doFinally(signalType -> {
                        // Clear MDC on completion
                        mdcContext.keySet().forEach(MDC::remove);
                    });
            });
    }

    private void extractHeader(ServerHttpRequest request, String headerName,
                               String mdcKey, Map<String, String> mdcContext) {
        String value = request.getHeaders().getFirst(headerName);
        if (value != null && !value.isBlank()) {
            mdcContext.put(mdcKey, value);
        }
    }

    private Map<String, String> extractUserContext(Authentication authentication) {
        Map<String, String> context = new HashMap<>();

        try {
            Object principal = authentication.getPrincipal();

            if (principal instanceof Jwt jwt) {
                Optional.ofNullable(jwt.getSubject())
                    .filter(s -> !s.isBlank())
                    .ifPresent(userId -> context.put(MDC_USER_ID, userId));

                Optional.ofNullable(jwt.getClaimAsString("tenant_id"))
                    .filter(s -> !s.isBlank())
                    .ifPresent(tenantId -> context.putIfAbsent(MDC_TENANT_ID, tenantId));
            }
        } catch (Exception e) {
            log.debug("Could not extract user context for MDC: {}", e.getMessage());
        }

        return context;
    }

    private boolean shouldSkip(String path) {
        return path.startsWith("/actuator/") ||
               path.startsWith("/favicon") ||
               path.endsWith(".css") ||
               path.endsWith(".js") ||
               path.endsWith(".ico");
    }
}
