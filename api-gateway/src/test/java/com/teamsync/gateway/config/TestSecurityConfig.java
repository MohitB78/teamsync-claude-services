package com.teamsync.gateway.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Test security configuration that permits all requests for integration tests.
 * Used when testing route routing without actual JWT validation.
 *
 * <p>SECURITY FIX (Round 14 #H44): This class is TEST-ONLY and must never be
 * included in production builds. Safety measures:
 * <ul>
 *   <li>Located in src/test/java (excluded from production builds)</li>
 *   <li>Uses @TestConfiguration (only loaded in test context)</li>
 *   <li>Requires explicit import in test classes</li>
 * </ul>
 *
 * <p>WARNING: Never move this class to src/main/java or include it in production
 * Spring context. The @Order(-1) would override production security!
 */
@TestConfiguration
@EnableWebFluxSecurity
public class TestSecurityConfig {

    @Bean
    @Primary
    @Order(-1)  // Higher priority than other security configs
    public SecurityWebFilterChain testSecurityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(testCorsConfigurationSource()))
                .authorizeExchange(exchanges -> exchanges
                        // Permit all requests for testing routing
                        .anyExchange().permitAll())
                .build();
    }

    @Bean
    @Primary
    public CorsConfigurationSource testCorsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(Arrays.asList("X-Request-ID", "Content-Disposition"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Mock JWT decoder that accepts any valid-looking token for tests.
     * Returns a valid JWT with Zitadel-style claims.
     */
    @Bean
    @Primary
    public ReactiveJwtDecoder testJwtDecoder() {
        return token -> {
            // Create a mock JWT with Zitadel-style claims for testing
            // Zitadel uses: urn:zitadel:iam:org:project:{projectId}:roles
            String testProjectId = "test-project-id";
            String rolesKey = "urn:zitadel:iam:org:project:" + testProjectId + ":roles";

            Jwt jwt = Jwt.withTokenValue(token)
                    .header("alg", "RS256")
                    .header("typ", "JWT")
                    .subject("test-user-id")
                    .claim("email", "test@example.com")
                    .claim("name", "Test User")
                    .claim("preferred_username", "testuser")
                    .claim("tenant_id", "default")
                    // Zitadel role format: role -> { orgId -> orgDomain }
                    .claim(rolesKey, Map.of(
                            "user", Map.of("test-org-id", "teamsync.local"),
                            "admin", Map.of("test-org-id", "teamsync.local")
                    ))
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();
            return Mono.just(jwt);
        };
    }
}
