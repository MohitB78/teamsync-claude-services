package com.teamsync.gateway.config;

import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Dynamic route properties that can be updated at runtime via Config Server.
 *
 * Routes are defined in config-repo/gateway-routes/teamsync-routes.yml
 * and automatically refresh when Config Server broadcasts changes.
 *
 * Usage:
 * 1. Modify routes in config-repo
 * 2. POST /actuator/busrefresh to broadcast changes
 * 3. Gateway routes update automatically
 *
 * This configuration is only enabled when dynamic routes are enabled via property.
 */
@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "teamsync.gateway")
@ConditionalOnProperty(name = "teamsync.gateway.dynamic-routes.enabled", havingValue = "true", matchIfMissing = false)
public class DynamicRouteProperties {

    /**
     * List of dynamic route definitions
     */
    private List<RouteDefinition> routes = new ArrayList<>();

    @Data
    public static class RouteDefinition {
        /**
         * Unique route identifier
         */
        private String id;

        /**
         * Path pattern for matching requests (e.g., /api/documents/**)
         */
        private String path;

        /**
         * Target service URI (e.g., http://content-service:9081)
         */
        private String uri;

        /**
         * Circuit breaker name for this route
         */
        private String circuitBreaker;

        /**
         * Whether this is a WebSocket route
         */
        private boolean websocket = false;

        /**
         * Route priority (lower = higher priority)
         */
        private int order = 0;

        /**
         * Optional fallback URI for circuit breaker
         */
        private String fallbackUri;
    }
}
