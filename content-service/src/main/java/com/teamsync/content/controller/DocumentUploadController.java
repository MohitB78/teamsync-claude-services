package com.teamsync.content.controller;

import com.teamsync.common.dto.ApiResponse;
import com.teamsync.content.dto.document.DocumentDTO;
import com.teamsync.content.dto.upload.DocumentUploadCompleteRequest;
import com.teamsync.content.dto.upload.DocumentUploadInitRequest;
import com.teamsync.content.dto.upload.DocumentUploadInitResponse;
import com.teamsync.content.dto.upload.UploadPrepareResponse;
import com.teamsync.content.service.DocumentUploadService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * REST controller for document upload operations in the TeamSync content service.
 *
 * <h2>Overview</h2>
 * <p>Content Service owns the upload flow to ensure document metadata and storage
 * are always in sync. This controller provides multiple upload strategies optimized
 * for different file sizes.
 *
 * <h2>Upload Strategies</h2>
 *
 * <h3>Recommended Flow (Backend-Controlled Routing)</h3>
 * <pre>
 * 1. POST /prepare - Backend analyzes file size and returns strategy
 *    ├─ DIRECT (&lt;10MB): POST to /direct with multipart form
 *    └─ PRESIGNED (≥10MB): PUT to presigned URL, then POST /complete
 * </pre>
 *
 * <h3>Alternative Flow (Direct Init)</h3>
 * <pre>
 * 1. POST /init - Creates PENDING document + presigned URLs
 * 2. PUT file to presigned URL (via API Gateway /storage-proxy)
 * 3. POST /complete - Activates document, finalizes quota
 * </pre>
 *
 * <h3>Cancellation Flow</h3>
 * <pre>
 * POST /{documentId}/cancel - Deletes PENDING document + releases reserved quota
 * </pre>
 *
 * <h2>Security Model</h2>
 * <ul>
 *   <li>MinIO is never exposed externally - all URLs route through API Gateway</li>
 *   <li>Presigned URLs are rewritten: {@code minio:9000} → {@code gateway:9080/storage-proxy}</li>
 *   <li>Upload sessions track reserved quota to prevent over-allocation</li>
 *   <li>PENDING documents are cleaned up by scheduled job after 24 hours</li>
 * </ul>
 *
 * <h2>Quota Management</h2>
 * <ul>
 *   <li>{@code /prepare} or {@code /init}: Reserves quota (reservedStorage += fileSize)</li>
 *   <li>{@code /complete}: Finalizes quota (usedStorage += fileSize, reservedStorage -= fileSize)</li>
 *   <li>{@code /cancel}: Releases quota (reservedStorage -= fileSize)</li>
 * </ul>
 *
 * <h2>Error Handling</h2>
 * <ul>
 *   <li>507 Insufficient Storage: Quota exceeded</li>
 *   <li>503 Service Unavailable: Storage Service unreachable</li>
 *   <li>409 Conflict: Duplicate filename in folder</li>
 * </ul>
 *
 * @author TeamSync Platform Team
 * @version 1.0.0
 * @since Spring Boot 4.0.0
 * @see DocumentUploadService
 */
/**
 * SECURITY FIX (Round 15 #H22): Added @Validated and path variable validation.
 */
@RestController
@RequestMapping("/api/documents/upload")
@RequiredArgsConstructor
@Validated
@Slf4j
@PreAuthorize("isAuthenticated()")
public class DocumentUploadController {

    /**
     * SECURITY FIX (Round 15 #H22): Valid ID pattern for path variables.
     */
    private static final String VALID_ID_PATTERN = "^[a-zA-Z0-9-]{1,64}$";

    private final DocumentUploadService uploadService;

    /**
     * Prepares an upload by analyzing file size and returning the optimal strategy.
     *
     * <p><b>This is the RECOMMENDED entry point for all uploads.</b> The backend
     * decides the best strategy based on file size:
     *
     * <h4>DIRECT Strategy (files &lt; 10MB)</h4>
     * <ul>
     *   <li>Returns {@code uploadStrategy: "DIRECT"}</li>
     *   <li>Client should POST file to {@code /api/documents/upload/direct}</li>
     *   <li>File streams through Content Service → Storage Service → MinIO</li>
     *   <li>Simpler flow, no presigned URLs needed</li>
     * </ul>
     *
     * <h4>PRESIGNED Strategy (files ≥ 10MB)</h4>
     * <ul>
     *   <li>Returns {@code uploadStrategy: "PRESIGNED"} with presigned URL</li>
     *   <li>Creates PENDING document and reserves quota</li>
     *   <li>Client PUTs file directly to presigned URL (via API Gateway proxy)</li>
     *   <li>Client then calls {@code /complete} to activate document</li>
     * </ul>
     *
     * @param request upload preparation request with filename, fileSize, contentType
     * @return 200 OK with upload strategy and instructions
     * @throws com.teamsync.common.exception.StorageQuotaExceededException if quota exceeded
     */
    @PostMapping("/prepare")
    public ResponseEntity<ApiResponse<UploadPrepareResponse>> prepareUpload(
            @Valid @RequestBody DocumentUploadInitRequest request) {

        log.info("POST /api/documents/upload/prepare - filename: {}, size: {}",
                request.getFilename(), request.getFileSize());

        UploadPrepareResponse response = uploadService.prepareUpload(request);

        return ResponseEntity.ok(ApiResponse.<UploadPrepareResponse>builder()
                .success(true)
                .data(response)
                .message(response.getMessage())
                .build());
    }

    /**
     * Initializes a document upload with presigned URLs (alternative to prepare).
     *
     * <p>Use this endpoint if you always want presigned URLs regardless of file size.
     * Creates a PENDING document record and reserves storage quota.
     *
     * <p><b>Response includes:</b>
     * <ul>
     *   <li>{@code documentId} - The created PENDING document ID</li>
     *   <li>{@code sessionId} - Storage session for tracking</li>
     *   <li>{@code uploadUrl} - Presigned PUT URL (via API Gateway proxy)</li>
     *   <li>{@code expiresAt} - URL expiration timestamp</li>
     * </ul>
     *
     * <p>After uploading the file, call {@code POST /complete} to activate the document.
     *
     * @param request upload initialization request with filename, fileSize, contentType
     * @return 201 Created with presigned URL and document info
     * @throws com.teamsync.common.exception.StorageQuotaExceededException if quota exceeded
     * @see #completeUpload for activation step
     */
    @PostMapping("/init")
    public ResponseEntity<ApiResponse<DocumentUploadInitResponse>> initializeUpload(
            @Valid @RequestBody DocumentUploadInitRequest request) {

        log.info("POST /api/documents/upload/init - filename: {}, size: {}",
                request.getFilename(), request.getFileSize());

        DocumentUploadInitResponse response = uploadService.initializeUpload(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<DocumentUploadInitResponse>builder()
                        .success(true)
                        .data(response)
                        .message("Upload initialized. Use the provided URL(s) to upload the file.")
                        .build());
    }

    /**
     * Completes a document upload and activates the document.
     *
     * <p>Call this after successfully uploading the file to the presigned URL.
     * This endpoint:
     * <ul>
     *   <li>Verifies the file exists in storage</li>
     *   <li>Updates document status from PENDING to ACTIVE</li>
     *   <li>Finalizes quota (moves from reserved to used)</li>
     *   <li>Sets actual file size and checksum</li>
     *   <li>Publishes document.uploaded event for async processing</li>
     * </ul>
     *
     * @param request completion request with documentId, sessionId, and optional ETags
     * @return 200 OK with the activated document
     * @throws com.teamsync.common.exception.ResourceNotFoundException if document not found
     * @throws IllegalStateException if document is not in PENDING status
     * @throws com.teamsync.common.exception.ServiceUnavailableException if storage verification fails
     */
    @PostMapping("/complete")
    public ResponseEntity<ApiResponse<DocumentDTO>> completeUpload(
            @Valid @RequestBody DocumentUploadCompleteRequest request) {

        log.info("POST /api/documents/upload/complete - documentId: {}, sessionId: {}",
                request.getDocumentId(), request.getSessionId());

        DocumentDTO document = uploadService.completeUpload(request);

        return ResponseEntity.ok(ApiResponse.<DocumentDTO>builder()
                .success(true)
                .data(document)
                .message("Upload completed successfully. Document is now active.")
                .build());
    }

    /**
     * Cancels a document upload and cleans up resources.
     *
     * <p>Use this to abort an in-progress or incomplete upload. This endpoint:
     * <ul>
     *   <li>Deletes the PENDING document record</li>
     *   <li>Cancels the storage upload session</li>
     *   <li>Releases reserved quota back to available</li>
     *   <li>Cleans up any partially uploaded data</li>
     * </ul>
     *
     * <p><b>Note:</b> Only PENDING documents can be cancelled. Active documents
     * must be deleted through the normal delete flow.
     *
     * @param documentId the PENDING document to cancel
     * @return 200 OK on successful cancellation
     * @throws com.teamsync.common.exception.ResourceNotFoundException if document not found
     * @throws IllegalStateException if document is not in PENDING status
     */
    @PostMapping("/{documentId}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelUpload(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid document ID format")
            String documentId) {

        log.info("POST /api/documents/upload/{}/cancel", documentId);

        uploadService.cancelUpload(documentId);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Upload cancelled. Document and storage session cleaned up.")
                .build());
    }

    /**
     * Uploads a small file directly through the backend (recommended for files &lt;10MB).
     *
     * <p>This endpoint provides a simple single-request upload flow:
     * <ol>
     *   <li>File streams through Content Service → Storage Service → MinIO</li>
     *   <li>Document record is created as ACTIVE immediately</li>
     *   <li>Quota is allocated in a single transaction</li>
     *   <li>No presigned URLs - MinIO remains completely internal</li>
     * </ol>
     *
     * <p><b>Advantages:</b>
     * <ul>
     *   <li>Single HTTP request (simpler client code)</li>
     *   <li>Atomic operation (no cleanup needed on failure)</li>
     *   <li>No presigned URL management</li>
     * </ul>
     *
     * <p><b>Trade-offs:</b>
     * <ul>
     *   <li>Higher latency (file proxied through backend)</li>
     *   <li>More memory/CPU on backend during upload</li>
     *   <li>10MB size limit (use presigned flow for larger files)</li>
     * </ul>
     *
     * @param file          the file to upload (multipart form data)
     * @param folderId      optional target folder (null for root)
     * @param description   optional document description
     * @param documentTypeId optional business document type
     * @param tags          optional list of tags
     * @return 201 Created with the new active document
     * @throws com.teamsync.common.exception.StorageQuotaExceededException if quota exceeded
     * @throws IllegalStateException if duplicate filename in folder
     */
    @PostMapping(value = "/direct", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DocumentDTO>> uploadFileDirect(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String folderId,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String documentTypeId,
            @RequestParam(required = false) List<String> tags) {

        log.info("POST /api/documents/upload/direct - filename: {}, size: {}",
                file.getOriginalFilename(), file.getSize());

        DocumentDTO document = uploadService.uploadFileDirect(file, folderId, description, documentTypeId, tags);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<DocumentDTO>builder()
                        .success(true)
                        .data(document)
                        .message("File uploaded successfully")
                        .build());
    }
}
