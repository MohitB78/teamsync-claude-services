package com.teamsync.presence.controller;

import com.teamsync.common.dto.ApiResponse;
import com.teamsync.common.exception.AccessDeniedException;
import com.teamsync.presence.dto.*;
import com.teamsync.presence.service.DocumentPresenceService;
import com.teamsync.presence.service.UserPresenceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * REST controller for managing user and document presence.
 *
 * SECURITY (Round 6): Added authorization checks to prevent BOLA vulnerabilities.
 * - Users can only query their own presence details and documents
 * - Cross-user presence queries require the requesting user's ID from header
 *
 * SECURITY FIX (Round 14 #H26): Added @Validated and path variable validation
 * to prevent injection attacks via malicious IDs.
 */
/**
 * SECURITY FIX (Round 15 #H16): Added class-level @PreAuthorize for defense-in-depth.
 */
@RestController
@RequestMapping("/api/presence")
@RequiredArgsConstructor
@Validated
@Slf4j
@PreAuthorize("isAuthenticated()")
public class PresenceController {

    /**
     * SECURITY FIX (Round 14 #H26): Valid ID pattern for path variables.
     */
    private static final String VALID_ID_PATTERN = "^[a-zA-Z0-9-]{1,64}$";

    private final UserPresenceService userPresenceService;
    private final DocumentPresenceService documentPresenceService;

    // ========================
    // User Presence Endpoints
    // ========================

    @PostMapping("/heartbeat")
    public ResponseEntity<ApiResponse<HeartbeatResponse>> heartbeat(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-User-ID") String userId,
            @RequestHeader(value = "X-User-Name", required = false) String userName,
            @RequestHeader(value = "X-User-Email", required = false) String email,
            @RequestHeader(value = "X-User-Avatar", required = false) String avatarUrl,
            @Valid @RequestBody HeartbeatRequest request) {

        log.debug("Heartbeat received from user: {} in tenant: {}", userId, tenantId);

        // Override request userId with header userId for security
        request.setUserId(userId);
        request.setTenantId(tenantId);

        HeartbeatResponse response = userPresenceService.processHeartbeat(
                request, tenantId, userId, userName, email, avatarUrl);

        return ResponseEntity.ok(ApiResponse.success(response, "Heartbeat acknowledged"));
    }

    /**
     * Get all online users for a tenant.
     *
     * SECURITY FIX (Round 8): Added @PreAuthorize to require authentication.
     * Without authentication, this endpoint exposed all online users for a tenant
     * to anyone who knew the tenant ID, enabling user enumeration attacks.
     */
    @GetMapping("/online")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<UserPresenceDTO>>> getOnlineUsers(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-User-ID") String requestingUserId) {

        log.debug("Getting online users for tenant: {} requested by user: {}", tenantId, requestingUserId);

        List<UserPresenceDTO> onlineUsers = userPresenceService.getOnlineUsers(tenantId);

        return ResponseEntity.ok(ApiResponse.success(onlineUsers,
                "Retrieved " + onlineUsers.size() + " online users"));
    }

    /**
     * Get presence for a specific user.
     * SECURITY FIX (Round 6): Users can only query their own presence to prevent user enumeration.
     * For querying other users' presence, use /bulk endpoint which returns limited info.
     *
     * SECURITY FIX (Round 14 #H26): Added path variable validation.
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<UserPresenceDTO>> getUserPresence(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-User-ID") String requestingUserId,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid user ID format")
            String userId) {

        // SECURITY FIX: Users can only query their own detailed presence
        // This prevents user enumeration and privacy leakage
        if (!userId.equals(requestingUserId)) {
            log.warn("SECURITY: User {} attempted to access presence of user {}", requestingUserId, userId);
            throw new AccessDeniedException("You can only view your own presence details. Use /bulk for other users.");
        }

        log.debug("Getting presence for user: {} in tenant: {}", userId, tenantId);

        return userPresenceService.getUserPresence(tenantId, userId)
                .map(presence -> ResponseEntity.ok(ApiResponse.success(presence)))
                .orElseGet(() -> ResponseEntity.ok(ApiResponse.<UserPresenceDTO>builder()
                        .success(true)
                        .data(UserPresenceDTO.builder()
                                .userId(userId)
                                .tenantId(tenantId)
                                .status(UserPresenceDTO.PresenceStatus.OFFLINE)
                                .build())
                        .message("User is offline")
                        .build()));
    }

    @PostMapping("/bulk")
    public ResponseEntity<ApiResponse<List<UserPresenceDTO>>> getBulkPresence(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @Valid @RequestBody BulkPresenceRequest request) {

        log.debug("Getting bulk presence for {} users in tenant: {}", request.getUserIds().size(), tenantId);

        List<UserPresenceDTO> presences = userPresenceService.getBulkPresence(
                tenantId, request.getUserIds(), request.isIncludeDocumentInfo());

        return ResponseEntity.ok(ApiResponse.success(presences,
                "Retrieved presence for " + presences.size() + " users"));
    }

    @PutMapping("/status")
    public ResponseEntity<ApiResponse<Void>> updateStatus(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-User-ID") String userId,
            @RequestParam UserPresenceDTO.PresenceStatus status,
            @RequestParam(required = false) String statusMessage) {

        log.debug("Updating status for user: {} to {}", userId, status);

        userPresenceService.updateStatus(tenantId, userId, status, statusMessage);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Status updated to " + status)
                .build());
    }

    @DeleteMapping("/offline")
    public ResponseEntity<ApiResponse<Void>> setOffline(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-User-ID") String userId) {

        log.info("User {} going offline in tenant {}", userId, tenantId);

        userPresenceService.setUserOffline(tenantId, userId);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("User set to offline")
                .build());
    }

    /**
     * Get presence statistics for a tenant.
     * SECURITY FIX (Round 14 #H4): Added @PreAuthorize to require authentication.
     * Stats could leak sensitive information about organization activity patterns.
     */
    @GetMapping("/stats")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PresenceStatsDTO>> getPresenceStats(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-User-ID") String requestingUserId) {

        log.debug("Getting presence stats for tenant: {} requested by user: {}", tenantId, requestingUserId);

        PresenceStatsDTO stats = userPresenceService.getPresenceStats(tenantId);

        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    // ==========================
    // Document Presence Endpoints
    // ==========================

    /**
     * SECURITY FIX (Round 14 #H26): Added path variable validation.
     */
    @GetMapping("/document/{documentId}")
    public ResponseEntity<ApiResponse<DocumentPresenceDTO>> getDocumentPresence(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid document ID format")
            String documentId) {

        log.debug("Getting presence for document: {} in tenant: {}", documentId, tenantId);

        DocumentPresenceDTO presence = documentPresenceService.getDocumentPresence(tenantId, documentId);

        return ResponseEntity.ok(ApiResponse.success(presence));
    }

    /**
     * SECURITY FIX (Round 14 #H26): Added path variable validation.
     */
    @PostMapping("/document/{documentId}/join")
    public ResponseEntity<ApiResponse<JoinDocumentResponse>> joinDocument(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-User-ID") String userId,
            @RequestHeader(value = "X-User-Name", required = false) String userName,
            @RequestHeader(value = "X-User-Email", required = false) String email,
            @RequestHeader(value = "X-User-Avatar", required = false) String avatarUrl,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid document ID format")
            String documentId,
            @Valid @RequestBody(required = false) JoinDocumentRequest request) {

        log.info("User {} joining document {} in tenant {}", userId, documentId, tenantId);

        if (request == null) {
            request = new JoinDocumentRequest();
        }
        request.setDocumentId(documentId);

        JoinDocumentResponse response = documentPresenceService.joinDocument(
                request, tenantId, userId, userName, email, avatarUrl);

        return ResponseEntity.ok(ApiResponse.success(response, "Joined document"));
    }

    /**
     * SECURITY FIX (Round 14 #H26): Added path variable validation.
     */
    @PostMapping("/document/{documentId}/leave")
    public ResponseEntity<ApiResponse<Void>> leaveDocument(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-User-ID") String userId,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid document ID format")
            String documentId) {

        log.info("User {} leaving document {} in tenant {}", userId, documentId, tenantId);

        documentPresenceService.leaveDocument(tenantId, documentId, userId);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Left document: " + documentId)
                .build());
    }

    /**
     * SECURITY FIX (Round 14 #H26): Added path variable validation.
     */
    @PutMapping("/document/{documentId}/cursor")
    public ResponseEntity<ApiResponse<Void>> updateCursor(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-User-ID") String userId,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid document ID format")
            String documentId,
            @Valid @RequestBody UpdateCursorRequest request) {

        log.debug("Updating cursor for user {} in document {}", userId, documentId);

        request.setDocumentId(documentId);
        documentPresenceService.updateCursor(request, tenantId, userId);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Cursor updated")
                .build());
    }

    /**
     * SECURITY FIX (Round 14 #H26): Added path variable validation.
     */
    @PutMapping("/document/{documentId}/state")
    public ResponseEntity<ApiResponse<Void>> updateEditorState(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-User-ID") String userId,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid document ID format")
            String documentId,
            @RequestParam DocumentPresenceDTO.EditorState state) {

        log.debug("Updating editor state for user {} in document {} to {}", userId, documentId, state);

        documentPresenceService.updateEditorState(tenantId, documentId, userId, state);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Editor state updated to " + state)
                .build());
    }

    @GetMapping("/documents/active")
    public ResponseEntity<ApiResponse<List<DocumentPresenceDTO>>> getActiveDocuments(
            @RequestHeader("X-Tenant-ID") String tenantId) {

        log.debug("Getting active documents for tenant: {}", tenantId);

        List<DocumentPresenceDTO> activeDocuments = documentPresenceService.getActiveDocuments(tenantId);

        return ResponseEntity.ok(ApiResponse.success(activeDocuments,
                "Retrieved " + activeDocuments.size() + " active documents"));
    }

    /**
     * Get documents a user is currently viewing/editing.
     * SECURITY FIX (Round 6): Users can only query their own active documents to prevent
     * information disclosure about what other users are working on.
     *
     * SECURITY FIX (Round 14 #H26): Added path variable validation.
     */
    @GetMapping("/user/{userId}/documents")
    public ResponseEntity<ApiResponse<Set<String>>> getUserDocuments(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-User-ID") String requestingUserId,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid user ID format")
            String userId) {

        // SECURITY FIX: Users can only see their own active documents
        // This prevents tracking what documents other users are working on
        if (!userId.equals(requestingUserId)) {
            log.warn("SECURITY: User {} attempted to access document list of user {}", requestingUserId, userId);
            throw new AccessDeniedException("You can only view your own active documents.");
        }

        log.debug("Getting documents for user: {} in tenant: {}", userId, tenantId);

        Set<String> documents = documentPresenceService.getUserDocuments(tenantId, userId);

        return ResponseEntity.ok(ApiResponse.success(documents,
                "User is in " + documents.size() + " documents"));
    }

    // ==========================
    // Health Check
    // ==========================

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<String>> healthCheck() {
        return ResponseEntity.ok(ApiResponse.success("healthy", "Presence service is running"));
    }
}
