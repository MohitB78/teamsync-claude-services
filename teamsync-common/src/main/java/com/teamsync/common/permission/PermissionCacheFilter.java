package com.teamsync.common.permission;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter that clears the request-scoped permission cache after each request.
 * This ensures that permission checks are fresh for each request.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class PermissionCacheFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } finally {
            // Always clear the request-scoped cache
            PermissionService.clearRequestCache();
        }
    }
}
