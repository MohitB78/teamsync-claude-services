package com.teamsync.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 * Base security configuration for all TeamSync backend services.
 *
 * <p>SECURITY FIX (Round 13 #32): Enhanced base security configuration:
 * <ul>
 *   <li>Actuator endpoints restricted (only health/prometheus public)</li>
 *   <li>Swagger/OpenAPI disabled in production environments</li>
 *   <li>Service token validation available for production</li>
 * </ul>
 *
 * <p>SECURITY FIX (Round 14 #H8): Removed /actuator/info from public access.
 * The info endpoint exposes build version, git commit hash, and dependencies
 * which can help attackers identify vulnerabilities for specific versions.
 *
 * <p>Security model:
 * <ul>
 *   <li>API Gateway validates JWT tokens</li>
 *   <li>Backend services trust X-Tenant-ID, X-User-ID headers from gateway</li>
 *   <li>Network policies ensure only gateway can reach backend services</li>
 *   <li>Only specific actuator endpoints are accessible (health, info, prometheus)</li>
 * </ul>
 *
 * <p><strong>SECURITY FIX (Round 7):</strong> For production deployments, enable service
 * token validation to prevent header spoofing attacks if someone bypasses the gateway.
 *
 * <p>To enable service token validation, add to application.yml:
 * <pre>
 * teamsync:
 *   security:
 *     service-token:
 *       enabled: true
 *       secret: ${SERVICE_TOKEN_SECRET}  # Inject from Vault
 *       timestamp-tolerance-seconds: 300  # 5 minute window
 * </pre>
 *
 * <p>This config is ONLY loaded when teamsync.security.use-base-config=true (default).
 * Services with their own SecurityConfig should set this property to false.
 *
 * @see com.teamsync.common.security.ServiceTokenFilter
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@ConditionalOnProperty(name = "teamsync.security.use-base-config", havingValue = "true", matchIfMissing = true)
public class BaseSecurityConfig {

    /**
     * SECURITY FIX (Round 13 #32): Production profiles where Swagger should be disabled.
     */
    private static final Set<String> PRODUCTION_PROFILES = Set.of(
            "prod", "production", "railway", "staging"
    );

    private final Environment environment;

    @Value("${teamsync.security.swagger-enabled:true}")
    private boolean swaggerEnabled;

    public BaseSecurityConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    public SecurityFilterChain baseSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    // SECURITY FIX (Round 13 #33): Restrict actuator endpoints
                    // Only health for K8s probes - block env, beans, heapdump, etc.
                    // SECURITY FIX (Round 14 #H8): Removed /actuator/info from public access
                    // Info endpoint can expose build version, git commit, dependencies which
                    // helps attackers identify vulnerabilities for specific versions.
                    auth.requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/actuator/prometheus").permitAll()
                        .requestMatchers("/actuator/**").denyAll();

                    // SECURITY FIX (Round 13 #34): Disable Swagger in production
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

                    // All other requests - permit since gateway handles auth
                    auth.anyRequest().permitAll();
                });

        return http.build();
    }

    /**
     * Checks if the current environment is a production environment.
     */
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
