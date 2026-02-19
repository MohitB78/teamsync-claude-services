package com.teamsync.content.service;

import com.teamsync.common.context.TenantContext;
import com.teamsync.common.dto.CursorPage;
import com.teamsync.common.event.DocumentEvent;
import com.teamsync.common.exception.AccessDeniedException;
import com.teamsync.common.exception.ResourceNotFoundException;
import com.teamsync.common.model.Permission;
import com.teamsync.common.permission.PermissionService;
import com.teamsync.common.permission.RequiresPermission;
import com.teamsync.common.storage.CloudStorageProvider;
import com.teamsync.common.util.DownloadTokenUtil;
import com.teamsync.content.client.StorageServiceClient;
import com.teamsync.content.dto.document.CreateDocumentRequest;
import com.teamsync.content.dto.document.DocumentDTO;
import com.teamsync.content.dto.document.UpdateDocumentRequest;
import com.teamsync.content.mapper.DocumentMapper;
import com.teamsync.content.model.Document;
import com.teamsync.content.model.Document.DocumentStatus;
import com.teamsync.content.model.DocumentVersion;
import com.teamsync.content.repository.DocumentRepository;
import com.teamsync.content.repository.DocumentVersionRepository;
import com.teamsync.content.repository.FolderRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.annotation.Observed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.teamsync.content.config.CacheConfig.DOCUMENTS_CACHE;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Core service for document management operations in the TeamSync platform.
 *
 * <h2>Overview</h2>
 * <p>This service provides comprehensive document lifecycle management including
 * CRUD operations, versioning, locking, trash/restore, and file downloads.
 * All operations enforce Drive-Level RBAC via {@link RequiresPermission} annotations.</p>
 *
 * <h2>Security Model</h2>
 * <p>Documents are isolated by tenant and drive. Every operation requires:</p>
 * <ul>
 *   <li><b>Tenant isolation</b>: All queries filter by {@code tenantId}</li>
 *   <li><b>Drive isolation</b>: All queries filter by {@code driveId}</li>
 *   <li><b>Permission check</b>: {@code @RequiresPermission} enforced via AOP</li>
 * </ul>
 *
 * <h2>Permission Requirements</h2>
 * <table border="1">
 *   <tr><th>Operation</th><th>Permission</th><th>Method</th></tr>
 *   <tr><td>View documents</td><td>READ</td><td>{@link #getDocument}, {@link #listDocuments}</td></tr>
 *   <tr><td>Create/Update</td><td>WRITE</td><td>{@link #createDocument}, {@link #updateDocument}</td></tr>
 *   <tr><td>Delete</td><td>DELETE</td><td>{@link #trashDocument}, {@link #deleteDocument}</td></tr>
 *   <tr><td>Lock/Unlock</td><td>WRITE</td><td>{@link #lockDocument}, {@link #unlockDocument}</td></tr>
 * </table>
 *
 * <h2>Edge Cases Handled</h2>
 * <ul>
 *   <li><b>Duplicate names</b>: Checked before create/update/restore</li>
 *   <li><b>Document locking</b>: Prevents concurrent edits, only lock owner can unlock</li>
 *   <li><b>Optimistic locking</b>: {@code @Version} field + {@code If-Match} header</li>
 *   <li><b>Two-stage deletion</b>: Trash (soft) → Permanent delete (hard)</li>
 *   <li><b>Restore conflicts</b>: Appends timestamp if name exists</li>
 *   <li><b>Download tokens</b>: HMAC-SHA256 signed for browser downloads</li>
 * </ul>
 *
 * <h2>Event Publishing</h2>
 * <p>All mutations publish events to Kafka topic {@code teamsync.content.events}
 * for downstream processing (search indexing, notifications, audit trail).</p>
 *
 * <h2>Dependencies</h2>
 * <ul>
 *   <li>{@link DocumentRepository}: MongoDB persistence</li>
 *   <li>{@link CloudStorageProvider}: File storage abstraction (MinIO/S3/GCS)</li>
 *   <li>{@link StorageServiceClient}: Storage Service HTTP client for quota management</li>
 *   <li>{@link PermissionService}: Drive-Level RBAC checking</li>
 *   <li>{@link KafkaTemplate}: Event publishing</li>
 * </ul>
 *
 * @author TeamSync Platform Team
 * @version 1.0.0
 * @since Spring Boot 4.0.0
 * @see Document
 * @see RequiresPermission
 * @see TenantContext
 */
@Service
@Slf4j
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final FolderRepository folderRepository;
    private final CloudStorageProvider storageProvider;
    private final StorageServiceClient storageServiceClient;
    private final DocumentMapper documentMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final PermissionService permissionService;
    private final DownloadTokenUtil downloadTokenUtil;
    private final AccessTrackingService accessTrackingService;

    // Metrics for monitoring storage deletion failures
    private final Counter storageDeleteFailureCounter;
    private final Counter eventPublishFailureCounter;

    @org.springframework.beans.factory.annotation.Value("${teamsync.content.service-url:http://localhost:9081}")
    private String contentServiceUrl;

    @org.springframework.beans.factory.annotation.Value("${teamsync.storage.default-bucket:teamsync-documents}")
    private String defaultBucket;

    private static final String DOCUMENT_EVENTS_TOPIC = "teamsync.content.events";

    public DocumentService(
            DocumentRepository documentRepository,
            DocumentVersionRepository versionRepository,
            FolderRepository folderRepository,
            CloudStorageProvider storageProvider,
            StorageServiceClient storageServiceClient,
            DocumentMapper documentMapper,
            KafkaTemplate<String, Object> kafkaTemplate,
            PermissionService permissionService,
            DownloadTokenUtil downloadTokenUtil,
            AccessTrackingService accessTrackingService,
            MeterRegistry meterRegistry) {
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
        this.folderRepository = folderRepository;
        this.storageProvider = storageProvider;
        this.storageServiceClient = storageServiceClient;
        this.documentMapper = documentMapper;
        this.kafkaTemplate = kafkaTemplate;
        this.permissionService = permissionService;
        this.downloadTokenUtil = downloadTokenUtil;
        this.accessTrackingService = accessTrackingService;

        // Register metrics for monitoring
        this.storageDeleteFailureCounter = Counter.builder("teamsync.storage.delete.failures")
                .description("Number of failed storage deletion operations")
                .tag("service", "content-service")
                .register(meterRegistry);

        this.eventPublishFailureCounter = Counter.builder("teamsync.events.publish.failures")
                .description("Number of failed Kafka event publish operations")
                .tag("service", "content-service")
                .tag("topic", DOCUMENT_EVENTS_TOPIC)
                .register(meterRegistry);
    }

    /**
     * Retrieves a document by ID with full tenant and drive isolation.
     *
     * <p>This method performs a secure document lookup ensuring the document
     * belongs to the current tenant AND drive context. The document's
     * {@code accessedAt} timestamp is updated for recency tracking.</p>
     *
     * <h3>Security</h3>
     * <ul>
     *   <li>Requires {@link Permission#READ} on the current drive</li>
     *   <li>Uses compound query: {@code tenantId + driveId + documentId}</li>
     *   <li>Context extracted from {@link TenantContext} ThreadLocal</li>
     * </ul>
     *
     * <h3>Edge Cases</h3>
     * <ul>
     *   <li>Document not found: throws {@link ResourceNotFoundException}</li>
     *   <li>Wrong drive: throws {@link ResourceNotFoundException} (document not visible)</li>
     *   <li>Trashed documents: still returned if status filter not applied</li>
     * </ul>
     *
     * @param documentId the unique document identifier (UUID)
     * @return the document DTO with all metadata
     * @throws ResourceNotFoundException if document not found in current tenant/drive
     * @throws AccessDeniedException if user lacks READ permission on the drive
     */
    @RequiresPermission(Permission.READ)
    @Observed(name = "document.get", contextualName = "get-document-by-id")
    public DocumentDTO getDocument(String documentId) {
        String tenantId = TenantContext.getTenantId();
        String driveId = TenantContext.getDriveId();

        log.debug("Fetching document: {} for tenant: {}, drive: {}", documentId, tenantId, driveId);

        Document document = documentRepository.findByIdAndTenantIdAndDriveId(documentId, tenantId, driveId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        // Buffer access time update asynchronously to avoid write amplification
        accessTrackingService.recordDocumentAccess(tenantId, driveId, documentId, Instant.now());

        return documentMapper.toDTO(document);
    }

    /**
     * Retrieves a document by ID with tenant isolation and drive-level permission check.
     *
     * <p>This method is designed for cross-drive document access scenarios, such as:</p>
     * <ul>
     *   <li>Document viewer pages with direct URLs</li>
     *   <li>Shared document access where drive context is unknown</li>
     *   <li>Search result previews spanning multiple drives</li>
     * </ul>
     *
     * <h3>Security Model</h3>
     * <p>This method enforces both tenant isolation AND drive-level RBAC:</p>
     * <ul>
     *   <li>Document must belong to the current tenant</li>
     *   <li>User must have READ permission on the document's drive</li>
     *   <li>Permission check is performed against the document's actual driveId</li>
     * </ul>
     *
     * <h3>Behavior</h3>
     * <ul>
     *   <li>Only returns ACTIVE documents (filters out TRASHED, PENDING, ARCHIVED)</li>
     *   <li>Updates {@code accessedAt} timestamp for recency tracking</li>
     * </ul>
     *
     * @param documentId the unique document identifier (UUID)
     * @return the document DTO with all metadata
     * @throws ResourceNotFoundException if document not found or not ACTIVE
     * @throws AccessDeniedException if user lacks READ permission on the document's drive
     */
    @Observed(name = "document.get.byId", contextualName = "get-document-cross-drive")
    public DocumentDTO getDocumentById(String documentId) {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();

        log.debug("Fetching document by ID only: {} for tenant: {}, user: {}", documentId, tenantId, userId);

        Document document = documentRepository.findByIdAndTenantId(documentId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        // Check if document is active
        if (document.getStatus() != DocumentStatus.ACTIVE) {
            throw new ResourceNotFoundException("Document not found: " + documentId);
        }

        // SECURITY FIX: Check permission on document's drive
        // Even without X-Drive-ID header, user must have READ permission on document's drive
        boolean hasAccess = permissionService.hasPermission(userId, document.getDriveId(), Permission.READ);
        if (!hasAccess) {
            log.warn("Access denied: user {} attempted to access document {} in drive {} without permission",
                    userId, documentId, document.getDriveId());
            throw new AccessDeniedException("Access denied to document: " + documentId);
        }

        // Buffer access time update asynchronously to avoid write amplification
        accessTrackingService.recordDocumentAccess(tenantId, document.getDriveId(), documentId, Instant.now());

        return documentMapper.toDTO(document);
    }

    /**
     * Lists documents in a folder using cursor-based pagination.
     *
     * <p>This method implements scalable pagination that works efficiently even
     * with millions of documents. Unlike offset-based pagination, cursor pagination
     * maintains consistent performance regardless of page depth.</p>
     *
     * <h3>Pagination Strategy</h3>
     * <p>Uses the document's {@code _id} as the cursor (MongoDB ObjectId ensures
     * natural ordering). The query fetches {@code limit + 1} records to determine
     * if more pages exist without a separate count query.</p>
     *
     * <h3>Folder Handling</h3>
     * <ul>
     *   <li>{@code folderId = null}: Returns root-level documents</li>
     *   <li>{@code folderId = "folder-uuid"}: Returns documents in that folder</li>
     *   <li>Only ACTIVE documents are returned (excludes TRASHED, PENDING)</li>
     * </ul>
     *
     * <h3>Performance</h3>
     * <ul>
     *   <li>Uses compound index: {@code tenant_drive_folder_idx}</li>
     *   <li>O(log n) query performance with B-tree index</li>
     *   <li>No skip() operation (which degrades with offset)</li>
     * </ul>
     *
     * @param folderId the parent folder ID, or null for root level
     * @param cursor   the cursor from previous page (document ID), or null for first page
     * @param limit    maximum number of documents to return (recommended: 50)
     * @return a cursor page containing documents and pagination metadata
     * @throws AccessDeniedException if user lacks READ permission on the drive
     * @see CursorPage
     */
    /**
     * SECURITY FIX (Round 6): MongoDB ObjectId pattern for validation.
     * ObjectIds are 24 hex characters. This prevents injection via cursor or folderId.
     */
    private static final java.util.regex.Pattern MONGO_ID_PATTERN =
            java.util.regex.Pattern.compile("^[a-fA-F0-9]{24}$");

    @RequiresPermission(Permission.READ)
    @Observed(name = "document.list", contextualName = "list-documents-in-folder")
    public CursorPage<DocumentDTO> listDocuments(String folderId, String cursor, int limit) {
        String tenantId = TenantContext.getTenantId();
        String driveId = TenantContext.getDriveId();

        // SECURITY FIX (Round 6): Validate folderId format to prevent NoSQL injection
        // FolderId should be a valid MongoDB ObjectId (24 hex chars) or null
        if (folderId != null && !folderId.isEmpty() && !MONGO_ID_PATTERN.matcher(folderId).matches()) {
            log.warn("SECURITY: Invalid folderId format attempted: {} by user in tenant {}",
                    folderId.substring(0, Math.min(folderId.length(), 50)), tenantId);
            throw new IllegalArgumentException("Invalid folder ID format");
        }

        // SECURITY FIX (Round 6): Validate cursor format to prevent NoSQL injection
        // Cursor is a document ID (MongoDB ObjectId)
        if (cursor != null && !cursor.isEmpty() && !MONGO_ID_PATTERN.matcher(cursor).matches()) {
            log.warn("SECURITY: Invalid cursor format attempted: {} by user in tenant {}",
                    cursor.substring(0, Math.min(cursor.length(), 50)), tenantId);
            throw new IllegalArgumentException("Invalid cursor format");
        }

        log.debug("Listing documents in folder: {} for tenant: {}, drive: {}", folderId, tenantId, driveId);

        Pageable pageable = PageRequest.of(0, limit + 1, Sort.by(Sort.Direction.ASC, "_id"));
        List<Document> documents;

        if (cursor != null && !cursor.isEmpty()) {
            documents = documentRepository.findByTenantIdAndDriveIdAndFolderIdAndStatusAfterCursor(
                    tenantId, driveId, folderId, DocumentStatus.ACTIVE, cursor, pageable);
        } else if (folderId == null) {
            documents = documentRepository.findRootDocuments(tenantId, driveId, DocumentStatus.ACTIVE, pageable);
        } else {
            documents = documentRepository.findByTenantIdAndDriveIdAndFolderIdAndStatus(
                    tenantId, driveId, folderId, DocumentStatus.ACTIVE, pageable);
        }

        boolean hasMore = documents.size() > limit;
        if (hasMore) {
            documents = documents.subList(0, limit);
        }

        String nextCursor = hasMore && !documents.isEmpty()
                ? documents.get(documents.size() - 1).getId()
                : null;

        List<DocumentDTO> dtos = documents.stream()
                .map(documentMapper::toDTO)
                .toList();

        return CursorPage.<DocumentDTO>builder()
                .items(dtos)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .limit(limit)
                .build();
    }

    /**
     * Creates a new document in the specified folder.
     *
     * <p>This method creates a document record along with its initial version (v1).
     * The actual file content should already be uploaded to storage; this method
     * records the metadata and storage location.</p>
     *
     * <h3>Creation Flow</h3>
     * <ol>
     *   <li>Validate duplicate name doesn't exist in target folder</li>
     *   <li>Create document record with ACTIVE status</li>
     *   <li>Create initial DocumentVersion (v1)</li>
     *   <li>Update parent folder statistics (documentCount, totalSize)</li>
     *   <li>Publish CREATED event to Kafka</li>
     * </ol>
     *
     * <h3>Edge Cases</h3>
     * <ul>
     *   <li><b>Duplicate name:</b> Throws {@link IllegalArgumentException} (checked against non-TRASHED documents)</li>
     *   <li><b>Root folder:</b> Pass {@code folderId = null} for root-level documents</li>
     *   <li><b>Extension extraction:</b> Automatically extracted from filename (e.g., "file.pdf" → "pdf")</li>
     * </ul>
     *
     * <h3>Folder Statistics</h3>
     * <p>Parent folder's {@code documentCount} and {@code totalSize} are atomically
     * incremented using MongoDB's $inc operator for consistency.</p>
     *
     * @param request the document creation request containing name, folderId, contentType, etc.
     * @return the created document DTO with generated ID
     * @throws IllegalArgumentException if a document with the same name exists in the folder
     * @throws AccessDeniedException if user lacks WRITE permission on the drive
     * @see DocumentVersion
     */
    @RequiresPermission(Permission.WRITE)
    @Transactional
    @Observed(name = "document.create", contextualName = "create-document")
    public DocumentDTO createDocument(CreateDocumentRequest request) {
        String tenantId = TenantContext.getTenantId();
        String driveId = TenantContext.getDriveId();
        String userId = TenantContext.getUserId();

        log.info("Creating document: {} in folder: {} for tenant: {}", request.getName(), request.getFolderId(), tenantId);

        // Check for duplicate name in folder
        if (documentRepository.existsByTenantIdAndDriveIdAndFolderIdAndNameAndStatusNot(
                tenantId, driveId, request.getFolderId(), request.getName(), DocumentStatus.TRASHED)) {
            throw new IllegalArgumentException("Document with name '" + request.getName() + "' already exists in this folder");
        }

        // Extract extension from name
        String extension = extractExtension(request.getName());

        Document document = Document.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .driveId(driveId)
                .folderId(request.getFolderId())
                .name(request.getName())
                .description(request.getDescription())
                .contentType(request.getContentType())
                .fileSize(request.getFileSize())
                .extension(extension)
                .storageKey(request.getStorageKey())
                .storageBucket(request.getStorageBucket())
                .documentTypeId(request.getDocumentTypeId())
                .metadata(request.getMetadata())
                .currentVersion(1)
                .versionCount(1)
                .tags(request.getTags())
                .isStarred(false)
                .isPinned(false)
                .ownerId(userId)
                .createdBy(userId)
                .lastModifiedBy(userId)
                .status(DocumentStatus.ACTIVE)
                .isLocked(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .accessedAt(Instant.now())
                .build();

        Document savedDocument;
        try {
            savedDocument = documentRepository.save(document);
        } catch (DuplicateKeyException e) {
            // SECURITY FIX: Unique index prevents race condition - two concurrent creates
            // with same name will have one fail at database level
            log.warn("Duplicate document name detected via unique index: {} in folder: {}",
                    request.getName(), request.getFolderId());
            throw new IllegalArgumentException(
                    "Document with name '" + request.getName() + "' already exists in this folder");
        }

        // Create initial version
        DocumentVersion version = DocumentVersion.builder()
                .id(UUID.randomUUID().toString())
                .documentId(savedDocument.getId())
                .tenantId(tenantId)
                .versionNumber(1)
                .storageKey(request.getStorageKey())
                .storageBucket(request.getStorageBucket())
                .fileSize(request.getFileSize())
                .contentType(request.getContentType())
                .checksum(null)
                .createdBy(userId)
                .createdAt(Instant.now())
                .build();

        versionRepository.save(version);

        // SECURITY FIX: Use atomic update for folder stats to prevent inconsistent state
        if (request.getFolderId() != null) {
            long fileSize = request.getFileSize() != null ? request.getFileSize() : 0L;
            folderRepository.atomicUpdateDocumentStats(
                    request.getFolderId(), tenantId, driveId,
                    1, fileSize, Instant.now());
        }

        // Publish event
        publishDocumentEvent(savedDocument, DocumentEvent.EventType.CREATED);

        log.info("Document created successfully: {}", savedDocument.getId());
        return documentMapper.toDTO(savedDocument);
    }

    /**
     * Updates document metadata with optimistic locking support.
     *
     * <p>This method updates non-null fields in the request, leaving other fields unchanged.
     * It supports optimistic locking to detect concurrent modifications and prevent
     * lost updates in collaborative editing scenarios.</p>
     *
     * <h3>Optimistic Locking</h3>
     * <p>When {@code expectedVersion} is provided (from {@code If-Match} header):</p>
     * <ol>
     *   <li>Compare with document's {@code entityVersion} (MongoDB @Version field)</li>
     *   <li>If mismatch: throw {@link OptimisticLockingFailureException}</li>
     *   <li>If match: proceed with update (MongoDB auto-increments version)</li>
     * </ol>
     *
     * <h3>Document Locking</h3>
     * <p>If the document is locked by another user, the update is rejected.
     * The lock owner can update freely.</p>
     *
     * <h3>Partial Updates</h3>
     * <p>Only non-null fields in the request are updated:</p>
     * <ul>
     *   <li>{@code name}: Updates name and recalculates extension</li>
     *   <li>{@code description}: Updates description</li>
     *   <li>{@code documentTypeId}: Changes business document type</li>
     *   <li>{@code metadata}: Replaces custom metadata map</li>
     *   <li>{@code tags}: Replaces tags list</li>
     *   <li>{@code isStarred}, {@code isPinned}: User preference flags</li>
     * </ul>
     *
     * @param documentId      the document ID to update
     * @param request         the update request with fields to change
     * @param expectedVersion optional expected entity version for optimistic locking
     * @return the updated document DTO
     * @throws ResourceNotFoundException if document not found
     * @throws OptimisticLockingFailureException if version mismatch (concurrent modification)
     * @throws AccessDeniedException if document is locked by another user or user lacks WRITE permission
     * @throws IllegalArgumentException if renaming to a duplicate name
     */
    @RequiresPermission(Permission.WRITE)
    @Transactional
    @CacheEvict(cacheNames = DOCUMENTS_CACHE, key = "T(com.teamsync.common.context.TenantContext).getTenantId() + ':' + T(com.teamsync.common.context.TenantContext).getDriveId() + ':' + #documentId")
    public DocumentDTO updateDocument(String documentId, UpdateDocumentRequest request, Long expectedVersion) {
        String tenantId = TenantContext.getTenantId();
        String driveId = TenantContext.getDriveId();
        String userId = TenantContext.getUserId();

        log.info("Updating document: {} for tenant: {}", documentId, tenantId);

        Document document = documentRepository.findByIdAndTenantIdAndDriveId(documentId, tenantId, driveId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        // Optimistic locking check - detect concurrent edits
        if (expectedVersion != null && !expectedVersion.equals(document.getEntityVersion())) {
            log.warn("Optimistic lock failure: document {} expected version {} but found {}",
                    documentId, expectedVersion, document.getEntityVersion());
            throw new OptimisticLockingFailureException(
                    "Document has been modified by another user. Please refresh and try again.");
        }

        // Check if document is locked by another user
        if (document.getIsLocked() && !userId.equals(document.getLockedBy())) {
            throw new AccessDeniedException("Document is locked by another user");
        }

        // Update fields if provided
        if (request.getName() != null) {
            // Check for duplicate name
            if (!document.getName().equals(request.getName()) &&
                    documentRepository.existsByTenantIdAndDriveIdAndFolderIdAndNameAndStatusNot(
                            tenantId, driveId, document.getFolderId(), request.getName(), DocumentStatus.TRASHED)) {
                throw new IllegalArgumentException("Document with name '" + request.getName() + "' already exists in this folder");
            }
            document.setName(request.getName());
            document.setExtension(extractExtension(request.getName()));
        }

        if (request.getDescription() != null) {
            document.setDescription(request.getDescription());
        }

        if (request.getDocumentTypeId() != null) {
            document.setDocumentTypeId(request.getDocumentTypeId());
        }

        if (request.getMetadata() != null) {
            document.setMetadata(request.getMetadata());
        }

        if (request.getTags() != null) {
            document.setTags(request.getTags());
        }

        if (request.getIsStarred() != null) {
            document.setIsStarred(request.getIsStarred());
        }

        if (request.getIsPinned() != null) {
            document.setIsPinned(request.getIsPinned());
        }

        document.setLastModifiedBy(userId);
        document.setUpdatedAt(Instant.now());

        Document savedDocument;
        try {
            savedDocument = documentRepository.save(document);
        } catch (DuplicateKeyException e) {
            // SECURITY FIX: Unique index prevents race condition during rename
            log.warn("Duplicate document name detected via unique index during rename: {}", document.getName());
            throw new IllegalArgumentException(
                    "Document with name '" + document.getName() + "' already exists in this folder");
        }

        // Publish event
        publishDocumentEvent(savedDocument, DocumentEvent.EventType.UPDATED);

        log.info("Document updated successfully: {}", documentId);
        return documentMapper.toDTO(savedDocument);
    }

    /**
     * Moves a document to trash (soft delete - first stage of two-stage deletion).
     *
     * <p>Trashing a document changes its status to TRASHED but preserves all data.
     * The document remains in the database and storage for a retention period
     * (typically 30 days) before permanent deletion.</p>
     *
     * <h3>Two-Stage Deletion</h3>
     * <ol>
     *   <li><b>Stage 1 (this method):</b> Trash - document hidden but recoverable</li>
     *   <li><b>Stage 2:</b> {@link #deleteDocument} - permanent deletion</li>
     * </ol>
     *
     * <h3>Side Effects</h3>
     * <ul>
     *   <li>Parent folder's {@code documentCount} decremented by 1</li>
     *   <li>Parent folder's {@code totalSize} decremented by file size</li>
     *   <li>TRASHED event published to Kafka</li>
     * </ul>
     *
     * <h3>Locking</h3>
     * <p>Cannot trash a document locked by another user. The lock owner
     * can trash their own locked document.</p>
     *
     * @param documentId the document ID to trash
     * @throws ResourceNotFoundException if document not found
     * @throws AccessDeniedException if document is locked by another user or user lacks DELETE permission
     */
    @RequiresPermission(Permission.DELETE)
    @Transactional
    @CacheEvict(cacheNames = DOCUMENTS_CACHE, key = "T(com.teamsync.common.context.TenantContext).getTenantId() + ':' + T(com.teamsync.common.context.TenantContext).getDriveId() + ':' + #documentId")
    public void trashDocument(String documentId) {
        String tenantId = TenantContext.getTenantId();
        String driveId = TenantContext.getDriveId();
        String userId = TenantContext.getUserId();

        log.info("Trashing document: {} for tenant: {}", documentId, tenantId);

        Document document = documentRepository.findByIdAndTenantIdAndDriveId(documentId, tenantId, driveId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        if (document.getIsLocked() && !userId.equals(document.getLockedBy())) {
            throw new AccessDeniedException("Document is locked by another user");
        }

        // SECURITY FIX: Use atomic update for folder stats to prevent inconsistent state
        if (document.getFolderId() != null && document.getStatus() == DocumentStatus.ACTIVE) {
            long fileSize = document.getFileSize() != null ? document.getFileSize() : 0L;
            folderRepository.atomicUpdateDocumentStats(
                    document.getFolderId(), tenantId, driveId,
                    -1, -fileSize, Instant.now());
        }

        document.setStatus(DocumentStatus.TRASHED);
        document.setLastModifiedBy(userId);
        document.setUpdatedAt(Instant.now());

        documentRepository.save(document);

        // Publish event
        publishDocumentEvent(document, DocumentEvent.EventType.TRASHED);

        log.info("Document trashed successfully: {}", documentId);
    }

    /**
     * Restores a document from trash back to ACTIVE status.
     *
     * <p>This method reverses the trash operation, making the document visible
     * and accessible again. If a naming conflict exists in the original folder,
     * the document is renamed automatically.</p>
     *
     * <h3>Name Conflict Resolution</h3>
     * <p>If another document with the same name exists in the original folder
     * (created after this document was trashed), the restored document is
     * renamed with pattern: {@code originalName_restored_timestamp.ext}</p>
     *
     * <h3>Side Effects</h3>
     * <ul>
     *   <li>Parent folder's {@code documentCount} incremented by 1</li>
     *   <li>Parent folder's {@code totalSize} incremented by file size</li>
     *   <li>RESTORED event published to Kafka</li>
     * </ul>
     *
     * <h3>Prerequisites</h3>
     * <p>Document must be in TRASHED status. Attempting to restore an ACTIVE
     * document throws {@link IllegalStateException}.</p>
     *
     * @param documentId the document ID to restore
     * @return the restored document DTO with updated status
     * @throws ResourceNotFoundException if document not found
     * @throws IllegalStateException if document is not in TRASHED status
     * @throws AccessDeniedException if user lacks WRITE permission on the drive
     */
    @RequiresPermission(Permission.WRITE)
    @Transactional
    @CacheEvict(cacheNames = DOCUMENTS_CACHE, key = "T(com.teamsync.common.context.TenantContext).getTenantId() + ':' + T(com.teamsync.common.context.TenantContext).getDriveId() + ':' + #documentId")
    public DocumentDTO restoreDocument(String documentId) {
        String tenantId = TenantContext.getTenantId();
        String driveId = TenantContext.getDriveId();
        String userId = TenantContext.getUserId();

        log.info("Restoring document: {} for tenant: {}", documentId, tenantId);

        Document document = documentRepository.findByIdAndTenantId(documentId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        if (document.getStatus() != DocumentStatus.TRASHED) {
            throw new IllegalStateException("Document is not in trash");
        }

        // Check if name conflicts exist in original folder
        if (documentRepository.existsByTenantIdAndDriveIdAndFolderIdAndNameAndStatusNot(
                tenantId, driveId, document.getFolderId(), document.getName(), DocumentStatus.TRASHED)) {
            document.setName(generateUniqueName(document.getName()));
        }

        document.setStatus(DocumentStatus.ACTIVE);
        document.setLastModifiedBy(userId);
        document.setUpdatedAt(Instant.now());

        Document savedDocument;
        try {
            savedDocument = documentRepository.save(document);
        } catch (DuplicateKeyException e) {
            // SECURITY FIX: Unique index prevents race condition during restore
            // This can happen if another document was created with same name after trash
            log.warn("Duplicate document name detected during restore: {}", document.getName());
            // Generate a unique name and retry
            document.setName(generateUniqueName(document.getName()));
            savedDocument = documentRepository.save(document);
        }

        // SECURITY FIX: Use atomic update for folder stats to prevent inconsistent state
        if (document.getFolderId() != null) {
            long fileSize = document.getFileSize() != null ? document.getFileSize() : 0L;
            folderRepository.atomicUpdateDocumentStats(
                    document.getFolderId(), tenantId, driveId,
                    1, fileSize, Instant.now());
        }

        // Publish event
        publishDocumentEvent(savedDocument, DocumentEvent.EventType.RESTORED);

        log.info("Document restored successfully: {}", documentId);
        return documentMapper.toDTO(savedDocument);
    }

    /**
     * Lists all trashed documents for the current user with pagination.
     *
     * <p>This method returns documents that are in TRASHED status and owned by
     * the current user across all their drives. Used by the trash page UI.</p>
     *
     * <h3>Security</h3>
     * <p>Only returns documents owned by the current user, regardless of
     * drive permissions. Users can only see their own trashed items.</p>
     *
     * @param cursor the cursor from previous page (document ID), or null for first page
     * @param limit maximum number of documents to return (recommended: 50)
     * @return a cursor page containing trashed documents and pagination metadata
     */
    public CursorPage<DocumentDTO> listTrashedDocuments(String cursor, int limit) {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();

        log.debug("Listing trashed documents for user: {} tenant: {}", userId, tenantId);

        // Validate and sanitize limit
        int safeLimit = Math.min(Math.max(limit, 1), 100);

        // Create pageable with sorting by updatedAt descending (most recently deleted first)
        Pageable pageable = PageRequest.of(0, safeLimit + 1, Sort.by(Sort.Direction.DESC, "updatedAt"));

        List<Document> documents = documentRepository.findTrashedDocumentsByOwner(tenantId, userId, pageable);

        boolean hasMore = documents.size() > safeLimit;
        if (hasMore) {
            documents = documents.subList(0, safeLimit);
        }

        String nextCursor = hasMore && !documents.isEmpty()
                ? documents.get(documents.size() - 1).getId()
                : null;

        long totalCount = documentRepository.countByTenantIdAndOwnerIdAndStatus(
                tenantId, userId, DocumentStatus.TRASHED);

        List<DocumentDTO> dtos = documents.stream()
                .map(documentMapper::toDTO)
                .toList();

        log.info("Found {} trashed documents for user: {}", dtos.size(), userId);

        return CursorPage.<DocumentDTO>builder()
                .items(dtos)
                .nextCursor(nextCursor)
                .previousCursor(cursor)
                .hasMore(hasMore)
                .totalCount(totalCount)
                .limit(safeLimit)
                .build();
    }

    /**
     * Permanently deletes a document and all its versions (second stage of two-stage deletion).
     *
     * <p>This is an <b>irreversible</b> operation that removes:</p>
     * <ul>
     *   <li>All file versions from cloud storage (via Storage Service)</li>
     *   <li>All DocumentVersion records from MongoDB</li>
     *   <li>The Document record from MongoDB</li>
     * </ul>
     *
     * <h3>Storage Cleanup</h3>
     * <p>Each version's file is deleted via {@link StorageServiceClient#deleteFile}
     * which also updates the drive's storage quota. If a storage deletion fails,
     * the error is logged but the operation continues (best-effort cleanup).</p>
     *
     * <h3>When to Use</h3>
     * <ul>
     *   <li>Emptying trash (documents already in TRASHED status)</li>
     *   <li>Immediate permanent deletion (bypassing trash)</li>
     *   <li>Storage quota reclamation</li>
     * </ul>
     *
     * <h3>Event</h3>
     * <p>Publishes DELETED event to Kafka for audit trail and downstream processing.</p>
     *
     * @param documentId the document ID to permanently delete
     * @throws ResourceNotFoundException if document not found
     * @throws AccessDeniedException if user lacks DELETE permission on the drive
     */
    @RequiresPermission(Permission.DELETE)
    @Transactional
    @CacheEvict(cacheNames = DOCUMENTS_CACHE, key = "T(com.teamsync.common.context.TenantContext).getTenantId() + ':' + T(com.teamsync.common.context.TenantContext).getDriveId() + ':' + #documentId")
    public void deleteDocument(String documentId) {
        String tenantId = TenantContext.getTenantId();
        String driveId = TenantContext.getDriveId();

        log.info("Permanently deleting document: {} for tenant: {}", documentId, tenantId);

        Document document = documentRepository.findByIdAndTenantIdAndDriveId(documentId, tenantId, driveId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        // Delete all versions from storage via Storage Service (ensures quota is updated)
        // Use large page size to get all versions - documents rarely have many versions
        Pageable versionPageable = PageRequest.of(0, 1000);
        List<DocumentVersion> versions = versionRepository.findByDocumentIdOrderByVersionNumberDesc(documentId, versionPageable);
        for (DocumentVersion version : versions) {
            try {
                storageServiceClient.deleteFile(version.getStorageBucket(), version.getStorageKey());
            } catch (Exception e) {
                log.error("Failed to delete version {} from storage: {}", version.getId(), e.getMessage());
                // Record metric for monitoring/alerting - orphaned files need cleanup
                storageDeleteFailureCounter.increment();
                // Continue with deletion even if storage delete fails to avoid orphaned DB records
            }
        }

        // Delete version records
        versionRepository.deleteByDocumentId(documentId);

        // Delete document record
        documentRepository.delete(document);

        // Publish event
        publishDocumentEvent(document, DocumentEvent.EventType.DELETED);

        log.info("Document permanently deleted: {}", documentId);
    }

    /**
     * Locks a document to prevent concurrent editing by other users.
     *
     * <p>Document locking provides pessimistic concurrency control for scenarios
     * where optimistic locking is insufficient (e.g., long editing sessions,
     * WOPI/Office editing integration).</p>
     *
     * <h3>Lock Semantics</h3>
     * <ul>
     *   <li>Only one user can hold the lock at a time</li>
     *   <li>Lock owner is recorded in {@code lockedBy} field</li>
     *   <li>Lock timestamp recorded in {@code lockedAt} field</li>
     *   <li>Only the lock owner can unlock or modify the document</li>
     * </ul>
     *
     * <h3>Use Cases</h3>
     * <ul>
     *   <li>WOPI (Office Online) editing sessions</li>
     *   <li>Long-running document workflows</li>
     *   <li>Preventing edits during review/approval</li>
     * </ul>
     *
     * <h3>Lock Duration</h3>
     * <p>Locks persist until explicitly unlocked. Consider implementing
     * lock expiration (e.g., 24 hours) for abandoned locks.</p>
     *
     * @param documentId the document ID to lock
     * @return the locked document DTO
     * @throws ResourceNotFoundException if document not found
     * @throws IllegalStateException if document is already locked
     * @throws AccessDeniedException if user lacks WRITE permission on the drive
     */
    /**
     * SECURITY FIX: Using atomic lock acquisition to prevent race condition.
     * Previously, the check (isLocked) and set (save) were not atomic, allowing
     * two concurrent requests to both see isLocked=false and both acquire the lock.
     */
    @RequiresPermission(Permission.WRITE)
    @Transactional
    @CacheEvict(cacheNames = DOCUMENTS_CACHE, key = "T(com.teamsync.common.context.TenantContext).getTenantId() + ':' + T(com.teamsync.common.context.TenantContext).getDriveId() + ':' + #documentId")
    public DocumentDTO lockDocument(String documentId) {
        String tenantId = TenantContext.getTenantId();
        String driveId = TenantContext.getDriveId();
        String userId = TenantContext.getUserId();

        // First verify the document exists
        Document document = documentRepository.findByIdAndTenantIdAndDriveId(documentId, tenantId, driveId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        // SECURITY FIX: Atomic lock acquisition - check and set in single operation
        // This prevents race condition where two users could both acquire the lock
        Instant now = Instant.now();
        long modified = documentRepository.atomicAcquireLock(documentId, tenantId, driveId, userId, now);

        if (modified == 0) {
            // Lock acquisition failed - either document was already locked or doesn't exist
            // Re-fetch to get current lock holder for error message
            Document currentDoc = documentRepository.findByIdAndTenantIdAndDriveId(documentId, tenantId, driveId)
                    .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

            if (currentDoc.getIsLocked()) {
                throw new IllegalStateException("Document is already locked by " + currentDoc.getLockedBy());
            }
            // This shouldn't happen, but handle it gracefully
            throw new IllegalStateException("Failed to acquire lock on document");
        }

        // Fetch the updated document
        Document lockedDocument = documentRepository.findByIdAndTenantIdAndDriveId(documentId, tenantId, driveId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found after lock: " + documentId));

        log.info("Document locked: {} by user: {}", documentId, userId);
        return documentMapper.toDTO(lockedDocument);
    }

    /**
     * Unlocks a previously locked document, allowing other users to edit.
     *
     * <p>Only the user who locked the document can unlock it. This prevents
     * accidental or malicious unlocking by other users.</p>
     *
     * <h3>Unlock Behavior</h3>
     * <ul>
     *   <li>Clears {@code isLocked} flag to {@code false}</li>
     *   <li>Clears {@code lockedBy} and {@code lockedAt} fields</li>
     *   <li>Updates {@code updatedAt} timestamp</li>
     * </ul>
     *
     * <h3>Error Cases</h3>
     * <ul>
     *   <li><b>Not locked:</b> Throws {@link IllegalStateException}</li>
     *   <li><b>Wrong user:</b> Throws {@link AccessDeniedException}</li>
     * </ul>
     *
     * <h3>Admin Override</h3>
     * <p>For stuck locks (e.g., user left company), admins should use a
     * separate admin endpoint that bypasses the ownership check.</p>
     *
     * @param documentId the document ID to unlock
     * @return the unlocked document DTO
     * @throws ResourceNotFoundException if document not found
     * @throws IllegalStateException if document is not locked
     * @throws AccessDeniedException if user is not the lock owner or lacks WRITE permission
     */
    /**
     * SECURITY FIX: Using atomic lock release to prevent race condition.
     * The atomic operation ensures only the lock owner can release it and
     * prevents double-release scenarios.
     */
    @RequiresPermission(Permission.WRITE)
    @Transactional
    @CacheEvict(cacheNames = DOCUMENTS_CACHE, key = "T(com.teamsync.common.context.TenantContext).getTenantId() + ':' + T(com.teamsync.common.context.TenantContext).getDriveId() + ':' + #documentId")
    public DocumentDTO unlockDocument(String documentId) {
        String tenantId = TenantContext.getTenantId();
        String driveId = TenantContext.getDriveId();
        String userId = TenantContext.getUserId();

        // First verify the document exists
        Document document = documentRepository.findByIdAndTenantIdAndDriveId(documentId, tenantId, driveId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        // SECURITY FIX: Atomic lock release - only releases if locked by this user
        Instant now = Instant.now();
        long modified = documentRepository.atomicReleaseLock(documentId, tenantId, driveId, userId, now);

        if (modified == 0) {
            // Release failed - either not locked, wrong user, or doesn't exist
            // Re-fetch to determine the actual error
            Document currentDoc = documentRepository.findByIdAndTenantIdAndDriveId(documentId, tenantId, driveId)
                    .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

            if (!currentDoc.getIsLocked()) {
                throw new IllegalStateException("Document is not locked");
            }

            if (!userId.equals(currentDoc.getLockedBy())) {
                throw new AccessDeniedException("Only the user who locked the document can unlock it");
            }

            // This shouldn't happen, but handle gracefully
            throw new IllegalStateException("Failed to release lock on document");
        }

        // Fetch the updated document
        Document unlockedDocument = documentRepository.findByIdAndTenantIdAndDriveId(documentId, tenantId, driveId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found after unlock: " + documentId));

        log.info("Document unlocked: {}", documentId);
        return documentMapper.toDTO(unlockedDocument);
    }

    /**
     * Retrieves all starred documents in the current drive.
     *
     * <p>Returns documents where {@code isStarred = true} and status is ACTIVE.
     * Starred documents are a user preference for quick access to important files.</p>
     *
     * @return list of starred document DTOs (may be empty)
     * @throws AccessDeniedException if user lacks READ permission on the drive
     */
    /**
     * SECURITY FIX (Round 13 #9): Added limit to prevent unbounded result sets.
     * Previously could return unlimited documents if user starred many files.
     */
    private static final int MAX_STARRED_DOCUMENTS = 100;

    @RequiresPermission(Permission.READ)
    @Observed(name = "document.list.starred", contextualName = "get-starred-documents")
    public List<DocumentDTO> getStarredDocuments() {
        String tenantId = TenantContext.getTenantId();
        String driveId = TenantContext.getDriveId();

        // SECURITY FIX (Round 13 #9): Add pagination to prevent unbounded results
        Pageable pageable = PageRequest.of(0, MAX_STARRED_DOCUMENTS);
        List<Document> documents = documentRepository.findByTenantIdAndDriveIdAndIsStarredAndStatus(
                tenantId, driveId, true, DocumentStatus.ACTIVE, pageable);

        return documents.stream()
                .map(documentMapper::toDTO)
                .toList();
    }

    /**
     * Retrieves the user's most recently accessed documents.
     *
     * <p>Returns documents owned by the current user, sorted by {@code accessedAt}
     * in descending order (most recent first). Useful for "Recent Documents" UI.</p>
     *
     * <p><b>Note:</b> This method queries by {@code ownerId}, not current drive,
     * so it shows the user's recent documents across all drives.</p>
     *
     * @param limit maximum number of documents to return (recommended: 10-50)
     * @return list of recent document DTOs sorted by access time
     * @throws AccessDeniedException if user lacks READ permission on the drive
     */
    @RequiresPermission(Permission.READ)
    @Observed(name = "document.list.recent", contextualName = "get-recent-documents")
    public List<DocumentDTO> getRecentDocuments(int limit) {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();

        Pageable pageable = PageRequest.of(0, limit);
        List<Document> documents = documentRepository.findByTenantIdAndOwnerIdAndStatusOrderByAccessedAtDesc(
                tenantId, userId, DocumentStatus.ACTIVE, pageable);

        return documents.stream()
                .map(documentMapper::toDTO)
                .toList();
    }

    /**
     * Searches documents by name within the current drive.
     *
     * <p>Performs a case-insensitive partial match on document names using
     * MongoDB regex query. For full-text search with relevance ranking,
     * use the Search Service (Elasticsearch) instead.</p>
     *
     * <p><b>Performance:</b> Uses {@code tenant_drive_name_idx} compound index
     * for efficient filtering before regex matching.</p>
     *
     * @param query the search query (matched against document name)
     * @param limit maximum number of results to return
     * @return list of matching document DTOs
     * @throws AccessDeniedException if user lacks READ permission on the drive
     */
    @RequiresPermission(Permission.READ)
    @Observed(name = "document.search", contextualName = "search-documents")
    public List<DocumentDTO> searchDocuments(String query, int limit) {
        String tenantId = TenantContext.getTenantId();
        String driveId = TenantContext.getDriveId();

        // SECURITY FIX (Round 5): Escape regex special characters to prevent ReDoS attacks
        // MongoDB $regex without escaping allows attackers to craft patterns like
        // (a+)+$ that cause catastrophic backtracking (exponential time complexity)
        String escapedQuery = escapeRegexForSearch(query);

        Pageable pageable = PageRequest.of(0, limit);
        List<Document> documents = documentRepository.searchByName(
                tenantId, driveId, escapedQuery, DocumentStatus.ACTIVE, pageable);

        return documents.stream()
                .map(documentMapper::toDTO)
                .toList();
    }

    /**
     * SECURITY FIX (Round 5): Escape regex special characters for safe use in MongoDB $regex.
     * Prevents ReDoS attacks where crafted patterns cause exponential backtracking.
     */
    private String escapeRegexForSearch(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        // Escape all regex metacharacters: . ^ $ * + ? { } [ ] \ | ( )
        // This ensures the search is a literal substring match
        return input.replaceAll("([\\\\\\[\\](){}.*+?^$|])", "\\\\$1");
    }

    /**
     * Generates a signed download URL for browser-compatible document downloads.
     *
     * <p>This method creates a URL with an HMAC-SHA256 signed token that allows
     * direct browser downloads without requiring Authorization headers. This is
     * necessary because browsers can't send custom headers for {@code <a>} clicks
     * or iframe embeds.</p>
     *
     * <h3>Token Contents</h3>
     * <p>The token encodes (and signs):</p>
     * <ul>
     *   <li>{@code tenantId}: Tenant context for validation</li>
     *   <li>{@code driveId}: Drive context for audit</li>
     *   <li>{@code userId}: User performing the download</li>
     *   <li>{@code bucket}: Storage bucket name</li>
     *   <li>{@code storageKey}: File path in storage</li>
     *   <li>{@code expiresAt}: Token expiration timestamp</li>
     * </ul>
     *
     * <h3>Download Flow</h3>
     * <ol>
     *   <li>Frontend calls this method to get signed URL</li>
     *   <li>User clicks download link or browser fetches URL</li>
     *   <li>Content Service validates token signature and expiry</li>
     *   <li>File streamed from MinIO/S3 to browser</li>
     * </ol>
     *
     * <h3>Security</h3>
     * <ul>
     *   <li>Token signed with {@code DOWNLOAD_TOKEN_SECRET}</li>
     *   <li>Expiry prevents token reuse after timeout</li>
     *   <li>User/tenant embedded for audit trail</li>
     * </ul>
     *
     * @param documentId the document ID to generate URL for
     * @param expiry     how long the URL should be valid (e.g., 1 hour)
     * @return the signed download URL
     * @throws ResourceNotFoundException if document not found
     * @throws AccessDeniedException if user lacks READ permission on the drive
     * @see DownloadTokenUtil
     */
    @RequiresPermission(Permission.READ)
    public String generateDownloadUrl(String documentId, Duration expiry) {
        String tenantId = TenantContext.getTenantId();
        String driveId = TenantContext.getDriveId();
        String userId = TenantContext.getUserId();

        Document document = documentRepository.findByIdAndTenantIdAndDriveId(documentId, tenantId, driveId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        String bucket = document.getStorageBucket() != null ? document.getStorageBucket() : "teamsync-documents";
        Instant expiresAt = Instant.now().plus(expiry);

        // Generate signed download token
        String token = downloadTokenUtil.generateToken(
                tenantId, driveId, userId, bucket, document.getStorageKey(), expiresAt);

        // Build Content Service download URL with signed token
        String encodedToken = java.net.URLEncoder.encode(token, java.nio.charset.StandardCharsets.UTF_8);
        String encodedFilename = java.net.URLEncoder.encode(document.getName(), java.nio.charset.StandardCharsets.UTF_8);

        return String.format("%s/api/documents/download?token=%s&filename=%s",
                contentServiceUrl,
                encodedToken,
                encodedFilename);
    }

    /**
     * Downloads a file from storage using pre-validated token credentials.
     *
     * <p>This method is called after token validation and does NOT perform
     * additional permission checks. The signed token (validated by the controller)
     * already authorized the download when {@link #generateDownloadUrl} was called.</p>
     *
     * <h3>Usage</h3>
     * <p>Called by the controller after validating the download token:</p>
     * <pre>
     * TokenData tokenData = downloadTokenUtil.validateToken(token);
     * FileDownload download = documentService.downloadFileWithToken(
     *     tokenData.bucket(), tokenData.storageKey());
     * </pre>
     *
     * @param bucket     the storage bucket containing the file
     * @param storageKey the file's storage key (path within bucket)
     * @return FileDownload containing input stream, content type, and file size
     * @see #generateDownloadUrl
     */
    public FileDownload downloadFileWithToken(String bucket, String storageKey) {
        log.debug("Downloading file from storage with token: {}/{}", bucket, storageKey);

        // Get file metadata from storage
        long fileSize = storageProvider.getObjectSize(bucket, storageKey);
        String contentType = storageProvider.getContentType(bucket, storageKey);

        // Get input stream from storage
        InputStream inputStream = storageProvider.download(bucket, storageKey);

        return new FileDownload(inputStream, contentType, fileSize);
    }

    /**
     * Downloads a byte range of a file using a validated download token.
     *
     * <p>Used for HTTP Range requests to support PDF streaming viewers like
     * TeamSync PDF Viewer. The viewer can request specific byte ranges to display
     * pages before the full document is downloaded.</p>
     *
     * @param bucket     the storage bucket containing the file
     * @param storageKey the file's storage key (path within bucket)
     * @param offset     the starting byte offset (0-based)
     * @param length     the number of bytes to read
     * @return FileDownload containing input stream for the range, content type, and range size
     */
    public FileDownload downloadFileRangeWithToken(String bucket, String storageKey, long offset, long length) {
        log.debug("Downloading file range from storage: {}/{} (offset={}, length={})", bucket, storageKey, offset, length);

        // Get file metadata from storage
        String contentType = storageProvider.getContentType(bucket, storageKey);

        // Get input stream for the requested range
        InputStream inputStream = storageProvider.downloadRange(bucket, storageKey, offset, length);

        return new FileDownload(inputStream, contentType, length);
    }

    /**
     * Gets file metadata (size, content type) for a file using a validated download token.
     *
     * <p>Used for HTTP HEAD requests to support PDF streaming viewers that need
     * to know the file size before making range requests.</p>
     *
     * @param bucket     the storage bucket containing the file
     * @param storageKey the file's storage key (path within bucket)
     * @return FileMetadata containing content type and file size
     */
    public FileMetadata getFileMetadataWithToken(String bucket, String storageKey) {
        log.debug("Getting file metadata from storage: {}/{}", bucket, storageKey);

        long fileSize = storageProvider.getObjectSize(bucket, storageKey);
        String contentType = storageProvider.getContentType(bucket, storageKey);

        return new FileMetadata(contentType, fileSize);
    }

    /**
     * Record class containing file metadata for HEAD requests.
     */
    public record FileMetadata(String contentType, long fileSize) {}

    /**
     * SECURITY: Verifies that a resource (document) exists in the specified tenant/drive
     * and matches the expected bucket/storageKey from a download token.
     *
     * <p>This prevents attackers from forging download tokens to access files
     * in other tenants or drives by verifying the token's claims match an
     * actual document in the database.</p>
     *
     * @param tenantId   the tenant ID from the download token
     * @param driveId    the drive ID from the download token
     * @param bucket     the storage bucket from the download token
     * @param storageKey the storage key from the download token
     * @return true if a document exists matching all criteria, false otherwise
     */
    public boolean verifyResourceOwnership(String tenantId, String driveId, String bucket, String storageKey) {
        log.debug("Verifying resource ownership - tenant: {}, drive: {}, key: {}",
                tenantId, driveId, storageKey);

        // Check if any document exists with matching tenant, drive, and storage key
        // This is a security check - we don't need to return the document
        return documentRepository.existsByTenantIdAndDriveIdAndStorageKey(tenantId, driveId, storageKey);
    }

    /**
     * Record class containing file download data and metadata.
     *
     * <p>Used as the return type for download operations, providing all
     * information needed to stream the file to the client with correct headers.</p>
     *
     * @param inputStream the file content stream (caller must close)
     * @param contentType the MIME type (e.g., "application/pdf")
     * @param fileSize    the file size in bytes (for Content-Length header)
     * @param filename    the original filename (for Content-Disposition header)
     */
    public record FileDownload(InputStream inputStream, String contentType, long fileSize, String filename) {
        // Constructor for backwards compatibility
        public FileDownload(InputStream inputStream, String contentType, long fileSize) {
            this(inputStream, contentType, fileSize, null);
        }
    }

    /**
     * Downloads a document by ID with full permission checking.
     *
     * <p>Unlike {@link #downloadFileWithToken}, this method performs full
     * permission validation via {@code @RequiresPermission(READ)}. Use this
     * for authenticated API calls where the client can send headers.</p>
     *
     * <h3>Use Cases</h3>
     * <ul>
     *   <li>Mobile app downloads (can send Authorization header)</li>
     *   <li>Service-to-service downloads</li>
     *   <li>API client downloads</li>
     * </ul>
     *
     * @param documentId the document ID to download
     * @return FileDownload with input stream, content type, size, and filename
     * @throws ResourceNotFoundException if document not found or not ACTIVE
     * @throws IllegalStateException if document has no storage key
     * @throws AccessDeniedException if user lacks READ permission on the drive
     */
    @RequiresPermission(Permission.READ)
    @Observed(name = "document.download", contextualName = "download-document")
    public FileDownload downloadDocument(String documentId) {
        String tenantId = TenantContext.getTenantId();
        String driveId = TenantContext.getDriveId();

        log.info("Downloading document: {} for tenant: {}, drive: {}", documentId, tenantId, driveId);

        // Get document metadata
        Document document = documentRepository.findByIdAndTenantIdAndDriveIdAndStatusIn(
                        documentId, tenantId, driveId, List.of(DocumentStatus.ACTIVE))
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        // Get file from storage
        String bucket = defaultBucket;
        String storageKey = document.getStorageKey();

        if (storageKey == null) {
            throw new IllegalStateException("Document has no storage key: " + documentId);
        }

        log.debug("Downloading from storage: {}/{}", bucket, storageKey);

        // Get file metadata and stream
        long fileSize = storageProvider.getObjectSize(bucket, storageKey);
        String contentType = document.getContentType();
        InputStream inputStream = storageProvider.download(bucket, storageKey);

        return new FileDownload(inputStream, contentType, fileSize, document.getName());
    }

    /**
     * Returns the count of ACTIVE documents in the current drive.
     *
     * <p>Uses an efficient count query that leverages the {@code tenant_drive_idx}
     * compound index. Does not load document data into memory.</p>
     *
     * @return the number of active documents in the drive
     * @throws AccessDeniedException if user lacks READ permission on the drive
     */
    @RequiresPermission(Permission.READ)
    public long getDocumentCount() {
        String tenantId = TenantContext.getTenantId();
        String driveId = TenantContext.getDriveId();

        return documentRepository.countByTenantIdAndDriveIdAndStatus(tenantId, driveId, DocumentStatus.ACTIVE);
    }

    /**
     * Bulk trashes multiple documents in a single transaction.
     *
     * <p>This method processes each document individually, collecting successes
     * and failures. A failure for one document does not prevent others from
     * being trashed (partial success is possible).</p>
     *
     * <h3>Failure Reasons</h3>
     * <ul>
     *   <li>Document not found in current tenant/drive</li>
     *   <li>Document locked by another user</li>
     *   <li>Unexpected errors during processing</li>
     * </ul>
     *
     * <h3>Side Effects</h3>
     * <p>For each successfully trashed document:</p>
     * <ul>
     *   <li>Parent folder statistics updated</li>
     *   <li>TRASHED event published to Kafka</li>
     * </ul>
     *
     * @param documentIds list of document IDs to trash
     * @return map containing: successCount, failedCount, failedIds (list)
     * @throws AccessDeniedException if user lacks DELETE permission on the drive
     */
    @RequiresPermission(Permission.DELETE)
    @Transactional
    public java.util.Map<String, Object> bulkTrashDocuments(List<String> documentIds) {
        String tenantId = TenantContext.getTenantId();
        String driveId = TenantContext.getDriveId();
        String userId = TenantContext.getUserId();

        log.info("Bulk trashing {} documents for tenant: {}", documentIds.size(), tenantId);

        int successCount = 0;
        int failedCount = 0;
        java.util.List<String> failedIds = new java.util.ArrayList<>();

        for (String documentId : documentIds) {
            try {
                Document document = documentRepository.findByIdAndTenantIdAndDriveId(documentId, tenantId, driveId)
                        .orElse(null);

                if (document == null) {
                    log.warn("Document not found: {}", documentId);
                    failedCount++;
                    failedIds.add(documentId);
                    continue;
                }

                // Check if locked by another user
                if (document.getIsLocked() && !userId.equals(document.getLockedBy())) {
                    log.warn("Document is locked by another user: {}", documentId);
                    failedCount++;
                    failedIds.add(documentId);
                    continue;
                }

                // SECURITY FIX: Use atomic update for folder stats to prevent inconsistent state
                if (document.getFolderId() != null && document.getStatus() == DocumentStatus.ACTIVE) {
                    long fileSize = document.getFileSize() != null ? document.getFileSize() : 0L;
                    folderRepository.atomicUpdateDocumentStats(
                            document.getFolderId(), tenantId, driveId,
                            -1, -fileSize, Instant.now());
                }

                document.setStatus(DocumentStatus.TRASHED);
                document.setLastModifiedBy(userId);
                document.setUpdatedAt(Instant.now());
                documentRepository.save(document);

                publishDocumentEvent(document, DocumentEvent.EventType.TRASHED);
                successCount++;
            } catch (Exception e) {
                log.error("Failed to trash document: {}", documentId, e);
                failedCount++;
                failedIds.add(documentId);
            }
        }

        log.info("Bulk trash completed: {} succeeded, {} failed", successCount, failedCount);

        return java.util.Map.of(
                "successCount", successCount,
                "failedCount", failedCount,
                "failedIds", failedIds
        );
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    /**
     * Extracts the file extension from a filename.
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>"document.pdf" → "pdf"</li>
     *   <li>"archive.tar.gz" → "gz"</li>
     *   <li>"README" → null</li>
     *   <li>null → null</li>
     * </ul>
     *
     * @param filename the filename to extract extension from
     * @return the lowercase extension without dot, or null if no extension
     */
    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return null;
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    /**
     * Generates a unique filename by appending a timestamp suffix.
     *
     * <p>Used when restoring a document from trash where a name conflict exists.
     * The suffix pattern is: {@code _restored_<timestamp>}</p>
     *
     * <p>Example: "report.pdf" → "report_restored_1702838400000.pdf"</p>
     *
     * @param originalName the original filename
     * @return the unique filename with timestamp suffix
     */
    private String generateUniqueName(String originalName) {
        String baseName = originalName;
        String extension = "";

        int dotIndex = originalName.lastIndexOf('.');
        if (dotIndex > 0) {
            baseName = originalName.substring(0, dotIndex);
            extension = originalName.substring(dotIndex);
        }

        return baseName + "_restored_" + System.currentTimeMillis() + extension;
    }

    /**
     * Publishes a document event to Kafka for async processing.
     *
     * <p>Events are published to topic {@code teamsync.content.events} and consumed by:</p>
     * <ul>
     *   <li>Search Service - for index updates</li>
     *   <li>Notification Service - for user notifications</li>
     *   <li>Activity Service - for audit trail</li>
     * </ul>
     *
     * <p><b>Failure Handling:</b> Event publishing failures are logged but do NOT
     * fail the primary operation. This ensures document operations succeed even
     * if Kafka is temporarily unavailable.</p>
     *
     * @param document  the document that was modified
     * @param eventType the type of event (CREATED, UPDATED, TRASHED, etc.)
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
            // Record metric for monitoring/alerting
            eventPublishFailureCounter.increment();
            // Don't fail the operation if event publishing fails
        }
    }
}
