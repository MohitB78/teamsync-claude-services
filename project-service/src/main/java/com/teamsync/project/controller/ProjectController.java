package com.teamsync.project.controller;

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
 * Project Controller - manages project operations.
 *
 * SECURITY FIX (Round 12): Added @PreAuthorize annotations to all endpoints.
 * Previously, endpoints were accessible without authentication which could
 * allow unauthorized access to project data and management functions.
 *
 * SECURITY FIX (Round 14 #H16): Added @Validated and path variable validation
 * to prevent injection attacks via malicious project IDs.
 */
/**
 * SECURITY FIX (Round 15 #H13): Added class-level @PreAuthorize for defense-in-depth.
 */
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
@Validated
@Slf4j
@PreAuthorize("isAuthenticated()")
public class ProjectController {

    /**
     * SECURITY FIX (Round 14 #H16): Valid ID pattern for path variables.
     */
    private static final String VALID_ID_PATTERN = "^[a-zA-Z0-9-]{1,64}$";

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<String>> listProjects() {
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .success(true)
                .message("Project Service - List Projects endpoint")
                .build());
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<String>> createProject() {
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .success(true)
                .message("Project Service - Create Project endpoint")
                .build());
    }

    /**
     * Get project by ID.
     * SECURITY FIX (Round 14 #H16): Added path variable validation.
     */
    @GetMapping("/{projectId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<String>> getProject(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid project ID format")
            String projectId) {
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .success(true)
                .message("Project Service - Get Project: " + projectId)
                .build());
    }
}
