package com.teamsync.notification.controller;

import com.teamsync.common.context.TenantContext;
import com.teamsync.common.dto.ApiResponse;
import com.teamsync.notification.dto.*;
import com.teamsync.notification.model.Notification;
import com.teamsync.notification.service.NotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/**
 * REST controller for managing notifications.
 *
 * SECURITY (Round 6): This controller implements mandatory tenant isolation and user authentication.
 * - Tenant ID must be provided via JWT claim or X-Tenant-ID header (validated against JWT)
 * - User ID is extracted from JWT and cannot be spoofed
 * - No "default" or "anonymous" fallbacks are allowed (prevents cross-tenant data leakage)
 *
 * SECURITY FIX (Round 14 #H17): Added @Validated and path variable validation
 * to prevent injection attacks via malicious notification IDs.
 *
 * SECURITY FIX (Round 15 #H2): Added @PreAuthorize("isAuthenticated()") for defense-in-depth.
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Validated
@Slf4j
@PreAuthorize("isAuthenticated()")
public class NotificationController {

    /**
     * SECURITY FIX (Round 14 #H17): Valid ID pattern for path variables.
     */
    private static final String VALID_ID_PATTERN = "^[a-zA-Z0-9-]{1,64}$";

    private final NotificationService notificationService;

    // ==================== List and Get Notifications ====================

    /**
     * List notifications for the current user.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<NotificationDTO>>> listNotifications(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantIdHeader,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        String tenantId = resolveTenantId(jwt, tenantIdHeader);
        String userId = getUserId(jwt);

        Notification.NotificationType notificationType = null;
        if (type != null && !type.isEmpty()) {
            try {
                notificationType = Notification.NotificationType.valueOf(type);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid notification type: {}", type);
            }
        }

        Page<NotificationDTO> notifications = notificationService.getNotifications(
                tenantId, userId, unreadOnly, includeArchived, notificationType, page, size);

        return ResponseEntity.ok(ApiResponse.success(notifications, "Notifications retrieved"));
    }

    /**
     * Get archived notifications.
     */
    @GetMapping("/archived")
    public ResponseEntity<ApiResponse<Page<NotificationDTO>>> getArchivedNotifications(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantIdHeader,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        String tenantId = resolveTenantId(jwt, tenantIdHeader);
        String userId = getUserId(jwt);

        Page<NotificationDTO> notifications = notificationService.getArchivedNotifications(
                tenantId, userId, page, size);

        return ResponseEntity.ok(ApiResponse.success(notifications, "Archived notifications retrieved"));
    }

    /**
     * Get a specific notification.
     * SECURITY FIX (Round 14 #H17): Added path variable validation.
     */
    @GetMapping("/{notificationId}")
    public ResponseEntity<ApiResponse<NotificationDTO>> getNotification(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantIdHeader,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid notification ID format")
            String notificationId) {

        String tenantId = resolveTenantId(jwt, tenantIdHeader);
        String userId = getUserId(jwt);

        NotificationDTO notification = notificationService.getNotification(tenantId, userId, notificationId);

        return ResponseEntity.ok(ApiResponse.success(notification, "Notification retrieved"));
    }

    /**
     * Get unread notification count with breakdown.
     */
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<NotificationCountDTO>> getUnreadCount(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantIdHeader) {

        String tenantId = resolveTenantId(jwt, tenantIdHeader);
        String userId = getUserId(jwt);

        NotificationCountDTO count = notificationService.getUnreadCount(tenantId, userId);

        return ResponseEntity.ok(ApiResponse.success(count, "Unread count retrieved"));
    }

    // ==================== Create Notifications ====================

    /**
     * Create notifications (internal API for other services).
     */
    @PostMapping
    public ResponseEntity<ApiResponse<List<NotificationDTO>>> createNotifications(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantIdHeader,
            @Valid @RequestBody CreateNotificationRequest request) {

        String tenantId = resolveTenantId(jwt, tenantIdHeader);
        String userId = getUserId(jwt);
        String userName = getUserName(jwt);

        List<NotificationDTO> notifications = notificationService.createNotifications(
                tenantId, request, userId, userName);

        return ResponseEntity.ok(ApiResponse.success(notifications, "Notifications created"));
    }

    // ==================== Read Operations ====================

    /**
     * Mark a notification as read.
     * SECURITY FIX (Round 14 #H17): Added path variable validation.
     */
    @PostMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<NotificationDTO>> markAsRead(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantIdHeader,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid notification ID format")
            String notificationId) {

        String tenantId = resolveTenantId(jwt, tenantIdHeader);
        String userId = getUserId(jwt);

        NotificationDTO notification = notificationService.markAsRead(tenantId, userId, notificationId);

        return ResponseEntity.ok(ApiResponse.success(notification, "Notification marked as read"));
    }

    /**
     * Mark a notification as unread.
     * SECURITY FIX (Round 14 #H17): Added path variable validation.
     */
    @PostMapping("/{notificationId}/unread")
    public ResponseEntity<ApiResponse<NotificationDTO>> markAsUnread(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantIdHeader,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid notification ID format")
            String notificationId) {

        String tenantId = resolveTenantId(jwt, tenantIdHeader);
        String userId = getUserId(jwt);

        NotificationDTO notification = notificationService.markAsUnread(tenantId, userId, notificationId);

        return ResponseEntity.ok(ApiResponse.success(notification, "Notification marked as unread"));
    }

    /**
     * Mark all notifications as read.
     */
    @PostMapping("/read-all")
    public ResponseEntity<ApiResponse<Long>> markAllAsRead(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantIdHeader) {

        String tenantId = resolveTenantId(jwt, tenantIdHeader);
        String userId = getUserId(jwt);

        long count = notificationService.markAllAsRead(tenantId, userId);

        return ResponseEntity.ok(ApiResponse.success(count, count + " notifications marked as read"));
    }

    /**
     * Mark all notifications of a specific type as read.
     * SECURITY FIX (Round 14 #H8): Added path variable validation and error handling.
     */
    @PostMapping("/read-all/{type}")
    public ResponseEntity<ApiResponse<Long>> markTypeAsRead(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantIdHeader,
            @PathVariable
            @NotBlank
            @Pattern(regexp = "^[A-Z_]{1,32}$", message = "Invalid notification type format")
            String type) {

        String tenantId = resolveTenantId(jwt, tenantIdHeader);
        String userId = getUserId(jwt);

        // SECURITY FIX (Round 14 #H8): Proper error handling for invalid enum values
        Notification.NotificationType notificationType;
        try {
            notificationType = Notification.NotificationType.valueOf(type);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid notification type requested: {}", type);
            throw new IllegalArgumentException("Invalid notification type: " + type);
        }
        long count = notificationService.markTypeAsRead(tenantId, userId, notificationType);

        return ResponseEntity.ok(ApiResponse.success(count, count + " " + type + " notifications marked as read"));
    }

    // ==================== Archive Operations ====================

    /**
     * Archive a notification.
     * SECURITY FIX (Round 14 #H17): Added path variable validation.
     */
    @PostMapping("/{notificationId}/archive")
    public ResponseEntity<ApiResponse<NotificationDTO>> archiveNotification(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantIdHeader,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid notification ID format")
            String notificationId) {

        String tenantId = resolveTenantId(jwt, tenantIdHeader);
        String userId = getUserId(jwt);

        NotificationDTO notification = notificationService.archiveNotification(tenantId, userId, notificationId);

        return ResponseEntity.ok(ApiResponse.success(notification, "Notification archived"));
    }

    /**
     * Unarchive a notification.
     * SECURITY FIX (Round 14 #H17): Added path variable validation.
     */
    @PostMapping("/{notificationId}/unarchive")
    public ResponseEntity<ApiResponse<NotificationDTO>> unarchiveNotification(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantIdHeader,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid notification ID format")
            String notificationId) {

        String tenantId = resolveTenantId(jwt, tenantIdHeader);
        String userId = getUserId(jwt);

        NotificationDTO notification = notificationService.unarchiveNotification(tenantId, userId, notificationId);

        return ResponseEntity.ok(ApiResponse.success(notification, "Notification unarchived"));
    }

    // ==================== Delete Operations ====================

    /**
     * Delete a notification.
     * SECURITY FIX (Round 14 #H17): Added path variable validation.
     */
    @DeleteMapping("/{notificationId}")
    public ResponseEntity<ApiResponse<Void>> deleteNotification(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantIdHeader,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid notification ID format")
            String notificationId) {

        String tenantId = resolveTenantId(jwt, tenantIdHeader);
        String userId = getUserId(jwt);

        notificationService.deleteNotification(tenantId, userId, notificationId);

        return ResponseEntity.ok(ApiResponse.success(null, "Notification deleted"));
    }

    // ==================== Bulk Operations ====================

    /**
     * Bulk notification operations (mark read, archive, delete).
     *
     * SECURITY FIX (Round 7): Added @Valid for input validation to ensure
     * notificationIds and operation are properly validated.
     */
    @PostMapping("/bulk")
    public ResponseEntity<ApiResponse<Integer>> bulkOperation(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantIdHeader,
            @Valid @RequestBody BulkNotificationRequest request) {

        String tenantId = resolveTenantId(jwt, tenantIdHeader);
        String userId = getUserId(jwt);

        int count = notificationService.bulkOperation(tenantId, userId, request);

        return ResponseEntity.ok(ApiResponse.success(count, count + " notifications updated"));
    }

    // ==================== Preferences ====================

    /**
     * Get notification preferences.
     */
    @GetMapping("/preferences")
    public ResponseEntity<ApiResponse<NotificationPreferenceDTO>> getPreferences(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantIdHeader) {

        String tenantId = resolveTenantId(jwt, tenantIdHeader);
        String userId = getUserId(jwt);

        NotificationPreferenceDTO preferences = notificationService.getPreferences(tenantId, userId);

        return ResponseEntity.ok(ApiResponse.success(preferences, "Preferences retrieved"));
    }

    /**
     * Update notification preferences.
     * SECURITY FIX (Round 6): Uses typed DTO instead of Map<String, Object> to prevent mass assignment.
     */
    @PatchMapping("/preferences")
    public ResponseEntity<ApiResponse<NotificationPreferenceDTO>> updatePreferences(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantIdHeader,
            @Valid @RequestBody UpdatePreferenceRequest request) {

        String tenantId = resolveTenantId(jwt, tenantIdHeader);
        String userId = getUserId(jwt);

        NotificationPreferenceDTO updated = notificationService.updatePreferences(tenantId, userId, request.getPreferences());

        return ResponseEntity.ok(ApiResponse.success(updated, "Preferences updated"));
    }

    // ==================== Mute/Unmute Resources ====================

    /**
     * Mute notifications for a resource.
     * SECURITY FIX (Round 14 #H8): Added path variable validation.
     */
    @PostMapping("/mute/{resourceType}/{resourceId}")
    public ResponseEntity<ApiResponse<Void>> muteResource(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantIdHeader,
            @PathVariable
            @NotBlank
            @Pattern(regexp = "^[a-zA-Z_]{1,32}$", message = "Invalid resource type format")
            String resourceType,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid resource ID format")
            String resourceId,
            @RequestParam(required = false) Long expiresInHours) {

        String tenantId = resolveTenantId(jwt, tenantIdHeader);
        String userId = getUserId(jwt);

        // SECURITY FIX (Round 14 #H8): Validate expiresInHours range (max 30 days)
        if (expiresInHours != null && (expiresInHours < 1 || expiresInHours > 720)) {
            throw new IllegalArgumentException("expiresInHours must be between 1 and 720 (30 days)");
        }

        Instant expiresAt = expiresInHours != null ?
                Instant.now().plusSeconds(expiresInHours * 3600) : null;

        notificationService.muteResource(tenantId, userId, resourceType, resourceId, expiresAt);

        return ResponseEntity.ok(ApiResponse.success(null, "Resource muted"));
    }

    /**
     * Unmute notifications for a resource.
     * SECURITY FIX (Round 14 #H8): Added path variable validation.
     */
    @DeleteMapping("/mute/{resourceType}/{resourceId}")
    public ResponseEntity<ApiResponse<Void>> unmuteResource(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantIdHeader,
            @PathVariable
            @NotBlank
            @Pattern(regexp = "^[a-zA-Z_]{1,32}$", message = "Invalid resource type format")
            String resourceType,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid resource ID format")
            String resourceId) {

        String tenantId = resolveTenantId(jwt, tenantIdHeader);
        String userId = getUserId(jwt);

        notificationService.unmuteResource(tenantId, userId, resourceType, resourceId);

        return ResponseEntity.ok(ApiResponse.success(null, "Resource unmuted"));
    }

    // ==================== Helper Methods ====================

    /**
     * Resolve tenant ID with security validation.
     * SECURITY FIX (Round 6): Validates header against JWT claim to prevent cross-tenant access.
     *
     * @throws IllegalArgumentException if tenant ID cannot be resolved
     * @throws com.teamsync.common.exception.AccessDeniedException if header doesn't match JWT
     */
    private String resolveTenantId(Jwt jwt, String headerTenantId) {
        String jwtTenantId = null;
        if (jwt != null && jwt.hasClaim("tenantId")) {
            jwtTenantId = jwt.getClaimAsString("tenantId");
            if (jwtTenantId != null) {
                jwtTenantId = jwtTenantId.trim();
            }
        }

        // SECURITY FIX (Round 8): UNCONDITIONAL tenant validation
        // If BOTH header and JWT have tenant ID, they MUST match - period.
        if (headerTenantId != null && !headerTenantId.isBlank()) {
            String headerValue = headerTenantId.trim();

            // SECURITY FIX (Round 8): Always validate header against JWT when both present
            if (jwtTenantId != null && !jwtTenantId.isBlank()) {
                if (!jwtTenantId.equals(headerValue)) {
                    log.warn("SECURITY: Tenant ID mismatch - header: {}, JWT: {}", headerValue, jwtTenantId);
                    throw new com.teamsync.common.exception.AccessDeniedException(
                        "X-Tenant-ID header does not match authenticated tenant");
                }
                return headerValue;
            }

            // Log when header is used without JWT validation
            log.warn("SECURITY: Using X-Tenant-ID header '{}' without JWT tenant claim validation.", headerValue);
            return headerValue;
        }

        // Fall back to JWT claim
        if (jwtTenantId != null && !jwtTenantId.isBlank()) {
            return jwtTenantId;
        }

        // SECURITY FIX (Round 8): Removed TenantContext fallback
        // ThreadLocal values can contain stale data from previous requests due to thread reuse,
        // leading to cross-tenant data access vulnerabilities.
        // Previous vulnerable code:
        // String contextTenantId = TenantContext.getTenantId();
        // if (contextTenantId != null && !contextTenantId.isBlank()) {
        //     return contextTenantId.trim();
        // }

        // SECURITY: Do not fall back to "default" - tenant isolation is mandatory
        throw new IllegalArgumentException(
            "Tenant ID is required. Provide X-Tenant-ID header or ensure JWT contains tenantId claim.");
    }

    /**
     * Extract user ID from JWT.
     * SECURITY FIX (Round 6): No "anonymous" fallback - authentication is mandatory.
     *
     * @throws IllegalArgumentException if user ID cannot be resolved
     */
    private String getUserId(Jwt jwt) {
        if (jwt == null) {
            throw new IllegalArgumentException("Authentication is required");
        }
        // Try different claim names in order of preference
        if (jwt.getSubject() != null && !jwt.getSubject().isBlank()) {
            return jwt.getSubject();
        }
        if (jwt.hasClaim("userId")) {
            String userId = jwt.getClaimAsString("userId");
            if (userId != null && !userId.isBlank()) {
                return userId;
            }
        }
        if (jwt.hasClaim("preferred_username")) {
            String username = jwt.getClaimAsString("preferred_username");
            if (username != null && !username.isBlank()) {
                return username;
            }
        }
        // SECURITY FIX: Do not fall back to "anonymous" - user identification is mandatory
        throw new IllegalArgumentException(
            "User ID could not be determined from JWT. Ensure token contains sub, userId, or preferred_username claim.");
    }

    /**
     * Extract user display name from JWT.
     * Falls back to "Unknown" only for display purposes (not for identification).
     */
    private String getUserName(Jwt jwt) {
        if (jwt == null) {
            return "Unknown";
        }
        if (jwt.hasClaim("name")) {
            String name = jwt.getClaimAsString("name");
            if (name != null && !name.isBlank()) {
                return name;
            }
        }
        if (jwt.hasClaim("preferred_username")) {
            String username = jwt.getClaimAsString("preferred_username");
            if (username != null && !username.isBlank()) {
                return username;
            }
        }
        return "Unknown";
    }
}
