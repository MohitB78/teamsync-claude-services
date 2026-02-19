package com.teamsync.gateway.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Fallback controller for circuit breaker responses.
 *
 * <p>When upstream services are unavailable or timeout, the circuit breaker
 * routes requests here to return a proper 503 Service Unavailable response
 * instead of connection errors or timeouts.</p>
 *
 * <p>This ensures clients receive a clear error message and appropriate
 * HTTP status code when services are down (e.g., during Railway cold starts).</p>
 */
@RestController
@RequestMapping("/fallback")
@Slf4j
public class FallbackController {

    /**
     * Fallback for unified content endpoint (/api/content/**)
     * This is the primary endpoint for listing folders and documents.
     */
    @GetMapping("/content")
    public Mono<ResponseEntity<Map<String, Object>>> contentFallback() {
        log.warn("Content Service fallback triggered - service unavailable or timeout");
        return createFallbackResponse("Content Service");
    }

    @GetMapping("/documents")
    public Mono<ResponseEntity<Map<String, Object>>> documentsFallback() {
        log.warn("Document Service fallback triggered - service unavailable or timeout");
        return createFallbackResponse("Document Service");
    }

    @GetMapping("/folders")
    public Mono<ResponseEntity<Map<String, Object>>> foldersFallback() {
        log.warn("Folder Service fallback triggered - service unavailable or timeout");
        return createFallbackResponse("Folder Service");
    }

    @GetMapping("/storage")
    public Mono<ResponseEntity<Map<String, Object>>> storageFallback() {
        log.warn("Storage Service fallback triggered - service unavailable or timeout");
        return createFallbackResponse("Storage Service");
    }

    @GetMapping("/sharing")
    public Mono<ResponseEntity<Map<String, Object>>> sharingFallback() {
        log.warn("Sharing Service fallback triggered - service unavailable or timeout");
        return createFallbackResponse("Sharing Service");
    }

    @GetMapping("/teams")
    public Mono<ResponseEntity<Map<String, Object>>> teamsFallback() {
        log.warn("Team Service fallback triggered - service unavailable or timeout");
        return createFallbackResponse("Team Service");
    }

    @GetMapping("/projects")
    public Mono<ResponseEntity<Map<String, Object>>> projectsFallback() {
        log.warn("Project Service fallback triggered - service unavailable or timeout");
        return createFallbackResponse("Project Service");
    }

    @GetMapping("/workflows")
    public Mono<ResponseEntity<Map<String, Object>>> workflowsFallback() {
        log.warn("Workflow Execution Service fallback triggered - service unavailable or timeout");
        return createFallbackResponse("Workflow Execution Service");
    }

    @GetMapping("/trash")
    public Mono<ResponseEntity<Map<String, Object>>> trashFallback() {
        log.warn("Trash Service fallback triggered - service unavailable or timeout");
        return createFallbackResponse("Trash Service");
    }

    @GetMapping("/search")
    public Mono<ResponseEntity<Map<String, Object>>> searchFallback() {
        log.warn("Search Service fallback triggered - service unavailable or timeout");
        return createFallbackResponse("Search Service");
    }

    @GetMapping("/chat")
    public Mono<ResponseEntity<Map<String, Object>>> chatFallback() {
        log.warn("Chat Service fallback triggered - service unavailable or timeout");
        return createFallbackResponse("Chat Service");
    }

    @GetMapping("/notifications")
    public Mono<ResponseEntity<Map<String, Object>>> notificationsFallback() {
        log.warn("Notification Service fallback triggered - service unavailable or timeout");
        return createFallbackResponse("Notification Service");
    }

    @GetMapping("/activities")
    public Mono<ResponseEntity<Map<String, Object>>> activitiesFallback() {
        log.warn("Activity Service fallback triggered - service unavailable or timeout");
        return createFallbackResponse("Activity Service");
    }

    @GetMapping("/wopi")
    public Mono<ResponseEntity<Map<String, Object>>> wopiFallback() {
        log.warn("WOPI Service fallback triggered - service unavailable or timeout");
        return createFallbackResponse("WOPI Service");
    }

    @GetMapping("/settings")
    public Mono<ResponseEntity<Map<String, Object>>> settingsFallback() {
        log.warn("Settings Service fallback triggered - service unavailable or timeout");
        return createFallbackResponse("Settings Service");
    }

    @GetMapping("/presence")
    public Mono<ResponseEntity<Map<String, Object>>> presenceFallback() {
        log.warn("Presence Service fallback triggered - service unavailable or timeout");
        return createFallbackResponse("Presence Service");
    }

    /**
     * Fallback for Permission Manager Service (/api/permissions/**, /api/drives/**, /api/roles/**)
     */
    @GetMapping("/permissions")
    public Mono<ResponseEntity<Map<String, Object>>> permissionsFallback() {
        log.warn("Permission Manager Service fallback triggered - service unavailable or timeout");
        return createFallbackResponse("Permission Manager Service");
    }

    private Mono<ResponseEntity<Map<String, Object>>> createFallbackResponse(String serviceName) {
        log.info("Returning 503 fallback response for: {}", serviceName);
        Map<String, Object> response = Map.of(
                "success", false,
                "error", serviceName + " is currently unavailable. Please try again later.",
                "code", "SERVICE_UNAVAILABLE"
        );
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response));
    }
}
