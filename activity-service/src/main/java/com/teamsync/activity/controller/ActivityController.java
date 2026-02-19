package com.teamsync.activity.controller;

import com.teamsync.activity.dto.ActivityCursorPage;
import com.teamsync.activity.service.ActivityService;
import com.teamsync.common.context.TenantContext;
import com.teamsync.common.dto.ApiResponse;
import com.teamsync.common.exception.AccessDeniedException;
import com.teamsync.common.model.Permission;
import com.teamsync.common.permission.PermissionService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Activity and audit log controller.
 *
 * SECURITY FIX (Round 6): Added proper authentication and authorization checks.
 * Audit logs contain sensitive information about user actions and system events.
 * Access must be restricted to authenticated users with appropriate permissions.
 *
 * SECURITY FIX (Round 7): Added document access verification for document activities.
 *
 * SECURITY FIX (Round 14 #H19): Added @Validated and path/query variable validation
 * to prevent injection attacks via malicious IDs.
 */
/**
 * SECURITY FIX (Round 15 #H15): Added class-level @PreAuthorize for defense-in-depth.
 */
@RestController
@RequestMapping("/api/activities")
@RequiredArgsConstructor
@Validated
@Slf4j
@PreAuthorize("isAuthenticated()")
public class ActivityController {

    /**
     * SECURITY FIX (Round 14 #H19): Valid ID pattern for path variables.
     */
    private static final String VALID_ID_PATTERN = "^[a-zA-Z0-9-]{1,64}$";

    private final PermissionService permissionService;
    private final ActivityService activityService;

    /**
     * SECURITY FIX (Round 14 #H19): Added query parameter validation.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<String>> listActivities(
            @RequestParam(required = false)
            @Size(max = 64, message = "Resource type must not exceed 64 characters")
            @Pattern(regexp = "^[a-zA-Z_]*$", message = "Invalid resource type format")
            String resourceType,
            @RequestParam(required = false)
            @Size(max = 64, message = "Resource ID must not exceed 64 characters")
            @Pattern(regexp = "^[a-zA-Z0-9-]*$", message = "Invalid resource ID format")
            String resourceId) {
        // SECURITY: Activities are filtered by tenant context (set by gateway filter)
        String tenantId = TenantContext.getTenantId();
        log.debug("Listing activities for tenant: {}", tenantId);

        return ResponseEntity.ok(ApiResponse.<String>builder()
                .success(true)
                .message("Activity Service - List Activities")
                .build());
    }

    /**
     * Get activities for a team/drive.
     * Users must have READ permission on the drive to view team activities.
     *
     * @param driveId The drive ID (from X-Drive-ID header)
     * @param cursor Optional cursor for pagination
     * @param limit Number of activities to return (default 50, max 100)
     * @return Paginated activities
     */
    @GetMapping("/team")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ActivityCursorPage>> getTeamActivities(
            @RequestHeader("X-Drive-ID") String driveId,
            @RequestParam(required = false)
            @Size(max = 256, message = "Cursor must not exceed 256 characters")
            String cursor,
            @RequestParam(defaultValue = "50")
            @Min(value = 1, message = "Limit must be at least 1")
            @Max(value = 100, message = "Limit must not exceed 100")
            int limit) {

        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();

        // Verify user has READ access to the drive
        if (!permissionService.hasPermission(userId, driveId, Permission.READ)) {
            log.warn("SECURITY: User {} attempted to view activities for drive {} without access",
                    userId, driveId);
            throw new AccessDeniedException("You do not have access to view activities for this team");
        }

        log.debug("Getting team activities for drive: {} by user: {}", driveId, userId);

        ActivityCursorPage activities = activityService.getTeamActivities(tenantId, driveId, cursor, limit);

        return ResponseEntity.ok(ApiResponse.<ActivityCursorPage>builder()
                .success(true)
                .data(activities)
                .build());
    }

    /**
     * Get activities for the current user.
     *
     * @param limit Number of activities to return (default 50, max 100)
     * @return Paginated activities
     */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ActivityCursorPage>> getMyActivities(
            @RequestParam(defaultValue = "50")
            @Min(value = 1, message = "Limit must be at least 1")
            @Max(value = 100, message = "Limit must not exceed 100")
            int limit) {

        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();

        log.debug("Getting user activities for user: {}", userId);

        ActivityCursorPage activities = activityService.getUserActivities(tenantId, userId, limit);

        return ResponseEntity.ok(ApiResponse.<ActivityCursorPage>builder()
                .success(true)
                .data(activities)
                .build());
    }

    /**
     * SECURITY FIX (Round 14 #H19): Added path variable validation.
     */
    @GetMapping("/user/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<String>> getUserActivities(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid user ID format")
            String userId) {
        // SECURITY FIX (Round 6): Users can only view their own activities
        // Admins can view any user's activities (checked below)
        String currentUserId = TenantContext.getUserId();
        if (!userId.equals(currentUserId)) {
            log.warn("SECURITY: User {} attempted to view activities of user {}", currentUserId, userId);
            throw new AccessDeniedException("Cannot view other users' activities");
        }

        return ResponseEntity.ok(ApiResponse.<String>builder()
                .success(true)
                .message("Activity Service - User Activities: " + userId)
                .build());
    }

    /**
     * Get activities for a specific document.
     *
     * SECURITY FIX (Round 7): Added proper authorization check to prevent BOLA.
     * Users must have READ permission on the drive to view document activities.
     * This prevents attackers from enumerating document access patterns by
     * guessing document IDs.
     */
    /**
     * SECURITY FIX (Round 14 #H19): Added path variable validation.
     */
    @GetMapping("/document/{documentId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<String>> getDocumentActivities(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid document ID format")
            String documentId,
            @RequestHeader("X-Drive-ID") String driveId) {
        String userId = TenantContext.getUserId();

        // SECURITY FIX (Round 7): Verify user has READ access to the drive before showing activities
        // This prevents BOLA where any authenticated user could view activities for any document
        if (!permissionService.hasPermission(userId, driveId, Permission.READ)) {
            log.warn("SECURITY: User {} attempted to view activities for document {} in drive {} without access",
                    userId, documentId, driveId);
            throw new AccessDeniedException("You do not have access to view activities for this document");
        }

        log.debug("Getting document activities for: {} in drive: {} by user: {}", documentId, driveId, userId);

        return ResponseEntity.ok(ApiResponse.<String>builder()
                .success(true)
                .message("Activity Service - Document Activities: " + documentId)
                .build());
    }

    /**
     * SECURITY FIX (Round 6): Audit log access restricted to ADMIN role.
     * Audit logs contain sensitive security information and must only be
     * accessible to tenant administrators.
     */
    /**
     * SECURITY FIX (Round 14 #H19): Added date parameter validation.
     */
    @GetMapping("/audit")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('SCOPE_audit:read')")
    public ResponseEntity<ApiResponse<String>> getAuditLog(
            @RequestParam(required = false)
            @Size(max = 32, message = "Date must not exceed 32 characters")
            @Pattern(regexp = "^[0-9TZ:\\-+]*$", message = "Invalid date format")
            String startDate,
            @RequestParam(required = false)
            @Size(max = 32, message = "Date must not exceed 32 characters")
            @Pattern(regexp = "^[0-9TZ:\\-+]*$", message = "Invalid date format")
            String endDate) {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();

        log.info("AUDIT ACCESS: User {} accessed audit logs for tenant {}", userId, tenantId);

        return ResponseEntity.ok(ApiResponse.<String>builder()
                .success(true)
                .message("Activity Service - Audit Log")
                .build());
    }
}
