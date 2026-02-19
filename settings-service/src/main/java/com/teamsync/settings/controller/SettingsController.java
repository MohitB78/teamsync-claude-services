package com.teamsync.settings.controller;

import com.teamsync.common.context.TenantContext;
import com.teamsync.common.dto.ApiResponse;
import com.teamsync.common.dto.CursorPage;
import com.teamsync.settings.dto.DriveSettingsDTO;
import com.teamsync.settings.dto.TenantSettingsDTO;
import com.teamsync.settings.dto.UpdateDriveSettingsRequest;
import com.teamsync.settings.dto.UpdateTenantSettingsRequest;
import com.teamsync.settings.dto.UpdateUserSettingsRequest;
import com.teamsync.settings.dto.UserSettingsDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamsync.settings.exception.InvalidSettingsException;
import com.teamsync.settings.service.DriveSettingsService;
import com.teamsync.settings.service.SettingsValidationService;
import com.teamsync.settings.service.TenantSettingsService;
import com.teamsync.settings.service.UserSettingsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for managing user, tenant, and drive settings.
 *
 * SECURITY FIX (Round 14 #H18): Added @Validated and path variable validation
 * to prevent injection attacks via malicious IDs.
 */
/**
 * SECURITY FIX (Round 15 #H14): Added class-level @PreAuthorize for defense-in-depth.
 */
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
@Validated
@Slf4j
@PreAuthorize("isAuthenticated()")
public class SettingsController {

    /**
     * SECURITY FIX (Round 14 #H18): Valid ID pattern for path variables.
     * Prevents NoSQL injection and path traversal attacks.
     */
    private static final String VALID_ID_PATTERN = "^[a-zA-Z0-9-]{1,64}$";

    private final UserSettingsService userSettingsService;
    private final TenantSettingsService tenantSettingsService;
    private final DriveSettingsService driveSettingsService;
    private final SettingsValidationService validationService;
    private final ObjectMapper objectMapper;

    // ==================== User Settings ====================

    /**
     * Get current user's settings.
     */
    @GetMapping("/user")
    public ResponseEntity<ApiResponse<UserSettingsDTO>> getUserSettings(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantIdHeader) {

        String tenantId = resolveTenantId(jwt, tenantIdHeader);
        String userId = getUserId(jwt);

        log.debug("Getting user settings for tenant: {}, user: {}", tenantId, userId);

        UserSettingsDTO settings = userSettingsService.getUserSettings(tenantId, userId);

        return ResponseEntity.ok(ApiResponse.success(settings, "User settings retrieved"));
    }

    /**
     * Update current user's settings.
     *
     * SECURITY: Uses typed DTO with validation to prevent NoSQL injection
     * and ensure only valid settings keys/values are accepted.
     */
    @PatchMapping("/user")
    public ResponseEntity<ApiResponse<UserSettingsDTO>> updateUserSettings(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantIdHeader,
            @RequestBody @Valid UpdateUserSettingsRequest request) {

        String tenantId = resolveTenantId(jwt, tenantIdHeader);
        String userId = getUserId(jwt);

        log.info("Updating user settings for tenant: {}, user: {}", tenantId, userId);

        // SECURITY: Convert typed DTO to map - validation already done by @Valid
        @SuppressWarnings("unchecked")
        Map<String, Object> settings = objectMapper.convertValue(request, Map.class);
        // Remove null values to support partial updates
        settings.values().removeIf(java.util.Objects::isNull);

        UserSettingsDTO updated = userSettingsService.updateUserSettings(tenantId, userId, settings);

        return ResponseEntity.ok(ApiResponse.success(updated, "User settings updated"));
    }

    /**
     * Reset user settings to defaults.
     */
    @PostMapping("/user/reset")
    public ResponseEntity<ApiResponse<UserSettingsDTO>> resetUserSettings(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantIdHeader) {

        String tenantId = resolveTenantId(jwt, tenantIdHeader);
        String userId = getUserId(jwt);

        log.info("Resetting user settings for tenant: {}, user: {}", tenantId, userId);

        UserSettingsDTO settings = userSettingsService.resetUserSettings(tenantId, userId);

        return ResponseEntity.ok(ApiResponse.success(settings, "User settings reset to defaults"));
    }

    // ==================== Tenant Settings ====================

    /**
     * Get tenant settings.
     */
    @GetMapping("/tenant")
    public ResponseEntity<ApiResponse<TenantSettingsDTO>> getTenantSettings(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantIdHeader) {

        String tenantId = resolveTenantId(jwt, tenantIdHeader);

        log.debug("Getting tenant settings for tenant: {}", tenantId);

        TenantSettingsDTO settings = tenantSettingsService.getTenantSettings(tenantId);

        return ResponseEntity.ok(ApiResponse.success(settings, "Tenant settings retrieved"));
    }

    /**
     * Update tenant settings (requires admin role).
     *
     * SECURITY: Uses typed DTO with validation to prevent NoSQL injection
     * and ensure only valid settings keys/values are accepted.
     */
    @PatchMapping("/tenant")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<TenantSettingsDTO>> updateTenantSettings(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantIdHeader,
            @RequestBody @Valid UpdateTenantSettingsRequest request) {

        String tenantId = resolveTenantId(jwt, tenantIdHeader);

        log.info("Updating tenant settings for tenant: {} by user: {}", tenantId, getUserId(jwt));

        // SECURITY: Convert typed DTO to map - validation already done by @Valid
        @SuppressWarnings("unchecked")
        Map<String, Object> settings = objectMapper.convertValue(request, Map.class);
        // Remove null values to support partial updates
        settings.values().removeIf(java.util.Objects::isNull);

        TenantSettingsDTO updated = tenantSettingsService.updateTenantSettings(tenantId, settings);

        return ResponseEntity.ok(ApiResponse.success(updated, "Tenant settings updated"));
    }

    /**
     * Reset tenant settings to defaults (requires admin role).
     */
    @PostMapping("/tenant/reset")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<TenantSettingsDTO>> resetTenantSettings(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantIdHeader) {

        String tenantId = resolveTenantId(jwt, tenantIdHeader);

        log.info("Resetting tenant settings for tenant: {} by user: {}", tenantId, getUserId(jwt));

        TenantSettingsDTO settings = tenantSettingsService.resetTenantSettings(tenantId);

        return ResponseEntity.ok(ApiResponse.success(settings, "Tenant settings reset to defaults"));
    }

    /**
     * Check if a feature is enabled for the tenant.
     */
    /**
     * SECURITY FIX (Round 14 #H18): Added path variable validation.
     */
    @GetMapping("/tenant/features/{feature}")
    public ResponseEntity<ApiResponse<Boolean>> isFeatureEnabled(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantIdHeader,
            @PathVariable
            @NotBlank
            @Pattern(regexp = "^[a-zA-Z0-9_-]{1,64}$", message = "Invalid feature name format")
            String feature) {

        String tenantId = resolveTenantId(jwt, tenantIdHeader);

        boolean enabled = tenantSettingsService.isFeatureEnabled(tenantId, feature);

        return ResponseEntity.ok(ApiResponse.success(enabled, "Feature status retrieved"));
    }

    // ==================== Drive Settings ====================

    /**
     * Get drive settings for the current user.
     */
    /**
     * SECURITY FIX (Round 14 #H18): Added path variable validation.
     */
    @GetMapping("/drive/{driveId}")
    public ResponseEntity<ApiResponse<DriveSettingsDTO>> getDriveSettings(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantIdHeader,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid drive ID format")
            String driveId) {

        String tenantId = resolveTenantId(jwt, tenantIdHeader);
        String userId = getUserId(jwt);

        log.debug("Getting drive settings for tenant: {}, user: {}, drive: {}", tenantId, userId, driveId);

        DriveSettingsDTO settings = driveSettingsService.getDriveSettings(tenantId, userId, driveId);

        return ResponseEntity.ok(ApiResponse.success(settings, "Drive settings retrieved"));
    }

    /**
     * Get default drive settings (without specific drive ID).
     */
    @GetMapping("/drive")
    public ResponseEntity<ApiResponse<DriveSettingsDTO>> getDefaultDriveSettings(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantIdHeader,
            @RequestHeader(value = "X-Drive-ID", required = false) String driveIdHeader) {

        String tenantId = resolveTenantId(jwt, tenantIdHeader);
        String userId = getUserId(jwt);
        String driveId = driveIdHeader != null ? driveIdHeader : "default";

        DriveSettingsDTO settings = driveSettingsService.getDriveSettings(tenantId, userId, driveId);

        return ResponseEntity.ok(ApiResponse.success(settings, "Drive settings retrieved"));
    }

    /**
     * Get all drive settings for the current user with cursor-based pagination.
     *
     * SECURITY FIX (Round 14 #H18): Added cursor and limit validation.
     *
     * @param cursor the cursor for pagination (null for first page)
     * @param limit the page size (default 50, max 100)
     */
    @GetMapping("/drives")
    public ResponseEntity<ApiResponse<CursorPage<DriveSettingsDTO>>> getAllDriveSettings(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantIdHeader,
            @RequestParam(required = false)
            @Size(max = 256, message = "Cursor must not exceed 256 characters")
            String cursor,
            @RequestParam(required = false, defaultValue = "50")
            @Min(value = 1, message = "Limit must be at least 1")
            @Max(value = 100, message = "Limit must not exceed 100")
            Integer limit) {

        String tenantId = resolveTenantId(jwt, tenantIdHeader);
        String userId = getUserId(jwt);

        // Enforce limits even if validation is bypassed
        int safeLimit = Math.max(1, Math.min(limit != null ? limit : 50, 100));
        CursorPage<DriveSettingsDTO> settings = driveSettingsService.getDriveSettingsPaginated(tenantId, userId, cursor, safeLimit);

        return ResponseEntity.ok(ApiResponse.success(settings, "Drive settings retrieved"));
    }

    /**
     * Update drive settings.
     *
     * SECURITY: Uses typed DTO with validation to prevent NoSQL injection
     * and ensure only valid settings keys/values are accepted.
     *
     * SECURITY FIX (Round 14 #H18): Added path variable validation.
     */
    @PatchMapping("/drive/{driveId}")
    public ResponseEntity<ApiResponse<DriveSettingsDTO>> updateDriveSettings(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantIdHeader,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid drive ID format")
            String driveId,
            @RequestBody @Valid UpdateDriveSettingsRequest request) {

        String tenantId = resolveTenantId(jwt, tenantIdHeader);
        String userId = getUserId(jwt);

        log.info("Updating drive settings for tenant: {}, user: {}, drive: {}", tenantId, userId, driveId);

        // SECURITY: Convert typed DTO to map - validation already done by @Valid
        @SuppressWarnings("unchecked")
        Map<String, Object> settings = objectMapper.convertValue(request, Map.class);
        // Remove null values to support partial updates
        settings.values().removeIf(java.util.Objects::isNull);

        DriveSettingsDTO updated = driveSettingsService.updateDriveSettings(tenantId, userId, driveId, settings);

        return ResponseEntity.ok(ApiResponse.success(updated, "Drive settings updated"));
    }

    /**
     * Pin a folder.
     *
     * SECURITY FIX (Round 14 #H18): Added path variable validation.
     */
    @PostMapping("/drive/{driveId}/pin/{folderId}")
    public ResponseEntity<ApiResponse<DriveSettingsDTO>> pinFolder(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantIdHeader,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid drive ID format")
            String driveId,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid folder ID format")
            String folderId) {

        String tenantId = resolveTenantId(jwt, tenantIdHeader);
        String userId = getUserId(jwt);

        DriveSettingsDTO settings = driveSettingsService.pinFolder(tenantId, userId, driveId, folderId);

        return ResponseEntity.ok(ApiResponse.success(settings, "Folder pinned"));
    }

    /**
     * Unpin a folder.
     *
     * SECURITY FIX (Round 14 #H18): Added path variable validation.
     */
    @DeleteMapping("/drive/{driveId}/pin/{folderId}")
    public ResponseEntity<ApiResponse<DriveSettingsDTO>> unpinFolder(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantIdHeader,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid drive ID format")
            String driveId,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid folder ID format")
            String folderId) {

        String tenantId = resolveTenantId(jwt, tenantIdHeader);
        String userId = getUserId(jwt);

        DriveSettingsDTO settings = driveSettingsService.unpinFolder(tenantId, userId, driveId, folderId);

        return ResponseEntity.ok(ApiResponse.success(settings, "Folder unpinned"));
    }

    /**
     * Favorite a document.
     *
     * SECURITY FIX (Round 14 #H18): Added path variable validation.
     */
    @PostMapping("/drive/{driveId}/favorite/{documentId}")
    public ResponseEntity<ApiResponse<DriveSettingsDTO>> favoriteDocument(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantIdHeader,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid drive ID format")
            String driveId,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid document ID format")
            String documentId) {

        String tenantId = resolveTenantId(jwt, tenantIdHeader);
        String userId = getUserId(jwt);

        DriveSettingsDTO settings = driveSettingsService.favoriteDocument(tenantId, userId, driveId, documentId);

        return ResponseEntity.ok(ApiResponse.success(settings, "Document added to favorites"));
    }

    /**
     * Unfavorite a document.
     *
     * SECURITY FIX (Round 14 #H18): Added path variable validation.
     */
    @DeleteMapping("/drive/{driveId}/favorite/{documentId}")
    public ResponseEntity<ApiResponse<DriveSettingsDTO>> unfavoriteDocument(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantIdHeader,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid drive ID format")
            String driveId,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid document ID format")
            String documentId) {

        String tenantId = resolveTenantId(jwt, tenantIdHeader);
        String userId = getUserId(jwt);

        DriveSettingsDTO settings = driveSettingsService.unfavoriteDocument(tenantId, userId, driveId, documentId);

        return ResponseEntity.ok(ApiResponse.success(settings, "Document removed from favorites"));
    }

    /**
     * Reset drive settings to defaults.
     *
     * SECURITY FIX (Round 14 #H18): Added path variable validation.
     */
    @PostMapping("/drive/{driveId}/reset")
    public ResponseEntity<ApiResponse<DriveSettingsDTO>> resetDriveSettings(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantIdHeader,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid drive ID format")
            String driveId) {

        String tenantId = resolveTenantId(jwt, tenantIdHeader);
        String userId = getUserId(jwt);

        DriveSettingsDTO settings = driveSettingsService.resetDriveSettings(tenantId, userId, driveId);

        return ResponseEntity.ok(ApiResponse.success(settings, "Drive settings reset to defaults"));
    }

    // ==================== Helper Methods ====================

    /**
     * Resolve tenant ID from multiple sources.
     * Priority: header > JWT claim > TenantContext
     *
     * SECURITY FIX (Round 6): Validate that header tenant ID matches JWT claim if both present.
     * This prevents privilege escalation where an attacker could set X-Tenant-ID header
     * to access another tenant's settings while authenticated to their own tenant.
     *
     * @throws IllegalArgumentException if tenant ID cannot be resolved
     * @throws AccessDeniedException if header tenant ID doesn't match JWT tenant ID
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
        // Previously, the check was only done if JWT had a tenant claim, which allowed
        // bypass when JWT lacked the claim.
        if (headerTenantId != null && !headerTenantId.isBlank()) {
            String headerValue = headerTenantId.trim();

            // SECURITY FIX (Round 8): Always validate header against JWT when both present
            // Even if JWT doesn't have explicit tenantId claim, we should be cautious
            if (jwtTenantId != null && !jwtTenantId.isBlank()) {
                if (!jwtTenantId.equals(headerValue)) {
                    log.warn("SECURITY: Tenant ID mismatch - header: {}, JWT: {}", headerValue, jwtTenantId);
                    throw new com.teamsync.common.exception.AccessDeniedException(
                        "X-Tenant-ID header does not match authenticated tenant");
                }
                // Both present and match - use the validated value
                return headerValue;
            }

            // SECURITY FIX (Round 8): Log when header is used without JWT validation
            // This could indicate a configuration issue or potential attack
            log.warn("SECURITY: Using X-Tenant-ID header '{}' without JWT tenant claim validation. " +
                     "Ensure JWT always contains tenantId claim in production.", headerValue);
            return headerValue;
        }

        // Fall back to JWT claim
        if (jwtTenantId != null && !jwtTenantId.isBlank()) {
            return jwtTenantId;
        }

        // SECURITY FIX (Round 8): Removed TenantContext fallback
        // ThreadLocal values can contain stale data from previous requests,
        // leading to cross-tenant data access. Only trust JWT or validated header.
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
     * Priority: sub > userId > preferred_username
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
        // SECURITY: Do not fall back to "anonymous" - user identification is mandatory
        throw new IllegalArgumentException(
            "User ID could not be determined from JWT. Ensure token contains sub, userId, or preferred_username claim.");
    }
}
