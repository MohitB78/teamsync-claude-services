package com.teamsync.trash.controller;

import com.teamsync.common.dto.ApiResponse;
import com.teamsync.common.model.Permission;
import com.teamsync.common.permission.RequiresPermission;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for trash operations.
 *
 * SECURITY FIX (Round 13 #45): Added @Validated and path variable validation
 * to prevent injection attacks via malicious item IDs.
 *
 * SECURITY FIX (Round 15 #H4): Added @PreAuthorize("isAuthenticated()") for defense-in-depth.
 */
@RestController
@RequestMapping("/api/trash")
@RequiredArgsConstructor
@Validated
@Slf4j
@PreAuthorize("isAuthenticated()")
public class TrashController {

    /**
     * SECURITY FIX (Round 13 #45): Valid ID pattern for path variables.
     */
    private static final String VALID_ID_PATTERN = "^[a-zA-Z0-9-]{1,64}$";

    /**
     * List trashed items.
     * Requires READ permission on the drive.
     */
    @GetMapping
    @RequiresPermission(Permission.READ)
    public ResponseEntity<ApiResponse<String>> listTrash() {
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .success(true)
                .message("Trash Service - List Trashed Items endpoint")
                .build());
    }

    /**
     * Restore item from trash.
     * Requires WRITE permission on the drive.
     *
     * SECURITY FIX (Round 13 #46): Added path variable validation.
     */
    @PostMapping("/{itemId}/restore")
    @RequiresPermission(Permission.WRITE)
    public ResponseEntity<ApiResponse<String>> restoreItem(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid item ID format")
            String itemId) {
        log.info("POST /api/trash/{}/restore", itemId);
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .success(true)
                .message("Trash Service - Restore Item: " + itemId)
                .build());
    }

    /**
     * Permanently delete item from trash.
     * Requires DELETE permission on the drive.
     *
     * SECURITY FIX (Round 13 #47): Added path variable validation.
     */
    @DeleteMapping("/{itemId}")
    @RequiresPermission(Permission.DELETE)
    public ResponseEntity<ApiResponse<String>> permanentDelete(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid item ID format")
            String itemId) {
        log.info("DELETE /api/trash/{}", itemId);
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .success(true)
                .message("Trash Service - Permanent Delete: " + itemId)
                .build());
    }

    /**
     * Empty all items from trash.
     * Requires DELETE permission on the drive.
     */
    @DeleteMapping("/empty")
    @RequiresPermission(Permission.DELETE)
    public ResponseEntity<ApiResponse<String>> emptyTrash() {
        log.info("DELETE /api/trash/empty");
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .success(true)
                .message("Trash Service - Empty Trash endpoint")
                .build());
    }
}
