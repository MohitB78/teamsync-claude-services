package com.teamsync.content.controller;

import com.teamsync.common.dto.ApiResponse;
import com.teamsync.common.dto.CursorPage;
import com.teamsync.common.util.DownloadTokenUtil;
import com.teamsync.content.dto.document.BulkDeleteRequest;
import com.teamsync.content.dto.document.CreateBlankDocumentRequest;
import com.teamsync.content.dto.document.CreateDocumentRequest;
import com.teamsync.content.dto.document.DocumentDTO;
import com.teamsync.content.dto.document.UpdateDocumentRequest;
import com.teamsync.content.service.BlankDocumentService;
import com.teamsync.content.service.DocumentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ContentDisposition;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * REST controller for document management operations in the TeamSync content service.
 *
 * <h2>Overview</h2>
 * <p>This controller provides comprehensive document lifecycle management including:
 * <ul>
 *   <li>CRUD operations (create, read, update, delete)</li>
 *   <li>Document state management (lock/unlock, star/pin, trash/restore)</li>
 *   <li>File download with secure token-based access</li>
 *   <li>Search and filtering capabilities</li>
 *   <li>Bulk operations for batch processing</li>
 * </ul>
 *
 * <h2>Security Model</h2>
 * <p>All endpoints require authentication via JWT token validated at the API Gateway.
 * Context is extracted from headers:
 * <ul>
 *   <li>{@code X-Tenant-ID} - Multi-tenant isolation (required)</li>
 *   <li>{@code X-Drive-ID} - Drive-level access control (required for most operations)</li>
 *   <li>{@code X-User-ID} - User identification for auditing</li>
 * </ul>
 *
 * <h2>Download Security</h2>
 * <p>Two download mechanisms are supported:
 * <ul>
 *   <li><b>Token-based</b> ({@code GET /download?token=}): HMAC-SHA256 signed tokens for
 *       browser-compatible downloads without Bearer headers. Tokens include tenant/drive/user
 *       context and time-limited expiry.</li>
 *   <li><b>Direct</b> ({@code GET /{id}/download}): Requires full authentication headers,
 *       used for programmatic access.</li>
 * </ul>
 *
 * <h2>Concurrency Control</h2>
 * <p>Optimistic locking is supported via {@code If-Match} header containing the expected
 * {@code entityVersion}. If version mismatch occurs, returns 409 Conflict.</p>
 *
 * <h2>Two-Stage Deletion</h2>
 * <p>Documents follow a two-stage deletion pattern:
 * <ol>
 *   <li>{@code DELETE /{id}} - Soft delete (moves to trash, 30-day retention)</li>
 *   <li>{@code DELETE /{id}/permanent} - Hard delete (removes from storage)</li>
 * </ol>
 *
 * <h2>Pagination</h2>
 * <p>List endpoints use cursor-based pagination for scalability. Response includes:
 * <ul>
 *   <li>{@code items} - Current page items</li>
 *   <li>{@code nextCursor} - Cursor for next page (null if no more)</li>
 *   <li>{@code hasMore} - Boolean indicating more results exist</li>
 * </ul>
 *
 * @author TeamSync Platform Team
 * @version 1.0.0
 * @since Spring Boot 4.0.0
 * @see DocumentService
 * @see DownloadTokenUtil
 *
 * SECURITY FIX (Round 14 #H21): Added @Validated and path variable validation
 * to prevent injection attacks via malicious IDs.
 *
 * SECURITY FIX (Round 15 #H5): Added @PreAuthorize("isAuthenticated()") for defense-in-depth.
 */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Validated
@Slf4j
@PreAuthorize("isAuthenticated()")
public class DocumentController {

    /**
     * SECURITY FIX (Round 14 #H21): Valid ID pattern for path variables.
     */
    private static final String VALID_ID_PATTERN = "^[a-zA-Z0-9-]{1,64}$";

    private final DocumentService documentService;
    private final BlankDocumentService blankDocumentService;
    private final DownloadTokenUtil downloadTokenUtil;

    /**
     * Retrieves a document by its unique identifier.
     *
     * <p>SECURITY: Always requires drive context for proper access control.
     * The document must belong to the specified drive and the user must have
     * READ permission on that drive.
     *
     * @param documentId the unique document identifier (MongoDB ObjectId)
     * @param driveId    required drive context for permission validation
     * @return 200 OK with document data, or 404 if not found
     * @throws com.teamsync.common.exception.ResourceNotFoundException if document not found
     * @throws com.teamsync.common.exception.AccessDeniedException if user lacks READ permission
     * @throws IllegalArgumentException if driveId header is missing
     *
     * SECURITY FIX (Round 14 #H21): Added path variable validation.
     */
    @GetMapping("/{documentId}")
    public ResponseEntity<ApiResponse<DocumentDTO>> getDocument(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid document ID format")
            String documentId,
            @RequestHeader(value = "X-Drive-ID", required = false) String driveId) {
        log.debug("GET /api/documents/{} (driveId: {})", documentId, driveId);

        // SECURITY FIX: Always require explicit driveId - no "any" bypass allowed
        // This prevents cross-drive data access vulnerabilities
        if (driveId == null || driveId.isBlank()) {
            throw new IllegalArgumentException("X-Drive-ID header is required for document access");
        }

        // Fetch with full drive isolation - this validates the document belongs to this drive
        DocumentDTO document = documentService.getDocument(documentId);

        return ResponseEntity.ok(ApiResponse.<DocumentDTO>builder()
                .success(true)
                .data(document)
                .message("Document retrieved successfully")
                .build());
    }

    /**
     * Lists documents within a folder using cursor-based pagination.
     *
     * <p>Returns documents in the current drive context (from {@code X-Drive-ID} header).
     * Results are sorted by creation date descending (newest first).
     *
     * <p><b>Pagination:</b> Uses cursor-based pagination for scalability.
     * Pass the {@code nextCursor} from a previous response to fetch the next page.
     *
     * @param folderId the parent folder ID (null or omitted for root-level documents)
     * @param cursor   pagination cursor from previous response (null for first page)
     * @param limit    maximum items per page (default 50, max 100)
     * @return 200 OK with paginated document list
     *
     * SECURITY FIX (Round 14 #H21): Added query parameter validation.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<CursorPage<DocumentDTO>>> listDocuments(
            @RequestParam(required = false)
            @Size(max = 64, message = "Folder ID must not exceed 64 characters")
            @Pattern(regexp = "^[a-zA-Z0-9-]*$", message = "Invalid folder ID format")
            String folderId,
            @RequestParam(required = false)
            @Size(max = 256, message = "Cursor must not exceed 256 characters")
            String cursor,
            @RequestParam(defaultValue = "50")
            @Min(value = 1, message = "Limit must be at least 1")
            @Max(value = 100, message = "Limit must not exceed 100")
            int limit) {

        log.debug("GET /api/documents - folderId: {}, cursor: {}, limit: {}", folderId, cursor, limit);

        // SECURITY FIX (Round 9): Validate limit to prevent negative values and enforce max
        limit = Math.max(1, Math.min(limit, 100));

        CursorPage<DocumentDTO> documents = documentService.listDocuments(folderId, cursor, limit);

        return ResponseEntity.ok(ApiResponse.<CursorPage<DocumentDTO>>builder()
                .success(true)
                .data(documents)
                .message("Documents retrieved successfully")
                .build());
    }

    /**
     * Creates a new document metadata record.
     *
     * <p>This creates document metadata only. For file uploads, use
     * {@link DocumentUploadController} which handles both metadata and file storage.
     *
     * <p><b>Duplicate Detection:</b> Returns 409 Conflict if a document with the
     * same name already exists in the target folder within the same drive.
     *
     * @param request the document creation request with name, folderId, and optional metadata
     * @return 201 Created with the new document data
     * @throws IllegalArgumentException if request validation fails
     * @throws IllegalStateException if duplicate name exists in folder
     * @see DocumentUploadController#uploadFileDirect for file upload with metadata
     */
    @PostMapping
    public ResponseEntity<ApiResponse<DocumentDTO>> createDocument(
            @Valid @RequestBody CreateDocumentRequest request) {

        log.info("POST /api/documents - name: {}", request.getName());

        DocumentDTO document = documentService.createDocument(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<DocumentDTO>builder()
                        .success(true)
                        .data(document)
                        .message("Document created successfully")
                        .build());
    }

    /**
     * Creates a new blank Office document (Word, Excel, or PowerPoint).
     *
     * <p>This endpoint creates a new document from a blank template and uploads it
     * to storage. The document is immediately ready for editing in Collabora Online
     * or another WOPI-compatible editor.</p>
     *
     * <h3>Supported Document Types</h3>
     * <ul>
     *   <li><b>WORD</b> or <b>DOCX</b>: Creates a blank Word document (.docx)</li>
     *   <li><b>EXCEL</b> or <b>XLSX</b>: Creates a blank Excel spreadsheet (.xlsx)</li>
     *   <li><b>POWERPOINT</b> or <b>PPTX</b>: Creates a blank PowerPoint presentation (.pptx)</li>
     * </ul>
     *
     * <h3>Usage</h3>
     * <p>After creation, the frontend should redirect the user to the document editor
     * using the returned document ID.</p>
     *
     * @param request the blank document creation request with type, name, and optional folderId
     * @return 201 Created with the new document data
     * @throws IllegalArgumentException if document type is not supported or validation fails
     * @throws IllegalStateException if a document with the same name exists in the folder
     */
    @PostMapping("/blank")
    public ResponseEntity<ApiResponse<DocumentDTO>> createBlankDocument(
            @Valid @RequestBody CreateBlankDocumentRequest request) {

        log.info("POST /api/documents/blank - type: {}, name: {}", request.getType(), request.getName());

        BlankDocumentService.BlankDocumentType type =
                BlankDocumentService.BlankDocumentType.fromString(request.getType());

        DocumentDTO document = blankDocumentService.createBlankDocument(
                type, request.getName(), request.getFolderId());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<DocumentDTO>builder()
                        .success(true)
                        .data(document)
                        .message("Blank " + type.name().toLowerCase() + " document created successfully")
                        .build());
    }

    /**
     * Update document metadata.
     * Supports optimistic locking via If-Match header containing the expected entityVersion.
     * If the version doesn't match, returns 409 Conflict.
     *
     * @param documentId The document ID
     * @param request The update request
     * @param ifMatch Optional If-Match header with expected entity version
     * @return Updated document
     *
     * SECURITY FIX (Round 14 #H21): Added path variable validation.
     */
    @PatchMapping("/{documentId}")
    public ResponseEntity<ApiResponse<DocumentDTO>> updateDocument(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid document ID format")
            String documentId,
            @Valid @RequestBody UpdateDocumentRequest request,
            @RequestHeader(value = "If-Match", required = false) Long ifMatch) {

        log.info("PATCH /api/documents/{} (If-Match: {})", documentId, ifMatch);

        DocumentDTO document = documentService.updateDocument(documentId, request, ifMatch);

        return ResponseEntity.ok(ApiResponse.<DocumentDTO>builder()
                .success(true)
                .data(document)
                .message("Document updated successfully")
                .build());
    }

    /**
     * Moves a document to trash (soft delete).
     *
     * <p>This is stage 1 of the two-stage deletion process. The document:
     * <ul>
     *   <li>Changes status from ACTIVE to TRASHED</li>
     *   <li>Remains in storage with 30-day retention</li>
     *   <li>Can be restored via {@code POST /{id}/restore}</li>
     *   <li>Is excluded from search and listing results</li>
     * </ul>
     *
     * <p>For permanent deletion, use {@code DELETE /{id}/permanent}.
     *
     * @param documentId the document to move to trash
     * @return 200 OK on success
     * @throws com.teamsync.common.exception.ResourceNotFoundException if document not found
     * @throws com.teamsync.common.exception.AccessDeniedException if user lacks DELETE permission
     * @see #deleteDocument for permanent deletion
     * @see #restoreDocument for restore operation
     *
     * SECURITY FIX (Round 14 #H21): Added path variable validation.
     */
    @DeleteMapping("/{documentId}")
    public ResponseEntity<ApiResponse<Void>> trashDocument(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid document ID format")
            String documentId) {
        log.info("DELETE /api/documents/{} (trash)", documentId);

        documentService.trashDocument(documentId);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Document moved to trash")
                .build());
    }

    /**
     * Permanently deletes a document and its storage.
     *
     * <p>This is stage 2 of the two-stage deletion process. <b>This action is irreversible.</b>
     * <ul>
     *   <li>Removes all document versions from object storage</li>
     *   <li>Deletes the document record from MongoDB</li>
     *   <li>Decrements storage quota usage</li>
     *   <li>Cannot be undone - data is permanently lost</li>
     * </ul>
     *
     * <p><b>Note:</b> The document must be in TRASHED status first. Use {@code DELETE /{id}}
     * to move active documents to trash.
     *
     * @param documentId the document to permanently delete
     * @return 200 OK on success
     * @throws com.teamsync.common.exception.ResourceNotFoundException if document not found
     * @throws IllegalStateException if document is not in TRASHED status
     * @see #trashDocument for soft delete
     *
     * SECURITY FIX (Round 14 #H21): Added path variable validation.
     */
    @DeleteMapping("/{documentId}/permanent")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid document ID format")
            String documentId) {
        log.info("DELETE /api/documents/{}/permanent", documentId);

        documentService.deleteDocument(documentId);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Document permanently deleted")
                .build());
    }

    /**
     * Restores a document from trash to active status.
     *
     * <p>Restores a previously trashed document:
     * <ul>
     *   <li>Changes status from TRASHED back to ACTIVE</li>
     *   <li>Document reappears in search and listing results</li>
     *   <li>If name conflict exists, appends timestamp (e.g., "file_restored_1234567890.pdf")</li>
     * </ul>
     *
     * @param documentId the trashed document to restore
     * @return 200 OK with the restored document data
     * @throws com.teamsync.common.exception.ResourceNotFoundException if document not found
     * @throws IllegalStateException if document is not in TRASHED status
     *
     * SECURITY FIX (Round 14 #H21): Added path variable validation.
     */
    @PostMapping("/{documentId}/restore")
    public ResponseEntity<ApiResponse<DocumentDTO>> restoreDocument(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid document ID format")
            String documentId) {
        log.info("POST /api/documents/{}/restore", documentId);

        DocumentDTO document = documentService.restoreDocument(documentId);

        return ResponseEntity.ok(ApiResponse.<DocumentDTO>builder()
                .success(true)
                .data(document)
                .message("Document restored successfully")
                .build());
    }

    /**
     * Lists all trashed documents for the current user.
     *
     * <p>Returns documents in TRASHED status owned by the current user across
     * all their drives. Used by the trash page UI to display items that can
     * be restored or permanently deleted.</p>
     *
     * <p><b>Note:</b> This endpoint does not require a drive ID header since it
     * returns items from all user's drives. Access is controlled by ownership.</p>
     *
     * @param cursor optional cursor for pagination (document ID from previous page)
     * @param limit maximum number of documents per page (default: 50, max: 100)
     * @return 200 OK with paginated list of trashed documents
     */
    @GetMapping("/trashed")
    public ResponseEntity<ApiResponse<CursorPage<DocumentDTO>>> listTrashedDocuments(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        log.info("GET /api/documents/trashed (cursor: {}, limit: {})", cursor, limit);

        CursorPage<DocumentDTO> result = documentService.listTrashedDocuments(cursor, limit);

        return ResponseEntity.ok(ApiResponse.<CursorPage<DocumentDTO>>builder()
                .success(true)
                .data(result)
                .message("Trashed documents retrieved successfully")
                .build());
    }

    /**
     * Acquires an exclusive lock on a document for editing.
     *
     * <p>Document locking prevents concurrent edits:
     * <ul>
     *   <li>Only one user can hold the lock at a time</li>
     *   <li>Lock owner is recorded in {@code lockedBy} with timestamp</li>
     *   <li>Other users see "locked" status and cannot edit</li>
     *   <li>Only the lock owner (or admin) can unlock</li>
     * </ul>
     *
     * <p><b>Note:</b> Locks are advisory. Use WOPI integration for real-time
     * collaborative editing with automatic lock management.
     *
     * @param documentId the document to lock
     * @return 200 OK with updated document showing lock status
     * @throws IllegalStateException if document is already locked by another user
     *
     * SECURITY FIX (Round 14 #H21): Added path variable validation.
     */
    @PostMapping("/{documentId}/lock")
    public ResponseEntity<ApiResponse<DocumentDTO>> lockDocument(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid document ID format")
            String documentId) {
        log.info("POST /api/documents/{}/lock", documentId);

        DocumentDTO document = documentService.lockDocument(documentId);

        return ResponseEntity.ok(ApiResponse.<DocumentDTO>builder()
                .success(true)
                .data(document)
                .message("Document locked successfully")
                .build());
    }

    /**
     * Releases the exclusive lock on a document.
     *
     * <p>Only the lock owner can release their lock. After unlocking:
     * <ul>
     *   <li>{@code lockedBy} and {@code lockedAt} are cleared</li>
     *   <li>Document becomes available for others to lock/edit</li>
     * </ul>
     *
     * @param documentId the document to unlock
     * @return 200 OK with updated document
     * @throws IllegalStateException if document is not locked or locked by different user
     *
     * SECURITY FIX (Round 14 #H21): Added path variable validation.
     */
    @PostMapping("/{documentId}/unlock")
    public ResponseEntity<ApiResponse<DocumentDTO>> unlockDocument(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid document ID format")
            String documentId) {
        log.info("POST /api/documents/{}/unlock", documentId);

        DocumentDTO document = documentService.unlockDocument(documentId);

        return ResponseEntity.ok(ApiResponse.<DocumentDTO>builder()
                .success(true)
                .data(document)
                .message("Document unlocked successfully")
                .build());
    }

    /**
     * Toggles the starred status of a document.
     *
     * <p>Starred documents appear in the user's "Starred" collection for quick access.
     * Starring is user-specific and does not affect other users.
     *
     * @param documentId the document to star/unstar
     * @param body       JSON object with {@code starred} boolean (default true)
     * @return 200 OK with updated document
     *
     * SECURITY FIX (Round 14 #H21): Added path variable validation.
     */
    @PostMapping("/{documentId}/star")
    public ResponseEntity<ApiResponse<DocumentDTO>> toggleStar(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid document ID format")
            String documentId,
            @RequestBody Map<String, Boolean> body) {

        log.info("POST /api/documents/{}/star", documentId);

        boolean starred = body.getOrDefault("starred", true);
        UpdateDocumentRequest request = UpdateDocumentRequest.builder()
                .isStarred(starred)
                .build();

        // Star/unstar operations don't require optimistic locking
        DocumentDTO document = documentService.updateDocument(documentId, request, null);

        return ResponseEntity.ok(ApiResponse.<DocumentDTO>builder()
                .success(true)
                .data(document)
                .message(starred ? "Document starred" : "Document unstarred")
                .build());
    }

    /**
     * Toggles the pinned status of a document.
     *
     * <p>Pinned documents appear at the top of folder listings for prominence.
     * Pinning affects all users viewing the folder (drive-scoped).
     *
     * @param documentId the document to pin/unpin
     * @param body       JSON object with {@code pinned} boolean (default true)
     * @return 200 OK with updated document
     *
     * SECURITY FIX (Round 14 #H21): Added path variable validation.
     */
    @PostMapping("/{documentId}/pin")
    public ResponseEntity<ApiResponse<DocumentDTO>> togglePin(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid document ID format")
            String documentId,
            @RequestBody Map<String, Boolean> body) {

        log.info("POST /api/documents/{}/pin", documentId);

        boolean pinned = body.getOrDefault("pinned", true);
        UpdateDocumentRequest request = UpdateDocumentRequest.builder()
                .isPinned(pinned)
                .build();

        // Pin/unpin operations don't require optimistic locking
        DocumentDTO document = documentService.updateDocument(documentId, request, null);

        return ResponseEntity.ok(ApiResponse.<DocumentDTO>builder()
                .success(true)
                .data(document)
                .message(pinned ? "Document pinned" : "Document unpinned")
                .build());
    }

    /**
     * Retrieves all starred documents for the current user in the current drive.
     *
     * @return 200 OK with list of starred documents
     */
    @GetMapping("/starred")
    public ResponseEntity<ApiResponse<List<DocumentDTO>>> getStarredDocuments() {
        log.debug("GET /api/documents/starred");

        List<DocumentDTO> documents = documentService.getStarredDocuments();

        return ResponseEntity.ok(ApiResponse.<List<DocumentDTO>>builder()
                .success(true)
                .data(documents)
                .message("Starred documents retrieved successfully")
                .build());
    }

    /**
     * Retrieves recently accessed documents for the current user.
     *
     * <p>Returns documents ordered by last access time (most recent first).
     * Limited to documents within the current drive context.
     *
     * @param limit maximum number of documents to return (default 10, max 50)
     * @return 200 OK with list of recently accessed documents
     *
     * SECURITY FIX (Round 14 #H21): Added query parameter validation.
     */
    @GetMapping("/recent")
    public ResponseEntity<ApiResponse<List<DocumentDTO>>> getRecentDocuments(
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "Limit must be at least 1")
            @Max(value = 50, message = "Limit must not exceed 50")
            int limit) {

        log.debug("GET /api/documents/recent - limit: {}", limit);

        // SECURITY FIX (Round 9): Validate limit to prevent negative values and enforce max
        limit = Math.max(1, Math.min(limit, 50));
        List<DocumentDTO> documents = documentService.getRecentDocuments(limit);

        return ResponseEntity.ok(ApiResponse.<List<DocumentDTO>>builder()
                .success(true)
                .data(documents)
                .message("Recent documents retrieved successfully")
                .build());
    }

    /**
     * Searches documents by name within the current drive.
     *
     * <p>Performs case-insensitive partial match on document names.
     * Only returns ACTIVE documents (excludes TRASHED and PENDING).
     *
     * <p><b>Security:</b> Query input is validated to prevent injection attacks:
     * <ul>
     *   <li>Length limited to 2-100 characters</li>
     *   <li>Only alphanumeric, spaces, hyphens, underscores, and dots allowed</li>
     *   <li>Special regex characters are escaped before query execution</li>
     * </ul>
     *
     * <p><b>Note:</b> For full-text search across content, use the dedicated
     * Search Service which provides NLP-powered semantic search.
     *
     * @param q     the search query (2-100 characters, alphanumeric with spaces/hyphens/dots)
     * @param limit maximum results to return (default 50, max 100)
     * @return 200 OK with matching documents
     *
     * SECURITY FIX (Round 14 #H21): Added query parameter validation.
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<DocumentDTO>>> searchDocuments(
            @RequestParam
            @NotBlank(message = "Search query is required")
            @Size(min = 2, max = 100, message = "Search query must be between 2 and 100 characters")
            @Pattern(regexp = "^[\\w\\s\\-\\.]+$", message = "Search query contains invalid characters")
            String q,
            @RequestParam(defaultValue = "50")
            @Min(value = 1, message = "Limit must be at least 1")
            @Max(value = 100, message = "Limit must not exceed 100")
            int limit) {

        log.debug("GET /api/documents/search - query: {}, limit: {}", q, limit);

        // SECURITY FIX (Round 9): Validate limit to prevent negative values and enforce max
        limit = Math.max(1, Math.min(limit, 100));

        // SECURITY FIX: Escape special regex characters to prevent ReDoS attacks
        String sanitizedQuery = java.util.regex.Pattern.quote(q);
        List<DocumentDTO> documents = documentService.searchDocuments(sanitizedQuery, limit);

        return ResponseEntity.ok(ApiResponse.<List<DocumentDTO>>builder()
                .success(true)
                .data(documents)
                .message("Search completed successfully")
                .build());
    }

    /**
     * Generates a time-limited download URL for a document.
     *
     * <p>Returns a signed token URL that can be used for browser downloads
     * without requiring authentication headers. The token includes:
     * <ul>
     *   <li>Tenant, drive, and user context for audit</li>
     *   <li>Storage bucket and key for file location</li>
     *   <li>Expiration timestamp (default 1 hour, max 24 hours)</li>
     *   <li>HMAC-SHA256 signature to prevent tampering</li>
     * </ul>
     *
     * <p><b>Usage:</b> The returned URL can be used directly in browser
     * (e.g., anchor href, window.open) or fetch without auth headers.
     *
     * @param documentId   the document to generate download URL for
     * @param expirySeconds token validity in seconds (default 3600, max 86400)
     * @return 200 OK with download URL and expiry info
     * @throws com.teamsync.common.exception.ResourceNotFoundException if document not found
     *
     * SECURITY FIX (Round 14 #H21): Added path/query variable validation.
     */
    @GetMapping("/{documentId}/download-url")
    public ResponseEntity<ApiResponse<Map<String, String>>> getDownloadUrl(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid document ID format")
            String documentId,
            @RequestParam(defaultValue = "3600")
            @Min(value = 60, message = "Expiry must be at least 60 seconds")
            @Max(value = 86400, message = "Expiry must not exceed 86400 seconds (24 hours)")
            int expirySeconds) {

        log.debug("GET /api/documents/{}/download-url - expiry: {}s", documentId, expirySeconds);

        // Limit expiry to 24 hours
        expirySeconds = Math.min(expirySeconds, 86400);

        String url = documentService.generateDownloadUrl(documentId, Duration.ofSeconds(expirySeconds));

        return ResponseEntity.ok(ApiResponse.<Map<String, String>>builder()
                .success(true)
                .data(Map.of("url", url, "expiresIn", String.valueOf(expirySeconds)))
                .message("Download URL generated successfully")
                .build());
    }

    /**
     * Download document file using a signed token.
     *
     * <p>This endpoint supports HTTP Range requests for streaming PDF viewers like
     * TeamSync PDF Viewer. Range requests allow the viewer to display pages before
     * the full document is downloaded.</p>
     *
     * <h3>Supported Headers</h3>
     * <ul>
     *   <li>{@code Range: bytes=start-end} - Request specific byte range</li>
     * </ul>
     *
     * <h3>Response Headers</h3>
     * <ul>
     *   <li>{@code Accept-Ranges: bytes} - Indicates range request support</li>
     *   <li>{@code Content-Range: bytes start-end/total} - For 206 Partial Content</li>
     * </ul>
     *
     * The token is generated by generateDownloadUrl() and contains:
     * - tenantId, driveId, userId (for context and audit)
     * - bucket, storageKey (storage location)
     * - expiresAt (time-limited access)
     *
     * This allows direct browser downloads without requiring auth headers.
     *
     * SECURITY FIX (Round 14 #H21): Added query parameter validation.
     * SECURITY FIX: Override class-level @PreAuthorize to allow token-based downloads
     * without JWT authentication. Security is enforced via HMAC-SHA256 signed tokens
     * validated in the method body, plus resource ownership verification.
     */
    @GetMapping("/download")
    @PreAuthorize("permitAll()")
    public ResponseEntity<?> downloadFile(
            @RequestParam
            @NotBlank(message = "Token is required")
            @Size(max = 2048, message = "Token must not exceed 2048 characters")
            String token,
            @RequestParam(required = false)
            @Size(max = 255, message = "Filename must not exceed 255 characters")
            String filename,
            @RequestHeader(value = "Range", required = false) String rangeHeader) {

        log.info("GET /api/documents/download - validating token, range: {}", rangeHeader);

        // Validate and parse the signed token
        DownloadTokenUtil.TokenData tokenData = downloadTokenUtil.validateToken(token);

        if (tokenData == null) {
            log.warn("Invalid or expired download token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.<Void>builder()
                            .success(false)
                            .error("Invalid or expired download token")
                            .code("INVALID_TOKEN")
                            .build());
        }

        log.info("GET /api/documents/download - bucket: {}, key: {}, user: {}",
                tokenData.bucket(), tokenData.storageKey(), tokenData.userId());

        // SECURITY FIX: Verify the resource actually exists and belongs to the claimed tenant/drive
        // This prevents forged tokens from accessing arbitrary files
        if (!documentService.verifyResourceOwnership(
                tokenData.tenantId(), tokenData.driveId(),
                tokenData.bucket(), tokenData.storageKey())) {
            log.warn("SECURITY: Download token resource verification failed - " +
                     "tenant: {}, drive: {}, bucket: {}, key: {}",
                     tokenData.tenantId(), tokenData.driveId(),
                     tokenData.bucket(), tokenData.storageKey());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.<Void>builder()
                            .success(false)
                            .error("Resource not found or access denied")
                            .code("RESOURCE_NOT_FOUND")
                            .build());
        }

        // Use provided filename or extract from storage key
        String downloadFilename = filename;
        if (downloadFilename == null || downloadFilename.isBlank()) {
            String storageKey = tokenData.storageKey();
            downloadFilename = storageKey.contains("/")
                    ? storageKey.substring(storageKey.lastIndexOf('/') + 1)
                    : storageKey;
        }

        // Get file metadata first (needed for both full and range requests)
        DocumentService.FileMetadata metadata = documentService.getFileMetadataWithToken(
                tokenData.bucket(), tokenData.storageKey());
        long totalSize = metadata.fileSize();
        String contentType = metadata.contentType();

        // SECURITY FIX (Round 8): Use ContentDisposition builder to prevent header injection
        // The filename is properly encoded per RFC 5987 to handle special characters safely
        ContentDisposition contentDisposition = "application/pdf".equals(contentType)
                ? ContentDisposition.inline()
                        .filename(sanitizeFilename(downloadFilename), StandardCharsets.UTF_8)
                        .build()
                : ContentDisposition.attachment()
                        .filename(sanitizeFilename(downloadFilename), StandardCharsets.UTF_8)
                        .build();
        String disposition = contentDisposition.toString();

        // Handle Range request
        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            return handleRangeRequest(tokenData, rangeHeader, totalSize, contentType, disposition);
        }

        // Full file download
        DocumentService.FileDownload download = documentService.downloadFileWithToken(
                tokenData.bucket(), tokenData.storageKey());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setContentLength(totalSize);
        headers.set("Content-Disposition", disposition);
        headers.set("Accept-Ranges", "bytes");
        headers.setCacheControl("private, max-age=3600");

        return ResponseEntity.ok()
                .headers(headers)
                .body(new InputStreamResource(download.inputStream()));
    }

    /**
     * Handle HTTP Range request for partial content download.
     */
    private ResponseEntity<?> handleRangeRequest(
            DownloadTokenUtil.TokenData tokenData,
            String rangeHeader,
            long totalSize,
            String contentType,
            String disposition) {

        try {
            // Parse range header: "bytes=start-end" or "bytes=start-"
            String rangeSpec = rangeHeader.substring(6); // Remove "bytes="
            String[] parts = rangeSpec.split("-");

            long start = Long.parseLong(parts[0]);
            long end = parts.length > 1 && !parts[1].isEmpty()
                    ? Long.parseLong(parts[1])
                    : totalSize - 1;

            // Validate range
            if (start >= totalSize || end >= totalSize || start > end) {
                return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                        .header("Content-Range", "bytes */" + totalSize)
                        .build();
            }

            long length = end - start + 1;

            log.debug("Range request: bytes {}-{}/{} (length={})", start, end, totalSize, length);

            DocumentService.FileDownload download = documentService.downloadFileRangeWithToken(
                    tokenData.bucket(), tokenData.storageKey(), start, length);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(contentType));
            headers.setContentLength(length);
            headers.set("Content-Disposition", disposition);
            headers.set("Accept-Ranges", "bytes");
            headers.set("Content-Range", String.format("bytes %d-%d/%d", start, end, totalSize));
            headers.setCacheControl("private, max-age=3600");

            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .headers(headers)
                    .body(new InputStreamResource(download.inputStream()));

        } catch (NumberFormatException e) {
            log.warn("Invalid Range header format: {}", rangeHeader);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * HEAD request for download endpoint - returns file metadata without body.
     *
     * <p>Used by PDF viewers to determine file size before making range requests.</p>
     *
     * SECURITY FIX (Round 14 #H21): Added query parameter validation.
     * SECURITY FIX: Override class-level @PreAuthorize for token-based access.
     */
    @RequestMapping(value = "/download", method = RequestMethod.HEAD)
    @PreAuthorize("permitAll()")
    public ResponseEntity<Void> downloadFileHead(
            @RequestParam
            @NotBlank(message = "Token is required")
            @Size(max = 2048, message = "Token must not exceed 2048 characters")
            String token,
            @RequestParam(required = false)
            @Size(max = 255, message = "Filename must not exceed 255 characters")
            String filename) {

        log.info("HEAD /api/documents/download - validating token");

        // Validate token
        DownloadTokenUtil.TokenData tokenData = downloadTokenUtil.validateToken(token);

        if (tokenData == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Get file metadata
        DocumentService.FileMetadata metadata = documentService.getFileMetadataWithToken(
                tokenData.bucket(), tokenData.storageKey());

        // Use provided filename or extract from storage key
        String downloadFilename = filename;
        if (downloadFilename == null || downloadFilename.isBlank()) {
            String storageKey = tokenData.storageKey();
            downloadFilename = storageKey.contains("/")
                    ? storageKey.substring(storageKey.lastIndexOf('/') + 1)
                    : storageKey;
        }

        // SECURITY FIX (Round 8): Use ContentDisposition builder to prevent header injection
        // The filename is properly encoded per RFC 5987 to handle special characters safely
        ContentDisposition contentDisposition = "application/pdf".equals(metadata.contentType())
                ? ContentDisposition.inline()
                        .filename(sanitizeFilename(downloadFilename), StandardCharsets.UTF_8)
                        .build()
                : ContentDisposition.attachment()
                        .filename(sanitizeFilename(downloadFilename), StandardCharsets.UTF_8)
                        .build();
        String disposition = contentDisposition.toString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(metadata.contentType()));
        headers.setContentLength(metadata.fileSize());
        headers.set("Content-Disposition", disposition);
        headers.set("Accept-Ranges", "bytes");
        headers.setCacheControl("private, max-age=3600");

        return ResponseEntity.ok().headers(headers).build();
    }

    /**
     * Download document file directly by ID.
     * This endpoint requires authentication via headers (X-Tenant-ID, X-Drive-ID, X-User-ID).
     * Streams the file directly to the client.
     *
     * SECURITY FIX (Round 14 #H21): Added path variable validation.
     */
    @GetMapping("/{documentId}/download")
    public ResponseEntity<?> downloadDocument(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid document ID format")
            String documentId) {
        log.info("GET /api/documents/{}/download", documentId);

        try {
            DocumentService.FileDownload download = documentService.downloadDocument(documentId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(download.contentType()));
            headers.setContentLength(download.fileSize());
            headers.setContentDispositionFormData("attachment", download.filename());
            headers.setCacheControl("private, max-age=3600");

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(new InputStreamResource(download.inputStream()));
        } catch (Exception e) {
            // SECURITY FIX (Round 14 #H24): Don't expose exception message to client
            // Exception messages can reveal internal storage paths, bucket names, or configuration details
            log.error("Failed to download document: {}", documentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.<Void>builder()
                            .success(false)
                            .error("Failed to download document. Please try again later.")
                            .code("DOWNLOAD_ERROR")
                            .build());
        }
    }

    /**
     * Returns the total count of active documents in the current drive.
     *
     * <p>Only counts ACTIVE documents (excludes TRASHED and PENDING).
     *
     * @return 200 OK with document count
     */
    @GetMapping("/count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getDocumentCount() {
        log.debug("GET /api/documents/count");

        long count = documentService.getDocumentCount();

        return ResponseEntity.ok(ApiResponse.<Map<String, Long>>builder()
                .success(true)
                .data(Map.of("count", count))
                .message("Document count retrieved successfully")
                .build());
    }

    /**
     * Bulk delete (trash) multiple documents.
     *
     * <p>Moves multiple documents to trash in a single operation.
     * Documents can be restored from trash within the retention period.</p>
     *
     * <p>Processes each document independently - partial success is possible.
     * The response includes:
     * <ul>
     *   <li>{@code successCount} - Number of documents successfully trashed</li>
     *   <li>{@code failureCount} - Number of documents that failed</li>
     *   <li>{@code failures} - Map of documentId to error message for failures</li>
     * </ul>
     *
     * <p>SECURITY (Round 6): Limited to {@value BulkDeleteRequest#MAX_BULK_SIZE} documents per request
     * to prevent DoS attacks via resource exhaustion.</p>
     *
     * @param request Validated request with document IDs (max 100)
     * @return 200 OK with operation summary
     */
    @PostMapping("/bulk-delete")
    public ResponseEntity<ApiResponse<Map<String, Object>>> bulkDeleteDocuments(
            @Valid @RequestBody BulkDeleteRequest request) {

        List<String> documentIds = request.getDocumentIds();

        log.info("POST /api/documents/bulk-delete - {} documents (max allowed: {})",
                documentIds.size(), BulkDeleteRequest.MAX_BULK_SIZE);

        Map<String, Object> result = documentService.bulkTrashDocuments(documentIds);

        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .success(true)
                .data(result)
                .message("Bulk delete completed")
                .build());
    }

    /**
     * Sanitizes a filename to prevent HTTP header injection attacks.
     *
     * <p>SECURITY FIX (Round 8): This method removes or replaces characters that could
     * be used for HTTP header injection:
     * <ul>
     *   <li>Carriage return (\r) and newline (\n) - could inject new headers</li>
     *   <li>Null bytes (\0) - could truncate the filename</li>
     *   <li>Control characters (0x00-0x1F, 0x7F) - could cause parsing issues</li>
     * </ul>
     *
     * @param filename The original filename
     * @return A sanitized filename safe for use in HTTP headers
     */
    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "download";
        }

        // Remove control characters including CR, LF, and null bytes
        // These could be used for HTTP response splitting attacks
        String sanitized = filename
                .replaceAll("[\\x00-\\x1f\\x7f]", "")  // Remove control characters
                .replaceAll("[\\r\\n]", "")            // Explicitly remove CR/LF
                .trim();

        // Ensure filename is not empty after sanitization
        if (sanitized.isEmpty()) {
            return "download";
        }

        // Limit filename length to prevent buffer issues
        if (sanitized.length() > 255) {
            // Preserve extension if possible
            int extIndex = sanitized.lastIndexOf('.');
            if (extIndex > 0 && extIndex > sanitized.length() - 10) {
                String ext = sanitized.substring(extIndex);
                sanitized = sanitized.substring(0, 255 - ext.length()) + ext;
            } else {
                sanitized = sanitized.substring(0, 255);
            }
        }

        return sanitized;
    }
}
