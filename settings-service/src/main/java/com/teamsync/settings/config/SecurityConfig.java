package com.teamsync.settings.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Security configuration for the Settings Service.
 * Includes Zitadel role extraction for admin authorization.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Value("${teamsync.zitadel.project-id:}")
    private String zitadelProjectId;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // SECURITY FIX (Round 13 #29): Restrict actuator endpoints
                .requestMatchers("/actuator/health/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/actuator/info").permitAll()
                .requestMatchers("/actuator/prometheus").permitAll()
                .requestMatchers("/actuator/**").denyAll()
                // Allow health checks
                .requestMatchers("/health/**").permitAll()
                // All other requests require authentication
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            );

        return http.build();
    }

    /**
     * JWT authentication converter that extracts Zitadel roles as Spring Security authorities.
     * Zitadel role claim format: urn:zitadel:iam:org:project:{projectId}:roles
     */
    @Bean
    public Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(this::extractAuthorities);
        return converter;
    }

    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>();

        // Extract Zitadel project-specific roles
        String rolesClaimKey = "urn:zitadel:iam:org:project:" + zitadelProjectId + ":roles";
        Map<String, Object> rolesMap = jwt.getClaim(rolesClaimKey);

        if (rolesMap != null) {
            for (String role : rolesMap.keySet()) {
                // Convert role names to Spring Security authorities
                // e.g., "super-admin" -> "ROLE_SUPER_ADMIN"
                String authority = "ROLE_" + role.toUpperCase().replace("-", "_");
                authorities.add(new SimpleGrantedAuthority(authority));
            }
        }

        // Also check for standard roles claim (fallback for other IdPs)
        List<String> standardRoles = jwt.getClaim("roles");
        if (standardRoles != null) {
            for (String role : standardRoles) {
                String authority = role.startsWith("ROLE_") ? role : "ROLE_" + role.toUpperCase();
                authorities.add(new SimpleGrantedAuthority(authority));
            }
        }

        return authorities;
    }
}
