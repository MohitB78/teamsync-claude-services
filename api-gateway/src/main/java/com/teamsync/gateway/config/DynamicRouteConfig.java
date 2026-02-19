package com.teamsync.gateway.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * Dynamic route configuration that loads routes from Config Server.
 *
 * Routes are defined externally in config-repo/gateway-routes/teamsync-routes.yml
 * and can be updated at runtime without redeploying the gateway.
 *
 * When configuration changes are detected (via Spring Cloud Bus), this config
 * automatically rebuilds the route locator and publishes a RefreshRoutesEvent.
 *
 * This configuration is only enabled when dynamic routes are enabled via property.
 * When disabled, routes from GatewayConfig are used instead.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "teamsync.gateway.dynamic-routes.enabled", havingValue = "true", matchIfMissing = false)
public class DynamicRouteConfig {

    private final DynamicRouteProperties routeProperties;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Creates a RouteLocator from the dynamic route properties.
     */
    @Bean
    public RouteLocator dynamicRouteLocator(RouteLocatorBuilder builder) {
        log.info("Building dynamic routes from Config Server. Route count: {}",
                routeProperties.getRoutes().size());

        RouteLocatorBuilder.Builder routesBuilder = builder.routes();

        for (DynamicRouteProperties.RouteDefinition route : routeProperties.getRoutes()) {
            log.debug("Adding route: {} -> {} (path: {})", route.getId(), route.getUri(), route.getPath());

            // Capture order for use in lambda
            final int routeOrder = route.getOrder();

            if (route.isWebsocket()) {
                // WebSocket route - order is set on PredicateSpec before path()
                routesBuilder.route(route.getId(), r -> r
                        .order(routeOrder)
                        .path(route.getPath())
                        .uri(route.getUri()));
            } else {
                // HTTP route with circuit breaker - order is set on PredicateSpec
                routesBuilder.route(route.getId(), r -> r
                        .order(routeOrder)
                        .path(route.getPath())
                        .filters(f -> {
                            f.stripPrefix(0);
                            if (route.getCircuitBreaker() != null) {
                                String fallbackUri = route.getFallbackUri() != null
                                        ? route.getFallbackUri()
                                        : "forward:/fallback/" + route.getId();
                                f.circuitBreaker(c -> c
                                        .setName(route.getCircuitBreaker())
                                        .setFallbackUri(fallbackUri));
                            }
                            return f;
                        })
                        .uri(route.getUri()));
            }
        }

        return routesBuilder.build();
    }

    /**
     * Listens for configuration refresh events and triggers route refresh.
     * This is called when Spring Cloud Bus broadcasts a config change.
     */
    @EventListener
    public void onRefreshScopeRefreshed(RefreshScopeRefreshedEvent event) {
        log.info("Configuration refreshed, rebuilding gateway routes...");
        eventPublisher.publishEvent(new RefreshRoutesEvent(this));
        log.info("Gateway routes refresh triggered. New route count: {}",
                routeProperties.getRoutes().size());
    }
}
