package com.teamsync.signing.config;

import com.teamsync.common.security.GatewayAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Set;

/**
 * Security configuration for Signing Service.
 *
 * Security model:
 * - /portal/signing/** endpoints are PUBLIC (token-based auth handled by service)
 * - /api/signing/** endpoints require authentication via gateway (internal users)
 * - Actuator health endpoints are public for Kubernetes probes
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final Set<String> PRODUCTION_PROFILES = Set.of(
            "prod", "production", "railway", "staging"
    );

    private final Environment environment;
    private final GatewayAuthenticationFilter gatewayAuthenticationFilter;

    @Value("${teamsync.security.swagger-enabled:true}")
    private boolean swaggerEnabled;

    public SecurityConfig(Environment environment, GatewayAuthenticationFilter gatewayAuthenticationFilter) {
        this.environment = environment;
        this.gatewayAuthenticationFilter = gatewayAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    // Actuator endpoints - only health/info/prometheus public
                    auth.requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/actuator/info").permitAll()
                        .requestMatchers("/actuator/prometheus").permitAll()
                        .requestMatchers("/actuator/**").denyAll();

                    // Portal signing endpoints - PUBLIC (token-based auth handled by controller)
                    // External signers access these without JWT - they use signing tokens
                    auth.requestMatchers("/portal/signing/**").permitAll();

                    // Swagger/OpenAPI - disabled in production
                    if (isProductionEnvironment() || !swaggerEnabled) {
                        auth.requestMatchers("/v3/api-docs/**").denyAll()
                            .requestMatchers("/swagger-ui/**").denyAll()
                            .requestMatchers("/swagger-ui.html").denyAll()
                            .requestMatchers("/swagger-resources/**").denyAll();
                    } else {
                        auth.requestMatchers("/v3/api-docs/**").permitAll()
                            .requestMatchers("/swagger-ui/**").permitAll()
                            .requestMatchers("/swagger-ui.html").permitAll();
                    }

                    // All other requests (internal API) - gateway handles auth
                    auth.anyRequest().permitAll();
                })
                .addFilterBefore(gatewayAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private boolean isProductionEnvironment() {
        String[] activeProfiles = environment.getActiveProfiles();
        for (String profile : activeProfiles) {
            if (PRODUCTION_PROFILES.contains(profile.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
