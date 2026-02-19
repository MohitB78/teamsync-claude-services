package com.teamsync.gateway.filter;

import com.teamsync.common.security.ServiceTokenUtil;
import com.teamsync.gateway.model.BffSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.regex.Pattern;

/**
 * Global filter that extracts tenant and user context from JWT claims
 * and adds them as headers to downstream service requests.
 *
 * <p>SECURITY: This filter validates user-supplied X-Drive-ID headers to prevent
 * unauthorized access to other users' personal drives.
 *
 * <p>SECURITY FIX (Round 7): This filter now generates a service token (HMAC-SHA256)
 * that backend services can verify to ensure the request came from the API Gateway.
 * This prevents header spoofing attacks if someone bypasses the gateway.
 *
 * <p>This enables zero-trust architecture where backend services can trust
 * the headers set by the API Gateway without re-validating JWT tokens.
 */
@Component
@Slf4j
public class TenantContextFilter implements GlobalFilter, Ordered {

    private static final String TENANT_HEADER = "X-Tenant-ID";
    private static final String DRIVE_HEADER = "X-Drive-ID";
    private static final String USER_HEADER = "X-User-ID";
    private static final String EMAIL_HEADER = "X-User-Email";

    /**
     * SECURITY FIX (Round 7): Shared secret for generating service tokens.
     * This should be injected from HashiCorp Vault in production.
     */
    @Value("${teamsync.security.service-token.secret:}")
    private String serviceTokenSecret;

    /**
     * Default tenant ID for single-tenant deployments.
     * When using Zitadel (which doesn't include tenant_id in JWT claims by default),
     * this allows the system to operate in single-tenant mode.
     * Set to empty string to enforce multi-tenant mode (require tenant_id in JWT).
     */
    @Value("${teamsync.security.default-tenant-id:default}")
    private String defaultTenantId;

    /**
     * SECURITY FIX (Round 5): Tenant ID validation pattern.
     * Tenant IDs must be alphanumeric with hyphens/underscores, 1-64 characters.
     * This prevents:
     * 1. Log injection via CRLF characters in tenant ID
     * 2. Path traversal in tenant-based file operations
     * 3. NoSQL injection via special characters
     */
    private static final Pattern VALID_TENANT_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}$");

    /**
     * SECURITY FIX (Round 11): Valid drive ID pattern.
     * Drive IDs must be:
     * - personal-{userId} for personal drives
     * - dept-{departmentId} for department drives
     * - team-{teamId} for team drives
     * All IDs are alphanumeric with hyphens only (MongoDB ObjectIds or UUIDs).
     */
    private static final Pattern VALID_DRIVE_ID_PATTERN = Pattern.compile("^(personal|dept|team)-[a-zA-Z0-9-]{1,64}$");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        boolean isApiPath = path.startsWith("/api/");

        return ReactiveSecurityContextHolder.getContext()
                .doOnNext(ctx -> {
                    if (isApiPath) {
                        log.info("[TenantContextFilter] SecurityContext found for path: {}, auth type: {}",
                                path, ctx.getAuthentication() != null ? ctx.getAuthentication().getClass().getSimpleName() : "null");
                    }
                })
                .map(SecurityContext::getAuthentication)
                .filter(auth -> auth instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .flatMap(jwtAuth -> {
                    Jwt jwt = jwtAuth.getToken();

                    // Extract user info from JWT
                    String userId = jwt.getSubject();
                    String email = jwt.getClaimAsString("email");
                    String tenantId = jwt.getClaimAsString("tenant_id");

                    // Handle missing tenant_id claim - use default for single-tenant deployments
                    // For Zitadel (which doesn't include tenant_id in JWT claims by default),
                    // fall back to the configured default tenant ID.
                    // In multi-tenant mode (defaultTenantId is empty), reject the request.
                    if (tenantId == null || tenantId.isBlank()) {
                        if (defaultTenantId != null && !defaultTenantId.isBlank()) {
                            tenantId = defaultTenantId;
                            log.debug("Using default tenant ID '{}' for user: {} (JWT has no tenant_id claim)",
                                    tenantId, userId);
                        } else {
                            log.warn("SECURITY: JWT missing tenant_id claim for user: {} (multi-tenant mode requires tenant_id)",
                                    userId);
                            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                            return exchange.getResponse().setComplete();
                        }
                    }

                    // SECURITY FIX (Round 5): Validate tenant ID format to prevent log injection
                    // and other attacks via malicious tenant IDs with CRLF, path traversal, etc.
                    if (!VALID_TENANT_ID_PATTERN.matcher(tenantId).matches()) {
                        // Use sanitized version for logging to prevent log injection
                        String sanitizedTenantId = tenantId.replaceAll("[\\r\\n]", "_").substring(0, Math.min(20, tenantId.length()));
                        log.warn("SECURITY: Invalid tenant_id format for user: {}, tenantId starts with: {}...", userId, sanitizedTenantId);
                        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                        return exchange.getResponse().setComplete();
                    }

                    // Get drive ID from header or default to personal drive
                    String driveId = exchange.getRequest().getHeaders().getFirst(DRIVE_HEADER);
                    if (driveId == null || driveId.isBlank()) {
                        driveId = "personal-" + userId;
                    } else {
                        // SECURITY FIX (Round 11): Validate X-Drive-ID format to prevent injection attacks
                        if (!VALID_DRIVE_ID_PATTERN.matcher(driveId).matches()) {
                            String sanitizedDriveId = driveId.replaceAll("[\\r\\n]", "_")
                                    .substring(0, Math.min(30, driveId.length()));
                            log.warn("SECURITY: User {} provided invalid drive ID format: {}...",
                                    userId, sanitizedDriveId);
                            exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
                            return exchange.getResponse().setComplete();
                        }

                        // SECURITY FIX: Validate X-Drive-ID header to prevent personal drive spoofing
                        // Users cannot access another user's personal drive by setting X-Drive-ID header
                        if (driveId.startsWith("personal-") && !driveId.equals("personal-" + userId)) {
                            log.warn("SECURITY: User {} attempted to access another user's personal drive: {}",
                                    userId, driveId);
                            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                            return exchange.getResponse().setComplete();
                        }

                        // SECURITY FIX (Round 11): For department/team drives, permission check
                        // is deferred to backend services (permission-manager-service) which have
                        // access to the user's role assignments. The gateway validates format only.
                        // Backend services MUST verify user has access to the specified drive.
                        if (driveId.startsWith("dept-") || driveId.startsWith("team-")) {
                            log.debug("Drive access check deferred to backend for {} drive: {}",
                                    driveId.startsWith("dept-") ? "department" : "team", driveId);
                        }
                    }

                    // SECURITY FIX (Round 10 #13): Sanitize email before adding to headers
                    // to prevent CRLF injection and HTTP header manipulation
                    String sanitizedEmail = "";
                    if (email != null && !email.isBlank()) {
                        // Remove CRLF characters and validate basic email format
                        sanitizedEmail = email.replaceAll("[\\r\\n\\t]", "").trim();
                        // Truncate excessively long emails
                        if (sanitizedEmail.length() > 254) {
                            sanitizedEmail = sanitizedEmail.substring(0, 254);
                        }
                        // Basic email format validation
                        if (!sanitizedEmail.matches("^[^@]+@[^@]+\\.[^@]+$")) {
                            log.warn("SECURITY: Invalid email format in JWT for user {}, not forwarding email", userId);
                            sanitizedEmail = "";
                        }
                    }

                    // Build mutated request with context headers
                    ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate()
                            .header(TENANT_HEADER, tenantId)
                            .header(DRIVE_HEADER, driveId)
                            .header(USER_HEADER, userId)
                            .header(EMAIL_HEADER, sanitizedEmail);

                    // SECURITY FIX (Round 7): Add service token for backend service authentication
                    // This prevents header spoofing if someone bypasses the API Gateway
                    if (serviceTokenSecret != null && !serviceTokenSecret.isBlank()) {
                        try {
                            ServiceTokenUtil.ServiceToken serviceToken =
                                    ServiceTokenUtil.generate(tenantId, userId, serviceTokenSecret);
                            requestBuilder.header(ServiceTokenUtil.SERVICE_TOKEN_HEADER, serviceToken.token());
                            requestBuilder.header(ServiceTokenUtil.SERVICE_TIMESTAMP_HEADER,
                                    String.valueOf(serviceToken.timestamp()));
                            log.trace("Service token generated for request to downstream service");
                        } catch (Exception e) {
                            log.error("Failed to generate service token, proceeding without it", e);
                        }
                    }

                    ServerHttpRequest mutatedRequest = requestBuilder.build();

                    ServerWebExchange mutatedExchange = exchange.mutate()
                            .request(mutatedRequest)
                            .build();

                    log.debug("Tenant context set - Tenant: {}, User: {}, Drive: {}, Email: {}",
                            tenantId, userId, driveId, email);

                    return chain.filter(mutatedExchange);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // Fallback: Try to use cached BffSession from SessionAuthenticationWebFilter
                    // This handles cases where ReactiveSecurityContextHolder doesn't propagate to GlobalFilters
                    BffSession cachedSession = exchange.getAttribute(SessionAuthenticationWebFilter.CACHED_BFF_SESSION_ATTR);

                    if (cachedSession != null && cachedSession.getUserId() != null) {
                        log.info("[TenantContextFilter] Using cached BffSession for path: {}, userId: {}",
                                path, cachedSession.getUserId());
                        return processSessionContext(exchange, chain, cachedSession, path);
                    }

                    if (isApiPath) {
                        log.warn("[TenantContextFilter] No JwtAuthenticationToken and no cached BffSession for API path: {}, " +
                                "X-User-ID header will NOT be set. Backend service may reject with 401/403.",
                                path);
                    }
                    return chain.filter(exchange);
                })); // Pass through if no JWT or session
    }

    /**
     * Process context from cached BffSession (fallback when JWT not in SecurityContext).
     */
    private Mono<Void> processSessionContext(ServerWebExchange exchange, GatewayFilterChain chain,
                                              BffSession session, String path) {
        String userId = session.getUserId();
        String email = session.getEmail();
        String tenantId = session.getTenantId();

        // Handle missing tenant_id - use default for single-tenant deployments
        if (tenantId == null || tenantId.isBlank()) {
            if (defaultTenantId != null && !defaultTenantId.isBlank()) {
                tenantId = defaultTenantId;
                log.debug("Using default tenant ID '{}' for user: {} (session has no tenant_id)",
                        tenantId, userId);
            } else {
                log.warn("SECURITY: Session missing tenant_id for user: {} (multi-tenant mode requires tenant_id)", userId);
                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                return exchange.getResponse().setComplete();
            }
        }

        // Validate tenant ID format
        if (!VALID_TENANT_ID_PATTERN.matcher(tenantId).matches()) {
            String sanitizedTenantId = tenantId.replaceAll("[\\r\\n]", "_").substring(0, Math.min(20, tenantId.length()));
            log.warn("SECURITY: Invalid tenant_id format for user: {}, tenantId starts with: {}...", userId, sanitizedTenantId);
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }

        // Get drive ID from header or default to personal drive
        String driveId = exchange.getRequest().getHeaders().getFirst(DRIVE_HEADER);
        if (driveId == null || driveId.isBlank()) {
            driveId = "personal-" + userId;
        } else {
            // Validate drive ID format
            if (!VALID_DRIVE_ID_PATTERN.matcher(driveId).matches()) {
                String sanitizedDriveId = driveId.replaceAll("[\\r\\n]", "_")
                        .substring(0, Math.min(30, driveId.length()));
                log.warn("SECURITY: User {} provided invalid drive ID format: {}...", userId, sanitizedDriveId);
                exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
                return exchange.getResponse().setComplete();
            }

            // Validate personal drive ownership
            if (driveId.startsWith("personal-") && !driveId.equals("personal-" + userId)) {
                log.warn("SECURITY: User {} attempted to access another user's personal drive: {}", userId, driveId);
                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                return exchange.getResponse().setComplete();
            }
        }

        // Sanitize email
        String sanitizedEmail = "";
        if (email != null && !email.isBlank()) {
            sanitizedEmail = email.replaceAll("[\\r\\n\\t]", "").trim();
            if (sanitizedEmail.length() > 254) {
                sanitizedEmail = sanitizedEmail.substring(0, 254);
            }
            if (!sanitizedEmail.matches("^[^@]+@[^@]+\\.[^@]+$")) {
                log.warn("SECURITY: Invalid email format in session for user {}, not forwarding email", userId);
                sanitizedEmail = "";
            }
        }

        // Build mutated request with context headers
        ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate()
                .header(TENANT_HEADER, tenantId)
                .header(DRIVE_HEADER, driveId)
                .header(USER_HEADER, userId)
                .header(EMAIL_HEADER, sanitizedEmail);

        // Add service token for backend service authentication
        if (serviceTokenSecret != null && !serviceTokenSecret.isBlank()) {
            try {
                ServiceTokenUtil.ServiceToken serviceToken =
                        ServiceTokenUtil.generate(tenantId, userId, serviceTokenSecret);
                requestBuilder.header(ServiceTokenUtil.SERVICE_TOKEN_HEADER, serviceToken.token());
                requestBuilder.header(ServiceTokenUtil.SERVICE_TIMESTAMP_HEADER,
                        String.valueOf(serviceToken.timestamp()));
            } catch (Exception e) {
                log.error("Failed to generate service token, proceeding without it", e);
            }
        }

        ServerHttpRequest mutatedRequest = requestBuilder.build();
        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();

        log.info("[TenantContextFilter] Context set from BffSession - Tenant: {}, User: {}, Drive: {}, Email: {}",
                tenantId, userId, driveId, sanitizedEmail);

        return chain.filter(mutatedExchange);
    }

    @Override
    public int getOrder() {
        // Run after request logging but before routing
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
