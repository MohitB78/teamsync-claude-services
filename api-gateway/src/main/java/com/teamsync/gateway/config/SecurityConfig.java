package com.teamsync.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default security configuration (used when BFF is disabled).
 *
 * <p>When teamsync.bff.enabled=true, BffSecurityConfig is used instead.
 *
 * <p>This configuration validates JWTs from Zitadel identity provider.
 *
 * @see BffSecurityConfig
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "teamsync.bff.enabled", havingValue = "false", matchIfMissing = true)
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    @Value("${teamsync.zitadel.trusted-issuers:}")
    private String trustedIssuers;

    @Value("${teamsync.cors.allowed-origins:}")
    private String corsAllowedOrigins;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeExchange(exchanges -> exchanges
                        // SECURITY FIX (Round 13 #6): Restrict actuator endpoints
                        // Only health and info are public; other actuator endpoints require authentication
                        .pathMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .pathMatchers("/actuator/info").permitAll()
                        .pathMatchers("/actuator/**").authenticated()
                        // Public endpoints
                        .pathMatchers("/health").permitAll()
                        .pathMatchers("/fallback/**").permitAll()
                        // SECURITY FIX (Round 13 #7): Swagger should be disabled in production
                        // TODO: Add profile-based conditional to disable in production
                        .pathMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // WOPI endpoints use token-based auth
                        .pathMatchers("/wopi/**").permitAll()
                        // Token-based document downloads (HMAC signed, no JWT required)
                        .pathMatchers("/api/documents/download").permitAll()
                        // All other endpoints require authentication
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtDecoder(jwtDecoder())))
                .build();
    }

    /**
     * Custom JWT decoder for Zitadel tokens.
     *
     * <p>This decoder:
     * <ul>
     *   <li>Fetches JWK keys from Zitadel's /oauth/v2/keys endpoint</li>
     *   <li>Validates timestamp (expiry) AND issuer against whitelist</li>
     * </ul>
     *
     * <p>SECURITY: Issuer validation is now enabled with a whitelist of trusted issuers.
     * Configure teamsync.zitadel.trusted-issuers with comma-separated list of valid issuers.
     */
    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        // Use JWK Set URI to fetch keys from Zitadel
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();

        // SECURITY FIX: Validate both timestamp AND issuer
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
            new JwtTimestampValidator(),
            new TrustedIssuerValidator(getTrustedIssuersSet())
        );
        decoder.setJwtValidator(validator);

        return decoder;
    }

    /**
     * Parse trusted issuers from configuration.
     * Supports multiple issuers for different environments (localhost, Docker, Railway).
     */
    private Set<String> getTrustedIssuersSet() {
        if (trustedIssuers == null || trustedIssuers.isBlank()) {
            log.warn("SECURITY WARNING: No trusted issuers configured. " +
                     "Set teamsync.zitadel.trusted-issuers in application.yml");
            return Set.of();
        }
        Set<String> issuers = Arrays.stream(trustedIssuers.split(","))
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .collect(Collectors.toSet());
        log.info("Configured {} trusted JWT issuers", issuers.size());
        return issuers;
    }

    /**
     * SECURITY: Custom issuer validator that checks against a whitelist.
     * Rejects tokens from unknown OAuth2 providers.
     *
     * SECURITY FIX (Round 13 #2): Removed startsWith() matching which allowed issuer
     * bypass attacks. For example, if "https://zitadel.example.com" was trusted,
     * an attacker could use "https://zitadel.example.com.evil.com" as issuer.
     *
     * Now uses exact match only, with trailing slash normalization.
     */
    private static class TrustedIssuerValidator implements OAuth2TokenValidator<Jwt> {
        private static final OAuth2Error INVALID_ISSUER = new OAuth2Error(
            "invalid_issuer",
            "The JWT issuer is not in the trusted issuers whitelist",
            null
        );

        private final Set<String> normalizedIssuers;

        TrustedIssuerValidator(Set<String> trustedIssuers) {
            // Pre-normalize issuers (remove trailing slashes, lowercase for comparison)
            this.normalizedIssuers = trustedIssuers.stream()
                .map(TrustedIssuerValidator::normalizeIssuer)
                .collect(Collectors.toSet());
            log.debug("Initialized TrustedIssuerValidator with {} normalized issuers", normalizedIssuers.size());
        }

        @Override
        public OAuth2TokenValidatorResult validate(Jwt jwt) {
            String issuer = jwt.getIssuer() != null ? jwt.getIssuer().toString() : null;

            if (issuer == null || issuer.isBlank()) {
                return OAuth2TokenValidatorResult.failure(INVALID_ISSUER);
            }

            // SECURITY FIX (Round 13 #2): Use exact match only (with trailing slash normalization)
            // Previous code used startsWith() which allowed bypass via subdomain/path injection
            String normalizedIssuer = normalizeIssuer(issuer);

            if (!normalizedIssuers.contains(normalizedIssuer)) {
                log.warn("SECURITY: JWT from untrusted issuer rejected: {}", issuer);
                return OAuth2TokenValidatorResult.failure(INVALID_ISSUER);
            }

            return OAuth2TokenValidatorResult.success();
        }

        /**
         * Normalize issuer URL by removing trailing slashes and lowercasing.
         * This allows "https://example.com" and "https://example.com/" to match.
         */
        private static String normalizeIssuer(String issuer) {
            if (issuer == null) return null;
            String normalized = issuer.toLowerCase().trim();
            // Remove trailing slashes for consistent comparison
            while (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            return normalized;
        }
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // SECURITY FIX: Require explicit CORS origins - no wildcards in production
        List<String> allowedOrigins;
        if (corsAllowedOrigins != null && !corsAllowedOrigins.isBlank()) {
            // Use explicitly configured origins (required for production)
            allowedOrigins = Arrays.asList(corsAllowedOrigins.split(","));
            log.info("CORS configured with {} explicit origins", allowedOrigins.size());
        } else {
            // SECURITY: Only allow localhost in development when not configured
            log.warn("SECURITY WARNING: No CORS origins configured. Only localhost allowed. " +
                     "Set teamsync.cors.allowed-origins for production.");
            allowedOrigins = List.of(
                "http://localhost:3000",
                "http://localhost:3001",
                "http://127.0.0.1:3000",
                "http://127.0.0.1:3001"
            );
        }
        configuration.setAllowedOriginPatterns(allowedOrigins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "X-Tenant-ID",
                "X-Drive-ID",
                "X-Request-ID",
                "Accept",
                "Origin"
        ));
        configuration.setExposedHeaders(Arrays.asList(
                "X-Request-ID",
                "Content-Disposition"
        ));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
