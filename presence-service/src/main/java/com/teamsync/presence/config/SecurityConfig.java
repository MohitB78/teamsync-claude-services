package com.teamsync.presence.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // SECURITY FIX (Round 13 #30): Restrict actuator endpoints
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/actuator/info").permitAll()
                        .requestMatchers("/actuator/prometheus").permitAll()
                        .requestMatchers("/actuator/**").denyAll()
                        // WebSocket endpoints - authentication handled by WebSocketConfig
                        .requestMatchers("/ws/**").permitAll()
                        // SECURITY FIX (Round 13 #31): Swagger disabled - internal service
                        .requestMatchers("/v3/api-docs/**").denyAll()
                        .requestMatchers("/swagger-ui/**").denyAll()
                        .requestMatchers("/swagger-ui.html").denyAll()
                        // All other requests - permit since gateway handles auth
                        .anyRequest().permitAll());

        return http.build();
    }

    /**
     * SECURITY: JwtDecoder for validating WebSocket JWT tokens.
     * Used by WebSocketConfig to authenticate STOMP CONNECT requests.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }
}
