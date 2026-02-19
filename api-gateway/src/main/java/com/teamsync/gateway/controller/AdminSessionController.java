package com.teamsync.gateway.controller;

import com.teamsync.gateway.service.SessionInvalidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Admin controller for session management operations.
 *
 * <p>SECURITY: This endpoint is protected by a secret key that must be provided
 * in the X-Admin-Key header. The key is configured via TEAMSYNC_ADMIN_KEY env var.
 * This allows session cleanup even when sessions are corrupted and JWT auth fails.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /bff/admin/sessions/clear - Clear all sessions (for corrupt session recovery)</li>
 * </ul>
 */
@RestController
@RequestMapping("/bff/admin")
@ConditionalOnProperty(name = "teamsync.bff.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class AdminSessionController {

    private final SessionInvalidationService sessionInvalidationService;

    @Value("${teamsync.admin.key:}")
    private String adminKey;

    /**
     * Clear all BFF sessions from Redis.
     *
     * <p>Use this endpoint to recover from corrupted session data that causes
     * "creationTime key must not be null" errors. All users will need to re-authenticate.
     *
     * <p>SECURITY: Requires X-Admin-Key header with valid admin key.
     *
     * @param providedKey Admin key from X-Admin-Key header
     * @return Number of sessions cleared
     */
    @PostMapping("/sessions/clear")
    public Mono<ResponseEntity<Map<String, Object>>> clearAllSessions(
            @RequestHeader(value = "X-Admin-Key", required = false) String providedKey) {

        // Validate admin key
        if (adminKey == null || adminKey.isBlank()) {
            log.error("ADMIN: Session clear attempted but TEAMSYNC_ADMIN_KEY not configured");
            return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                    "success", false,
                    "error", "Admin key not configured"
                )));
        }

        if (providedKey == null || !adminKey.equals(providedKey)) {
            log.warn("ADMIN: Session clear attempted with invalid admin key");
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of(
                    "success", false,
                    "error", "Invalid admin key"
                )));
        }

        log.warn("ADMIN: Clear all sessions endpoint called with valid admin key");

        return sessionInvalidationService.clearAllSessions()
            .map(count -> ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Sessions cleared successfully. All users will need to re-authenticate.",
                "sessionsCleared", count
            )));
    }
}
