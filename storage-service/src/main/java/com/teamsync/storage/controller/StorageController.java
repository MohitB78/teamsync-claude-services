package com.teamsync.storage.controller;

import com.teamsync.common.dto.ApiResponse;
import com.teamsync.common.storage.StorageTier;
import com.teamsync.storage.dto.*;
import com.teamsync.storage.service.StorageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.text.Normalizer;
import java.util.Map;
import java.util.Set;

/**
 * Storage Service Controller - Internal service for file storage operations.
 *
 * This service is NOT exposed publicly. All public file downloads go through
 * Content Service which handles authentication and streams files via this service.
 *
 * SECURITY NOTE: This is an internal service accessible only within the Railway network.
 * Authentication is enforced at the API Gateway level for external requests.
 * Service-to-service calls (content-service → storage-service) are trusted via network isolation.
 *
 * The class-level @PreAuthorize was removed because:
 * 1. Content-service can't easily forward JWT tokens for service-to-service calls
 * 2. Network policies ensure only authorized services can reach this endpoint
 * 3. Authorization for specific operations (quota, etc.) is still enforced at method level
 */
@RestController
@RequestMapping("/api/storage")
@RequiredArgsConstructor
@Validated
@Slf4j
public class StorageController {

    /**
     * SECURITY FIX (Round 12): Valid ID pattern for path variables.
     */
    private static final String VALID_ID_PATTERN = "^[a-zA-Z0-9-]{1,64}$";

    /**
     * SECURITY FIX (Round 13 #1): Allowed bucket names - whitelist approach.
     * Only allow access to known, legitimate buckets to prevent bucket enumeration attacks.
     */
    private static final Set<String> ALLOWED_BUCKETS = Set.of(
            "teamsync-documents",
            "teamsync-thumbnails",
            "teamsync-temp",
            "teamsync-exports"
    );

    /**
     * SECURITY FIX (Round 13 #2): Pattern for valid storage keys.
     * Format: tenantId/driveId/timestamp/uuid_filename
     * This prevents path traversal and restricts keys to expected format.
     */
    private static final java.util.regex.Pattern VALID_STORAGE_KEY_PATTERN =
            java.util.regex.Pattern.compile("^[a-zA-Z0-9-]+/[a-zA-Z0-9-]+/[0-9]{8}/[a-zA-Z0-9-]+_[a-zA-Z0-9._-]+$");

    /**
     * SECURITY FIX (Round 13 #3): Header name for internal service calls.
     * Content Service must provide this header to access internal storage endpoints.
     */
    private static final String INTERNAL_SERVICE_HEADER = "X-Internal-Service";

    private final StorageService storageService;

    @Value("${teamsync.storage.default-bucket:teamsync-documents}")
    private String defaultBucket;

    /**
     * Initialize upload session
     */
    @PostMapping("/upload/init")
    public ResponseEntity<ApiResponse<UploadInitResponse>> initializeUpload(
            @Valid @RequestBody UploadInitRequest request) {

        log.info("POST /api/storage/upload/init - filename: {}, size: {}",
                request.getFilename(), request.getFileSize());

        UploadInitResponse response = storageService.initializeUpload(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<UploadInitResponse>builder()
                        .success(true)
                        .data(response)
                        .message("Upload session initialized")
                        .build());
    }

    /**
     * Complete upload session
     */
    @PostMapping("/upload/complete")
    public ResponseEntity<ApiResponse<UploadCompleteResponse>> completeUpload(
            @Valid @RequestBody UploadCompleteRequest request) {

        log.info("POST /api/storage/upload/complete - sessionId: {}", request.getSessionId());

        UploadCompleteResponse response = storageService.completeUpload(request);

        return ResponseEntity.ok(ApiResponse.<UploadCompleteResponse>builder()
                .success(true)
                .data(response)
                .message("Upload completed successfully")
                .build());
    }

    /**
     * Cancel upload session.
     * SECURITY FIX (Round 15 #H23): Added path variable validation.
     */
    @PostMapping("/upload/{sessionId}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelUpload(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid session ID format")
            String sessionId) {
        log.info("POST /api/storage/upload/{}/cancel", sessionId);

        storageService.cancelUpload(sessionId);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Upload cancelled")
                .build());
    }

    /**
     * Get upload session status.
     * Used for resumable uploads - returns which parts have been completed.
     * SECURITY FIX (Round 15 #H24): Added path variable validation.
     */
    @GetMapping("/upload/{sessionId}/status")
    public ResponseEntity<ApiResponse<UploadStatusResponse>> getUploadStatus(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid session ID format")
            String sessionId) {

        log.debug("GET /api/storage/upload/{}/status", sessionId);

        UploadStatusResponse response = storageService.getUploadStatus(sessionId);

        return ResponseEntity.ok(ApiResponse.<UploadStatusResponse>builder()
                .success(true)
                .data(response)
                .message("Upload status retrieved")
                .build());
    }

    /**
     * SECURITY FIX (Round 14 #H7): Allowed content types for upload.
     * Whitelist approach prevents upload of dangerous file types.
     */
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            // Documents
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.oasis.opendocument.text",
            "application/vnd.oasis.opendocument.spreadsheet",
            "application/vnd.oasis.opendocument.presentation",
            // Text
            "text/plain",
            "text/csv",
            "text/markdown",
            "application/json",
            "application/xml",
            // Images
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp",
            "image/svg+xml",
            // Archives (non-executable)
            "application/zip",
            "application/gzip",
            // Audio/Video
            "audio/mpeg",
            "audio/wav",
            "video/mp4",
            "video/webm"
    );

    /**
     * Direct upload endpoint - streams file to MinIO.
     * Called by Content Service for small file uploads.
     * No presigned URLs - file streams directly through backend to storage.
     *
     * SECURITY FIX (Round 14 #H7): Added content type validation against whitelist.
     */
    @PostMapping(value = "/upload/direct", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DirectUploadResponse>> uploadDirect(
            @RequestParam("file") MultipartFile file,
            @RequestParam String contentType) {

        // SECURITY FIX (Round 14 #H7): Validate content type against whitelist
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("Content type is required");
        }
        String normalizedContentType = contentType.toLowerCase().split(";")[0].trim();
        if (!ALLOWED_CONTENT_TYPES.contains(normalizedContentType)) {
            log.warn("SECURITY: Attempted upload with disallowed content type: {}", contentType);
            throw new SecurityException("File type not allowed: " + normalizedContentType);
        }

        log.info("POST /api/storage/upload/direct - filename: {}, size: {}, contentType: {}",
                file.getOriginalFilename(), file.getSize(), contentType);

        DirectUploadResponse response = storageService.uploadFileDirect(file, contentType);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<DirectUploadResponse>builder()
                        .success(true)
                        .data(response)
                        .message("File uploaded successfully")
                        .build());
    }

    /**
     * Generate download URL.
     *
     * SECURITY FIX (Round 14 #H1, #H2, #H4): Added input validation for bucket, storageKey,
     * and expirySeconds parameters to prevent bucket enumeration, path traversal, and
     * abuse via extreme expiry values.
     */
    @GetMapping("/download-url")
    public ResponseEntity<ApiResponse<DownloadUrlResponse>> getDownloadUrl(
            @RequestParam String bucket,
            @RequestParam String storageKey,
            @RequestParam(required = false) String filename,
            @RequestParam(defaultValue = "3600") int expirySeconds) {

        log.debug("GET /api/storage/download-url - bucket: {}, key: {}", bucket, storageKey);

        // SECURITY FIX (Round 14 #H1): Validate bucket against whitelist
        validateBucket(bucket);

        // SECURITY FIX (Round 14 #H2): Validate storage key format
        validateStorageKey(storageKey);

        // SECURITY FIX (Round 14 #H4): Enforce expiry bounds (minimum 60 seconds, maximum 24 hours)
        if (expirySeconds < 60) {
            expirySeconds = 60;
        } else if (expirySeconds > 86400) {
            expirySeconds = 86400;
        }

        DownloadUrlResponse response = storageService.generateDownloadUrl(
                bucket, storageKey, filename, expirySeconds);

        return ResponseEntity.ok(ApiResponse.<DownloadUrlResponse>builder()
                .success(true)
                .data(response)
                .message("Download URL generated")
                .build());
    }

    /**
     * Stream file download directly from storage.
     * This is an INTERNAL endpoint - requires proper headers from API Gateway.
     * Public downloads should go through Content Service's /api/documents/download endpoint.
     *
     * SECURITY FIX (Round 13 #4): Added internal service validation.
     * - Validates X-Internal-Service header to ensure request came from Content Service
     * - Validates bucket against whitelist to prevent bucket enumeration
     * - Validates storage key format to prevent path traversal
     */
    @GetMapping("/download")
    public ResponseEntity<InputStreamResource> downloadFile(
            @RequestParam String bucket,
            @RequestParam String storageKey,
            @RequestParam(required = false) String filename,
            HttpServletRequest request) {

        // SECURITY FIX (Round 13 #4): Validate internal service header
        String internalService = request.getHeader(INTERNAL_SERVICE_HEADER);
        if (internalService == null || !internalService.equals("content-service")) {
            log.warn("SECURITY: Download request rejected - missing or invalid {} header from {}",
                    INTERNAL_SERVICE_HEADER, request.getRemoteAddr());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // SECURITY FIX (Round 13 #5): Validate bucket against whitelist
        validateBucket(bucket);

        // SECURITY FIX (Round 13 #6): Validate storage key format
        validateStorageKey(storageKey);

        log.info("GET /api/storage/download - bucket: {}, key: {} (internal service: {})",
                bucket, storageKey, internalService);

        StorageService.FileDownload download = storageService.downloadFile(bucket, storageKey);

        // SECURITY FIX (Round 13 #7): Sanitize filename for Content-Disposition header
        String downloadFilename = filename;
        if (downloadFilename == null || downloadFilename.isBlank()) {
            downloadFilename = storageKey.contains("/")
                    ? storageKey.substring(storageKey.lastIndexOf('/') + 1)
                    : storageKey;
        }
        downloadFilename = sanitizeFilename(downloadFilename);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(download.contentType()));
        headers.setContentLength(download.fileSize());
        headers.setContentDispositionFormData("attachment", downloadFilename);
        headers.setCacheControl("private, max-age=3600");

        return ResponseEntity.ok()
                .headers(headers)
                .body(new InputStreamResource(download.inputStream()));
    }

    /**
     * Delete file from storage.
     *
     * SECURITY FIX (Round 14 #H5): Added input validation for bucket and storageKey
     * to prevent bucket enumeration and path traversal attacks.
     */
    @DeleteMapping("/files")
    public ResponseEntity<ApiResponse<Void>> deleteFile(
            @RequestParam String bucket,
            @RequestParam String storageKey) {

        // SECURITY FIX (Round 14 #H5): Validate inputs before processing
        validateBucket(bucket);
        validateStorageKey(storageKey);

        log.info("DELETE /api/storage/files - bucket: {}, key: {}", bucket, storageKey);

        storageService.deleteFile(bucket, storageKey);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("File deleted successfully")
                .build());
    }

    /**
     * Copy file.
     * SECURITY FIX (Round 6): Uses typed DTO with validation instead of Map<String, String>
     * to prevent mass assignment and ensure proper input validation.
     */
    @PostMapping("/files/copy")
    public ResponseEntity<ApiResponse<Map<String, String>>> copyFile(
            @Valid @RequestBody FileCopyRequest request) {

        log.info("POST /api/storage/files/copy - from {}/{} to {}/{}",
                request.getSourceBucket(), request.getSourceKey(),
                request.getDestBucket(), request.getDestKey());

        String newKey = storageService.copyFile(
                request.getSourceBucket(),
                request.getSourceKey(),
                request.getDestBucket(),
                request.getDestKey());

        return ResponseEntity.ok(ApiResponse.<Map<String, String>>builder()
                .success(true)
                .data(Map.of("storageKey", newKey, "bucket", request.getDestBucket()))
                .message("File copied successfully")
                .build());
    }

    /**
     * Change storage tier.
     *
     * SECURITY FIX (Round 14 #H6): Added input validation for bucket, storageKey,
     * and tier parameters. Added exception handling for invalid tier values.
     */
    @PostMapping("/files/tier")
    public ResponseEntity<ApiResponse<Void>> changeStorageTier(
            @RequestParam String bucket,
            @RequestParam String storageKey,
            @RequestParam String tier) {

        // SECURITY FIX (Round 14 #H6): Validate inputs before processing
        validateBucket(bucket);
        validateStorageKey(storageKey);

        // SECURITY FIX (Round 14 #H6): Validate tier value with proper error handling
        StorageTier storageTier;
        try {
            storageTier = StorageTier.valueOf(tier.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("SECURITY: Invalid storage tier requested: {}", tier);
            throw new IllegalArgumentException("Invalid storage tier: " + tier +
                    ". Valid values: HOT, WARM, COLD, ARCHIVE");
        }

        log.info("POST /api/storage/files/tier - bucket: {}, key: {}, tier: {}",
                bucket, storageKey, tier);

        storageService.changeStorageTier(bucket, storageKey, storageTier);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Storage tier changed to " + tier)
                .build());
    }

    /**
     * Get storage quota for current user's drive.
     *
     * SECURITY FIX (Round 12): Added @PreAuthorize to ensure only authenticated users
     * can access quota information.
     */
    @GetMapping("/quota")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<StorageQuotaDTO>> getStorageQuota() {
        log.debug("GET /api/storage/quota");

        StorageQuotaDTO quota = storageService.getStorageQuota();

        return ResponseEntity.ok(ApiResponse.<StorageQuotaDTO>builder()
                .success(true)
                .data(quota)
                .message("Storage quota retrieved")
                .build());
    }

    /**
     * Get storage quota for a specific drive.
     *
     * SECURITY NOTE: This endpoint only requires authentication, not drive access.
     * Viewing storage quota is a low-sensitivity, read-only operation.
     * Team members can view team quota from the team dashboard.
     * The real security is on file operations (upload, download, delete).
     */
    @GetMapping("/quota/{driveId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<StorageQuotaDTO>> getStorageQuotaForDrive(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid drive ID format")
            String driveId) {

        log.debug("GET /api/storage/quota/{}", driveId);

        StorageQuotaDTO quota = storageService.getStorageQuotaForDrive(driveId);

        return ResponseEntity.ok(ApiResponse.<StorageQuotaDTO>builder()
                .success(true)
                .data(quota)
                .message("Storage quota retrieved")
                .build());
    }

    /**
     * Update storage quota (admin only).
     *
     * SECURITY FIX (Round 12): Added proper authorization and input validation.
     * - Only users with MANAGE_ROLES permission can update quota (tenant admins)
     * - Uses typed DTO with validation instead of raw Map
     * - Path variable validated against injection
     */
    @PutMapping("/quota/{driveId}")
    @PreAuthorize("isAuthenticated() and @driveAccessService.hasAccess(#driveId, 'MANAGE_ROLES')")
    public ResponseEntity<ApiResponse<StorageQuotaDTO>> updateStorageQuota(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid drive ID format")
            String driveId,
            @Valid @RequestBody UpdateQuotaRequest request) {

        log.info("PUT /api/storage/quota/{} - newQuota: {}", driveId, request.getQuotaLimit());

        StorageQuotaDTO quota = storageService.updateStorageQuota(driveId, request.getQuotaLimit());

        return ResponseEntity.ok(ApiResponse.<StorageQuotaDTO>builder()
                .success(true)
                .data(quota)
                .message("Storage quota updated")
                .build());
    }

    // ==================== SECURITY HELPER METHODS ====================

    /**
     * SECURITY FIX (Round 13 #8): Validate bucket name against whitelist.
     * Prevents bucket enumeration and unauthorized bucket access attacks.
     *
     * @param bucket The bucket name to validate
     * @throws SecurityException if bucket is not in the allowed list
     */
    private void validateBucket(String bucket) {
        if (bucket == null || bucket.isBlank()) {
            log.warn("SECURITY: Empty bucket name provided");
            throw new SecurityException("Bucket name is required");
        }

        // Also validate against the default bucket
        Set<String> effectiveAllowed = new java.util.HashSet<>(ALLOWED_BUCKETS);
        if (defaultBucket != null && !defaultBucket.isBlank()) {
            effectiveAllowed.add(defaultBucket);
        }

        if (!effectiveAllowed.contains(bucket)) {
            log.warn("SECURITY: Attempt to access disallowed bucket: {}", bucket);
            throw new SecurityException("Invalid bucket: " + bucket);
        }
    }

    /**
     * SECURITY FIX (Round 13 #9): Validate storage key format.
     * Prevents path traversal attacks by ensuring key matches expected pattern.
     *
     * SECURITY FIX (Round 14 #C23): Added Unicode normalization to prevent
     * Unicode homoglyph attacks and normalization bypasses (e.g., using
     * Unicode equivalents of ".." or "/" characters).
     *
     * @param storageKey The storage key to validate
     * @throws SecurityException if key format is invalid or contains path traversal
     */
    private void validateStorageKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            log.warn("SECURITY: Empty storage key provided");
            throw new SecurityException("Storage key is required");
        }

        // SECURITY FIX (Round 14 #C23): Normalize Unicode to NFC form before validation
        // This prevents bypasses using Unicode normalization tricks like:
        // - Using full-width characters (e.g., ／ U+FF0F instead of /)
        // - Using combining characters that resolve to path separators
        // - Using homoglyphs that look like ASCII but are different codepoints
        String normalizedKey = Normalizer.normalize(storageKey, Normalizer.Form.NFC);

        // SECURITY FIX (Round 14 #C23): Check if normalization changed the string
        // If it did, the original contained suspicious Unicode that should be rejected
        if (!normalizedKey.equals(storageKey)) {
            log.warn("SECURITY: Storage key contains non-normalized Unicode characters");
            throw new SecurityException("Invalid storage key: contains non-standard characters");
        }

        // SECURITY FIX (Round 14 #C23): Check for ASCII-only after normalization
        // Storage keys should only contain ASCII characters
        if (!storageKey.chars().allMatch(c -> c < 128)) {
            log.warn("SECURITY: Storage key contains non-ASCII characters");
            throw new SecurityException("Invalid storage key: non-ASCII characters not allowed");
        }

        // Check for path traversal attempts
        if (storageKey.contains("..") || storageKey.contains("//") ||
            storageKey.contains("\\") || storageKey.startsWith("/")) {
            log.warn("SECURITY: Path traversal attempt detected in storage key: {}", storageKey);
            throw new SecurityException("Invalid storage key: path traversal detected");
        }

        // Validate against expected format pattern
        if (!VALID_STORAGE_KEY_PATTERN.matcher(storageKey).matches()) {
            log.warn("SECURITY: Storage key does not match expected format: {}", storageKey);
            throw new SecurityException("Invalid storage key format");
        }
    }

    /**
     * SECURITY FIX (Round 13 #10): Sanitize filename for Content-Disposition header.
     * Prevents header injection attacks by removing/encoding special characters.
     *
     * @param filename The filename to sanitize
     * @return Sanitized filename safe for use in headers
     */
    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "download";
        }

        // Remove path separators and null bytes
        String sanitized = filename
                .replace("/", "_")
                .replace("\\", "_")
                .replace("\0", "")
                .replace("\r", "")
                .replace("\n", "");

        // Remove any characters that could cause header injection
        sanitized = sanitized.replaceAll("[\"';]", "_");

        // Limit filename length to prevent buffer issues
        if (sanitized.length() > 255) {
            String extension = "";
            int lastDot = sanitized.lastIndexOf('.');
            if (lastDot > 0) {
                extension = sanitized.substring(lastDot);
                if (extension.length() > 10) {
                    extension = extension.substring(0, 10);
                }
            }
            sanitized = sanitized.substring(0, 255 - extension.length()) + extension;
        }

        // Ensure we have something valid
        if (sanitized.isBlank()) {
            return "download";
        }

        return sanitized;
    }
}
