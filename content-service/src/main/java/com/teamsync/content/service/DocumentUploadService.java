package com.teamsync.content.service;

import com.teamsync.common.context.TenantContext;
import com.teamsync.common.dto.ApiResponse;
import com.teamsync.common.dto.storage.StorageUploadCompleteRequest;
import com.teamsync.common.dto.storage.StorageUploadCompleteResponse;
import com.teamsync.common.dto.storage.StorageUploadInitRequest;
import com.teamsync.common.dto.storage.StorageUploadInitResponse;
import com.teamsync.common.event.DocumentEvent;
import com.teamsync.common.exception.ResourceNotFoundException;
import com.teamsync.common.exception.ServiceUnavailableException;
import com.teamsync.common.model.Permission;
import com.teamsync.common.permission.RequiresPermission;
import com.teamsync.content.client.StorageDirectUploadClient;
import com.teamsync.content.client.StorageServiceClient;
import com.teamsync.content.dto.document.DocumentDTO;
import com.teamsync.content.dto.upload.DocumentUploadCompleteRequest;
import com.teamsync.content.dto.upload.DocumentUploadInitRequest;
import com.teamsync.content.dto.upload.DocumentUploadInitResponse;
import com.teamsync.content.dto.upload.UploadPrepareResponse;
import com.teamsync.content.mapper.DocumentMapper;
import com.teamsync.content.model.Document;
import com.teamsync.content.model.Document.DocumentStatus;
import com.teamsync.content.model.DocumentVersion;
import com.teamsync.content.repository.DocumentRepository;
import com.teamsync.content.repository.DocumentVersionRepository;
import com.teamsync.content.repository.FolderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for orchestrating document uploads with hybrid upload strategy.
 *
 * <h2>Overview</h2>
 * <p>This service manages the complete document upload lifecycle, coordinating between
 * Content Service (metadata) and Storage Service (file storage). The Content Service
 * owns the upload flow to ensure document records and storage are always in sync.</p>
 *
 * <h2>Upload Strategies</h2>
 * <p>The backend decides the upload strategy based on file size:</p>
 * <table border="1">
 *   <tr><th>Strategy</th><th>Trigger</th><th>Flow</th></tr>
 *   <tr>
 *     <td>DIRECT</td>
 *     <td>File &lt; 10MB (configurable)</td>
 *     <td>Frontend → Content Service → Storage Service → MinIO</td>
 *   </tr>
 *   <tr>
 *     <td>PRESIGNED</td>
 *     <td>File ≥ 10MB</td>
 *     <td>Frontend → API Gateway → Storage Proxy → MinIO (presigned URL)</td>
 *   </tr>
 * </table>
 *
 * <h2>Upload Flow (PRESIGNED strategy)</h2>
 * <pre>
 * 1. prepareUpload()     → Backend decides strategy based on fileSize
 * 2. initializeUpload()  → Creates PENDING document + storage session
 * 3. [Client uploads directly to presigned URL via API Gateway proxy]
 * 4. completeUpload()    → Activates document + finalizes storage
 *    OR cancelUpload()   → Cleans up PENDING document + storage session
 * </pre>
 *
 * <h2>Document State Machine</h2>
 * <pre>
 * DIRECT:    N/A → ACTIVE (single step)
 * PRESIGNED: PENDING_UPLOAD → ACTIVE (on complete)
 *            PENDING_UPLOAD → DELETED (on cancel or cleanup job)
 * </pre>
 *
 * <h2>Security Model</h2>
 * <ul>
 *   <li><b>Permission:</b> All operations require WRITE permission on the drive</li>
 *   <li><b>Tenant isolation:</b> Documents scoped to tenant and drive</li>
 *   <li><b>MinIO isolation:</b> MinIO never exposed externally; presigned URLs
 *       route through API Gateway storage-proxy</li>
 * </ul>
 *
 * <h2>Error Handling</h2>
 * <ul>
 *   <li><b>Storage failure on init:</b> Rolls back PENDING document</li>
 *   <li><b>Storage failure on complete:</b> Document remains PENDING for retry/cleanup</li>
 *   <li><b>Stale uploads:</b> {@code StalePendingDocumentCleanupJob} cleans up after 24h</li>
 * </ul>
 *
 * <h2>Configuration</h2>
 * <pre>
 * teamsync:
 *   upload:
 *     direct-max-size: 10485760  # 10MB threshold
 * </pre>
 *
 * @author TeamSync Platform Team
 * @version 1.0.0
 * @since Spring Boot 4.0.0
 * @see StorageServiceClient
 * @see StorageDirectUploadClient
 * @see com.teamsync.content.job.StalePendingDocumentCleanupJob
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentUploadService {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final FolderRepository folderRepository;
    private final StorageServiceClient storageServiceClient;
    private final StorageDirectUploadClient storageDirectUploadClient;
    private final DocumentMapper documentMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String DOCUMENT_EVENTS_TOPIC = "teamsync.content.events";

    // Max file size for direct upload (through backend streaming)
    // Files larger than this use presigned URLs via API Gateway proxy
    @Value("${teamsync.upload.direct-max-size:10485760}")
    private long directUploadMaxSize;

    /**
     * Prepares an upload by selecting the optimal strategy based on file size.
     *
     * <p>This is the entry point for all uploads. The backend decides the strategy:</p>
     * <ul>
     *   <li><b>DIRECT (fileSize &lt; threshold):</b> Returns endpoint for streaming upload
     *       through Content Service. Simpler flow, no presigned URLs.</li>
     *   <li><b>PRESIGNED (fileSize ≥ threshold):</b> Initializes storage session and
     *       returns presigned URLs for direct-to-storage upload via API Gateway proxy.</li>
     * </ul>
     *
     * <h3>Why Backend Decides</h3>
     * <p>Frontend doesn't know the threshold configuration. Having the backend decide
     * ensures consistent behavior and allows threshold changes without frontend updates.</p>
     *
     * <h3>Response Contents</h3>
     * <ul>
     *   <li>DIRECT: {@code directUploadUrl}, threshold info</li>
     *   <li>PRESIGNED: {@code sessionId}, {@code documentId}, presigned URLs, expiry</li>
     * </ul>
     *
     * @param request upload request containing filename, fileSize, contentType
     * @return strategy response with appropriate upload URLs
     * @throws AccessDeniedException if user lacks WRITE permission on the drive
     */
    @RequiresPermission(Permission.WRITE)
    public UploadPrepareResponse prepareUpload(DocumentUploadInitRequest request) {
        log.info("Preparing upload for file: {} size: {} (threshold: {})",
                request.getFilename(), request.getFileSize(), directUploadMaxSize);

        if (request.getFileSize() < directUploadMaxSize) {
            // Small file: return DIRECT strategy
            log.info("File size {} < threshold {}, using DIRECT strategy",
                    request.getFileSize(), directUploadMaxSize);
            return UploadPrepareResponse.directStrategy(directUploadMaxSize);
        } else {
            // Large file: initialize upload and return PRESIGNED strategy
            log.info("File size {} >= threshold {}, using PRESIGNED strategy",
                    request.getFileSize(), directUploadMaxSize);
            DocumentUploadInitResponse initResponse = initializeUpload(request);
            return UploadPrepareResponse.presignedStrategy(initResponse, directUploadMaxSize);
        }
    }

    /**
     * Uploads a small file directly through the backend (DIRECT strategy).
     *
     * <p>The file streams: Client → Content Service → Storage Service → MinIO.
     * No presigned URLs are involved, keeping MinIO completely internal.</p>
     *
     * <h3>Advantages</h3>
     * <ul>
     *   <li>Simpler flow - single request creates document and uploads file</li>
     *   <li>No presigned URL management or expiry concerns</li>
     *   <li>MinIO never exposed, even through proxy</li>
     * </ul>
     *
     * <h3>Size Limit</h3>
     * <p>Enforces {@code directUploadMaxSize} limit (default 10MB). Files exceeding
     * this limit must use the PRESIGNED strategy via {@link #prepareUpload}.</p>
     *
     * <h3>Atomicity</h3>
     * <p>Uses {@code @Transactional} to ensure document metadata and storage upload
     * are committed together. If storage fails, the transaction rolls back.</p>
     *
     * <h3>Side Effects</h3>
     * <ul>
     *   <li>Creates ACTIVE document (skips PENDING state)</li>
     *   <li>Creates version 1 record</li>
     *   <li>Updates folder's documentCount and totalSize</li>
     *   <li>Publishes CREATED event to Kafka</li>
     * </ul>
     *
     * @param file           the multipart file to upload
     * @param folderId       target folder ID, or null for root
     * @param description    optional document description
     * @param documentTypeId optional business document type ID
     * @param tags           optional list of tags
     * @return the created document DTO
     * @throws IllegalArgumentException if file exceeds direct upload size limit
     * @throws IllegalArgumentException if duplicate name exists in folder
     * @throws ResourceNotFoundException if folder not found
     * @throws RuntimeException if storage upload fails (wraps underlying exception)
     * @throws AccessDeniedException if user lacks WRITE permission on the drive
     */
    @RequiresPermission(Permission.WRITE)
    @Transactional
    public DocumentDTO uploadFileDirect(MultipartFile file, String folderId,
                                         String description, String documentTypeId, List<String> tags) {
        String tenantId = TenantContext.getTenantId();
        String driveId = TenantContext.getDriveId();
        String userId = TenantContext.getUserId();

        String filename = file.getOriginalFilename();
        log.info("Direct upload for file: {} size: {} in folder: {} for tenant: {}",
                filename, file.getSize(), folderId, tenantId);

        // Enforce size limit for direct upload
        if (file.getSize() > directUploadMaxSize) {
            throw new IllegalArgumentException(
                    String.format("File too large for direct upload. Max size: %d bytes. Use /prepare endpoint for large files.",
                            directUploadMaxSize));
        }

        // Validate folder exists if specified
        if (folderId != null) {
            folderRepository.findByIdAndTenantIdAndDriveId(folderId, tenantId, driveId)
                    .orElseThrow(() -> new ResourceNotFoundException("Folder not found: " + folderId));
        }

        // Check for duplicate name in folder
        if (documentRepository.existsByTenantIdAndDriveIdAndFolderIdAndNameAndStatusNot(
                tenantId, driveId, folderId, filename, DocumentStatus.TRASHED)) {
            throw new IllegalArgumentException("Document with name '" + filename + "' already exists in this folder");
        }

        // Upload to storage via Storage Service direct upload
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        ApiResponse<StorageDirectUploadClient.DirectUploadResponse> storageResponse;
        try {
            storageResponse = storageDirectUploadClient.uploadFile(file, contentType);
        } catch (Exception e) {
            log.error("Failed to upload file to storage: {}", e.getMessage());
            throw new ServiceUnavailableException("Storage Service", "Failed to upload file");
        }

        StorageDirectUploadClient.DirectUploadResponse uploadResult = storageResponse.getData();

        // Extract extension
        String extension = extractExtension(filename);

        // Create ACTIVE document (no PENDING state needed for direct upload)
        Document document = Document.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .driveId(driveId)
                .folderId(folderId)
                .name(filename)
                .description(description)
                .contentType(contentType)
                .fileSize(file.getSize())
                .extension(extension)
                .documentTypeId(documentTypeId)
                .tags(tags)
                .storageKey(uploadResult.storageKey())
                .storageBucket(uploadResult.bucket())
                .status(DocumentStatus.ACTIVE)
                .currentVersion(1)
                .versionCount(1)
                .ownerId(userId)
                .createdBy(userId)
                .lastModifiedBy(userId)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .accessedAt(Instant.now())
                .build();

        documentRepository.save(document);

        // Create initial version
        DocumentVersion version = DocumentVersion.builder()
                .id(UUID.randomUUID().toString())
                .documentId(document.getId())
                .tenantId(tenantId)
                .versionNumber(1)
                .storageKey(document.getStorageKey())
                .storageBucket(document.getStorageBucket())
                .fileSize(document.getFileSize())
                .contentType(document.getContentType())
                .createdBy(userId)
                .createdAt(Instant.now())
                .build();

        versionRepository.save(version);

        // Update folder stats
        if (folderId != null) {
            folderRepository.incrementDocumentCount(folderId, tenantId, driveId, 1);
            folderRepository.incrementTotalSize(folderId, tenantId, driveId, file.getSize());
        }

        // Publish event
        publishDocumentEvent(document, DocumentEvent.EventType.CREATED);

        log.info("Direct upload completed for document: {}", document.getId());
        return documentMapper.toDTO(document);
    }

    /**
     * Initializes a presigned upload session (PRESIGNED strategy - step 1).
     *
     * <p>Creates a PENDING document and initializes a storage upload session.
     * The client then uploads directly to the presigned URL via API Gateway proxy.</p>
     *
     * <h3>Two-Phase Upload</h3>
     * <ol>
     *   <li><b>Initialize (this method):</b> Create metadata, get presigned URL</li>
     *   <li><b>Upload:</b> Client uploads to presigned URL (bypasses this service)</li>
     *   <li><b>Complete:</b> Call {@link #completeUpload} to activate document</li>
     * </ol>
     *
     * <h3>Document State</h3>
     * <p>Document is created with status {@code PENDING_UPLOAD}. It remains in this
     * state until {@link #completeUpload} is called or {@code StalePendingDocumentCleanupJob}
     * deletes it after 24 hours.</p>
     *
     * <h3>Storage Session</h3>
     * <p>The response includes:</p>
     * <ul>
     *   <li>{@code sessionId} - unique identifier for this upload session</li>
     *   <li>{@code uploadUrl} - presigned URL for simple upload</li>
     *   <li>{@code partUrls} - presigned URLs for multipart upload (large files)</li>
     *   <li>{@code expiresAt} - when presigned URLs expire</li>
     * </ul>
     *
     * <h3>Error Recovery</h3>
     * <p>If storage initialization fails, the PENDING document is automatically
     * deleted (rolled back) before the exception propagates.</p>
     *
     * @param request upload initialization request with file metadata
     * @return initialization response with document ID, session ID, and presigned URLs
     * @throws IllegalArgumentException if duplicate name exists in folder
     * @throws ResourceNotFoundException if folder not found
     * @throws RuntimeException if storage service initialization fails
     * @throws AccessDeniedException if user lacks WRITE permission on the drive
     */
    @RequiresPermission(Permission.WRITE)
    @Transactional
    public DocumentUploadInitResponse initializeUpload(DocumentUploadInitRequest request) {
        String tenantId = TenantContext.getTenantId();
        String driveId = TenantContext.getDriveId();
        String userId = TenantContext.getUserId();

        log.info("Initializing upload for file: {} in folder: {} for tenant: {}",
                request.getFilename(), request.getFolderId(), tenantId);

        // Validate folder exists if specified
        if (request.getFolderId() != null) {
            folderRepository.findByIdAndTenantIdAndDriveId(request.getFolderId(), tenantId, driveId)
                    .orElseThrow(() -> new ResourceNotFoundException("Folder not found: " + request.getFolderId()));
        }

        // Check for duplicate name in folder
        if (documentRepository.existsByTenantIdAndDriveIdAndFolderIdAndNameAndStatusNot(
                tenantId, driveId, request.getFolderId(), request.getFilename(), DocumentStatus.TRASHED)) {
            throw new IllegalArgumentException("Document with name '" + request.getFilename() + "' already exists in this folder");
        }

        // Extract extension
        String extension = extractExtension(request.getFilename());

        // Create PENDING document
        Document document = Document.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .driveId(driveId)
                .folderId(request.getFolderId())
                .name(request.getFilename())
                .description(request.getDescription())
                .contentType(request.getContentType())
                .fileSize(request.getFileSize())
                .extension(extension)
                .documentTypeId(request.getDocumentTypeId())
                .metadata(request.getMetadata())
                .tags(request.getTags())
                .status(DocumentStatus.PENDING_UPLOAD)
                .ownerId(userId)
                .createdBy(userId)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        documentRepository.save(document);

        // Call Storage Service to init upload
        StorageUploadInitRequest storageRequest = StorageUploadInitRequest.builder()
                .filename(request.getFilename())
                .fileSize(request.getFileSize())
                .contentType(request.getContentType())
                .useMultipart(request.getUseMultipart())
                .chunkSize(request.getChunkSize())
                .build();

        ApiResponse<StorageUploadInitResponse> storageResponse;
        try {
            storageResponse = storageServiceClient.initializeUpload(storageRequest);
        } catch (Exception e) {
            // Rollback: delete the pending document
            documentRepository.delete(document);
            log.error("Failed to initialize storage upload, rolling back document: {}", e.getMessage());
            throw new ServiceUnavailableException("Storage Service", "Failed to initialize upload session");
        }

        StorageUploadInitResponse uploadInit = storageResponse.getData();

        // Update document with storage details
        document.setStorageKey(uploadInit.getStorageKey());
        document.setStorageBucket(uploadInit.getBucket());
        document.setUploadSessionId(uploadInit.getSessionId());
        documentRepository.save(document);

        log.info("Upload initialized for document: {} with session: {}", document.getId(), uploadInit.getSessionId());

        // Build response
        return DocumentUploadInitResponse.builder()
                .documentId(document.getId())
                .sessionId(uploadInit.getSessionId())
                .uploadType(uploadInit.getUploadType())
                .uploadUrl(uploadInit.getUploadUrl())
                .partUrls(mapPartUrls(uploadInit.getPartUrls()))
                .totalParts(uploadInit.getTotalParts())
                .chunkSize(uploadInit.getChunkSize())
                .bucket(uploadInit.getBucket())
                .storageKey(uploadInit.getStorageKey())
                .expiresAt(uploadInit.getExpiresAt())
                .urlValiditySeconds(uploadInit.getUrlValiditySeconds())
                .build();
    }

    /**
     * Completes a presigned upload session and activates the document (PRESIGNED strategy - step 2).
     *
     * <p>Called after the client has successfully uploaded the file to the presigned URL.
     * This method finalizes the storage session and transitions the document from
     * PENDING_UPLOAD to ACTIVE status.</p>
     *
     * <h3>Validation</h3>
     * <ul>
     *   <li>Document must exist with PENDING_UPLOAD status</li>
     *   <li>Session ID must match the document's upload session</li>
     *   <li>For multipart uploads, part ETags must be provided</li>
     * </ul>
     *
     * <h3>Storage Finalization</h3>
     * <p>For multipart uploads, Storage Service assembles the parts into a single
     * object using the provided ETags. For simple uploads, it verifies the upload
     * completed successfully.</p>
     *
     * <h3>Side Effects</h3>
     * <ul>
     *   <li>Document status: PENDING_UPLOAD → ACTIVE</li>
     *   <li>Creates version 1 record with checksum</li>
     *   <li>Updates folder's documentCount and totalSize</li>
     *   <li>Clears uploadSessionId from document</li>
     *   <li>Publishes CREATED event to Kafka</li>
     * </ul>
     *
     * <h3>Multipart ETags</h3>
     * <p>For multipart uploads, the client must provide the ETag returned by S3 for
     * each part. These are used to assemble and verify the final object.</p>
     *
     * @param request completion request with documentId, sessionId, and optional part ETags
     * @return the activated document DTO
     * @throws ResourceNotFoundException if PENDING document not found
     * @throws IllegalArgumentException if session ID doesn't match
     * @throws RuntimeException if storage completion fails
     * @throws AccessDeniedException if user lacks WRITE permission on the drive
     */
    @RequiresPermission(Permission.WRITE)
    @Transactional
    public DocumentDTO completeUpload(DocumentUploadCompleteRequest request) {
        String tenantId = TenantContext.getTenantId();
        String driveId = TenantContext.getDriveId();
        String userId = TenantContext.getUserId();

        log.info("Completing upload for document: {} session: {}", request.getDocumentId(), request.getSessionId());

        // Find PENDING document
        Document document = documentRepository.findByIdAndTenantIdAndDriveIdAndStatus(
                        request.getDocumentId(), tenantId, driveId, DocumentStatus.PENDING_UPLOAD)
                .orElseThrow(() -> new ResourceNotFoundException("Pending document not found: " + request.getDocumentId()));

        // Verify session ID matches
        if (!request.getSessionId().equals(document.getUploadSessionId())) {
            throw new IllegalArgumentException("Session ID does not match document");
        }

        // Complete storage upload
        StorageUploadCompleteRequest storageRequest = StorageUploadCompleteRequest.builder()
                .sessionId(request.getSessionId())
                .parts(mapPartsToStorage(request.getParts()))
                .build();

        ApiResponse<StorageUploadCompleteResponse> storageResponse;
        try {
            storageResponse = storageServiceClient.completeUpload(storageRequest);
        } catch (Exception e) {
            log.error("Failed to complete storage upload: {}", e.getMessage());
            throw new ServiceUnavailableException("Storage Service", "Failed to complete upload");
        }

        StorageUploadCompleteResponse uploadComplete = storageResponse.getData();

        // SECURITY FIX (Round 6): Verify file actually exists in storage before activation
        // This prevents bypass attacks where completeUpload is called without actual file upload
        if (uploadComplete == null || uploadComplete.getFileSize() == null || uploadComplete.getFileSize() <= 0) {
            log.error("SECURITY: Upload complete called but storage returned no file data for document: {}",
                    request.getDocumentId());
            throw new IllegalStateException("Upload verification failed: file not found in storage");
        }

        // SECURITY FIX (Round 6): Verify checksum exists - ensures data integrity
        if (uploadComplete.getChecksum() == null || uploadComplete.getChecksum().isBlank()) {
            log.error("SECURITY: Upload complete but no checksum returned for document: {}",
                    request.getDocumentId());
            throw new IllegalStateException("Upload verification failed: no checksum from storage");
        }

        // Activate document
        document.setStatus(DocumentStatus.ACTIVE);
        document.setFileSize(uploadComplete.getFileSize());
        document.setContentType(uploadComplete.getContentType());
        document.setChecksum(uploadComplete.getChecksum());
        document.setCurrentVersion(1);
        document.setVersionCount(1);
        document.setLastModifiedBy(userId);
        document.setUpdatedAt(Instant.now());
        document.setAccessedAt(Instant.now());
        document.setUploadSessionId(null); // Clear session reference

        documentRepository.save(document);

        // Create initial version
        DocumentVersion version = DocumentVersion.builder()
                .id(UUID.randomUUID().toString())
                .documentId(document.getId())
                .tenantId(tenantId)
                .versionNumber(1)
                .storageKey(document.getStorageKey())
                .storageBucket(document.getStorageBucket())
                .fileSize(document.getFileSize())
                .contentType(document.getContentType())
                .checksum(uploadComplete.getChecksum())
                .createdBy(userId)
                .createdAt(Instant.now())
                .build();

        versionRepository.save(version);

        // Update folder stats
        if (document.getFolderId() != null) {
            folderRepository.incrementDocumentCount(document.getFolderId(), tenantId, driveId, 1);
            if (document.getFileSize() != null) {
                folderRepository.incrementTotalSize(document.getFolderId(), tenantId, driveId, document.getFileSize());
            }
        }

        // Publish event
        publishDocumentEvent(document, DocumentEvent.EventType.CREATED);

        log.info("Upload completed for document: {}", document.getId());
        return documentMapper.toDTO(document);
    }

    /**
     * Cancels an in-progress presigned upload session.
     *
     * <p>Called when the user abandons an upload or an error occurs during file transfer.
     * This method cleans up both the PENDING document and the storage session.</p>
     *
     * <h3>Cleanup Behavior</h3>
     * <ol>
     *   <li>Cancels storage upload session (aborts multipart, deletes partial data)</li>
     *   <li>Deletes PENDING document from database</li>
     * </ol>
     *
     * <h3>Graceful Degradation</h3>
     * <p>If storage cancellation fails (e.g., session already expired), the method
     * logs a warning but continues to delete the document. Storage cleanup job will
     * handle orphaned data.</p>
     *
     * <h3>Idempotency</h3>
     * <p>Calling cancel on an already-cancelled or completed upload throws
     * {@link ResourceNotFoundException} since the PENDING document no longer exists.</p>
     *
     * @param documentId the PENDING document ID to cancel
     * @throws ResourceNotFoundException if PENDING document not found
     * @throws AccessDeniedException if user lacks WRITE permission on the drive
     */
    @RequiresPermission(Permission.WRITE)
    @Transactional
    public void cancelUpload(String documentId) {
        String tenantId = TenantContext.getTenantId();
        String driveId = TenantContext.getDriveId();

        log.info("Cancelling upload for document: {}", documentId);

        // Find PENDING document
        Document document = documentRepository.findByIdAndTenantIdAndDriveIdAndStatus(
                        documentId, tenantId, driveId, DocumentStatus.PENDING_UPLOAD)
                .orElseThrow(() -> new ResourceNotFoundException("Pending document not found: " + documentId));

        // Cancel storage upload
        if (document.getUploadSessionId() != null) {
            try {
                storageServiceClient.cancelUpload(document.getUploadSessionId());
            } catch (Exception e) {
                log.warn("Failed to cancel storage upload session: {}", e.getMessage());
                // Continue with document deletion even if storage cancel fails
            }
        }

        // Delete PENDING document
        documentRepository.delete(document);

        log.info("Upload cancelled for document: {}", documentId);
    }

    // ==================== Private Helper Methods ====================

    /**
     * Extracts the file extension from a filename.
     *
     * @param filename the filename to extract extension from
     * @return lowercase extension without dot, or null if no extension
     */
    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return null;
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    /**
     * Maps storage service part URLs to content service DTO format.
     *
     * @param storageUrls part URLs from storage service
     * @return mapped part URLs for response, or null if input is null
     */
    private List<DocumentUploadInitResponse.PartUploadUrl> mapPartUrls(List<StorageUploadInitResponse.PartUploadUrl> storageUrls) {
        if (storageUrls == null) {
            return null;
        }
        return storageUrls.stream()
                .map(p -> DocumentUploadInitResponse.PartUploadUrl.builder()
                        .partNumber(p.getPartNumber())
                        .uploadUrl(p.getUploadUrl())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Maps client part ETags to storage service request format.
     *
     * @param parts part ETags from client completion request
     * @return mapped part ETags for storage service, or null if input is null
     */
    private List<StorageUploadCompleteRequest.PartETag> mapPartsToStorage(List<DocumentUploadCompleteRequest.PartETag> parts) {
        if (parts == null) {
            return null;
        }
        return parts.stream()
                .map(p -> StorageUploadCompleteRequest.PartETag.builder()
                        .partNumber(p.getPartNumber())
                        .etag(p.getEtag())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Publishes a document lifecycle event to Kafka for async processing.
     *
     * <p>Events trigger downstream processing like:</p>
     * <ul>
     *   <li>Thumbnail generation (CREATED)</li>
     *   <li>Text extraction (CREATED)</li>
     *   <li>Search indexing (CREATED, UPDATED)</li>
     *   <li>Activity logging (all events)</li>
     * </ul>
     *
     * <p>Event publishing is fire-and-forget. Failures are logged but don't
     * fail the main operation.</p>
     *
     * @param document  the document to publish event for
     * @param eventType the type of event (CREATED, UPDATED, DELETED, etc.)
     */
    private void publishDocumentEvent(Document document, DocumentEvent.EventType eventType) {
        try {
            DocumentEvent event = DocumentEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(eventType.name())
                    .documentId(document.getId())
                    .tenantId(document.getTenantId())
                    .driveId(document.getDriveId())
                    .userId(TenantContext.getUserId())
                    .timestamp(Instant.now())
                    .build();

            kafkaTemplate.send(DOCUMENT_EVENTS_TOPIC, document.getId(), event);
            log.debug("Published document event: {} for document: {}", eventType, document.getId());
        } catch (Exception e) {
            log.error("Failed to publish document event: {}", e.getMessage());
            // Don't fail the operation if event publishing fails
        }
    }
}
