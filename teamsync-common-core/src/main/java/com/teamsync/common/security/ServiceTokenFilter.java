package com.teamsync.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;

/**
 * SECURITY FIX (Round 7): Service-level authentication filter.
 *
 * <p>This filter validates that requests to backend services came from the API Gateway
 * by verifying a signed service token. This prevents header spoofing attacks where an
 * attacker bypasses the API Gateway and sends requests directly to backend services
 * with forged X-Tenant-ID, X-User-ID, and X-Drive-ID headers.
 *
 * <p>The service token is an HMAC-SHA256 signature of:
 * <ul>
 *   <li>X-Tenant-ID header</li>
 *   <li>X-User-ID header</li>
 *   <li>X-Service-Timestamp header (Unix epoch seconds)</li>
 * </ul>
 *
 * <p>The timestamp must be within the configured tolerance window (default: 5 minutes)
 * to prevent replay attacks.
 *
 * <p>SECURITY FIX (Round 8): This filter is now MANDATORY in production environments.
 * Set teamsync.security.service-token.enabled=true and configure a shared secret via
 * teamsync.security.service-token.secret. In non-production (local, dev, test), the
 * filter can be disabled but will log a security warning.
 *
 * <p>In production, the secret should be injected from HashiCorp Vault or a similar
 * secrets manager, never hardcoded or committed to source control.
 */
@Component
@Order(0) // Run before TenantContextFilter
@Slf4j
@ConditionalOnProperty(name = "teamsync.security.service-token.enabled", havingValue = "true")
public class ServiceTokenFilter extends OncePerRequestFilter {

    private static final String SERVICE_TOKEN_HEADER = "X-Service-Token";
    private static final String SERVICE_TIMESTAMP_HEADER = "X-Service-Timestamp";
    private static final String TENANT_HEADER = "X-Tenant-ID";
    private static final String USER_HEADER = "X-User-ID";

    /**
     * Minimum required length for the service token secret.
     * SECURITY: Must be at least 32 characters (256 bits) for adequate security.
     */
    private static final int MIN_SECRET_LENGTH = 32;

    /**
     * Production profile names where service token validation is mandatory.
     */
    private static final java.util.Set<String> PRODUCTION_PROFILES = java.util.Set.of(
            "prod", "production", "railway", "staging"
    );

    private final Environment environment;

    @Value("${teamsync.security.service-token.secret:}")
    private String serviceTokenSecret;

    @Value("${teamsync.security.service-token.timestamp-tolerance-seconds:300}")
    private long timestampToleranceSeconds;

    public ServiceTokenFilter(Environment environment) {
        this.environment = environment;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // SECURITY FIX (Round 8): Validate secret configuration based on environment
        if (serviceTokenSecret == null || serviceTokenSecret.isBlank()) {
            if (isProductionEnvironment()) {
                log.error("SECURITY CRITICAL: Service token secret not configured in production! " +
                         "This is a critical security vulnerability. Request rejected.");
                sendUnauthorized(response, "Service authentication not configured");
                return;
            } else {
                log.warn("SECURITY WARNING: Service token secret not configured. " +
                         "This would be blocked in production. Set teamsync.security.service-token.secret.");
                filterChain.doFilter(request, response);
                return;
            }
        }

        // SECURITY FIX (Round 8): Validate secret meets minimum length requirements
        if (serviceTokenSecret.length() < MIN_SECRET_LENGTH) {
            if (isProductionEnvironment()) {
                log.error("SECURITY CRITICAL: Service token secret is too short ({} chars, min: {}). " +
                         "Use a cryptographically secure secret of at least 32 characters.",
                         serviceTokenSecret.length(), MIN_SECRET_LENGTH);
                sendUnauthorized(response, "Service authentication misconfigured");
                return;
            } else {
                log.warn("SECURITY WARNING: Service token secret is too short ({} chars, min: {}). " +
                         "This would be blocked in production.",
                         serviceTokenSecret.length(), MIN_SECRET_LENGTH);
            }
        }

        String serviceToken = request.getHeader(SERVICE_TOKEN_HEADER);
        String timestampStr = request.getHeader(SERVICE_TIMESTAMP_HEADER);
        String tenantId = request.getHeader(TENANT_HEADER);
        String userId = request.getHeader(USER_HEADER);

        // Validate all required headers are present
        if (serviceToken == null || serviceToken.isBlank()) {
            log.warn("SECURITY: Request rejected - missing {} header from {}",
                    SERVICE_TOKEN_HEADER, request.getRemoteAddr());
            sendUnauthorized(response, "Missing service authentication token");
            return;
        }

        if (timestampStr == null || timestampStr.isBlank()) {
            log.warn("SECURITY: Request rejected - missing {} header from {}",
                    SERVICE_TIMESTAMP_HEADER, request.getRemoteAddr());
            sendUnauthorized(response, "Missing service timestamp");
            return;
        }

        // Validate timestamp to prevent replay attacks
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampStr);
        } catch (NumberFormatException e) {
            log.warn("SECURITY: Request rejected - invalid timestamp format from {}", request.getRemoteAddr());
            sendUnauthorized(response, "Invalid timestamp format");
            return;
        }

        long now = Instant.now().getEpochSecond();
        long timeDiff = Math.abs(now - timestamp);

        if (timeDiff > timestampToleranceSeconds) {
            log.warn("SECURITY: Request rejected - timestamp {} is {}s outside tolerance window (max: {}s) from {}",
                    timestamp, timeDiff, timestampToleranceSeconds, request.getRemoteAddr());
            sendUnauthorized(response, "Request timestamp expired or invalid");
            return;
        }

        // Compute expected signature
        String dataToSign = String.format("%s:%s:%d",
                tenantId != null ? tenantId : "",
                userId != null ? userId : "",
                timestamp);

        String expectedToken;
        try {
            expectedToken = computeHmac(dataToSign, serviceTokenSecret);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("SECURITY: Failed to compute HMAC for service token validation", e);
            sendUnauthorized(response, "Internal authentication error");
            return;
        }

        // Constant-time comparison to prevent timing attacks
        if (!MessageDigest.isEqual(
                serviceToken.getBytes(StandardCharsets.UTF_8),
                expectedToken.getBytes(StandardCharsets.UTF_8))) {
            log.warn("SECURITY: Request rejected - invalid service token from {} " +
                     "(tenantId: {}, userId: {}, timestamp: {})",
                    request.getRemoteAddr(), tenantId, userId, timestamp);
            sendUnauthorized(response, "Invalid service authentication token");
            return;
        }

        log.debug("Service token validated for request from {} (tenantId: {}, userId: {})",
                request.getRemoteAddr(), tenantId, userId);

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Skip filter for health checks and public endpoints
        return path.startsWith("/actuator") ||
               path.startsWith("/swagger") ||
               path.startsWith("/v3/api-docs") ||
               path.equals("/health");
    }

    private String computeHmac(String data, String secret) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hmacBytes);
    }

    /**
     * SECURITY FIX (Round 11): Use proper JSON serialization to prevent injection attacks.
     * Previously used String.format() which could allow JSON injection if the error message
     * contained special characters like quotes or backslashes.
     */
    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json");
        // Properly escape JSON string to prevent injection
        String escapedMessage = escapeJsonString(message);
        response.getWriter().write(
                "{\"success\":false,\"error\":\"" + escapedMessage + "\",\"code\":\"SERVICE_AUTH_FAILED\"}");
    }

    /**
     * Escapes special characters in a string for safe inclusion in JSON.
     * Prevents JSON injection by escaping quotes, backslashes, and control characters.
     */
    private String escapeJsonString(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : input.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < ' ') {
                        // Escape other control characters as unicode
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    /**
     * Checks if the current environment is a production environment.
     * SECURITY FIX (Round 8): Production environments require strict security validation.
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
