package com.teamsync.trash.config;

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

import java.util.Set;

/**
 * Security configuration for Trash Service.
 *
 * SECURITY FIX (Round 13 #21): Enhanced security configuration:
 * - Actuator endpoints restricted (only health/info/prometheus public)
 * - Swagger/OpenAPI disabled in production
 * - All API endpoints require authentication via gateway
 *
 * Security model:
 * - API Gateway validates JWT tokens
 * - Backend services trust X-Tenant-ID, X-Drive-ID headers from gateway
 * - Service token filter validates internal calls (when enabled)
 * - Network policies ensure only gateway can reach this service
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * SECURITY FIX (Round 13 #21): Production profiles where Swagger should be disabled.
     */
    private static final Set<String> PRODUCTION_PROFILES = Set.of(
            "prod", "production", "railway", "staging"
    );

    private final Environment environment;

    @Value("${teamsync.security.swagger-enabled:true}")
    private boolean swaggerEnabled;

    public SecurityConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    // SECURITY FIX (Round 13 #22): Only allow specific actuator endpoints
                    auth.requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/actuator/info").permitAll()
                        .requestMatchers("/actuator/prometheus").permitAll()
                        .requestMatchers("/actuator/**").denyAll();

                    // SECURITY FIX (Round 13 #23): Disable Swagger in production
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

                    auth.anyRequest().permitAll();
                });

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
