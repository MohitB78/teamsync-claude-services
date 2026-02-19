package com.teamsync.common.security;

import com.teamsync.common.context.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter to extract tenant context from request headers and JWT claims.
 */
@Component
@Order(1)
@Slf4j
public class TenantContextFilter extends OncePerRequestFilter {

    private static final String TENANT_HEADER = "X-Tenant-ID";
    private static final String DRIVE_HEADER = "X-Drive-ID";
    private static final String USER_HEADER = "X-User-ID";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            // Set tenant ID from header
            String tenantId = request.getHeader(TENANT_HEADER);
            if (tenantId != null && !tenantId.isBlank()) {
                TenantContext.setTenantId(tenantId);
            }

            // Set drive ID from header
            String driveId = request.getHeader(DRIVE_HEADER);
            if (driveId != null && !driveId.isBlank()) {
                TenantContext.setDriveId(driveId);
            }

            // Set user ID from header (fallback before JWT)
            String userId = request.getHeader(USER_HEADER);
            if (userId != null && !userId.isBlank()) {
                TenantContext.setUserId(userId);
            }

            // Extract user ID from JWT (overrides header if present)
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof JwtAuthenticationToken jwtAuth) {
                Jwt jwt = jwtAuth.getToken();
                String jwtUserId = jwt.getSubject();
                if (jwtUserId != null && !jwtUserId.isBlank()) {
                    TenantContext.setUserId(jwtUserId);
                }

                // Optionally override tenant from JWT claim if not in header
                if (tenantId == null || tenantId.isBlank()) {
                    String jwtTenantId = jwt.getClaimAsString("tenant_id");
                    if (jwtTenantId != null) {
                        TenantContext.setTenantId(jwtTenantId);
                    }
                }
            }

            log.debug("Context set - Tenant: {}, User: {}, Drive: {}",
                    TenantContext.getTenantId(),
                    TenantContext.getUserId(),
                    TenantContext.getDriveId());

            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Skip filter for public endpoints
        return path.startsWith("/actuator") ||
               path.startsWith("/swagger") ||
               path.startsWith("/v3/api-docs") ||
               path.equals("/health");
    }
}
