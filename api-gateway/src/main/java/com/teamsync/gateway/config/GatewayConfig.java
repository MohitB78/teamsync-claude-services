package com.teamsync.gateway.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

/**
 * Static gateway routing configuration.
 * This configuration is active when dynamic routes are disabled.
 *
 * Service URIs are configurable via properties/environment variables.
 *
 * When teamsync.gateway.dynamic-routes.enabled=true, DynamicRouteConfig is used instead.
 */
@Configuration
@ConditionalOnProperty(name = "teamsync.gateway.dynamic-routes.enabled", havingValue = "false", matchIfMissing = true)
public class GatewayConfig {

    // Service URIs with single-level placeholders (Spring Boot 4 compatible)
    @Value("${teamsync.services.content-service:lb://content-service}")
    private String contentServiceUri;

    @Value("${teamsync.services.storage-service:lb://storage-service}")
    private String storageServiceUri;

    @Value("${teamsync.services.sharing-service:lb://sharing-service}")
    private String sharingServiceUri;

    @Value("${teamsync.services.team-service:lb://team-service}")
    private String teamServiceUri;

    @Value("${teamsync.services.project-service:lb://project-service}")
    private String projectServiceUri;

    @Value("${teamsync.services.workflow-execution-service:lb://workflow-execution-service}")
    private String workflowExecutionServiceUri;

    @Value("${teamsync.services.trash-service:lb://trash-service}")
    private String trashServiceUri;

    @Value("${teamsync.services.search-service:lb://search-service}")
    private String searchServiceUri;

    @Value("${teamsync.services.chat-service:lb://chat-service}")
    private String chatServiceUri;

    @Value("${teamsync.services.notification-service:lb://notification-service}")
    private String notificationServiceUri;

    @Value("${teamsync.services.activity-service:lb://activity-service}")
    private String activityServiceUri;

    @Value("${teamsync.services.wopi-service:lb://wopi-service}")
    private String wopiServiceUri;

    @Value("${teamsync.services.settings-service:lb://settings-service}")
    private String settingsServiceUri;

    @Value("${teamsync.services.presence-service:lb://presence-service}")
    private String presenceServiceUri;

    @Value("${teamsync.services.permission-manager-service:lb://permission-manager-service}")
    private String permissionManagerServiceUri;

    @Value("${teamsync.services.signing-service:lb://signing-service}")
    private String signingServiceUri;

    // MinIO proxy configuration for secure storage access
    @Value("${teamsync.gateway.minio.internal-url:http://teamsync-minio:9000}")
    private String minioInternalUrl;

    @Value("${teamsync.gateway.minio.host:teamsync-minio:9000}")
    private String minioHost;

    // Zitadel OIDC proxy configuration - keeps Zitadel internal, browser only sees API Gateway
    // Browser accesses: /oauth/v2/*, /.well-known/*, /oidc/v1/* → proxied to internal Zitadel
    @Value("${teamsync.bff.zitadel.internal-url:http://teamsync-zitadel:8080}")
    private String zitadelInternalUrl;

    // Zitadel OIDC proxy headers (per Zitadel documentation)
    // x-zitadel-public-host: The host where the API Gateway (OIDC proxy) is deployed
    // x-zitadel-instance-host: The EXTERNAL host of the Zitadel instance (NOT internal)
    @Value("${teamsync.zitadel.public-host:localhost:9080}")
    private String zitadelPublicHost;

    // The external domain of the Zitadel instance - used for x-zitadel-instance-host header
    // This MUST be the external/public domain, not the internal Railway hostname
    @Value("${teamsync.zitadel.external-host:${teamsync.zitadel.public-host:localhost:9080}}")
    private String zitadelExternalHost;

    @Value("${teamsync.frontend.url:http://localhost:3001}")
    private String frontendUrl;

    /**
     * Extract hostname (with port if present) from a URL.
     * e.g., "http://teamsync-zitadel:8080" → "teamsync-zitadel:8080"
     */
    private String extractHostFromUrl(String url) {
        if (url == null) return "";
        // Remove protocol prefix
        String withoutProtocol = url.replaceFirst("^https?://", "");
        // Remove any path suffix
        int slashIndex = withoutProtocol.indexOf('/');
        if (slashIndex > 0) {
            return withoutProtocol.substring(0, slashIndex);
        }
        return withoutProtocol;
    }

    /**
     * SECURITY FIX (Round 11): Sanitize header values to prevent HTTP header injection.
     *
     * Although these values come from configuration, they could be set via environment
     * variables from compromised sources. This sanitizes values to remove:
     * - CRLF characters (prevents header injection)
     * - Null bytes (prevents truncation)
     * - Other control characters
     *
     * @param value The header value to sanitize
     * @return Sanitized header value safe for use in HTTP headers
     */
    private String sanitizeHeaderValue(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        // Remove control characters including CR, LF, and null bytes
        return value.replaceAll("[\\x00-\\x1f\\x7f]", "").trim();
    }

    @Bean
    public RouteLocator routeLocator(RouteLocatorBuilder builder,
                                     @Qualifier("strictRateLimiter") RedisRateLimiter strictRateLimiter,
                                     @Qualifier("ipKeyResolver") KeyResolver ipKeyResolver) {
        return builder.routes()
                // Content Service (9081) - Unified folder and document management
                // Note: Must include both "/api/content" and "/api/content/**" because
                // AntPath "/**" does NOT match the exact path without trailing slash
                .route("content-service-unified", r -> r
                        .path("/api/content", "/api/content/**")
                        .filters(f -> f
                                .stripPrefix(0)
                                .circuitBreaker(c -> c.setName("contentService").setFallbackUri("forward:/fallback/content")))
                        .uri(contentServiceUri))

                // Document uploads with 1GB size limit (MUST come before generic /api/documents/** route)
                .route("content-service-document-upload", r -> r
                        .path("/api/documents/upload/**")
                        .filters(f -> f
                                .stripPrefix(0)
                                .setRequestSize(DataSize.ofGigabytes(1))  // 1GB limit for uploads
                                .circuitBreaker(c -> c.setName("contentService").setFallbackUri("forward:/fallback/documents")))
                        .uri(contentServiceUri))

                .route("content-service-documents", r -> r
                        .path("/api/documents", "/api/documents/**")
                        .filters(f -> f
                                .stripPrefix(0)
                                .circuitBreaker(c -> c.setName("contentService").setFallbackUri("forward:/fallback/documents")))
                        .uri(contentServiceUri))

                .route("content-service-folders", r -> r
                        .path("/api/folders", "/api/folders/**")
                        .filters(f -> f
                                .stripPrefix(0)
                                .circuitBreaker(c -> c.setName("contentService").setFallbackUri("forward:/fallback/folders")))
                        .uri(contentServiceUri))

                // SECURITY FIX (Round 15 #M7): Storage upload endpoints with larger size limits
                // MUST come before generic /api/storage/** route for proper matching
                // Note: /api/documents/upload/** is handled by content-service-document-upload route above
                .route("storage-upload-direct", r -> r
                        .path("/api/storage/upload/**")
                        .filters(f -> f
                                .stripPrefix(0)
                                .setRequestSize(DataSize.ofGigabytes(1))  // 1GB limit for uploads
                                .circuitBreaker(c -> c.setName("storageService").setFallbackUri("forward:/fallback/storage")))
                        .uri(storageServiceUri))

                // Storage Service (9083)
                .route("storage-service", r -> r
                        .path("/api/storage/**")
                        .filters(f -> f
                                .stripPrefix(0)
                                .circuitBreaker(c -> c.setName("storageService").setFallbackUri("forward:/fallback/storage")))
                        .uri(storageServiceUri))

                // SECURITY: Rate-limited public link access endpoint (MUST come before general sharing route)
                // Prevents brute force attacks on public link tokens
                .route("sharing-public-links-limited", r -> r
                        .path("/api/sharing/links/access/**")
                        .filters(f -> f
                                .stripPrefix(0)
                                .requestRateLimiter(config -> config
                                        .setRateLimiter(strictRateLimiter)
                                        .setKeyResolver(ipKeyResolver))
                                .circuitBreaker(c -> c.setName("sharingService").setFallbackUri("forward:/fallback/sharing")))
                        .uri(sharingServiceUri))

                // Sharing Service (9084) - sharing endpoints only
                // SECURITY FIX (Round 6): Removed /api/permissions/** and /api/drives/**
                // These are now handled exclusively by permission-manager-service to prevent
                // route collision that could bypass permission checks
                .route("sharing-service", r -> r
                        .path("/api/sharing/**", "/api/shares/**")
                        .filters(f -> f
                                .stripPrefix(0)
                                .circuitBreaker(c -> c.setName("sharingService").setFallbackUri("forward:/fallback/sharing")))
                        .uri(sharingServiceUri))

                // Team Service (9085)
                .route("team-service", r -> r
                        .path("/api/teams", "/api/teams/**")
                        .filters(f -> f
                                .stripPrefix(0)
                                .circuitBreaker(c -> c.setName("teamService").setFallbackUri("forward:/fallback/teams")))
                        .uri(teamServiceUri))

                // Project Service (9086)
                .route("project-service", r -> r
                        .path("/api/projects", "/api/projects/**")
                        .filters(f -> f
                                .stripPrefix(0)
                                .circuitBreaker(c -> c.setName("projectService").setFallbackUri("forward:/fallback/projects")))
                        .uri(projectServiceUri))

                // Workflow Execution Service (9087)
                .route("workflow-execution-service", r -> r
                        .path("/api/workflow-executions/**")
                        .filters(f -> f
                                .stripPrefix(0)
                                .circuitBreaker(c -> c.setName("workflowExecutionService").setFallbackUri("forward:/fallback/workflows")))
                        .uri(workflowExecutionServiceUri))

                // Trash Service (9088)
                .route("trash-service", r -> r
                        .path("/api/trash/**")
                        .filters(f -> f
                                .stripPrefix(0)
                                .circuitBreaker(c -> c.setName("trashService").setFallbackUri("forward:/fallback/trash")))
                        .uri(trashServiceUri))

                // Search Service (9089)
                .route("search-service", r -> r
                        .path("/api/search/**")
                        .filters(f -> f
                                .stripPrefix(0)
                                .circuitBreaker(c -> c.setName("searchService").setFallbackUri("forward:/fallback/search")))
                        .uri(searchServiceUri))

                // Chat/AI Service (9090)
                .route("chat-service", r -> r
                        .path("/api/chat/**", "/api/ai/**")
                        .filters(f -> f
                                .stripPrefix(0)
                                .circuitBreaker(c -> c.setName("chatService").setFallbackUri("forward:/fallback/chat")))
                        .uri(chatServiceUri))

                // Notification Service (9091)
                .route("notification-service", r -> r
                        .path("/api/notifications/**")
                        .filters(f -> f
                                .stripPrefix(0)
                                .circuitBreaker(c -> c.setName("notificationService").setFallbackUri("forward:/fallback/notifications")))
                        .uri(notificationServiceUri))

                // Activity Service (9092)
                .route("activity-service", r -> r
                        .path("/api/activities/**", "/api/audit/**")
                        .filters(f -> f
                                .stripPrefix(0)
                                .circuitBreaker(c -> c.setName("activityService").setFallbackUri("forward:/fallback/activities")))
                        .uri(activityServiceUri))

                // WOPI Host Service (9093)
                .route("wopi-service", r -> r
                        .path("/wopi/**")
                        .filters(f -> f
                                .stripPrefix(0)
                                .circuitBreaker(c -> c.setName("wopiService").setFallbackUri("forward:/fallback/wopi")))
                        .uri(wopiServiceUri))

                // Settings Service (9094)
                .route("settings-service", r -> r
                        .path("/api/settings/**", "/api/preferences/**")
                        .filters(f -> f
                                .stripPrefix(0)
                                .circuitBreaker(c -> c.setName("settingsService").setFallbackUri("forward:/fallback/settings")))
                        .uri(settingsServiceUri))

                // Presence Service (9095)
                .route("presence-service", r -> r
                        .path("/api/presence/**")
                        .filters(f -> f
                                .stripPrefix(0)
                                .circuitBreaker(c -> c.setName("presenceService").setFallbackUri("forward:/fallback/presence")))
                        .uri(presenceServiceUri))

                // WebSocket for Presence (9095)
                .route("presence-websocket", r -> r
                        .path("/ws/**")
                        .uri(presenceServiceUri.replace("http://", "ws://")))

                // Permission Manager Service (9096) - Drive-Level RBAC with O(1) checks
                .route("permission-manager-service", r -> r
                        .path("/api/permissions/**", "/api/drives/**", "/api/roles/**")
                        .filters(f -> f
                                .stripPrefix(0)
                                .circuitBreaker(c -> c.setName("permissionManagerService").setFallbackUri("forward:/fallback/permissions")))
                        .uri(permissionManagerServiceUri))

                // MinIO Proxy - Routes presigned URL requests through gateway
                // Keeps MinIO internal while supporting large file uploads via presigned URLs
                // Path: /storage-proxy/{bucket}/{key}?X-Amz-... → MinIO /{bucket}/{key}?X-Amz-...
                // SECURITY FIX (Round 11): Sanitize minioHost to prevent header injection
                .route("minio-proxy", r -> r
                        .path("/storage-proxy/**")
                        .filters(f -> f
                                .rewritePath("/storage-proxy/(?<segment>.*)", "/${segment}")
                                .setRequestHeader("Host", sanitizeHeaderValue(minioHost)))
                        .uri(minioInternalUrl))

                // ==========================================================================
                // Zitadel OIDC Proxy - Keeps Zitadel internal, browser only sees API Gateway
                // Browser accesses: /oauth/v2/*, /.well-known/*, /oidc/v1/*
                // All proxied to internal Zitadel
                //
                // Routes proxied:
                //   /oauth/v2/** → Zitadel OAuth endpoints (authorize, token, keys)
                //   /oidc/v1/** → Zitadel OIDC endpoints (userinfo, end_session)
                //   /.well-known/** → Discovery endpoints (openid-configuration, jwks.json)
                //   /v2/sessions/** → Session API v2 (for headless login)
                //   /v2/oidc/auth_requests/** → Auth request linking (per Zitadel OIDC v2 API)
                //
                // Per Zitadel documentation, OIDC proxy must set these headers:
                //   x-zitadel-public-host: The host where the OIDC proxy is deployed
                //   x-zitadel-instance-host: The host of the Zitadel instance
                // See: https://zitadel.com/docs/guides/integrate/login-ui/login-app
                // ==========================================================================
                // OIDC authorize endpoint - Zitadel redirects to our custom login UI
                // Note: rewriteResponseHeader was removed because it causes UnsupportedOperationException
                // when trying to modify Location header after response is committed (302 redirect).
                // The frontend handles the redirect URL via BFF's authorize endpoint instead.
                // SECURITY FIX (Round 11): Sanitize header values to prevent header injection
                .route("zitadel-oauth-proxy", r -> r
                        .path("/oauth/v2/**")
                        .filters(f -> f
                                .setRequestHeader("x-zitadel-public-host", sanitizeHeaderValue(zitadelPublicHost))
                                .setRequestHeader("x-zitadel-instance-host", sanitizeHeaderValue(zitadelExternalHost)))
                        .uri(zitadelInternalUrl))

                .route("zitadel-oidc-v1-proxy", r -> r
                        .path("/oidc/v1/**")
                        .filters(f -> f
                                .setRequestHeader("x-zitadel-public-host", sanitizeHeaderValue(zitadelPublicHost))
                                .setRequestHeader("x-zitadel-instance-host", sanitizeHeaderValue(zitadelExternalHost)))
                        .uri(zitadelInternalUrl))

                .route("zitadel-wellknown-proxy", r -> r
                        .path("/.well-known/**")
                        .filters(f -> f
                                .setRequestHeader("x-zitadel-public-host", sanitizeHeaderValue(zitadelPublicHost))
                                .setRequestHeader("x-zitadel-instance-host", sanitizeHeaderValue(zitadelExternalHost)))
                        .uri(zitadelInternalUrl))

                // Zitadel Session API v2 proxy (for headless login from Next.js API routes)
                .route("zitadel-session-api-proxy", r -> r
                        .path("/v2/sessions/**")
                        .filters(f -> f
                                .setRequestHeader("x-zitadel-public-host", sanitizeHeaderValue(zitadelPublicHost))
                                .setRequestHeader("x-zitadel-instance-host", sanitizeHeaderValue(zitadelExternalHost)))
                        .uri(zitadelInternalUrl))

                // Zitadel OIDC auth requests proxy (for linking session to auth request)
                // NOTE: The correct path is /v2/oidc/auth_requests (not /oidc/v2/auth_requests)
                // See: https://zitadel.com/docs/guides/integrate/login-ui/oidc-standard
                .route("zitadel-auth-requests-proxy", r -> r
                        .path("/v2/oidc/auth_requests/**")
                        .filters(f -> f
                                .setRequestHeader("x-zitadel-public-host", sanitizeHeaderValue(zitadelPublicHost))
                                .setRequestHeader("x-zitadel-instance-host", sanitizeHeaderValue(zitadelExternalHost)))
                        .uri(zitadelInternalUrl))

                // Zitadel UI Proxy - Serves the login UI and console
                .route("zitadel-ui-proxy", r -> r
                        .path("/ui/**")
                        .filters(f -> f
                                .setRequestHeader("x-zitadel-public-host", sanitizeHeaderValue(zitadelPublicHost))
                                .setRequestHeader("x-zitadel-instance-host", sanitizeHeaderValue(zitadelExternalHost)))
                        .uri(zitadelInternalUrl))

                // Zitadel Assets Proxy - Serves static assets (CSS, JS, images)
                .route("zitadel-assets-proxy", r -> r
                        .path("/assets/**")
                        .filters(f -> f
                                .setRequestHeader("x-zitadel-public-host", sanitizeHeaderValue(zitadelPublicHost))
                                .setRequestHeader("x-zitadel-instance-host", sanitizeHeaderValue(zitadelExternalHost)))
                        .uri(zitadelInternalUrl))

                // ==========================================================================
                // External Portal Routes (for external/guest team members)
                // The portal is a separate DMZ deployment with limited access
                // Portal routes use magic link authentication (not Zitadel)
                // ==========================================================================

                // Portal Authentication - Magic link based
                // Public endpoints for external users to authenticate
                .route("portal-auth", r -> r
                        .path("/portal/auth/**")
                        .filters(f -> f
                                .stripPrefix(0)
                                .circuitBreaker(c -> c.setName("teamService").setFallbackUri("forward:/fallback/portal")))
                        .uri(teamServiceUri))

                // Portal Team Access - Limited team views for external members
                // Authenticated via portal JWT (different from internal JWT)
                .route("portal-teams", r -> r
                        .path("/portal/teams/**")
                        .filters(f -> f
                                .stripPrefix(0)
                                .circuitBreaker(c -> c.setName("teamService").setFallbackUri("forward:/fallback/portal")))
                        .uri(teamServiceUri))

                // Portal Invitation Verification - Public endpoint
                // Used to verify invitation tokens before acceptance
                .route("portal-invitations", r -> r
                        .path("/portal/invitations/**")
                        .filters(f -> f
                                .stripPrefix(0)
                                .circuitBreaker(c -> c.setName("teamService").setFallbackUri("forward:/fallback/portal")))
                        .uri(teamServiceUri))

                // ==========================================================================
                // Signing Service Routes (9097) - Document signing for external users
                // ==========================================================================

                // Signing Service - Internal API (JWT auth via Zitadel)
                // For internal users: create templates, manage signature requests
                .route("signing-service-internal", r -> r
                        .path("/api/signing/**")
                        .filters(f -> f
                                .stripPrefix(0)
                                .circuitBreaker(c -> c.setName("signingService").setFallbackUri("forward:/fallback/signing")))
                        .uri(signingServiceUri))

                // Signing Service - Portal Routes (Token-based auth, no JWT)
                // For external signers: access documents via secure signing links
                // Public endpoints - token validation handled by signing-service
                .route("signing-service-portal", r -> r
                        .path("/portal/signing/**")
                        .filters(f -> f
                                .stripPrefix(0)
                                .circuitBreaker(c -> c.setName("signingService").setFallbackUri("forward:/fallback/signing")))
                        .uri(signingServiceUri))

                .build();
    }
}
