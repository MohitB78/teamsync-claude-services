package com.teamsync.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Filter that creates a Spring Security authentication from API Gateway headers.
 *
 * <p>In TeamSync's architecture, the API Gateway validates JWT tokens and forwards
 * authenticated requests to backend services with trusted headers:
 * <ul>
 *   <li>X-User-ID: The authenticated user's ID</li>
 *   <li>X-Tenant-ID: The tenant ID from JWT or default</li>
 *   <li>X-Drive-ID: The drive being accessed</li>
 * </ul>
 *
 * <p>Backend services trust these headers (they're in an internal network) and
 * this filter creates a basic authentication token so that {@code @PreAuthorize}
 * annotations work as expected.
 *
 * <p>SECURITY NOTE: This filter should only be enabled for services that are
 * NOT publicly accessible. The API Gateway handles JWT validation; backend
 * services trust the gateway headers via network isolation.
 *
 * @see ServiceTokenFilter for verifying gateway service tokens
 */
@Component
@Order(0) // Run before TenantContextFilter
@Slf4j
public class GatewayAuthenticationFilter extends OncePerRequestFilter {

    private static final String USER_HEADER = "X-User-ID";
    private static final String TENANT_HEADER = "X-Tenant-ID";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Only set authentication if not already present
        Authentication existingAuth = SecurityContextHolder.getContext().getAuthentication();
        if (existingAuth != null && existingAuth.isAuthenticated()) {
            log.trace("Authentication already present, skipping gateway auth filter");
            filterChain.doFilter(request, response);
            return;
        }

        // Get user ID from gateway header
        String userId = request.getHeader(USER_HEADER);
        String tenantId = request.getHeader(TENANT_HEADER);

        log.debug("GatewayAuthenticationFilter - path: {}, X-User-ID: {}, X-Tenant-ID: {}",
                request.getRequestURI(), userId, tenantId);

        if (userId != null && !userId.isBlank()) {
            // Create a simple authentication token from the header
            // The principal is the user ID, credentials are null (gateway already validated)
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userId,  // principal
                            null,    // credentials (not needed - gateway validated)
                            List.of(new SimpleGrantedAuthority("ROLE_USER"))  // basic authority
                    );

            // Add tenant ID as details for additional context
            if (tenantId != null && !tenantId.isBlank()) {
                authentication.setDetails(new GatewayAuthDetails(userId, tenantId));
            }

            SecurityContextHolder.getContext().setAuthentication(authentication);

            log.debug("Created authentication from gateway headers - userId: {}, tenantId: {}",
                    userId, tenantId);
        } else {
            log.trace("No X-User-ID header present, skipping gateway authentication");
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Skip filter for public endpoints
        return path.startsWith("/actuator/health") ||
               path.startsWith("/swagger") ||
               path.startsWith("/v3/api-docs") ||
               path.equals("/health");
    }

    /**
     * Details object containing additional context from gateway headers.
     */
    public record GatewayAuthDetails(String userId, String tenantId) {}
}
