package com.teamsync.common.tracing;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter that populates MDC with request context for log correlation.
 *
 * <p>This filter adds the following MDC fields:</p>
 * <ul>
 *   <li>{@code tenantId} - Tenant ID from X-Tenant-ID header</li>
 *   <li>{@code userId} - User ID from JWT subject</li>
 *   <li>{@code driveId} - Drive ID from X-Drive-ID header</li>
 *   <li>{@code requestId} - Request ID from X-Request-ID header (or generated UUID)</li>
 * </ul>
 *
 * <p>Note: {@code traceId} and {@code spanId} are automatically populated by
 * Micrometer Tracing Bridge when OpenTelemetry is enabled.</p>
 *
 * <p>This filter runs early (high precedence) to ensure MDC is populated
 * before any logging occurs in downstream filters/handlers.</p>
 *
 * @see org.slf4j.MDC
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@Slf4j
public class LoggingMdcFilter extends OncePerRequestFilter {

    private static final String MDC_TENANT_ID = "tenantId";
    private static final String MDC_USER_ID = "userId";
    private static final String MDC_DRIVE_ID = "driveId";
    private static final String MDC_REQUEST_ID = "requestId";

    private static final String HEADER_TENANT_ID = "X-Tenant-ID";
    private static final String HEADER_DRIVE_ID = "X-Drive-ID";
    private static final String HEADER_REQUEST_ID = "X-Request-ID";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        try {
            // Extract and set MDC fields
            setMdcFromHeaders(request);
            setMdcFromSecurityContext();

            // Continue filter chain
            filterChain.doFilter(request, response);

        } finally {
            // Always clear MDC to prevent leaking context to other threads
            clearMdc();
        }
    }

    private void setMdcFromHeaders(HttpServletRequest request) {
        // Tenant ID
        String tenantId = request.getHeader(HEADER_TENANT_ID);
        if (tenantId != null && !tenantId.isBlank()) {
            MDC.put(MDC_TENANT_ID, tenantId);
        }

        // Drive ID
        String driveId = request.getHeader(HEADER_DRIVE_ID);
        if (driveId != null && !driveId.isBlank()) {
            MDC.put(MDC_DRIVE_ID, driveId);
        }

        // Request ID (for request-level correlation)
        String requestId = request.getHeader(HEADER_REQUEST_ID);
        if (requestId != null && !requestId.isBlank()) {
            MDC.put(MDC_REQUEST_ID, requestId);
        }
    }

    private void setMdcFromSecurityContext() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                Object principal = authentication.getPrincipal();

                if (principal instanceof Jwt jwt) {
                    // Extract user ID from JWT subject
                    String userId = jwt.getSubject();
                    if (userId != null && !userId.isBlank()) {
                        MDC.put(MDC_USER_ID, userId);
                    }

                    // If tenantId not set from header, try to get from JWT claims
                    if (MDC.get(MDC_TENANT_ID) == null) {
                        String tenantIdClaim = jwt.getClaimAsString("tenant_id");
                        if (tenantIdClaim != null && !tenantIdClaim.isBlank()) {
                            MDC.put(MDC_TENANT_ID, tenantIdClaim);
                        }
                    }
                } else if (principal instanceof String userId && !userId.isBlank()) {
                    MDC.put(MDC_USER_ID, userId);
                }
            }
        } catch (Exception e) {
            // Log at debug level - security context may not be available for all requests
            log.debug("Could not extract user context for MDC: {}", e.getMessage());
        }
    }

    private void clearMdc() {
        MDC.remove(MDC_TENANT_ID);
        MDC.remove(MDC_USER_ID);
        MDC.remove(MDC_DRIVE_ID);
        MDC.remove(MDC_REQUEST_ID);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Skip MDC for actuator and static resources
        return path.startsWith("/actuator/") ||
               path.startsWith("/favicon") ||
               path.endsWith(".css") ||
               path.endsWith(".js") ||
               path.endsWith(".ico");
    }
}
