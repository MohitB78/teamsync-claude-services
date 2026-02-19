package com.teamsync.gateway.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.util.matcher.AndServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.NegatedServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.OrServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Security configuration for BFF (Backend for Frontend) pattern.
 *
 * <p>This configuration uses a SINGLE security chain with:
 * <ul>
 *   <li>Public paths configured with permitAll() - no authentication required</li>
 *   <li>Protected paths requiring JWT authentication (from session or direct Bearer token)</li>
 *   <li>Custom bearer token converter that skips extraction for public paths</li>
 * </ul>
 *
 * <p>The key trick is using a custom bearerTokenConverter that returns Mono.empty() for
 * public paths, which tells the OAuth2 Resource Server "there's no token to validate"
 * without triggering an authentication error.
 *
 * <p>CSRF protection is enabled for browser requests via CookieServerCsrfTokenRepository,
 * but excluded for API endpoints that use JWT directly.
 *
 * @see BffProperties
 * @see com.teamsync.gateway.filter.SessionTokenRelayFilter
 */
@Configuration
@EnableWebFluxSecurity
@ConditionalOnProperty(name = "teamsync.bff.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class BffSecurityConfig {

    private final BffProperties bffProperties;

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    /**
     * Check if the given path is a public endpoint that doesn't require authentication.
     *
     * <p>NOTE: The /realms/** path is included because Spring Cloud Gateway's rewritePath filter
     * transforms /auth/realms/... to /realms/... BEFORE Spring Security evaluates the path.
     * So when the Keycloak proxy route runs, Security sees /realms/... not /auth/realms/...
     */
    /**
     * SECURITY FIX (Round 8): Updated to only allow specific actuator endpoints
     * without authentication (health, info, prometheus for K8s/monitoring).
     */
    private boolean isPublicPath(String path) {
        return path.startsWith("/auth/") ||
               path.startsWith("/realms/") ||  // Keycloak paths after rewrite
               path.startsWith("/resources/") ||  // Keycloak static resources after rewrite
               path.startsWith("/bff/auth/") ||
               path.startsWith("/bff/admin/") ||  // Admin endpoints use X-Admin-Key auth
               // SECURITY FIX: Only specific actuator paths are public
               path.startsWith("/actuator/health") ||
               path.equals("/actuator/info") ||
               path.equals("/actuator/prometheus") ||
               path.equals("/health") ||
               path.startsWith("/fallback/") ||
               path.startsWith("/swagger-ui/") ||
               path.startsWith("/v3/api-docs") ||
               path.startsWith("/wopi/") ||
               path.startsWith("/storage-proxy/") ||
               // Token-based document downloads (HMAC signed, no JWT required)
               path.startsWith("/api/documents/download") ||
               // Zitadel OIDC proxy paths
               path.startsWith("/oauth/") ||
               path.startsWith("/oidc/") ||
               path.startsWith("/v2/oidc/") ||
               path.startsWith("/.well-known/") ||
               path.startsWith("/v2/sessions") ||
               // Zitadel UI paths
               path.startsWith("/ui/") ||
               path.startsWith("/assets/");
    }

    /**
     * Single security filter chain that handles both public and protected paths.
     *
     * <p>Uses a custom bearerTokenConverter that returns Mono.empty() for public paths,
     * effectively telling the OAuth2 Resource Server "no token to validate" without
     * triggering an authentication failure.
     *
     * <p>Also uses a custom authenticationEntryPoint that allows requests without tokens
     * to proceed for public paths (returns empty response instead of 401).
     */
    @Bean
    @Primary
    public SecurityWebFilterChain bffSecurityFilterChain(ServerHttpSecurity http) {
        log.info("Configuring BFF security chain with OAuth2 JWT validation");

        BffProperties.CsrfProperties csrfProps = bffProperties.csrf();

        // Custom authentication entry point that doesn't add www-authenticate header
        // For public paths, we use the access denied handler instead (see below)
        ServerAuthenticationEntryPoint customEntryPoint = (exchange, ex) -> {
            String path = exchange.getRequest().getPath().value();
            boolean isApiPath = path.startsWith("/api/");
            if (isApiPath) {
                String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
                log.warn("[AuthEntryPoint] 401 for API path: {}, hasAuthHeader={}, exception={}",
                    path, authHeader != null, ex.getClass().getSimpleName() + ": " + ex.getMessage());
            } else {
                log.debug("AuthenticationEntryPoint triggered for path: {}", path);
            }
            // Return 401 Unauthorized without www-authenticate header
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        };

        return http
            // CSRF protection with cookie-based token
            .csrf(csrf -> {
                if (csrfProps.enabled()) {
                    // Use cookie-based CSRF token (readable by JavaScript)
                    CookieServerCsrfTokenRepository tokenRepository =
                        CookieServerCsrfTokenRepository.withHttpOnlyFalse();
                    tokenRepository.setCookieName(csrfProps.cookieName());
                    tokenRepository.setHeaderName(csrfProps.headerName());

                    // Build matcher: require CSRF only for unsafe methods AND not in exclude patterns
                    ServerWebExchangeMatcher unsafeMethodMatcher = exchange ->
                        HttpMethod.POST.equals(exchange.getRequest().getMethod()) ||
                        HttpMethod.PUT.equals(exchange.getRequest().getMethod()) ||
                        HttpMethod.DELETE.equals(exchange.getRequest().getMethod()) ||
                        HttpMethod.PATCH.equals(exchange.getRequest().getMethod())
                            ? ServerWebExchangeMatcher.MatchResult.match()
                            : ServerWebExchangeMatcher.MatchResult.notMatch();

                    // Build matchers for excluded paths
                    ServerWebExchangeMatcher excludeMatcher = buildCsrfExcludeMatcher(csrfProps.excludePatterns());

                    // Require CSRF only for: unsafe methods AND paths not in exclude list
                    ServerWebExchangeMatcher csrfMatcher = new AndServerWebExchangeMatcher(
                        unsafeMethodMatcher,
                        new NegatedServerWebExchangeMatcher(excludeMatcher)
                    );

                    csrf.csrfTokenRepository(tokenRepository)
                        .requireCsrfProtectionMatcher(csrfMatcher);

                    log.info("CSRF enabled with cookie: {}, header: {}, excluded: {}",
                        csrfProps.cookieName(), csrfProps.headerName(), csrfProps.excludePatterns());
                } else {
                    csrf.disable();
                    log.info("CSRF disabled");
                }
            })

            // CORS configuration
            .cors(cors -> cors.configurationSource(bffCorsConfigurationSource()))

            // Authorization rules
            .authorizeExchange(exchanges -> exchanges
                // SECURITY FIX (Round 8): Actuator endpoints require authentication
                // except for health/readiness/liveness probes needed by Kubernetes
                .pathMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .pathMatchers("/actuator/info").permitAll()
                .pathMatchers("/actuator/prometheus").permitAll()  // Prometheus scraping
                .pathMatchers("/actuator/**").authenticated()  // All other actuator endpoints require auth
                // Public endpoints - no authentication required
                .pathMatchers("/health").permitAll()
                .pathMatchers("/fallback/**").permitAll()
                .pathMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .pathMatchers("/bff/auth/**").permitAll()
                .pathMatchers("/bff/admin/**").permitAll()  // Admin endpoints use X-Admin-Key header auth
                .pathMatchers("/auth/**").permitAll()  // Keycloak reverse proxy (before rewrite)
                .pathMatchers("/realms/**").permitAll()  // Keycloak paths (after rewrite)
                .pathMatchers("/resources/**").permitAll()  // Keycloak static resources (after rewrite)
                .pathMatchers("/wopi/**").permitAll()
                .pathMatchers("/storage-proxy/**").permitAll()
                // Token-based document downloads (HMAC signed, no JWT required)
                .pathMatchers("/api/documents/download").permitAll()
                // Zitadel OIDC proxy paths
                .pathMatchers("/oauth/**").permitAll()       // OAuth endpoints (authorize, token, keys)
                .pathMatchers("/oidc/**").permitAll()        // OIDC v1 endpoints (userinfo, end_session)
                .pathMatchers("/v2/oidc/**").permitAll()     // OIDC v2 endpoints (auth_requests)
                .pathMatchers("/.well-known/**").permitAll() // Discovery endpoints
                .pathMatchers("/v2/sessions/**").permitAll() // Session API v2 (headless login)
                // Zitadel UI paths
                .pathMatchers("/ui/**").permitAll()
                .pathMatchers("/assets/**").permitAll()
                // All other endpoints require authentication
                .anyExchange().authenticated())

            // OAuth2 Resource Server for JWT validation
            // Uses custom bearerTokenConverter to skip token extraction for public paths
            // Uses custom authenticationEntryPoint to not add www-authenticate header
            .oauth2ResourceServer(oauth2 -> oauth2
                .bearerTokenConverter(exchange -> {
                    String path = exchange.getRequest().getPath().value();
                    boolean isApiPath = path.startsWith("/api/");

                    if (isPublicPath(path)) {
                        // For public paths, don't try to extract Bearer token
                        // This tells OAuth2 Resource Server "there's no token" which should
                        // skip authentication and let permitAll() handle it
                        log.trace("Skipping bearer token extraction for public path: {}", path);
                        return Mono.empty();
                    }
                    // Check if there's an Authorization header with Bearer token
                    String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");

                    // DEBUG: Log what the bearer token converter sees for API paths
                    if (isApiPath) {
                        log.info("[BearerTokenConverter] path={}, hasAuthHeader={}, authHeaderPrefix={}",
                            path,
                            authHeader != null,
                            authHeader != null ? authHeader.substring(0, Math.min(15, authHeader.length())) + "..." : "null");
                    }

                    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                        // No Bearer token in request - return empty to skip JWT validation
                        // SessionAuthenticationWebFilter may have already added the token from session
                        if (isApiPath) {
                            log.warn("[BearerTokenConverter] No Bearer token found for API path: {}", path);
                        } else {
                            log.trace("No Bearer token in request for path: {}", path);
                        }
                        return Mono.empty();
                    }

                    // For protected paths with Bearer token, use default converter
                    if (isApiPath) {
                        log.info("[BearerTokenConverter] Extracting Bearer token for API path: {}", path);
                    }
                    return new ServerBearerTokenAuthenticationConverter().convert(exchange);
                })
                .authenticationEntryPoint(customEntryPoint)
                .jwt(jwt -> jwt.jwtDecoder(bffJwtDecoder())))

            // Security headers for protection against common attacks
            .headers(headers -> headers
                // X-Content-Type-Options: nosniff - prevents MIME type sniffing
                .contentTypeOptions(contentTypeOptions -> {})
                // X-Frame-Options: SAMEORIGIN - prevents clickjacking (allow same-origin iframes for WOPI)
                .frameOptions(frameOptions -> frameOptions.mode(
                    org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter.Mode.SAMEORIGIN))
                // X-XSS-Protection: 1; mode=block - legacy XSS protection for older browsers
                .xssProtection(xss -> xss.headerValue(
                    org.springframework.security.web.server.header.XXssProtectionServerHttpHeadersWriter.HeaderValue.ENABLED_MODE_BLOCK))
                // Strict-Transport-Security: max-age=31536000; includeSubDomains - force HTTPS
                .hsts(hsts -> {
                    hsts.maxAge(java.time.Duration.ofSeconds(31536000));
                    hsts.includeSubdomains(true);
                })
                // Referrer-Policy: strict-origin-when-cross-origin
                .referrerPolicy(referrer -> referrer.policy(
                    org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                // Content-Security-Policy - XSS protection
                // SECURITY: Removed 'unsafe-inline' and 'unsafe-eval' to prevent XSS attacks
                // Next.js apps use nonce-based CSP or are configured for strict CSP compliance
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; " +
                    "script-src 'self'; " +
                    "style-src 'self' 'unsafe-inline'; " +  // unsafe-inline needed for MUI/styled-components
                    "img-src 'self' data: blob: https:; " +
                    "font-src 'self' data:; " +
                    "connect-src 'self' https: wss:; " +
                    "frame-src 'self' https:; " +
                    "frame-ancestors 'self'"
                ))
                // Permissions-Policy (formerly Feature-Policy) - restrict browser features
                .permissionsPolicy(permissions -> permissions.policy(
                    "camera=(), microphone=(), geolocation=(), payment=()"
                ))
            )

            .build();
    }

    /**
     * Build a matcher that matches any of the CSRF exclude patterns.
     */
    private ServerWebExchangeMatcher buildCsrfExcludeMatcher(List<String> excludePatterns) {
        List<ServerWebExchangeMatcher> matchers = excludePatterns.stream()
            .map(PathPatternParserServerWebExchangeMatcher::new)
            .map(m -> (ServerWebExchangeMatcher) m)
            .toList();

        return new OrServerWebExchangeMatcher(matchers);
    }

    /**
     * JWT decoder for validating tokens from Zitadel.
     *
     * <p>SECURITY: Validates all three critical claims:
     * <ul>
     *   <li>Timestamp (expiry) - token must not be expired</li>
     *   <li>Issuer - token must be from our expected Zitadel instance</li>
     *   <li>Audience - token must contain our client ID in the audience claim</li>
     * </ul>
     *
     * <p>Issuer validation prevents token substitution attacks where a token
     * from a different Zitadel instance could be accepted.
     */
    @Bean
    @Primary
    public ReactiveJwtDecoder bffJwtDecoder() {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();

        String expectedClientId = bffProperties.zitadel().clientId();
        String expectedIssuer = bffProperties.zitadel().issuer();

        log.info("[JwtDecoder] Configuring decoder with jwkSetUri={}, expectedIssuer={}, expectedClientId={}",
            jwkSetUri, expectedIssuer, expectedClientId);

        // Build validators list
        java.util.List<OAuth2TokenValidator<Jwt>> validators = new java.util.ArrayList<>();

        // 1. Timestamp validation (always required)
        validators.add(new JwtTimestampValidator());

        // 2. SECURITY FIX (Round 8): Issuer validation is MANDATORY to prevent token substitution attacks
        // In production, an attacker could use tokens from a different Zitadel instance if issuer is not validated
        if (expectedIssuer != null && !expectedIssuer.isBlank()) {
            // TEMPORARY: Accept both new custom domain and old Railway domain during migration
            // This is needed because Zitadel's issuer is stored in its database, not just env vars.
            // Once Zitadel's instance domain is updated via Admin API, remove the legacy issuer.
            // TODO: Remove legacy issuer after Zitadel domain migration is complete
            final String legacyIssuer = "https://zitadel-production-7aed.up.railway.app";
            final String finalExpectedIssuer = expectedIssuer;

            validators.add(new org.springframework.security.oauth2.jwt.JwtClaimValidator<String>(
                "iss",
                actualIssuer -> {
                    boolean valid = finalExpectedIssuer.equals(actualIssuer) || legacyIssuer.equals(actualIssuer);
                    if (!valid) {
                        log.error("[JwtDecoder] ISSUER MISMATCH - expected: '{}' or '{}', actual: '{}'",
                            finalExpectedIssuer, legacyIssuer, actualIssuer);
                    } else if (legacyIssuer.equals(actualIssuer)) {
                        log.warn("[JwtDecoder] Token using legacy issuer '{}' - Zitadel domain migration pending", actualIssuer);
                    }
                    return valid;
                }
            ));
            log.info("JWT issuer validation enabled for: {} (also accepting legacy: {})", expectedIssuer, legacyIssuer);
        } else {
            String errorMsg = "SECURITY CRITICAL: JWT issuer validation is disabled! " +
                    "Set teamsync.bff.zitadel.issuer property. " +
                    "Without issuer validation, tokens from ANY Zitadel instance would be accepted.";
            log.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }

        // 3. Audience validation
        validators.add(new org.springframework.security.oauth2.jwt.JwtClaimValidator<>(
            "aud",
            aud -> {
                if (aud == null) {
                    log.warn("JWT rejected: missing 'aud' claim");
                    return false;
                }
                // Audience can be String or List<String>
                if (aud instanceof String audString) {
                    boolean valid = audString.equals(expectedClientId);
                    if (!valid) {
                        log.warn("JWT rejected: audience '{}' does not match expected '{}'", audString, expectedClientId);
                    }
                    return valid;
                } else if (aud instanceof java.util.Collection<?> audList) {
                    boolean valid = audList.contains(expectedClientId);
                    if (!valid) {
                        log.warn("JWT rejected: audience list {} does not contain expected '{}'", audList, expectedClientId);
                    }
                    return valid;
                }
                // SECURITY FIX: Log unexpected type at error level
                log.error("JWT rejected: unexpected audience type: {} (value: {})", aud.getClass().getName(), aud);
                return false;
            }
        ));

        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(validators);
        decoder.setJwtValidator(validator);

        log.info("JWT decoder configured with issuer={}, audience={}", expectedIssuer, expectedClientId);

        return decoder;
    }

    @Value("${teamsync.cors.allowed-origins:}")
    private String corsAllowedOrigins;

    /**
     * SECURITY FIX (Round 10 #24): Disallowed CORS origin patterns.
     * These patterns are too broad and could allow malicious sites to make
     * credentialed requests when combined with setAllowCredentials(true).
     */
    private static final Set<String> DISALLOWED_CORS_PATTERNS = Set.of(
        "*",          // Matches all origins
        "http://*",   // Matches all HTTP origins
        "https://*",  // Matches all HTTPS origins
        "null"        // Special origin value that could be exploited
    );

    /**
     * CORS configuration for BFF.
     *
     * <p>Allows credentials (cookies) from frontend origins.
     *
     * <p>SECURITY: Uses explicit origin whitelist from configuration instead of
     * broad wildcards. Set TEAMSYNC_CORS_ALLOWED_ORIGINS environment variable
     * to comma-separated list of allowed origins for production.</p>
     *
     * <p>SECURITY FIX (Round 10 #24): Added validation to reject overly broad
     * CORS patterns when credentials are enabled. Broad patterns like "*" combined
     * with allowCredentials=true would allow any website to make authenticated
     * requests on behalf of the user.
     */
    @Bean
    public CorsConfigurationSource bffCorsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // SECURITY: Use explicit origins from config, fallback to dev defaults
        List<String> allowedOrigins;
        if (corsAllowedOrigins != null && !corsAllowedOrigins.isBlank()) {
            // Production: use explicitly configured origins
            allowedOrigins = Arrays.stream(corsAllowedOrigins.split(","))
                    .map(String::trim)
                    .filter(o -> !o.isEmpty())
                    .toList();
            log.info("CORS configured with explicit origins: {}", allowedOrigins);
        } else {
            // SECURITY FIX (Round 14 #H36): Development defaults use specific subdomains instead of wildcards
            // Wildcard patterns like https://*.teamsync.com could match ANY subdomain, including
            // user-controlled subdomains in multi-tenant hosting scenarios.
            // In production, always set TEAMSYNC_CORS_ALLOWED_ORIGINS with exact origins.
            allowedOrigins = List.of(
                "http://localhost:3000",          // Frontend dev server
                "http://localhost:3001",          // Alternative dev port
                "http://127.0.0.1:3000",
                "https://app.teamsync.com",       // Production frontend
                "https://portal.teamsync.com",    // Admin portal
                "https://app.accessarc.com",      // AccessArc frontend
                "https://portal.accessarc.com"    // AccessArc admin
            );
            log.warn("CORS using default development origins (set TEAMSYNC_CORS_ALLOWED_ORIGINS for production)");
        }

        // SECURITY FIX (Round 10 #24): Validate configured origins are not too broad
        for (String origin : allowedOrigins) {
            String normalizedOrigin = origin.trim().toLowerCase();
            if (DISALLOWED_CORS_PATTERNS.contains(normalizedOrigin)) {
                log.error("SECURITY: Rejecting dangerous CORS pattern '{}' - would allow any origin with credentials", origin);
                throw new IllegalStateException(
                        "SECURITY ERROR: CORS pattern '" + origin + "' is too broad for use with credentials. " +
                        "Use specific origins like 'https://app.teamsync.com' instead.");
            }
        }

        configuration.setAllowedOriginPatterns(allowedOrigins);

        // Allow all standard methods
        configuration.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));

        // Allow necessary headers
        configuration.setAllowedHeaders(Arrays.asList(
            "Authorization",
            "Content-Type",
            "X-Tenant-ID",
            "X-Drive-ID",
            "X-Request-ID",
            "X-XSRF-TOKEN",  // CSRF token header
            "Accept",
            "Origin"
        ));

        // Expose headers to JavaScript
        configuration.setExposedHeaders(Arrays.asList(
            "X-Request-ID",
            "Content-Disposition",
            "Set-Cookie"  // Allow frontend to see session cookie being set
        ));

        // CRITICAL: Allow credentials (cookies) to be sent
        configuration.setAllowCredentials(true);

        // Cache preflight for 1 hour
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        log.info("BFF CORS configured with allowCredentials=true for session cookies");

        return source;
    }
}
