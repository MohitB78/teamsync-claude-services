package com.teamsync.workflow.controller;

import com.teamsync.common.dto.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Workflow Controller - manages workflow execution operations.
 *
 * SECURITY FIX (Round 14 #C6): Added @PreAuthorize annotations to all endpoints.
 * Previously, ALL endpoints were accessible without authentication which could
 * allow unauthorized access to workflow data and execution functions.
 *
 * SECURITY FIX (Round 14 #H16): Added @Validated and path variable validation
 * to prevent injection attacks via malicious execution IDs.
 */
/**
 * SECURITY FIX (Round 15 #H12): Added class-level @PreAuthorize for defense-in-depth.
 */
@RestController
@RequestMapping("/api/workflows")
@RequiredArgsConstructor
@Validated
@Slf4j
@PreAuthorize("isAuthenticated()")
public class WorkflowController {

    /**
     * SECURITY FIX (Round 14 #H16): Valid execution ID pattern.
     */
    private static final String VALID_ID_PATTERN = "^[a-zA-Z0-9-]{1,64}$";

    /**
     * List workflow executions.
     * SECURITY FIX (Round 14 #C6): Added authentication requirement.
     */
    @GetMapping("/executions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<String>> listExecutions() {
        log.debug("GET /api/workflows/executions");
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .success(true)
                .message("Workflow Service - List Executions endpoint")
                .build());
    }

    /**
     * Start a new workflow.
     * SECURITY FIX (Round 14 #C6): Added authentication requirement.
     */
    @PostMapping("/start")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<String>> startWorkflow() {
        log.info("POST /api/workflows/start");
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .success(true)
                .message("Workflow Service - Start Workflow endpoint")
                .build());
    }

    /**
     * Approve a workflow execution step.
     * SECURITY FIX (Round 14 #C6): Added authentication requirement.
     * SECURITY FIX (Round 14 #H16): Added path variable validation.
     */
    @PostMapping("/executions/{executionId}/approve")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<String>> approve(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid execution ID format")
            String executionId) {
        log.info("POST /api/workflows/executions/{}/approve", executionId);
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .success(true)
                .message("Workflow Service - Approve: " + executionId)
                .build());
    }

    /**
     * Reject a workflow execution step.
     * SECURITY FIX (Round 14 #C6): Added authentication requirement.
     * SECURITY FIX (Round 14 #H16): Added path variable validation.
     */
    @PostMapping("/executions/{executionId}/reject")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<String>> reject(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid execution ID format")
            String executionId) {
        log.info("POST /api/workflows/executions/{}/reject", executionId);
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .success(true)
                .message("Workflow Service - Reject: " + executionId)
                .build());
    }
}
