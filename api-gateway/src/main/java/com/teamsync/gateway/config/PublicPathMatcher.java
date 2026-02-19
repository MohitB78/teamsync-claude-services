package com.teamsync.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.List;

/**
 * Optimized public path matcher using pre-compiled PathPattern objects.
 *
 * <p>PERFORMANCE: This component pre-compiles all public path patterns at startup
 * rather than performing repeated string comparisons on each request. The PathPattern
 * matching is more efficient and provides consistent behavior with Spring WebFlux
 * path matching.
 *
 * <p>Usage:
 * <pre>
 * &#64;Autowired
 * private PublicPathMatcher publicPathMatcher;
 *
 * if (publicPathMatcher.isPublicPath(request.getPath().value())) {
 *     // Skip authentication
 * }
 * </pre>
 *
 * @see org.springframework.web.util.pattern.PathPattern
 */
@Component
@Slf4j
public class PublicPathMatcher {

    private final List<PathPattern> publicPatterns;

    /**
     * Pre-compile all public path patterns at startup.
     */
    public PublicPathMatcher() {
        // Spring Framework 7.x: PathPatternParser uses default settings
        // Note: setMatchOptionalTrailingSeparator was removed in Spring 7.x
        // The default behavior already handles trailing separators appropriately
        PathPatternParser parser = new PathPatternParser();

        // Pre-compile all public path patterns
        this.publicPatterns = List.of(
            // BFF and authentication endpoints
            parser.parse("/bff/auth/**"),
            parser.parse("/auth/**"),
            parser.parse("/realms/**"),       // Keycloak paths (after rewrite)
            parser.parse("/resources/**"),    // Keycloak static resources (after rewrite)

            // Actuator and health
            parser.parse("/actuator/**"),
            parser.parse("/health"),

            // Fallback endpoints
            parser.parse("/fallback/**"),

            // API documentation
            parser.parse("/swagger-ui/**"),
            parser.parse("/v3/api-docs/**"),

            // WOPI (Office editing)
            parser.parse("/wopi/**"),

            // Storage proxy (presigned URLs)
            parser.parse("/storage-proxy/**"),

            // Zitadel OIDC proxy paths
            parser.parse("/oauth/**"),
            parser.parse("/oidc/**"),
            parser.parse("/v2/oidc/**"),
            parser.parse("/.well-known/**"),
            parser.parse("/v2/sessions/**"),

            // Zitadel UI paths
            parser.parse("/ui/**"),
            parser.parse("/assets/**")
        );

        log.info("PublicPathMatcher initialized with {} pre-compiled patterns", publicPatterns.size());
    }

    /**
     * Check if the given path matches any public endpoint pattern.
     *
     * <p>PERFORMANCE: Uses pre-compiled PathPattern objects for efficient matching.
     * The patterns are checked in order, so more common patterns should be first.
     *
     * @param path The request path to check
     * @return true if the path is a public endpoint, false otherwise
     */
    public boolean isPublicPath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }

        PathContainer pathContainer = PathContainer.parsePath(path);

        for (PathPattern pattern : publicPatterns) {
            if (pattern.matches(pathContainer)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get the list of public path patterns (for logging/debugging).
     *
     * @return Immutable list of pattern strings
     */
    public List<String> getPatterns() {
        return publicPatterns.stream()
            .map(PathPattern::getPatternString)
            .toList();
    }
}
