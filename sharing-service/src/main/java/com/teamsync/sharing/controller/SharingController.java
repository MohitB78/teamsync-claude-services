package com.teamsync.sharing.controller;

import com.teamsync.common.dto.ApiResponse;
import com.teamsync.common.dto.CursorPage;
import com.teamsync.common.model.Permission;
import com.teamsync.common.permission.RequiresPermission;
import com.teamsync.sharing.dto.*;
import com.teamsync.sharing.service.SharingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for sharing operations.
 *
 * SECURITY FIX (Round 7): Added @Valid annotations and controller-level authorization
 * to ensure defense-in-depth security model. Added pagination to list endpoints to
 * prevent memory exhaustion DoS attacks.
 */
/**
 * SECURITY FIX (Round 15 #H10): Added class-level @PreAuthorize for defense-in-depth.
 */
@RestController
@RequestMapping("/api/sharing")
@RequiredArgsConstructor
@Validated
@Slf4j
@PreAuthorize("isAuthenticated()")
public class SharingController {

    private final SharingService sharingService;

    /**
     * Maximum number of shares returned per page.
     * SECURITY (Round 7): Prevents memory exhaustion from unbounded queries.
     */
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 50;

    /**
     * Maximum number of team IDs that can be specified in a single request.
     * SECURITY (Round 7): Prevents DoS via excessive list parameters.
     */
    private static final int MAX_TEAM_IDS = 50;

    /**
     * SECURITY FIX (Round 13 #37): Valid ID pattern for path variables.
     * Prevents injection attacks via malicious IDs.
     */
    private static final String VALID_ID_PATTERN = "^[a-zA-Z0-9-]{1,64}$";

    /**
     * SECURITY FIX (Round 13 #38): Valid token pattern for public link tokens.
     * Tokens should be URL-safe Base64 characters only.
     */
    private static final String VALID_TOKEN_PATTERN = "^[a-zA-Z0-9_-]{20,64}$";

    // ========== SHARES ==========

    /**
     * Create a share
     */
    @PostMapping("/shares")
    public ResponseEntity<ApiResponse<ShareDTO>> createShare(
            @Valid @RequestBody CreateShareRequest request) {

        log.info("POST /api/sharing/shares - resource: {}", request.getResourceId());

        ShareDTO share = sharingService.createShare(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<ShareDTO>builder()
                        .success(true)
                        .data(share)
                        .message("Share created successfully")
                        .build());
    }

    /**
     * Get shares for a resource
     *
     * SECURITY FIX (Round 13 #39): Added path variable validation.
     */
    @GetMapping("/shares/resource/{resourceId}")
    public ResponseEntity<ApiResponse<List<ShareDTO>>> getSharesForResource(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid resource ID format")
            String resourceId) {

        log.debug("GET /api/sharing/shares/resource/{}", resourceId);

        List<ShareDTO> shares = sharingService.getSharesForResource(resourceId);

        return ResponseEntity.ok(ApiResponse.<List<ShareDTO>>builder()
                .success(true)
                .data(shares)
                .message("Shares retrieved successfully")
                .build());
    }

    /**
     * Get resources shared with current user.
     * SECURITY FIX (Round 7): Added cursor-based pagination and teamIds size limit.
     */
    @GetMapping("/shares/shared-with-me")
    public ResponseEntity<ApiResponse<CursorPage<ShareDTO>>> getSharedWithMe(
            @RequestParam(required = false)
            @Size(max = MAX_TEAM_IDS, message = "Cannot specify more than " + MAX_TEAM_IDS + " team IDs")
            List<String> teamIds,
            @RequestParam(required = false)
            @Size(max = 64, message = "Department ID must not exceed 64 characters")
            String departmentId,
            @RequestParam(required = false)
            @Size(max = 256, message = "Cursor must not exceed 256 characters")
            String cursor,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE)
            @Min(value = 1, message = "Limit must be at least 1")
            @Max(value = MAX_PAGE_SIZE, message = "Limit cannot exceed " + MAX_PAGE_SIZE)
            int limit) {

        log.debug("GET /api/sharing/shares/shared-with-me - teamIds: {}, cursor: {}, limit: {}",
                teamIds != null ? teamIds.size() : 0, cursor, limit);

        // Enforce limit
        limit = Math.min(limit, MAX_PAGE_SIZE);

        CursorPage<ShareDTO> shares = sharingService.getSharedWithMePaginated(teamIds, departmentId, cursor, limit);

        return ResponseEntity.ok(ApiResponse.<CursorPage<ShareDTO>>builder()
                .success(true)
                .data(shares)
                .message("Shared items retrieved successfully")
                .build());
    }

    /**
     * Get resources shared by current user.
     * SECURITY FIX (Round 7): Added cursor-based pagination.
     */
    @GetMapping("/shares/shared-by-me")
    public ResponseEntity<ApiResponse<CursorPage<ShareDTO>>> getSharedByMe(
            @RequestParam(required = false)
            @Size(max = 256, message = "Cursor must not exceed 256 characters")
            String cursor,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE)
            @Min(value = 1, message = "Limit must be at least 1")
            @Max(value = MAX_PAGE_SIZE, message = "Limit cannot exceed " + MAX_PAGE_SIZE)
            int limit) {

        log.debug("GET /api/sharing/shares/shared-by-me - cursor: {}, limit: {}", cursor, limit);

        // Enforce limit
        limit = Math.min(limit, MAX_PAGE_SIZE);

        CursorPage<ShareDTO> shares = sharingService.getSharedByMePaginated(cursor, limit);

        return ResponseEntity.ok(ApiResponse.<CursorPage<ShareDTO>>builder()
                .success(true)
                .data(shares)
                .message("Shared items retrieved successfully")
                .build());
    }

    /**
     * Update a share.
     *
     * SECURITY FIX (Round 7): Added @Valid for input validation and @RequiresPermission
     * for controller-level authorization (defense-in-depth).
     */
    @PatchMapping("/shares/{shareId}")
    @RequiresPermission(Permission.SHARE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ShareDTO>> updateShare(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid share ID format")
            String shareId,
            @Valid @RequestBody UpdateShareRequest request) {

        log.info("PATCH /api/sharing/shares/{}", shareId);

        ShareDTO share = sharingService.updateShare(shareId, request);

        return ResponseEntity.ok(ApiResponse.<ShareDTO>builder()
                .success(true)
                .data(share)
                .message("Share updated successfully")
                .build());
    }

    /**
     * Delete a share.
     *
     * SECURITY FIX (Round 7): Added @RequiresPermission for controller-level
     * authorization (defense-in-depth).
     */
    @DeleteMapping("/shares/{shareId}")
    @RequiresPermission(Permission.SHARE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> deleteShare(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid share ID format")
            String shareId) {
        log.info("DELETE /api/sharing/shares/{}", shareId);

        sharingService.deleteShare(shareId);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Share deleted successfully")
                .build());
    }

    /**
     * Bulk create shares
     */
    @PostMapping("/bulk")
    public ResponseEntity<ApiResponse<List<ShareDTO>>> bulkShare(
            @Valid @RequestBody BulkShareRequest request) {

        log.info("POST /api/sharing/bulk - {} resources, {} targets",
                request.getResourceIds().size(), request.getShares().size());

        List<ShareDTO> shares = sharingService.bulkShare(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<List<ShareDTO>>builder()
                        .success(true)
                        .data(shares)
                        .message("Bulk share completed")
                        .build());
    }

    // ========== USER/TEAM SEARCH ==========

    /**
     * SECURITY FIX (Round 12): Added query validation pattern.
     * Valid search queries must:
     * - Be between 2 and 100 characters
     * - Contain only alphanumeric characters, spaces, hyphens, periods, and @ symbols
     * This prevents:
     * - ReDoS attacks via malicious regex patterns
     * - NoSQL injection via special characters
     * - User enumeration via timing differences
     */
    private static final java.util.regex.Pattern VALID_SEARCH_QUERY_PATTERN =
            java.util.regex.Pattern.compile("^[\\w\\s\\-\\.@]{2,100}$");

    /**
     * Search users for sharing
     *
     * SECURITY FIX (Round 12): Added @Size and @Pattern validation to prevent
     * injection attacks and ReDoS via malicious search queries.
     */
    @GetMapping("/users/search")
    public ResponseEntity<ApiResponse<List<UserSearchResult>>> searchUsersForSharing(
            @RequestParam
            @jakarta.validation.constraints.Size(min = 2, max = 100, message = "Query must be between 2 and 100 characters")
            String query) {

        // SECURITY FIX (Round 12): Validate query format
        if (!VALID_SEARCH_QUERY_PATTERN.matcher(query).matches()) {
            log.warn("SECURITY: Invalid search query format rejected");
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<List<UserSearchResult>>builder()
                            .success(false)
                            .error("Invalid search query format")
                            .code("INVALID_QUERY")
                            .build());
        }

        log.debug("GET /api/sharing/users/search - query length: {}", query.length());

        List<UserSearchResult> users = sharingService.searchUsersForSharing(query);

        return ResponseEntity.ok(ApiResponse.<List<UserSearchResult>>builder()
                .success(true)
                .data(users)
                .message("Users found")
                .build());
    }

    /**
     * Search teams for sharing
     *
     * SECURITY FIX (Round 12): Added @Size and @Pattern validation to prevent
     * injection attacks and ReDoS via malicious search queries.
     */
    @GetMapping("/teams/search")
    public ResponseEntity<ApiResponse<List<TeamSearchResult>>> searchTeamsForSharing(
            @RequestParam
            @jakarta.validation.constraints.Size(min = 2, max = 100, message = "Query must be between 2 and 100 characters")
            String query) {

        // SECURITY FIX (Round 12): Validate query format
        if (!VALID_SEARCH_QUERY_PATTERN.matcher(query).matches()) {
            log.warn("SECURITY: Invalid search query format rejected");
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<List<TeamSearchResult>>builder()
                            .success(false)
                            .error("Invalid search query format")
                            .code("INVALID_QUERY")
                            .build());
        }

        log.debug("GET /api/sharing/teams/search - query length: {}", query.length());

        List<TeamSearchResult> teams = sharingService.searchTeamsForSharing(query);

        return ResponseEntity.ok(ApiResponse.<List<TeamSearchResult>>builder()
                .success(true)
                .data(teams)
                .message("Teams found")
                .build());
    }

    // ========== PUBLIC LINKS ==========

    /**
     * Create a public link
     */
    @PostMapping("/links")
    public ResponseEntity<ApiResponse<PublicLinkDTO>> createPublicLink(
            @Valid @RequestBody CreatePublicLinkRequest request) {

        log.info("POST /api/sharing/links - resource: {}", request.getResourceId());

        PublicLinkDTO link = sharingService.createPublicLink(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<PublicLinkDTO>builder()
                        .success(true)
                        .data(link)
                        .message("Public link created successfully")
                        .build());
    }

    /**
     * Access a public link (no auth required - handled at gateway level)
     *
     * SECURITY FIX (Round 13 #40): Added token format validation.
     */
    @GetMapping("/links/access/{token}")
    public ResponseEntity<ApiResponse<PublicLinkDTO>> accessPublicLink(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_TOKEN_PATTERN, message = "Invalid link token format")
            String token,
            @RequestParam(required = false) String password) {

        log.debug("GET /api/sharing/links/access/{}", token);

        PublicLinkDTO link = sharingService.getPublicLink(token, password);

        return ResponseEntity.ok(ApiResponse.<PublicLinkDTO>builder()
                .success(true)
                .data(link)
                .message("Link accessed successfully")
                .build());
    }

    /**
     * Record a download for public link
     *
     * SECURITY FIX (Round 13 #41): Added token format validation.
     */
    @PostMapping("/links/access/{token}/download")
    public ResponseEntity<ApiResponse<Void>> recordDownload(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_TOKEN_PATTERN, message = "Invalid link token format")
            String token) {
        log.info("POST /api/sharing/links/access/{}/download", token);

        sharingService.recordDownload(token);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Download recorded")
                .build());
    }

    /**
     * Get public links for a resource
     *
     * SECURITY FIX (Round 13 #42): Added path variable validation.
     */
    @GetMapping("/links/resource/{resourceId}")
    public ResponseEntity<ApiResponse<List<PublicLinkDTO>>> getPublicLinksForResource(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid resource ID format")
            String resourceId) {

        log.debug("GET /api/sharing/links/resource/{}", resourceId);

        List<PublicLinkDTO> links = sharingService.getPublicLinksForResource(resourceId);

        return ResponseEntity.ok(ApiResponse.<List<PublicLinkDTO>>builder()
                .success(true)
                .data(links)
                .message("Public links retrieved successfully")
                .build());
    }

    /**
     * Disable a public link
     *
     * SECURITY FIX (Round 13 #43): Added path variable validation.
     */
    @PostMapping("/links/{linkId}/disable")
    public ResponseEntity<ApiResponse<Void>> disablePublicLink(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid link ID format")
            String linkId) {
        log.info("POST /api/sharing/links/{}/disable", linkId);

        sharingService.disablePublicLink(linkId);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Public link disabled")
                .build());
    }

    /**
     * Delete a public link
     *
     * SECURITY FIX (Round 13 #44): Added path variable validation.
     */
    @DeleteMapping("/links/{linkId}")
    public ResponseEntity<ApiResponse<Void>> deletePublicLink(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid link ID format")
            String linkId) {
        log.info("DELETE /api/sharing/links/{}", linkId);

        sharingService.deletePublicLink(linkId);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Public link deleted")
                .build());
    }

    // ========== ACCESS CHECK ==========

    /**
     * Check if current authenticated user has access to a resource.
     *
     * SECURITY FIX (Round 7): Added @Valid for input validation. User identity is now
     * extracted from the authenticated session headers, not from the request body.
     * Any userId, teamIds, or departmentId fields in the request are ignored.
     */
    @PostMapping("/access/check")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AccessCheckResponse>> checkAccess(
            @Valid @RequestBody AccessCheckRequest request,
            @RequestHeader(value = "X-Team-IDs", required = false) List<String> teamIds,
            @RequestHeader(value = "X-Department-ID", required = false) String departmentId) {

        log.debug("POST /api/sharing/access/check - resource: {}", request.getResourceId());

        AccessCheckResponse response = sharingService.checkAccess(request, teamIds, departmentId);

        return ResponseEntity.ok(ApiResponse.<AccessCheckResponse>builder()
                .success(true)
                .data(response)
                .build());
    }
}
