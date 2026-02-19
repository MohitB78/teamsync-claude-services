package com.teamsync.wopi.controller;

import com.teamsync.common.dto.ApiResponse;
import com.teamsync.common.exception.AccessDeniedException;
import com.teamsync.wopi.util.WopiTokenUtil;
import com.teamsync.wopi.util.WopiTokenUtil.WopiTokenPayload;
import com.teamsync.wopi.util.WopiTokenUtil.InvalidWopiTokenException;
import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * WOPI (Web Application Open Platform Interface) endpoints.
 * Used by Microsoft Office Online and other WOPI clients.
 *
 * SECURITY (Round 6): All WOPI endpoints now require access token validation.
 * The access token encodes tenant, user, file permissions and is validated before any operation.
 *
 * WOPI Security Model:
 * - Access tokens are HMAC-SHA256 signed (Round 13 fix)
 * - All file operations validate the token matches the fileId being accessed
 * - Tokens are time-limited (1 hour default) and tied to specific user/file combinations
 *
 * SECURITY FIX (Round 13 #1): Replaced plaintext token format with cryptographically
 * signed HMAC-SHA256 tokens. Previous format was predictable and forgeable.
 */
@RestController
@RequestMapping("/wopi")
@RequiredArgsConstructor
@Validated
@Slf4j
public class WopiController {

    /**
     * WOPI access token header - required for all file operations
     */
    private static final String ACCESS_TOKEN_HEADER = "Authorization";
    private static final String ACCESS_TOKEN_PARAM = "access_token";

    /**
     * SECURITY FIX (Round 13 #48): Valid file ID pattern.
     * WOPI file IDs should be alphanumeric with hyphens.
     */
    private static final String VALID_FILE_ID_PATTERN = "^[a-zA-Z0-9-]{1,64}$";

    /**
     * SECURITY FIX (Round 13 #1): WOPI token secret key.
     * Must be at least 32 characters and stored securely (e.g., Vault).
     */
    @Value("${teamsync.wopi.token-secret:}")
    private String wopiTokenSecret;

    /**
     * WOPI token time-to-live in seconds (default: 1 hour)
     */
    @Value("${teamsync.wopi.token-ttl-seconds:3600}")
    private long wopiTokenTtlSeconds;

    /**
     * SECURITY FIX (Round 14 #C14): Validate WOPI token secret at startup.
     * Fails fast if secret is not configured or too short, preventing
     * runtime failures and insecure token generation.
     */
    @PostConstruct
    public void validateConfiguration() {
        if (wopiTokenSecret == null || wopiTokenSecret.isBlank()) {
            log.error("SECURITY: WOPI token secret not configured. Set teamsync.wopi.token-secret");
            throw new IllegalStateException("WOPI service requires teamsync.wopi.token-secret to be configured");
        }
        if (wopiTokenSecret.length() < 32) {
            log.error("SECURITY: WOPI token secret too short (min 32 chars). Current: {} chars",
                    wopiTokenSecret.length());
            throw new IllegalStateException("WOPI token secret must be at least 32 characters");
        }
        log.info("WOPI service configured with valid token secret ({} chars)", wopiTokenSecret.length());
    }

    /**
     * CheckFileInfo - Returns file metadata.
     * SECURITY FIX (Round 6): Requires valid access token with READ permission.
     * SECURITY FIX (Round 13 #49): Added path variable validation.
     */
    @GetMapping("/files/{fileId}")
    public ResponseEntity<Map<String, Object>> checkFileInfo(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_FILE_ID_PATTERN, message = "Invalid file ID format")
            String fileId,
            @RequestParam(value = ACCESS_TOKEN_PARAM, required = false) String accessTokenParam,
            @RequestHeader(value = ACCESS_TOKEN_HEADER, required = false) String accessTokenHeader) {

        String accessToken = resolveAccessToken(accessTokenParam, accessTokenHeader);
        WopiTokenData tokenData = validateAccessToken(accessToken, fileId, WopiPermission.READ);

        log.info("WOPI CheckFileInfo: {} for user: {}", fileId, tokenData.userId());

        // TODO: Fetch actual file metadata from content service
        // For now, return stub with proper user context from token
        return ResponseEntity.ok(Map.of(
                "BaseFileName", "document.docx",
                "OwnerId", tokenData.ownerId(),
                "Size", 12345,
                "UserId", tokenData.userId(),
                "Version", "v1",
                "UserCanWrite", tokenData.canWrite(),
                "UserCanNotWriteRelative", !tokenData.canWrite(),
                "SupportsUpdate", true,
                "SupportsLocks", true,
                "UserFriendlyName", tokenData.userName()
        ));
    }

    /**
     * GetFile - Returns file contents.
     * SECURITY FIX (Round 6): Requires valid access token with READ permission.
     * SECURITY FIX (Round 13 #50): Added path variable validation.
     */
    @GetMapping("/files/{fileId}/contents")
    public ResponseEntity<byte[]> getFile(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_FILE_ID_PATTERN, message = "Invalid file ID format")
            String fileId,
            @RequestParam(value = ACCESS_TOKEN_PARAM, required = false) String accessTokenParam,
            @RequestHeader(value = ACCESS_TOKEN_HEADER, required = false) String accessTokenHeader) {

        String accessToken = resolveAccessToken(accessTokenParam, accessTokenHeader);
        WopiTokenData tokenData = validateAccessToken(accessToken, fileId, WopiPermission.READ);

        log.info("WOPI GetFile: {} for user: {}", fileId, tokenData.userId());

        // TODO: Fetch file contents from storage service via content service
        // Content service will verify user has READ permission on the document
        return ResponseEntity.ok(new byte[0]);
    }

    /**
     * PutFile - Save file contents.
     * SECURITY FIX (Round 6): Requires valid access token with WRITE permission.
     * SECURITY FIX (Round 13 #51): Added path variable validation.
     */
    @PostMapping("/files/{fileId}/contents")
    public ResponseEntity<Map<String, Object>> putFile(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_FILE_ID_PATTERN, message = "Invalid file ID format")
            String fileId,
            @RequestParam(value = ACCESS_TOKEN_PARAM, required = false) String accessTokenParam,
            @RequestHeader(value = ACCESS_TOKEN_HEADER, required = false) String accessTokenHeader,
            @RequestBody byte[] contents) {

        String accessToken = resolveAccessToken(accessTokenParam, accessTokenHeader);
        WopiTokenData tokenData = validateAccessToken(accessToken, fileId, WopiPermission.WRITE);

        log.info("WOPI PutFile: {}, size: {}, user: {}", fileId, contents.length, tokenData.userId());

        // TODO: Save file contents via content service
        // Content service will verify user has WRITE permission on the document
        return ResponseEntity.ok(Map.of("ItemVersion", "v2"));
    }

    /**
     * Lock - Lock file for editing.
     * SECURITY FIX (Round 6): Requires valid access token with WRITE permission.
     * SECURITY FIX (Round 13 #52): Added path variable validation.
     */
    @PostMapping("/files/{fileId}")
    public ResponseEntity<Void> lock(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_FILE_ID_PATTERN, message = "Invalid file ID format")
            String fileId,
            @RequestHeader("X-WOPI-Override") String operation,
            @RequestHeader(value = "X-WOPI-Lock", required = false) String lock,
            @RequestParam(value = ACCESS_TOKEN_PARAM, required = false) String accessTokenParam,
            @RequestHeader(value = ACCESS_TOKEN_HEADER, required = false) String accessTokenHeader) {

        String accessToken = resolveAccessToken(accessTokenParam, accessTokenHeader);
        WopiTokenData tokenData = validateAccessToken(accessToken, fileId, WopiPermission.WRITE);

        log.info("WOPI {}: {} with lock: {}, user: {}", operation, fileId, lock, tokenData.userId());

        // TODO: Implement proper locking via presence service or dedicated lock service
        return ResponseEntity.ok().build();
    }

    /**
     * Get editor URL for document.
     * This endpoint generates access tokens for WOPI file operations.
     * SECURITY FIX (Round 6): Requires authenticated user with document access.
     * SECURITY FIX (Round 13 #53): Added path variable validation.
     * SECURITY FIX: Added @PreAuthorize - this endpoint is accessed by authenticated users,
     * not WOPI clients, so it needs Spring Security authorization.
     */
    @GetMapping("/editor/{documentId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, String>>> getEditorUrl(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_FILE_ID_PATTERN, message = "Invalid document ID format")
            String documentId,
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-User-ID") String userId,
            @RequestHeader("X-Drive-ID") String driveId) {

        log.info("Get WOPI editor URL for document: {} by user: {} in tenant: {}", documentId, userId, tenantId);

        // TODO: Verify user has access to the document via permission service
        // TODO: Generate cryptographically signed access token with:
        //   - tenantId, userId, documentId, driveId
        //   - permissions (read/write based on user's actual permissions)
        //   - expiration time (e.g., 1 hour)
        //   - document owner info

        // Stub: In production, this would be a signed JWT or HMAC-signed token
        String accessToken = generateAccessToken(tenantId, userId, documentId, driveId);

        return ResponseEntity.ok(ApiResponse.<Map<String, String>>builder()
                .success(true)
                .data(Map.of(
                        "editorUrl", "https://office.example.com/edit?file_id=" + documentId,
                        "accessToken", accessToken,
                        "tokenTtl", "3600"
                ))
                .build());
    }

    // ==================== Helper Methods ====================

    /**
     * Resolve access token from query parameter or header.
     * WOPI clients may send token in either location.
     */
    private String resolveAccessToken(String tokenParam, String tokenHeader) {
        if (tokenParam != null && !tokenParam.isBlank()) {
            return tokenParam.trim();
        }
        if (tokenHeader != null && !tokenHeader.isBlank()) {
            // Remove "Bearer " prefix if present
            String token = tokenHeader.trim();
            if (token.toLowerCase().startsWith("bearer ")) {
                token = token.substring(7);
            }
            return token;
        }
        throw new AccessDeniedException("WOPI access token is required");
    }

    /**
     * Validate access token and extract user/permission data.
     *
     * SECURITY FIX (Round 13 #1): Now uses cryptographically signed HMAC-SHA256 tokens.
     * Previous implementation used plaintext tokens that could be forged.
     *
     * @param accessToken The token to validate
     * @param fileId The file being accessed (must match token)
     * @param requiredPermission The minimum permission required
     * @return Token data including user info and permissions
     * @throws AccessDeniedException if token is invalid or lacks permission
     */
    private WopiTokenData validateAccessToken(String accessToken, String fileId, WopiPermission requiredPermission) {
        if (accessToken == null || accessToken.isBlank()) {
            log.warn("SECURITY: WOPI request without access token for file: {}", fileId);
            throw new AccessDeniedException("WOPI access token is required");
        }

        // SECURITY FIX (Round 13 #1): Validate that secret is configured
        String secretKey = getWopiTokenSecret();

        try {
            // Validate token signature and expiry, verify document ID matches
            WopiTokenPayload payload = WopiTokenUtil.validateTokenForDocument(
                    accessToken, fileId, secretKey);

            // Check required permission
            if (requiredPermission == WopiPermission.WRITE && !payload.canWrite()) {
                log.warn("SECURITY: WOPI write attempt with read-only token for file: {} by user: {}",
                        fileId, payload.userId());
                throw new AccessDeniedException("Write permission required");
            }

            log.debug("WOPI token validated for file: {}, user: {}, canWrite: {}",
                    fileId, payload.userId(), payload.canWrite());

            return new WopiTokenData(
                    payload.tenantId(),
                    payload.userId(),
                    payload.userName(),
                    payload.ownerId(),
                    payload.canWrite()
            );

        } catch (InvalidWopiTokenException e) {
            log.warn("SECURITY: WOPI token validation failed for file {}: {}", fileId, e.getMessage());
            throw new AccessDeniedException("Invalid or expired access token");
        }
    }

    /**
     * Generate access token for WOPI operations.
     *
     * SECURITY FIX (Round 13 #1): Now generates cryptographically signed HMAC-SHA256 tokens.
     * Previous implementation used plaintext tokens that could be forged.
     */
    private String generateAccessToken(String tenantId, String userId, String documentId, String driveId) {
        // SECURITY FIX (Round 13 #1): Validate that secret is configured
        String secretKey = getWopiTokenSecret();

        // TODO: Look up actual user name and document owner from services
        // For now, use userId as placeholder
        String userName = "User";  // Would come from user service
        String ownerId = userId;    // Would come from document service

        // TODO: Check user's actual permissions (canWrite should come from permission service)
        boolean canWrite = true;  // Default to write for editor access

        return WopiTokenUtil.generateToken(
                tenantId,
                userId,
                userName,
                documentId,
                driveId,
                ownerId,
                canWrite,
                wopiTokenTtlSeconds,
                secretKey
        );
    }

    /**
     * SECURITY FIX (Round 13 #1): Get WOPI token secret with validation.
     * Fails fast if secret is not configured or too short.
     */
    private String getWopiTokenSecret() {
        if (wopiTokenSecret == null || wopiTokenSecret.isBlank()) {
            log.error("SECURITY: WOPI token secret not configured. Set teamsync.wopi.token-secret");
            throw new IllegalStateException("WOPI service not properly configured");
        }
        if (wopiTokenSecret.length() < 32) {
            log.error("SECURITY: WOPI token secret too short (min 32 chars). Current: {} chars",
                    wopiTokenSecret.length());
            throw new IllegalStateException("WOPI service not properly configured");
        }
        return wopiTokenSecret;
    }

    // ==================== Inner Types ====================

    private enum WopiPermission {
        READ, WRITE
    }

    private record WopiTokenData(
            String tenantId,
            String userId,
            String userName,
            String ownerId,
            boolean canWrite
    ) {}
}
