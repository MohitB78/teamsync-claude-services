package com.teamsync.content.service;

import com.teamsync.common.context.TenantContext;
import com.teamsync.common.dto.CursorPage;
import com.teamsync.common.exception.ResourceNotFoundException;
import com.teamsync.common.model.Permission;
import com.teamsync.common.permission.RequiresPermission;
import com.teamsync.common.storage.CloudStorageProvider;
import io.micrometer.observation.annotation.Observed;
import com.teamsync.content.dto.ContentItemDTO;
import com.teamsync.content.dto.folder.FolderDTO;
import com.teamsync.content.mapper.DocumentMapper;
import com.teamsync.content.mapper.FolderMapper;
import com.teamsync.content.model.Document;
import com.teamsync.content.model.Document.DocumentStatus;
import com.teamsync.content.model.DocumentVersion;
import com.teamsync.content.model.Folder;
import com.teamsync.content.model.Folder.FolderStatus;
import com.teamsync.content.repository.DocumentRepository;
import com.teamsync.content.repository.DocumentVersionRepository;
import com.teamsync.content.repository.FolderRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import com.teamsync.content.dto.ContentCursor;

/**
 * Service for orchestrating cross-domain operations between folders and documents.
 *
 * <h2>Overview</h2>
 * <p>This service handles operations that span both folders and documents, particularly
 * cascading operations that must maintain consistency across the content hierarchy.
 * It serves as the coordination layer between {@link FolderService} and {@link DocumentService}.</p>
 *
 * <h2>Key Responsibilities</h2>
 * <ul>
 *   <li><b>Unified listing:</b> Combines folders and documents in a single response</li>
 *   <li><b>Cascading trash:</b> Moves folder and all contents to trash</li>
 *   <li><b>Cascading restore:</b> Restores folder and all contents from trash</li>
 *   <li><b>Cascading delete:</b> Permanently deletes folder, all descendants, and storage files</li>
 *   <li><b>Statistics:</b> Aggregates folder contents statistics</li>
 * </ul>
 *
 * <h2>Cascading Operations</h2>
 * <p>When operating on a folder, all descendants are affected:</p>
 * <pre>
 * Folder A
 * ├── Folder B
 * │   ├── Document 1
 * │   └── Document 2
 * └── Document 3
 *
 * trashFolderWithContents("A") → Trashes A, B, Doc1, Doc2, Doc3
 * </pre>
 *
 * <h2>Two-Stage Deletion</h2>
 * <p>Content follows a two-stage deletion process:</p>
 * <ol>
 *   <li><b>Trash (soft delete):</b> Status → TRASHED, files retained in storage</li>
 *   <li><b>Permanent delete:</b> Records deleted, files removed from storage</li>
 * </ol>
 *
 * <h2>Storage Cleanup</h2>
 * <p>Permanent deletion removes:</p>
 * <ul>
 *   <li>All document version files from cloud storage</li>
 *   <li>Current document files from cloud storage</li>
 *   <li>Version records from MongoDB</li>
 *   <li>Document records from MongoDB</li>
 *   <li>Folder records from MongoDB</li>
 * </ul>
 *
 * <h2>Security Model</h2>
 * <p>Uses Drive-Level RBAC via {@code @RequiresPermission}:</p>
 * <ul>
 *   <li>Listing: READ permission</li>
 *   <li>Trash/Restore/Delete: DELETE permission (via calling controller)</li>
 * </ul>
 *
 * <h2>Transactional Behavior</h2>
 * <p>Cascading operations use {@code @Transactional} to ensure atomicity.
 * Storage deletions that fail are logged but don't roll back the transaction
 * to avoid orphaned database records.</p>
 *
 * @author TeamSync Platform Team
 * @version 1.0.0
 * @since Spring Boot 4.0.0
 * @see FolderService
 * @see DocumentService
 * @see CloudStorageProvider
 */
@Service
@Slf4j
public class ContentOrchestrationService {

    /**
     * Threshold for warning about large folder operations.
     * Operations on folders with more descendants than this will trigger warnings.
     */
    private static final int LARGE_FOLDER_WARNING_THRESHOLD = 10_000;

    /**
     * Maximum allowed descendants for a single folder operation.
     * Operations exceeding this limit will be rejected to prevent memory issues.
     */
    private static final int LARGE_FOLDER_LIMIT = 100_000;

    private final FolderRepository folderRepository;
    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final CloudStorageProvider storageProvider;
    private final FolderMapper folderMapper;
    private final DocumentMapper documentMapper;

    // Metrics counters for storage operations
    private final Counter storageDeleteFailureCounter;
    private final Counter storageTotalDeleteCounter;
    private final Counter largeFolderOperationCounter;

    public ContentOrchestrationService(
            FolderRepository folderRepository,
            DocumentRepository documentRepository,
            DocumentVersionRepository documentVersionRepository,
            CloudStorageProvider storageProvider,
            FolderMapper folderMapper,
            DocumentMapper documentMapper,
            MeterRegistry meterRegistry) {
        this.folderRepository = folderRepository;
        this.documentRepository = documentRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.storageProvider = storageProvider;
        this.folderMapper = folderMapper;
        this.documentMapper = documentMapper;

        // Initialize metrics counters
        this.storageDeleteFailureCounter = Counter.builder("content.storage.delete.failures")
                .description("Number of failed storage deletions during folder operations")
                .tag("service", "content-orchestration")
                .register(meterRegistry);

        this.storageTotalDeleteCounter = Counter.builder("content.storage.delete.total")
                .description("Total number of storage delete attempts during folder operations")
                .tag("service", "content-orchestration")
                .register(meterRegistry);

        this.largeFolderOperationCounter = Counter.builder("content.folder.large_operation")
                .description("Number of operations on large folders (>10k descendants)")
                .tag("service", "content-orchestration")
                .register(meterRegistry);
    }

    /**
     * Lists all content (folders and documents) in a parent folder as a unified response.
     *
     * <h3>Performance Optimizations</h3>
     * <ul>
     *   <li>Database-side sorting using compound indexes</li>
     *   <li>Cursor-based pagination across two collections</li>
     *   <li>No in-memory sorting or full collection fetches</li>
     * </ul>
     *
     * <h3>Pagination Strategy</h3>
     * <p>Folders are always returned before documents (type ordering). The cursor
     * tracks which phase we're in:</p>
     * <ol>
     *   <li>FOLDER phase: Fetch folders sorted by name until exhausted</li>
     *   <li>DOCUMENT phase: Fetch documents sorted by name</li>
     * </ol>
     *
     * <h3>Cursor Format</h3>
     * <p>Base64 encoded: "PHASE|lastName|lastId"</p>
     *
     * @param parentId   parent folder ID, or null for root level
     * @param typeFilter filter by content type, or null for all types
     * @param cursor     pagination cursor from previous response, or null for first page
     * @param limit      maximum number of items to return
     * @return cursor page with unified content items
     */
    @RequiresPermission(Permission.READ)
    @Observed(
        name = "content.list.unified",
        contextualName = "list-unified-content",
        lowCardinalityKeyValues = {"operation", "list", "entity", "content"}
    )
    public CursorPage<ContentItemDTO> listUnifiedContent(
            String parentId,
            ContentItemDTO.ContentType typeFilter,
            String cursor,
            int limit) {

        long startTime = System.currentTimeMillis();
        String tenantId = TenantContext.getTenantId();
        String driveId = TenantContext.getDriveId();

        log.debug("Listing unified content in parent: {} for tenant: {}, drive: {}, filter: {}, cursor: {}",
                parentId, tenantId, driveId, typeFilter, cursor != null ? "present" : "null");

        // Parse cursor or start fresh
        ContentCursor contentCursor = ContentCursor.decode(cursor);
        if (contentCursor == null) {
            contentCursor = ContentCursor.start();
        }

        List<ContentItemDTO> items = new ArrayList<>();
        int remaining = limit;
        ContentCursor nextCursor = null;
        boolean hasMore = false;

        // Pageable for sorted queries (fetch limit+1 to detect hasMore)
        Pageable sortedPageable = PageRequest.of(0, remaining + 1, Sort.by(Sort.Direction.ASC, "name", "_id"));

        // ========== PHASE 1: FOLDERS ==========
        if (contentCursor.getPhase() == ContentCursor.Phase.FOLDER &&
                (typeFilter == null || typeFilter == ContentItemDTO.ContentType.FOLDER)) {

            long folderQueryStart = System.currentTimeMillis();
            List<Folder> folders = fetchFoldersSorted(
                    tenantId, driveId, parentId, contentCursor, sortedPageable);
            log.info("PERF: Folder query took {}ms, returned {} folders",
                    System.currentTimeMillis() - folderQueryStart, folders.size());

            // Check if we have more folders than needed
            boolean moreFolders = folders.size() > remaining;
            if (moreFolders) {
                folders = folders.subList(0, remaining);
            }

            // Convert to DTOs
            long mappingStart = System.currentTimeMillis();
            for (Folder folder : folders) {
                items.add(mapFolderToContentItem(folder));
            }
            log.debug("PERF: Folder mapping took {}ms for {} items",
                    System.currentTimeMillis() - mappingStart, folders.size());

            remaining -= folders.size();

            // Determine next cursor for folders
            if (moreFolders) {
                // More folders to fetch
                Folder lastFolder = folders.get(folders.size() - 1);
                nextCursor = ContentCursor.afterFolder(lastFolder.getName(), lastFolder.getId());
                hasMore = true;
            } else if (remaining > 0 && (typeFilter == null || typeFilter == ContentItemDTO.ContentType.DOCUMENT)) {
                // Folders exhausted, switch to documents
                contentCursor = ContentCursor.startDocuments();
            }
        }

        // ========== PHASE 2: DOCUMENTS ==========
        if (remaining > 0 && !hasMore &&
                contentCursor.getPhase() == ContentCursor.Phase.DOCUMENT &&
                (typeFilter == null || typeFilter == ContentItemDTO.ContentType.DOCUMENT)) {

            Pageable docPageable = PageRequest.of(0, remaining + 1, Sort.by(Sort.Direction.ASC, "name", "_id"));

            long docQueryStart = System.currentTimeMillis();
            List<Document> documents = fetchDocumentsSorted(
                    tenantId, driveId, parentId, contentCursor, docPageable);
            log.info("PERF: Document query took {}ms, returned {} documents",
                    System.currentTimeMillis() - docQueryStart, documents.size());

            // Check if we have more documents than needed
            boolean moreDocuments = documents.size() > remaining;
            if (moreDocuments) {
                documents = documents.subList(0, remaining);
            }

            // Convert to DTOs
            long mappingStart = System.currentTimeMillis();
            for (Document document : documents) {
                items.add(mapDocumentToContentItem(document));
            }
            log.debug("PERF: Document mapping took {}ms for {} items",
                    System.currentTimeMillis() - mappingStart, documents.size());

            // Determine next cursor for documents
            if (moreDocuments) {
                Document lastDoc = documents.get(documents.size() - 1);
                nextCursor = ContentCursor.afterDocument(lastDoc.getName(), lastDoc.getId());
                hasMore = true;
            }
        }

        // Handle document-only filter starting from folder phase
        if (typeFilter == ContentItemDTO.ContentType.DOCUMENT &&
                contentCursor.getPhase() == ContentCursor.Phase.FOLDER) {
            // Skip straight to documents
            contentCursor = ContentCursor.startDocuments();
            return listUnifiedContent(parentId, typeFilter, contentCursor.encode(), limit);
        }

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("PERF: Total listUnifiedContent took {}ms - Found {} items ({} folders, {} documents) in parent: {}",
                totalTime,
                items.size(),
                items.stream().filter(ContentItemDTO::isFolder).count(),
                items.stream().filter(ContentItemDTO::isDocument).count(),
                parentId);

        return CursorPage.<ContentItemDTO>builder()
                .items(items)
                .nextCursor(nextCursor != null ? nextCursor.encode() : null)
                .hasMore(hasMore)
                .limit(limit)
                .build();
    }

    /**
     * Fetches folders sorted by name with cursor support.
     */
    @Observed(name = "content.fetch.folders", contextualName = "fetch-folders-sorted")
    private List<Folder> fetchFoldersSorted(
            String tenantId, String driveId, String parentId,
            ContentCursor cursor, Pageable pageable) {

        if (cursor.getName() != null && cursor.getId() != null) {
            // Cursor pagination: fetch items after cursor position
            if (parentId == null) {
                return folderRepository.findRootFoldersSortedAfterCursor(
                        tenantId, driveId, FolderStatus.ACTIVE,
                        cursor.getName(), cursor.getId(), pageable);
            } else {
                return folderRepository.findByParentIdSortedAfterCursor(
                        tenantId, driveId, parentId, FolderStatus.ACTIVE,
                        cursor.getName(), cursor.getId(), pageable);
            }
        } else {
            // First page: no cursor
            if (parentId == null) {
                return folderRepository.findRootFoldersSorted(
                        tenantId, driveId, FolderStatus.ACTIVE, pageable);
            } else {
                return folderRepository.findByTenantIdAndDriveIdAndParentIdAndStatusSorted(
                        tenantId, driveId, parentId, FolderStatus.ACTIVE, pageable);
            }
        }
    }

    /**
     * Fetches documents sorted by name with cursor support.
     */
    @Observed(name = "content.fetch.documents", contextualName = "fetch-documents-sorted")
    private List<Document> fetchDocumentsSorted(
            String tenantId, String driveId, String parentId,
            ContentCursor cursor, Pageable pageable) {

        if (cursor.getName() != null && cursor.getId() != null) {
            // Cursor pagination: fetch items after cursor position
            if (parentId == null) {
                return documentRepository.findRootDocumentsSortedAfterCursor(
                        tenantId, driveId, DocumentStatus.ACTIVE,
                        cursor.getName(), cursor.getId(), pageable);
            } else {
                return documentRepository.findByFolderIdSortedAfterCursor(
                        tenantId, driveId, parentId, DocumentStatus.ACTIVE,
                        cursor.getName(), cursor.getId(), pageable);
            }
        } else {
            // First page: no cursor
            if (parentId == null) {
                return documentRepository.findRootDocumentsSorted(
                        tenantId, driveId, DocumentStatus.ACTIVE, pageable);
            } else {
                return documentRepository.findByTenantIdAndDriveIdAndFolderIdAndStatusSorted(
                        tenantId, driveId, parentId, DocumentStatus.ACTIVE, pageable);
            }
        }
    }

    // ==================== Private Mapping Methods ====================

    /**
     * Maps a Folder entity to a unified ContentItemDTO.
     *
     * <p>Includes folder-specific fields like path, depth, ancestorIds, color, icon,
     * and content counts (folderCount, documentCount, totalSize).</p>
     *
     * @param folder the folder entity to map
     * @return content item DTO with type=FOLDER
     */
    private ContentItemDTO mapFolderToContentItem(Folder folder) {
        return ContentItemDTO.builder()
                .type(ContentItemDTO.ContentType.FOLDER)
                .id(folder.getId())
                .tenantId(folder.getTenantId())
                .driveId(folder.getDriveId())
                .name(folder.getName())
                .description(folder.getDescription())
                .metadata(folder.getMetadata())
                .tags(folder.getTags())
                .isStarred(folder.getIsStarred())
                .isPinned(folder.getIsPinned())
                .ownerId(folder.getOwnerId())
                .createdBy(folder.getCreatedBy())
                .lastModifiedBy(folder.getLastModifiedBy())
                .status(folder.getStatus().toString())
                .createdAt(folder.getCreatedAt())
                .updatedAt(folder.getUpdatedAt())
                .accessedAt(folder.getAccessedAt())
                // Folder-specific fields
                .parentId(folder.getParentId())
                .path(folder.getPath())
                .depth(folder.getDepth())
                .ancestorIds(folder.getAncestorIds())
                .color(folder.getColor())
                .icon(folder.getIcon())
                .folderCount(folder.getFolderCount())
                .documentCount(folder.getDocumentCount())
                .totalSize(folder.getTotalSize())
                .formattedSize(formatSize(folder.getTotalSize()))
                .build();
    }

    /**
     * Maps a Document entity to a unified ContentItemDTO.
     *
     * <p>Includes document-specific fields like contentType, fileSize, extension,
     * version info, and lock status.</p>
     *
     * @param document the document entity to map
     * @return content item DTO with type=DOCUMENT
     */
    private ContentItemDTO mapDocumentToContentItem(Document document) {
        return ContentItemDTO.builder()
                .type(ContentItemDTO.ContentType.DOCUMENT)
                .id(document.getId())
                .tenantId(document.getTenantId())
                .driveId(document.getDriveId())
                .name(document.getName())
                .description(document.getDescription())
                .metadata(document.getMetadata())
                .tags(document.getTags())
                .isStarred(document.getIsStarred())
                .isPinned(document.getIsPinned())
                .ownerId(document.getOwnerId())
                .createdBy(document.getCreatedBy())
                .lastModifiedBy(document.getLastModifiedBy())
                .status(document.getStatus().toString())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .accessedAt(document.getAccessedAt())
                // Document-specific fields
                .folderId(document.getFolderId())
                .contentType(document.getContentType())
                .fileSize(document.getFileSize())
                .extension(document.getExtension())
                .documentTypeId(document.getDocumentTypeId())
                .versionCount(document.getVersionCount())
                .entityVersion(document.getEntityVersion())
                .isLocked(document.getIsLocked())
                .lockedBy(document.getLockedBy())
                .lockedAt(document.getLockedAt())
                .formattedSize(formatSize(document.getFileSize()))
                .build();
    }

    /**
     * Formats a byte count as a human-readable size string.
     *
     * <p>Examples: 0 → "0 B", 1024 → "1.0 KB", 1048576 → "1.0 MB"</p>
     *
     * @param bytes the size in bytes, may be null
     * @return formatted size string with appropriate unit
     */
    private String formatSize(Long bytes) {
        if (bytes == null || bytes == 0) return "0 B";
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = 0;
        double size = bytes.doubleValue();
        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }
        return String.format("%.1f %s", size, units[unitIndex]);
    }

    // ==================== Cascading Operations ====================

    /**
     * Moves a folder and all its contents to trash (soft delete).
     *
     * <p>This cascading operation marks the target folder, all descendant folders,
     * and all documents within those folders as TRASHED. Files remain in storage
     * for potential restoration.</p>
     *
     * <h3>Cascade Scope</h3>
     * <ul>
     *   <li>Target folder → TRASHED</li>
     *   <li>All descendant folders → TRASHED</li>
     *   <li>All documents in all affected folders → TRASHED</li>
     * </ul>
     *
     * <h3>Parent Update</h3>
     * <p>If the folder has a parent, the parent's folderCount is decremented by 1.</p>
     *
     * <h3>Recovery</h3>
     * <p>Contents can be restored via {@link #restoreFolderWithContents} within
     * the trash retention period (default 30 days).</p>
     *
     * @param folderId the folder ID to trash
     * @throws ResourceNotFoundException if folder not found in current tenant/drive
     */
    @Transactional
    @Observed(name = "content.trash.folder", contextualName = "trash-folder-with-contents")
    public void trashFolderWithContents(String folderId) {
        String tenantId = TenantContext.getTenantId();
        String driveId = TenantContext.getDriveId();
        String userId = TenantContext.getUserId();
        Instant now = Instant.now();

        log.info("Trashing folder with contents: {} for tenant: {}", folderId, tenantId);

        Folder folder = folderRepository.findByIdAndTenantIdAndDriveId(folderId, tenantId, driveId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found: " + folderId));

        // Get all descendant folder IDs with pagination
        Pageable descendantPageable = PageRequest.of(0, LARGE_FOLDER_LIMIT + 1);
        List<Folder> descendants = folderRepository.findDescendantIds(tenantId, driveId, folderId, descendantPageable);
        List<String> descendantIds = descendants.stream().map(Folder::getId).collect(Collectors.toList());

        // Check for large folder operations
        int descendantCount = descendantIds.size();
        validateAndLogLargeFolderOperation(descendantCount, folderId, "trash");

        // Build list of all folder IDs (including the target folder)
        List<String> allFolderIds = new ArrayList<>(descendantIds);
        allFolderIds.add(folderId);

        // Trash all documents in these folders
        if (!allFolderIds.isEmpty()) {
            documentRepository.updateStatusByFolderIdIn(allFolderIds, tenantId, driveId, DocumentStatus.TRASHED, now);
            log.debug("Trashed documents in {} folders", allFolderIds.size());
        }

        // Trash all folders (bulk update)
        folderRepository.updateStatusByIdIn(allFolderIds, tenantId, driveId, FolderStatus.TRASHED, now);

        // Update parent folder count
        if (folder.getParentId() != null) {
            folderRepository.incrementFolderCount(folder.getParentId(), tenantId, driveId, -1);
        }

        log.info("Folder and contents trashed successfully: {} ({} descendant folders)", folderId, descendantCount);
    }

    /**
     * Restores a folder and all its contents from trash.
     *
     * <p>This cascading operation restores the target folder, all descendant folders,
     * and all documents within those folders to ACTIVE status.</p>
     *
     * <h3>Parent Validation</h3>
     * <p>If the original parent folder no longer exists or is itself trashed,
     * the folder is restored to root level with updated path and ancestors.</p>
     *
     * <h3>Name Conflict Resolution</h3>
     * <p>If a folder with the same name exists at the restore location,
     * the name is modified with a "_restored_{timestamp}" suffix.</p>
     *
     * <h3>Cascade Scope</h3>
     * <ul>
     *   <li>Target folder → ACTIVE</li>
     *   <li>All descendant folders → ACTIVE</li>
     *   <li>All documents in all affected folders → ACTIVE</li>
     * </ul>
     *
     * <h3>Parent Update</h3>
     * <p>If the folder is restored to a parent, the parent's folderCount is incremented.</p>
     *
     * @param folderId the folder ID to restore
     * @return the restored folder DTO
     * @throws ResourceNotFoundException if folder not found
     * @throws IllegalStateException if folder is not in TRASHED status
     */
    @Transactional
    @Observed(name = "content.restore.folder", contextualName = "restore-folder-with-contents")
    public FolderDTO restoreFolderWithContents(String folderId) {
        String tenantId = TenantContext.getTenantId();
        String driveId = TenantContext.getDriveId();
        String userId = TenantContext.getUserId();
        Instant now = Instant.now();

        log.info("Restoring folder with contents: {} for tenant: {}", folderId, tenantId);

        Folder folder = folderRepository.findByIdAndTenantId(folderId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found: " + folderId));

        if (folder.getStatus() != FolderStatus.TRASHED) {
            throw new IllegalStateException("Folder is not in trash");
        }

        // Check if parent still exists and is active
        if (folder.getParentId() != null) {
            var parentOpt = folderRepository.findByIdAndTenantIdAndDriveId(folder.getParentId(), tenantId, driveId);
            if (parentOpt.isEmpty() || parentOpt.get().getStatus() == FolderStatus.TRASHED) {
                // Move to root
                folder.setParentId(null);
                folder.setPath("/" + folder.getName());
                folder.setAncestorIds(Collections.emptyList());
                folder.setDepth(0);
            }
        }

        // Check for name conflicts
        if (folderRepository.existsByTenantIdAndDriveIdAndParentIdAndNameAndStatusNot(
                tenantId, driveId, folder.getParentId(), folder.getName(), FolderStatus.TRASHED)) {
            folder.setName(generateUniqueFolderName(folder.getName()));
            folder.setPath(folder.getParentId() == null
                    ? "/" + folder.getName()
                    : folder.getPath().substring(0, folder.getPath().lastIndexOf('/') + 1) + folder.getName());
        }

        // Get all descendant folder IDs with pagination
        Pageable descendantPageable = PageRequest.of(0, LARGE_FOLDER_LIMIT + 1);
        List<Folder> descendants = folderRepository.findDescendantIds(tenantId, driveId, folderId, descendantPageable);
        List<String> descendantIds = descendants.stream().map(Folder::getId).collect(Collectors.toList());

        // Check for large folder operations
        int descendantCount = descendantIds.size();
        validateAndLogLargeFolderOperation(descendantCount, folderId, "restore");

        // Build list of all folder IDs
        List<String> allFolderIds = new ArrayList<>(descendantIds);
        allFolderIds.add(folderId);

        // Restore all documents in these folders
        if (!allFolderIds.isEmpty()) {
            documentRepository.updateStatusByFolderIdIn(allFolderIds, tenantId, driveId, DocumentStatus.ACTIVE, now);
            log.debug("Restored documents in {} folders", allFolderIds.size());
        }

        // Restore all folders (bulk update)
        folderRepository.updateStatusByIdIn(allFolderIds, tenantId, driveId, FolderStatus.ACTIVE, now);

        // Save the main folder (with potential path updates)
        folder.setStatus(FolderStatus.ACTIVE);
        folder.setLastModifiedBy(userId);
        folder.setUpdatedAt(now);
        Folder savedFolder = folderRepository.save(folder);

        // Update parent folder count
        if (folder.getParentId() != null) {
            folderRepository.incrementFolderCount(folder.getParentId(), tenantId, driveId, 1);
        }

        log.info("Folder and contents restored successfully: {} ({} descendant folders)", folderId, descendantCount);
        return folderMapper.toDTO(savedFolder);
    }

    /**
     * Permanently deletes a folder and all its contents including storage files.
     *
     * <p>This is an irreversible operation that removes all database records and
     * deletes all files from cloud storage.</p>
     *
     * <h3>Deletion Order</h3>
     * <ol>
     *   <li>Collect all affected folder IDs (target + descendants)</li>
     *   <li>Collect all documents in affected folders</li>
     *   <li>Delete document version files from storage</li>
     *   <li>Delete document version records from MongoDB</li>
     *   <li>Delete document files from storage</li>
     *   <li>Delete document records from MongoDB</li>
     *   <li>Delete folder records from MongoDB</li>
     * </ol>
     *
     * <h3>Storage Cleanup</h3>
     * <p>Storage deletions are performed in a best-effort manner. If a file
     * deletion fails, it is logged but the operation continues. This prevents
     * orphaned database records which are harder to clean up than orphaned files.</p>
     *
     * <h3>Warning</h3>
     * <p>This operation cannot be undone. For recoverable deletion, use
     * {@link #trashFolderWithContents} instead.</p>
     *
     * @param folderId the folder ID to permanently delete
     * @throws ResourceNotFoundException if folder not found in current tenant/drive
     */
    @Transactional
    @Observed(name = "content.delete.folder", contextualName = "delete-folder-with-contents")
    public void deleteFolderWithContents(String folderId) {
        String tenantId = TenantContext.getTenantId();
        String driveId = TenantContext.getDriveId();

        log.info("Permanently deleting folder with contents: {} for tenant: {}", folderId, tenantId);

        Folder folder = folderRepository.findByIdAndTenantIdAndDriveId(folderId, tenantId, driveId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found: " + folderId));

        // Get all descendant folder IDs with pagination
        Pageable descendantPageable = PageRequest.of(0, LARGE_FOLDER_LIMIT + 1);
        List<Folder> descendants = folderRepository.findDescendantIds(tenantId, driveId, folderId, descendantPageable);
        List<String> descendantIds = descendants.stream().map(Folder::getId).collect(Collectors.toList());

        // Check for large folder operations
        int descendantCount = descendantIds.size();
        validateAndLogLargeFolderOperation(descendantCount, folderId, "delete");

        // Build list of all folder IDs
        List<String> allFolderIds = new ArrayList<>(descendantIds);
        allFolderIds.add(folderId);

        // Get all documents in these folders (need storage info for cleanup)
        List<Document> documentsToDelete = documentRepository.findStorageInfoByFolderIdIn(allFolderIds, tenantId, driveId);
        List<String> documentIds = documentsToDelete.stream().map(Document::getId).collect(Collectors.toList());

        int storageDeleteFailures = 0;
        int totalStorageDeletes = 0;

        // Delete document versions from storage
        if (!documentIds.isEmpty()) {
            // Use large page size to get all versions for cleanup
            Pageable versionPageable = PageRequest.of(0, LARGE_FOLDER_LIMIT);
            List<DocumentVersion> versions = documentVersionRepository.findStorageInfoByDocumentIdIn(documentIds, versionPageable);
            for (DocumentVersion version : versions) {
                if (version.getStorageBucket() != null && version.getStorageKey() != null) {
                    totalStorageDeletes++;
                    storageTotalDeleteCounter.increment();
                    try {
                        storageProvider.delete(version.getStorageBucket(), version.getStorageKey());
                    } catch (Exception e) {
                        storageDeleteFailures++;
                        storageDeleteFailureCounter.increment();
                        log.error("Failed to delete version {} from storage: {}", version.getId(), e.getMessage());
                    }
                }
            }

            // Delete version records
            documentVersionRepository.deleteByDocumentIdIn(documentIds);
        }

        // Delete documents from storage (current versions)
        for (Document doc : documentsToDelete) {
            if (doc.getStorageBucket() != null && doc.getStorageKey() != null) {
                totalStorageDeletes++;
                storageTotalDeleteCounter.increment();
                try {
                    storageProvider.delete(doc.getStorageBucket(), doc.getStorageKey());
                } catch (Exception e) {
                    storageDeleteFailures++;
                    storageDeleteFailureCounter.increment();
                    log.error("Failed to delete document {} from storage: {}", doc.getId(), e.getMessage());
                }
            }
        }

        // Delete document records
        if (!allFolderIds.isEmpty()) {
            documentRepository.deleteByFolderIdIn(allFolderIds, tenantId, driveId);
            log.debug("Deleted documents in {} folders", allFolderIds.size());
        }

        // Delete folder records
        folderRepository.deleteByIdIn(allFolderIds, tenantId, driveId);

        // Log summary with storage deletion stats
        if (storageDeleteFailures > 0) {
            log.warn("Folder deletion completed with {} storage failures out of {} total deletes: {} ({} folders, {} documents)",
                    storageDeleteFailures, totalStorageDeletes, folderId, allFolderIds.size(), documentsToDelete.size());
        } else {
            log.info("Folder and contents permanently deleted: {} ({} folders, {} documents, {} storage files)",
                    folderId, allFolderIds.size(), documentsToDelete.size(), totalStorageDeletes);
        }
    }

    // ==================== Statistics Methods ====================

    /**
     * Retrieves aggregated statistics for a folder's contents.
     *
     * <p>Calculates the total count of descendant folders and documents,
     * useful for displaying folder info or confirming deletion scope.</p>
     *
     * <h3>Statistics Included</h3>
     * <ul>
     *   <li><b>folderCount:</b> Number of descendant folders (excluding target)</li>
     *   <li><b>documentCount:</b> Total documents in all affected folders</li>
     *   <li><b>totalSize:</b> Aggregate size from folder's cached totalSize field</li>
     * </ul>
     *
     * @param folderId the folder ID to get statistics for
     * @return folder contents statistics
     * @throws ResourceNotFoundException if folder not found
     */
    @Observed(name = "content.stats.folder", contextualName = "get-folder-stats")
    public FolderContentsStats getFolderContentsStats(String folderId) {
        String tenantId = TenantContext.getTenantId();
        String driveId = TenantContext.getDriveId();

        Folder folder = folderRepository.findByIdAndTenantIdAndDriveId(folderId, tenantId, driveId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found: " + folderId));

        // Get all descendant folder IDs with pagination
        Pageable descendantPageable = PageRequest.of(0, LARGE_FOLDER_LIMIT + 1);
        List<Folder> descendants = folderRepository.findDescendantIds(tenantId, driveId, folderId, descendantPageable);
        List<String> descendantIds = descendants.stream().map(Folder::getId).collect(Collectors.toList());

        List<String> allFolderIds = new ArrayList<>(descendantIds);
        allFolderIds.add(folderId);

        // Count documents in all folders with single batch query (fixes N+1)
        long documentCount = documentRepository.countByTenantIdAndDriveIdAndFolderIdInAndStatus(
                tenantId, driveId, allFolderIds, DocumentStatus.ACTIVE);

        return FolderContentsStats.builder()
                .folderId(folderId)
                .folderCount(descendantIds.size())
                .documentCount(documentCount)
                .totalSize(folder.getTotalSize() != null ? folder.getTotalSize() : 0)
                .build();
    }

    /**
     * Lists all trashed content (folders and documents) for the current user.
     *
     * <p>Returns a unified list of trashed items owned by the current user across
     * all their drives. Used by the trash page UI.</p>
     *
     * <h3>Security</h3>
     * <p>Only returns items owned by the current user, regardless of drive permissions.</p>
     *
     * <h3>Ordering</h3>
     * <p>Items are sorted by updatedAt descending (most recently deleted first).</p>
     *
     * @param cursor optional cursor for pagination
     * @param limit maximum number of items per page
     * @return cursor page containing trashed content items
     */
    @Observed(name = "content.list.trashed", contextualName = "list-trashed-content")
    public CursorPage<ContentItemDTO> listTrashedContent(String cursor, int limit) {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();

        log.debug("Listing trashed content for user: {} tenant: {}", userId, tenantId);

        int safeLimit = Math.min(Math.max(limit, 1), 100);

        // Fetch trashed folders and documents
        Pageable pageable = PageRequest.of(0, safeLimit + 1, Sort.by(Sort.Direction.DESC, "updatedAt"));

        List<Folder> trashedFolders = folderRepository.findTrashedFoldersByOwner(tenantId, userId, pageable);
        List<Document> trashedDocuments = documentRepository.findTrashedDocumentsByOwner(tenantId, userId, pageable);

        // Combine and sort by updatedAt
        List<ContentItemDTO> items = new ArrayList<>();

        for (Folder folder : trashedFolders) {
            items.add(mapFolderToContentItem(folder));
        }

        for (Document document : trashedDocuments) {
            items.add(mapDocumentToContentItem(document));
        }

        // Sort combined list by updatedAt descending
        items.sort(Comparator.comparing(ContentItemDTO::getUpdatedAt).reversed());

        // Apply limit
        boolean hasMore = items.size() > safeLimit;
        if (hasMore) {
            items = items.subList(0, safeLimit);
        }

        String nextCursor = hasMore && !items.isEmpty()
                ? items.get(items.size() - 1).getId()
                : null;

        // Count totals
        long folderCount = folderRepository.countByTenantIdAndOwnerIdAndStatus(tenantId, userId, FolderStatus.TRASHED);
        long documentCount = documentRepository.countByTenantIdAndOwnerIdAndStatus(tenantId, userId, DocumentStatus.TRASHED);
        long totalCount = folderCount + documentCount;

        log.info("Found {} trashed items for user: {} ({} folders, {} documents)",
                items.size(), userId, folderCount, documentCount);

        return CursorPage.<ContentItemDTO>builder()
                .items(items)
                .nextCursor(nextCursor)
                .previousCursor(cursor)
                .hasMore(hasMore)
                .totalCount(totalCount)
                .limit(safeLimit)
                .build();
    }

    // ==================== Private Helper Methods ====================

    /**
     * Generates a unique folder name by appending a timestamp suffix.
     *
     * <p>Used when restoring a folder would create a name conflict.</p>
     *
     * @param originalName the original folder name
     * @return unique name with "_restored_{timestamp}" suffix
     */
    private String generateUniqueFolderName(String originalName) {
        return originalName + "_restored_" + System.currentTimeMillis();
    }

    /**
     * Validates and logs large folder operations.
     *
     * <p>This method performs two checks:</p>
     * <ol>
     *   <li><b>Hard limit:</b> Rejects operations exceeding {@link #LARGE_FOLDER_LIMIT}
     *       descendants to prevent potential OOM issues</li>
     *   <li><b>Warning threshold:</b> Logs a warning and increments metrics for operations
     *       exceeding {@link #LARGE_FOLDER_WARNING_THRESHOLD} descendants</li>
     * </ol>
     *
     * @param descendantCount number of descendant folders
     * @param folderId the folder ID being operated on
     * @param operation the operation type (trash, restore, delete)
     * @throws IllegalStateException if descendant count exceeds the hard limit
     */
    private void validateAndLogLargeFolderOperation(int descendantCount, String folderId, String operation) {
        // Hard limit to prevent OOM
        if (descendantCount > LARGE_FOLDER_LIMIT) {
            log.error("REJECTED: {} operation on folder {} has {} descendants, exceeding limit of {}",
                    operation, folderId, descendantCount, LARGE_FOLDER_LIMIT);
            throw new IllegalStateException(
                    String.format("Folder operation rejected: %d descendants exceeds maximum of %d. " +
                            "Please contact support for assistance with large folder operations.",
                            descendantCount, LARGE_FOLDER_LIMIT));
        }

        // Warning for large but allowed operations
        if (descendantCount > LARGE_FOLDER_WARNING_THRESHOLD) {
            largeFolderOperationCounter.increment();
            log.warn("LARGE FOLDER OPERATION: {} on folder {} with {} descendants (threshold: {})",
                    operation, folderId, descendantCount, LARGE_FOLDER_WARNING_THRESHOLD);
        }
    }

    // ==================== Inner Classes ====================

    /**
     * Statistics about a folder's contents for display or confirmation dialogs.
     *
     * <p>Used to show users what will be affected by operations like delete or trash.</p>
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class FolderContentsStats {
        private String folderId;
        private int folderCount;
        private long documentCount;
        private long totalSize;
    }
}
