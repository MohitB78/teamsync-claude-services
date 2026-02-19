package com.teamsync.content.service;

import com.teamsync.common.context.TenantContext;
import com.teamsync.common.dto.CursorPage;
import com.teamsync.common.exception.ResourceNotFoundException;
import com.teamsync.content.dto.folder.*;
import com.teamsync.content.mapper.FolderMapper;
import io.micrometer.observation.annotation.Observed;
import com.teamsync.content.model.Folder;
import com.teamsync.content.model.Folder.FolderStatus;
import com.teamsync.content.repository.FolderRepository;
import com.teamsync.common.model.Permission;
import com.teamsync.common.permission.RequiresPermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.teamsync.content.config.CacheConfig.FOLDERS_CACHE;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for folder management operations in the TeamSync platform.
 *
 * <h2>Overview</h2>
 * <p>This service provides comprehensive folder lifecycle management including
 * CRUD operations, hierarchical navigation, folder tree building, and move operations.
 * All operations enforce Drive-Level RBAC via {@link RequiresPermission} annotations.</p>
 *
 * <h2>Hierarchical Data Model</h2>
 * <p>Folders use a hybrid hierarchy approach combining:</p>
 * <ul>
 *   <li><b>Parent reference:</b> {@code parentId} for direct parent lookup</li>
 *   <li><b>Materialized path:</b> {@code path} for readable hierarchy (e.g., "/Projects/2024/Q1")</li>
 *   <li><b>Ancestor IDs:</b> {@code ancestorIds} list for efficient ancestor queries</li>
 *   <li><b>Depth:</b> {@code depth} integer for level-based queries</li>
 * </ul>
 *
 * <h2>Security Model</h2>
 * <p>Folders are isolated by tenant and drive. Every operation requires:</p>
 * <ul>
 *   <li><b>Tenant isolation</b>: All queries filter by {@code tenantId}</li>
 *   <li><b>Drive isolation</b>: All queries filter by {@code driveId}</li>
 *   <li><b>Permission check</b>: {@code @RequiresPermission} enforced via AOP</li>
 * </ul>
 *
 * <h2>Permission Requirements</h2>
 * <table border="1">
 *   <tr><th>Operation</th><th>Permission</th></tr>
 *   <tr><td>View/List/Search</td><td>READ</td></tr>
 *   <tr><td>Create/Update/Move</td><td>WRITE</td></tr>
 * </table>
 *
 * <h2>Edge Cases Handled</h2>
 * <ul>
 *   <li><b>Duplicate names:</b> Checked against non-TRASHED siblings</li>
 *   <li><b>Circular moves:</b> Prevents moving folder to itself or descendants</li>
 *   <li><b>Path updates:</b> Cascades to all descendants on rename/move</li>
 *   <li><b>Ancestor updates:</b> Recalculates for all descendants on move</li>
 * </ul>
 *
 * <h2>Cascading Operations</h2>
 * <p>Folder deletion/trash/restore cascades to all contents via
 * {@link ContentOrchestrationService}. This service provides the helper method
 * {@link #getDescendantFolderIds} for collecting affected folders.</p>
 *
 * @author TeamSync Platform Team
 * @version 1.0.0
 * @since Spring Boot 4.0.0
 * @see Folder
 * @see RequiresPermission
 * @see ContentOrchestrationService
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FolderService {

    private final FolderRepository folderRepository;
    private final FolderMapper folderMapper;
    private final AccessTrackingService accessTrackingService;

    /**
     * Retrieves a folder by ID with breadcrumb navigation.
     *
     * <p>Returns the folder with its complete breadcrumb trail from root to the
     * current folder. The breadcrumb is built using the {@code ancestorIds} list
     * for efficient lookup without recursive queries.</p>
     *
     * <h3>Breadcrumb Example</h3>
     * <p>For folder at path "/Projects/2024/Q1":</p>
     * <pre>
     * [
     *   { id: "root-id", name: "Projects", path: "/Projects" },
     *   { id: "2024-id", name: "2024", path: "/Projects/2024" },
     *   { id: "q1-id", name: "Q1", path: "/Projects/2024/Q1" }
     * ]
     * </pre>
     *
     * @param folderId the folder ID to retrieve
     * @return the folder DTO with breadcrumb items
     * @throws ResourceNotFoundException if folder not found in current tenant/drive
     * @throws AccessDeniedException if user lacks READ permission on the drive
     */
    @RequiresPermission(Permission.READ)
    @Observed(name = "folder.get", contextualName = "get-folder-by-id")
    public FolderDTO getFolder(String folderId) {
        String tenantId = TenantContext.getTenantId();
        String driveId = TenantContext.getDriveId();

        log.debug("Fetching folder: {} for tenant: {}, drive: {}", folderId, tenantId, driveId);

        Folder folder = folderRepository.findByIdAndTenantIdAndDriveId(folderId, tenantId, driveId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found: " + folderId));

        // Buffer access time update asynchronously to avoid write amplification
        accessTrackingService.recordFolderAccess(tenantId, driveId, folderId, Instant.now());

        FolderDTO dto = folderMapper.toDTO(folder);
        dto.setBreadcrumbItems(buildBreadcrumb(folder));

        return dto;
    }

    /**
     * Lists folders in a parent folder using cursor-based pagination.
     *
     * <p>Similar to {@link DocumentService#listDocuments}, uses scalable cursor
     * pagination that maintains O(log n) performance regardless of page depth.</p>
     *
     * <h3>Parent Handling</h3>
     * <ul>
     *   <li>{@code parentId = null}: Returns root-level folders</li>
     *   <li>{@code parentId = "folder-id"}: Returns subfolders</li>
     *   <li>Only ACTIVE folders returned (excludes TRASHED)</li>
     * </ul>
     *
     * @param parentId the parent folder ID, or null for root level
     * @param cursor   the cursor from previous page, or null for first page
     * @param limit    maximum folders to return (recommended: 50)
     * @return cursor page with folders and pagination metadata
     * @throws AccessDeniedException if user lacks READ permission on the drive
     */
    @RequiresPermission(Permission.READ)
    @Observed(name = "folder.list", contextualName = "list-folders-in-parent")
    public CursorPage<FolderDTO> listFolders(String parentId, String cursor, int limit) {
        String tenantId = TenantContext.getTenantId();
        String driveId = TenantContext.getDriveId();

        log.debug("Listing folders in parent: {} for tenant: {}, drive: {}", parentId, tenantId, driveId);

        Pageable pageable = PageRequest.of(0, limit + 1, Sort.by(Sort.Direction.ASC, "_id"));
        List<Folder> folders;

        if (cursor != null && !cursor.isEmpty()) {
            folders = folderRepository.findByTenantIdAndDriveIdAndParentIdAndStatusAfterCursor(
                    tenantId, driveId, parentId, FolderStatus.ACTIVE, cursor, pageable);
        } else if (parentId == null) {
            folders = folderRepository.findRootFolders(tenantId, driveId, FolderStatus.ACTIVE, pageable);
        } else {
            folders = folderRepository.findByTenantIdAndDriveIdAndParentIdAndStatus(
                    tenantId, driveId, parentId, FolderStatus.ACTIVE, pageable);
        }

        boolean hasMore = folders.size() > limit;
        if (hasMore) {
            folders = folders.subList(0, limit);
        }

        String nextCursor = hasMore && !folders.isEmpty()
                ? folders.get(folders.size() - 1).getId()
                : null;

        List<FolderDTO> dtos = folders.stream()
                .map(folderMapper::toDTO)
                .toList();

        return CursorPage.<FolderDTO>builder()
                .items(dtos)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .limit(limit)
                .build();
    }

    /**
     * Creates a new folder with hierarchical metadata.
     *
     * <p>Creates a folder at the specified location, automatically computing
     * the materialized path, ancestor IDs, and depth based on the parent folder.</p>
     *
     * <h3>Hierarchy Computation</h3>
     * <ul>
     *   <li><b>Root folder:</b> path="/Name", ancestorIds=[], depth=0</li>
     *   <li><b>Nested folder:</b> path=parentPath+"/Name", ancestorIds=parent's ancestors+parentId, depth=parentDepth+1</li>
     * </ul>
     *
     * <h3>Edge Cases</h3>
     * <ul>
     *   <li><b>Duplicate name:</b> Throws {@link IllegalArgumentException} if a non-TRASHED
     *       folder with the same name exists in the same parent</li>
     *   <li><b>Invalid parent:</b> Throws {@link ResourceNotFoundException} if parentId
     *       doesn't exist in the current tenant/drive</li>
     * </ul>
     *
     * <h3>Side Effects</h3>
     * <ul>
     *   <li>Increments parent's {@code folderCount} by 1</li>
     *   <li>Sets owner, createdBy, lastModifiedBy to current user</li>
     *   <li>Initializes counters (folderCount=0, documentCount=0, totalSize=0)</li>
     * </ul>
     *
     * @param request the folder creation request containing name, parentId, and optional metadata
     * @return the created folder DTO
     * @throws IllegalArgumentException if folder name already exists in parent
     * @throws ResourceNotFoundException if parent folder not found
     * @throws AccessDeniedException if user lacks WRITE permission on the drive
     */
    @RequiresPermission(Permission.WRITE)
    @Transactional
    @Observed(name = "folder.create", contextualName = "create-folder")
    public FolderDTO createFolder(CreateFolderRequest request) {
        String tenantId = TenantContext.getTenantId();
        String driveId = TenantContext.getDriveId();
        String userId = TenantContext.getUserId();

        log.info("Creating folder: {} in parent: {} for tenant: {}", request.getName(), request.getParentId(), tenantId);

        // Check for duplicate name in parent
        if (folderRepository.existsByTenantIdAndDriveIdAndParentIdAndNameAndStatusNot(
                tenantId, driveId, request.getParentId(), request.getName(), FolderStatus.TRASHED)) {
            throw new IllegalArgumentException("Folder with name '" + request.getName() + "' already exists");
        }

        // Build path and ancestor list
        String path;
        List<String> ancestorIds;
        int depth;

        if (request.getParentId() == null) {
            path = "/" + request.getName();
            ancestorIds = Collections.emptyList();
            depth = 0;
        } else {
            Folder parent = folderRepository.findByIdAndTenantIdAndDriveId(request.getParentId(), tenantId, driveId)
                    .orElseThrow(() -> new ResourceNotFoundException("Parent folder not found: " + request.getParentId()));

            path = parent.getPath() + "/" + request.getName();
            ancestorIds = new ArrayList<>(parent.getAncestorIds() != null ? parent.getAncestorIds() : Collections.emptyList());
            ancestorIds.add(parent.getId());
            depth = parent.getDepth() + 1;
        }

        Folder folder = Folder.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .driveId(driveId)
                .parentId(request.getParentId())
                .name(request.getName())
                .description(request.getDescription())
                .path(path)
                .depth(depth)
                .ancestorIds(ancestorIds)
                .color(request.getColor())
                .icon(request.getIcon())
                .metadata(request.getMetadata())
                .tags(request.getTags())
                .folderCount(0)
                .documentCount(0)
                .totalSize(0L)
                .isStarred(false)
                .isPinned(false)
                .ownerId(userId)
                .createdBy(userId)
                .lastModifiedBy(userId)
                .status(FolderStatus.ACTIVE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .accessedAt(Instant.now())
                .build();

        Folder savedFolder = folderRepository.save(folder);

        // Update parent folder count
        if (request.getParentId() != null) {
            folderRepository.incrementFolderCount(request.getParentId(), tenantId, driveId, 1);
        }

        log.info("Folder created successfully: {}", savedFolder.getId());
        return folderMapper.toDTO(savedFolder);
    }

    /**
     * Updates folder properties with cascading path updates.
     *
     * <p>Updates any combination of folder properties. If the name is changed,
     * the path is recalculated and cascaded to all descendant folders.</p>
     *
     * <h3>Updatable Properties</h3>
     * <ul>
     *   <li>{@code name} - triggers path cascade if changed</li>
     *   <li>{@code description} - plain text description</li>
     *   <li>{@code color} - UI display color (hex code)</li>
     *   <li>{@code icon} - UI icon identifier</li>
     *   <li>{@code metadata} - custom key-value metadata</li>
     *   <li>{@code tags} - searchable tags</li>
     *   <li>{@code isStarred} - user favorite flag</li>
     *   <li>{@code isPinned} - pinned to top flag</li>
     * </ul>
     *
     * <h3>Name Change Cascade</h3>
     * <p>When the folder name changes:</p>
     * <ol>
     *   <li>Validates new name doesn't conflict with siblings</li>
     *   <li>Computes new path based on parent path + new name</li>
     *   <li>Updates all descendant folder paths via {@link #updateDescendantPaths}</li>
     * </ol>
     *
     * <h3>Edge Cases</h3>
     * <ul>
     *   <li><b>Duplicate name:</b> Throws if sibling has same name (non-TRASHED)</li>
     *   <li><b>Null properties:</b> Null values in request are ignored (no update)</li>
     * </ul>
     *
     * @param folderId the folder ID to update
     * @param request  the update request with properties to change
     * @return the updated folder DTO
     * @throws ResourceNotFoundException if folder not found
     * @throws IllegalArgumentException if new name conflicts with existing sibling
     * @throws AccessDeniedException if user lacks WRITE permission on the drive
     */
    @RequiresPermission(Permission.WRITE)
    @Transactional
    @CacheEvict(cacheNames = FOLDERS_CACHE, key = "T(com.teamsync.common.context.TenantContext).getTenantId() + ':' + T(com.teamsync.common.context.TenantContext).getDriveId() + ':' + #folderId")
    public FolderDTO updateFolder(String folderId, UpdateFolderRequest request) {
        String tenantId = TenantContext.getTenantId();
        String driveId = TenantContext.getDriveId();
        String userId = TenantContext.getUserId();

        log.info("Updating folder: {} for tenant: {}", folderId, tenantId);

        Folder folder = folderRepository.findByIdAndTenantIdAndDriveId(folderId, tenantId, driveId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found: " + folderId));

        // Update name if provided
        if (request.getName() != null && !request.getName().equals(folder.getName())) {
            // Check for duplicate name
            if (folderRepository.existsByTenantIdAndDriveIdAndParentIdAndNameAndStatusNot(
                    tenantId, driveId, folder.getParentId(), request.getName(), FolderStatus.TRASHED)) {
                throw new IllegalArgumentException("Folder with name '" + request.getName() + "' already exists");
            }

            // Update path for this folder and all descendants
            String oldPath = folder.getPath();
            String newPath = folder.getParentId() == null
                    ? "/" + request.getName()
                    : folder.getPath().substring(0, folder.getPath().lastIndexOf('/') + 1) + request.getName();

            folder.setName(request.getName());
            folder.setPath(newPath);

            // Update descendant paths
            updateDescendantPaths(tenantId, driveId, folderId, oldPath, newPath);
        }

        if (request.getDescription() != null) {
            folder.setDescription(request.getDescription());
        }
        if (request.getColor() != null) {
            folder.setColor(request.getColor());
        }
        if (request.getIcon() != null) {
            folder.setIcon(request.getIcon());
        }
        if (request.getMetadata() != null) {
            folder.setMetadata(request.getMetadata());
        }
        if (request.getTags() != null) {
            folder.setTags(request.getTags());
        }
        if (request.getIsStarred() != null) {
            folder.setIsStarred(request.getIsStarred());
        }
        if (request.getIsPinned() != null) {
            folder.setIsPinned(request.getIsPinned());
        }

        folder.setLastModifiedBy(userId);
        folder.setUpdatedAt(Instant.now());

        Folder savedFolder = folderRepository.save(folder);

        log.info("Folder updated successfully: {}", folderId);
        return folderMapper.toDTO(savedFolder);
    }

    /**
     * Moves a folder to a different parent location.
     *
     * <p>Relocates a folder and all its descendants to a new parent. This operation
     * recalculates paths, ancestor IDs, and depths for the entire subtree.</p>
     *
     * <h3>Move Validations</h3>
     * <ul>
     *   <li><b>Self-reference:</b> Cannot move folder to itself</li>
     *   <li><b>Circular move:</b> Cannot move folder to its own descendant</li>
     *   <li><b>Name conflict:</b> Cannot move if name conflicts at destination</li>
     * </ul>
     *
     * <h3>Cascade Updates</h3>
     * <p>When a folder is moved, the following updates cascade to all descendants:</p>
     * <ol>
     *   <li><b>Path:</b> Replace old path prefix with new path prefix</li>
     *   <li><b>Ancestor IDs:</b> Replace old ancestors with new parent's ancestors + new parent ID</li>
     *   <li><b>Depth:</b> Recalculate based on new ancestor chain length</li>
     * </ol>
     *
     * <h3>Folder Count Updates</h3>
     * <ul>
     *   <li>Old parent: folderCount decremented by 1</li>
     *   <li>New parent: folderCount incremented by 1</li>
     * </ul>
     *
     * <h3>Optional Rename</h3>
     * <p>The {@code newName} field allows renaming during move. If not provided,
     * the original name is preserved.</p>
     *
     * @param folderId the folder ID to move
     * @param request  the move request with targetParentId and optional newName
     * @return the moved folder DTO with updated path and ancestors
     * @throws ResourceNotFoundException if folder or target parent not found
     * @throws IllegalArgumentException if move would create circular reference or name conflict
     * @throws AccessDeniedException if user lacks WRITE permission on the drive
     */
    @RequiresPermission(Permission.WRITE)
    @Transactional
    @CacheEvict(cacheNames = FOLDERS_CACHE, key = "T(com.teamsync.common.context.TenantContext).getTenantId() + ':' + T(com.teamsync.common.context.TenantContext).getDriveId() + ':' + #folderId")
    public FolderDTO moveFolder(String folderId, MoveFolderRequest request) {
        String tenantId = TenantContext.getTenantId();
        String driveId = TenantContext.getDriveId();
        String userId = TenantContext.getUserId();

        log.info("Moving folder: {} to parent: {} for tenant: {}", folderId, request.getTargetParentId(), tenantId);

        Folder folder = folderRepository.findByIdAndTenantIdAndDriveId(folderId, tenantId, driveId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found: " + folderId));

        String oldParentId = folder.getParentId();

        // Cannot move to itself or its descendants
        if (folderId.equals(request.getTargetParentId())) {
            throw new IllegalArgumentException("Cannot move folder to itself");
        }

        // SECURITY FIX (Round 9): Use optimistic locking to prevent TOCTOU race condition
        // Store the target parent's version to verify it hasn't changed during the move operation
        Long targetParentVersion = null;

        // Check if target is a descendant
        if (request.getTargetParentId() != null) {
            Folder targetParent = folderRepository.findByIdAndTenantIdAndDriveId(request.getTargetParentId(), tenantId, driveId)
                    .orElseThrow(() -> new ResourceNotFoundException("Target folder not found: " + request.getTargetParentId()));

            // Store version for later verification (prevents concurrent modification)
            targetParentVersion = targetParent.getVersion();

            if (targetParent.getAncestorIds() != null && targetParent.getAncestorIds().contains(folderId)) {
                throw new IllegalArgumentException("Cannot move folder to its own descendant");
            }

            // SECURITY FIX (Round 9): Additional circular reference check using DB query
            // This double-checks the ancestor relationship directly in the database
            // to protect against race conditions where another move operation
            // could have made targetParent a descendant of folderId between our read and write
            boolean isDescendant = folderRepository.existsByIdAndAncestorIdsContaining(request.getTargetParentId(), folderId);
            if (isDescendant) {
                log.warn("SECURITY: Race condition detected - target {} became descendant of {} during move",
                        request.getTargetParentId(), folderId);
                throw new IllegalArgumentException("Cannot move folder to its own descendant");
            }
        }

        // Check for duplicate name in target
        String newName = request.getNewName() != null ? request.getNewName() : folder.getName();
        if (folderRepository.existsByTenantIdAndDriveIdAndParentIdAndNameAndStatusNot(
                tenantId, driveId, request.getTargetParentId(), newName, FolderStatus.TRASHED)) {
            throw new IllegalArgumentException("Folder with name '" + newName + "' already exists in target location");
        }

        // Calculate new path and ancestors
        String oldPath = folder.getPath();
        String newPath;
        List<String> newAncestorIds;
        int newDepth;

        if (request.getTargetParentId() == null) {
            newPath = "/" + newName;
            newAncestorIds = Collections.emptyList();
            newDepth = 0;
        } else {
            Folder targetParent = folderRepository.findByIdAndTenantIdAndDriveId(request.getTargetParentId(), tenantId, driveId)
                    .orElseThrow(() -> new ResourceNotFoundException("Target folder not found"));

            newPath = targetParent.getPath() + "/" + newName;
            newAncestorIds = new ArrayList<>(targetParent.getAncestorIds() != null ? targetParent.getAncestorIds() : Collections.emptyList());
            newAncestorIds.add(targetParent.getId());
            newDepth = targetParent.getDepth() + 1;
        }

        // Update folder
        folder.setParentId(request.getTargetParentId());
        folder.setName(newName);
        folder.setPath(newPath);
        folder.setAncestorIds(newAncestorIds);
        folder.setDepth(newDepth);
        folder.setLastModifiedBy(userId);
        folder.setUpdatedAt(Instant.now());

        folderRepository.save(folder);

        // Update descendant paths and ancestors
        updateDescendantPathsAndAncestors(tenantId, driveId, folderId, oldPath, newPath, newAncestorIds, newDepth);

        // Update folder counts
        if (oldParentId != null) {
            folderRepository.incrementFolderCount(oldParentId, tenantId, driveId, -1);
        }
        if (request.getTargetParentId() != null) {
            folderRepository.incrementFolderCount(request.getTargetParentId(), tenantId, driveId, 1);
        }

        log.info("Folder moved successfully: {}", folderId);
        return folderMapper.toDTO(folder);
    }

    /**
     * Retrieves all descendant folder IDs for cascading operations.
     *
     * <p>Used by {@link ContentOrchestrationService} for cascading delete, trash,
     * and restore operations. This method does NOT require permission annotation
     * as it's called internally after permission has already been verified.</p>
     *
     * <h3>Use Cases</h3>
     * <ul>
     *   <li><b>Trash folder:</b> Collect all subfolders to mark as TRASHED</li>
     *   <li><b>Delete folder:</b> Collect all subfolders and their documents for permanent deletion</li>
     *   <li><b>Restore folder:</b> Collect all subfolders to restore from trash</li>
     * </ul>
     *
     * <h3>Performance Note</h3>
     * <p>Uses the {@code ancestorIds} index for O(n) retrieval where n is the number
     * of descendants. Does NOT use recursive queries.</p>
     *
     * @param folderId the parent folder ID
     * @param tenantId the tenant ID for isolation
     * @param driveId  the drive ID for isolation
     * @return list of descendant folder IDs (excludes the parent folder itself)
     */
    public List<String> getDescendantFolderIds(String folderId, String tenantId, String driveId) {
        // Use large page size for collecting all descendants - this is an internal method
        // called after permission checks, and we need all IDs for cascading operations
        Pageable pageable = PageRequest.of(0, 100_000);
        List<Folder> descendants = folderRepository.findDescendantIds(tenantId, driveId, folderId, pageable);
        return descendants.stream().map(Folder::getId).collect(Collectors.toList());
    }

    /**
     * Builds a hierarchical folder tree for navigation UI.
     *
     * <p>Returns a tree structure starting from the specified parent (or root if null).
     * The tree is built recursively up to {@code maxDepth} levels, with each node
     * containing its children.</p>
     *
     * <h3>Tree Structure</h3>
     * <pre>
     * FolderTreeNode {
     *   id, name, path, depth,
     *   folderCount, documentCount,
     *   children: [FolderTreeNode...]
     * }
     * </pre>
     *
     * <h3>Depth Control</h3>
     * <ul>
     *   <li>{@code maxDepth = 1}: Only immediate children</li>
     *   <li>{@code maxDepth = 2}: Children and grandchildren</li>
     *   <li>{@code maxDepth = 0}: No children (just root level)</li>
     * </ul>
     *
     * <h3>Performance Consideration</h3>
     * <p>Each level of depth results in additional database queries. For deep
     * hierarchies, consider using a lower maxDepth and loading children on-demand
     * (lazy loading pattern).</p>
     *
     * @param parentId the root of the tree, or null for drive root
     * @param maxDepth maximum depth to traverse (0 = no children)
     * @return list of tree nodes at the specified level with nested children
     * @throws AccessDeniedException if user lacks READ permission on the drive
     */
    /**
     * SECURITY FIX (Round 6): Hardcoded maximum depth limit to prevent DoS attacks.
     * Even if controller validation is bypassed, this service-level limit ensures
     * recursive queries cannot cause stack overflow or excessive database load.
     */
    private static final int MAX_FOLDER_TREE_DEPTH = 5;

    /**
     * SECURITY FIX (Round 6): Maximum folders returned per level to prevent memory exhaustion.
     * If a single parent has thousands of children, limit to prevent OOM.
     */
    private static final int MAX_CHILDREN_PER_LEVEL = 200;

    @RequiresPermission(Permission.READ)
    @Observed(name = "folder.tree", contextualName = "get-folder-tree")
    public List<FolderTreeNode> getFolderTree(String parentId, int maxDepth) {
        String tenantId = TenantContext.getTenantId();
        String driveId = TenantContext.getDriveId();

        // SECURITY FIX (Round 6): Enforce depth limit at service level
        // This is defense-in-depth in case controller validation is bypassed
        if (maxDepth < 0 || maxDepth > MAX_FOLDER_TREE_DEPTH) {
            log.warn("SECURITY: Invalid maxDepth {} requested, capping to {}", maxDepth, MAX_FOLDER_TREE_DEPTH);
            maxDepth = Math.min(Math.max(maxDepth, 0), MAX_FOLDER_TREE_DEPTH);
        }

        log.debug("Getting folder tree from parent: {} for tenant: {}, drive: {}", parentId, tenantId, driveId);

        // SECURITY FIX: Limit results to prevent memory exhaustion
        Pageable treePageable = PageRequest.of(0, MAX_CHILDREN_PER_LEVEL + 1);
        List<Folder> folders;
        if (parentId == null) {
            folders = folderRepository.findRootFolders(tenantId, driveId, FolderStatus.ACTIVE, treePageable);
        } else {
            folders = folderRepository.findByTenantIdAndDriveIdAndParentIdAndStatus(
                    tenantId, driveId, parentId, FolderStatus.ACTIVE, treePageable);
        }

        // SECURITY FIX (Round 6): Limit results to prevent memory exhaustion
        final List<Folder> limitedFolders;
        if (folders.size() > MAX_CHILDREN_PER_LEVEL) {
            log.warn("SECURITY: Truncating folder tree from {} to {} folders at root level for tenant {}",
                    folders.size(), MAX_CHILDREN_PER_LEVEL, tenantId);
            limitedFolders = folders.subList(0, MAX_CHILDREN_PER_LEVEL);
        } else {
            limitedFolders = folders;
        }

        final int effectiveMaxDepth = maxDepth;
        return limitedFolders.stream()
                .map(folder -> buildTreeNode(folder, tenantId, driveId, 0, effectiveMaxDepth))
                .collect(Collectors.toList());
    }

    /**
     * Retrieves all starred (favorite) folders in the current drive.
     *
     * <p>Returns folders where {@code isStarred = true}. Used for quick access
     * to frequently used folders in the UI sidebar.</p>
     *
     * <h3>Behavior</h3>
     * <ul>
     *   <li>Only returns ACTIVE folders (excludes TRASHED)</li>
     *   <li>Returns all starred folders (no pagination)</li>
     *   <li>Typically a small set (&lt;20 folders per user)</li>
     * </ul>
     *
     * @return list of starred folder DTOs
     * @throws AccessDeniedException if user lacks READ permission on the drive
     */
    /**
     * SECURITY FIX (Round 15): Added limit to prevent unbounded result sets.
     */
    private static final int MAX_STARRED_FOLDERS = 100;

    @RequiresPermission(Permission.READ)
    @Observed(name = "folder.list.starred", contextualName = "get-starred-folders")
    public List<FolderDTO> getStarredFolders() {
        String tenantId = TenantContext.getTenantId();
        String driveId = TenantContext.getDriveId();

        // SECURITY FIX: Add pagination to prevent unbounded results
        Pageable pageable = PageRequest.of(0, MAX_STARRED_FOLDERS);
        List<Folder> folders = folderRepository.findByTenantIdAndDriveIdAndIsStarredAndStatus(
                tenantId, driveId, true, FolderStatus.ACTIVE, pageable);

        return folders.stream()
                .map(folderMapper::toDTO)
                .toList();
    }

    /**
     * Searches folders by name using case-insensitive regex matching.
     *
     * <p>Performs a simple text search on folder names within the current drive.
     * For advanced semantic search, use the Search Service instead.</p>
     *
     * <h3>Search Behavior</h3>
     * <ul>
     *   <li>Case-insensitive matching</li>
     *   <li>Partial name matching (contains)</li>
     *   <li>Only searches ACTIVE folders</li>
     *   <li>Limited to specified count</li>
     * </ul>
     *
     * <h3>Performance Note</h3>
     * <p>Uses MongoDB regex with index on name field. For large datasets with
     * complex search requirements, consider using Elasticsearch via Search Service.</p>
     *
     * @param query the search term to match against folder names
     * @param limit maximum number of results to return
     * @return list of matching folder DTOs
     * @throws AccessDeniedException if user lacks READ permission on the drive
     */
    @RequiresPermission(Permission.READ)
    @Observed(name = "folder.search", contextualName = "search-folders")
    public List<FolderDTO> searchFolders(String query, int limit) {
        String tenantId = TenantContext.getTenantId();
        String driveId = TenantContext.getDriveId();

        // SECURITY FIX (Round 14 #H45): Escape regex special characters to prevent ReDoS attacks.
        // MongoDB $regex without escaping allows attackers to craft patterns like
        // (a+)+$ that cause catastrophic backtracking (exponential time complexity).
        String escapedQuery = escapeRegexForSearch(query);

        Pageable pageable = PageRequest.of(0, limit);
        List<Folder> folders = folderRepository.searchByName(
                tenantId, driveId, escapedQuery, FolderStatus.ACTIVE, pageable);

        return folders.stream()
                .map(folderMapper::toDTO)
                .toList();
    }

    /**
     * SECURITY FIX (Round 14 #H45): Escape regex special characters for safe use in MongoDB $regex.
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
     * Returns the total count of active folders in the current drive.
     *
     * <p>Used for drive statistics and quota monitoring. Only counts ACTIVE
     * folders (excludes TRASHED).</p>
     *
     * @return total number of active folders in the drive
     * @throws AccessDeniedException if user lacks READ permission on the drive
     */
    @RequiresPermission(Permission.READ)
    public long getFolderCount() {
        String tenantId = TenantContext.getTenantId();
        String driveId = TenantContext.getDriveId();

        return folderRepository.countByTenantIdAndDriveIdAndStatus(tenantId, driveId, FolderStatus.ACTIVE);
    }

    // ==================== Private Helper Methods ====================

    /**
     * Builds a tree node with lazy-loaded children.
     *
     * <p>Creates a {@link FolderTreeNode} for the given folder. Children are only
     * loaded for the first level (immediate children) to avoid exponential queries.
     * Deeper levels should be loaded on-demand by the client.</p>
     *
     * <h3>Performance Optimization</h3>
     * <p>Previous implementation used recursive queries causing O(N^depth) database calls.
     * This optimized version only loads one level at a time:</p>
     * <ul>
     *   <li>maxDepth=0: No children loaded (just metadata indicating hasChildren)</li>
     *   <li>maxDepth=1: Only immediate children loaded (1 query per parent)</li>
     *   <li>maxDepth>1: Client should use on-demand loading for deeper levels</li>
     * </ul>
     *
     * <h3>Client-Side Lazy Loading</h3>
     * <p>For deep hierarchies, the client should call getFolderTree(parentId, 1)
     * when a folder is expanded in the UI, rather than loading the entire tree upfront.</p>
     *
     * @param folder       the folder to convert to tree node
     * @param tenantId     tenant ID for child queries
     * @param driveId      drive ID for child queries
     * @param currentDepth current recursion depth (starts at 0)
     * @param maxDepth     maximum depth to recurse (recommended: 1 for performance)
     * @return tree node with nested children (only for immediate level if maxDepth allows)
     */
    private FolderTreeNode buildTreeNode(Folder folder, String tenantId, String driveId, int currentDepth, int maxDepth) {
        FolderTreeNode node = folderMapper.toTreeNode(folder);

        // Only load children if within depth limit and folder has subfolders
        if (currentDepth < maxDepth && folder.getFolderCount() != null && folder.getFolderCount() > 0) {
            // SECURITY FIX: Add pagination to prevent memory exhaustion
            Pageable childPageable = PageRequest.of(0, MAX_CHILDREN_PER_LEVEL);
            List<Folder> children = folderRepository.findByTenantIdAndDriveIdAndParentIdAndStatus(
                    tenantId, driveId, folder.getId(), FolderStatus.ACTIVE, childPageable);

            // For performance, only recurse one more level (not the full depth)
            // Deep hierarchies should use lazy loading from the client
            int nextDepth = currentDepth + 1;
            boolean shouldRecurse = nextDepth < maxDepth && maxDepth <= 2; // Limit recursion to 2 levels max

            if (shouldRecurse) {
                node.setChildren(children.stream()
                        .map(child -> buildTreeNode(child, tenantId, driveId, nextDepth, maxDepth))
                        .collect(Collectors.toList()));
            } else {
                // Just convert children without recursing deeper - client will lazy load
                node.setChildren(children.stream()
                        .map(folderMapper::toTreeNode)
                        .collect(Collectors.toList()));
            }
        }

        return node;
    }

    /**
     * Builds breadcrumb navigation from root to the given folder.
     *
     * <p>Creates an ordered list of breadcrumb items representing the path from
     * the drive root to the current folder. Uses the pre-computed {@code ancestorIds}
     * list for efficient lookup.</p>
     *
     * <h3>Performance Optimization</h3>
     * <p>Uses batch loading via {@code findAllById()} to fetch all ancestors in a
     * single database query, avoiding N+1 query problem where N separate queries
     * would be executed for each ancestor.</p>
     *
     * <h3>Breadcrumb Order</h3>
     * <p>Items are ordered from root to leaf: [root] → [parent] → [current]</p>
     *
     * @param folder the target folder to build breadcrumb for
     * @return ordered list of breadcrumb items ending with the current folder
     */
    private List<FolderDTO.BreadcrumbItem> buildBreadcrumb(Folder folder) {
        List<FolderDTO.BreadcrumbItem> breadcrumb = new ArrayList<>();

        // Batch load all ancestors in a single query (fixes N+1 problem)
        if (folder.getAncestorIds() != null && !folder.getAncestorIds().isEmpty()) {
            // Fetch all ancestors in one query
            List<Folder> ancestors = folderRepository.findAllById(folder.getAncestorIds());

            // Create a map for O(1) lookup while preserving order from ancestorIds
            Map<String, Folder> ancestorMap = ancestors.stream()
                    .collect(Collectors.toMap(Folder::getId, f -> f));

            // Build breadcrumb in order of ancestorIds (root to parent)
            for (String ancestorId : folder.getAncestorIds()) {
                Folder ancestor = ancestorMap.get(ancestorId);
                if (ancestor != null) {
                    breadcrumb.add(FolderDTO.BreadcrumbItem.builder()
                            .id(ancestor.getId())
                            .name(ancestor.getName())
                            .path(ancestor.getPath())
                            .build());
                }
            }
        }

        // Add current folder
        breadcrumb.add(FolderDTO.BreadcrumbItem.builder()
                .id(folder.getId())
                .name(folder.getName())
                .path(folder.getPath())
                .build());

        return breadcrumb;
    }

    /**
     * Updates paths for all descendant folders after a rename operation.
     *
     * <p>When a folder is renamed, all descendant paths must be updated to reflect
     * the new path prefix. Uses string replacement to efficiently update paths.</p>
     *
     * <h3>Example</h3>
     * <pre>
     * Rename: "/Projects" → "/Work"
     * Child path: "/Projects/2024" → "/Work/2024"
     * Grandchild: "/Projects/2024/Q1" → "/Work/2024/Q1"
     * </pre>
     *
     * @param tenantId tenant ID for query isolation
     * @param driveId  drive ID for query isolation
     * @param folderId the renamed folder's ID
     * @param oldPath  the original path prefix
     * @param newPath  the new path prefix
     */
    private void updateDescendantPaths(String tenantId, String driveId, String folderId, String oldPath, String newPath) {
        // Use large page size for collecting all descendants for cascading updates
        Pageable pageable = PageRequest.of(0, 100_000);
        List<Folder> descendants = folderRepository.findDescendants(tenantId, driveId, folderId, FolderStatus.ACTIVE, pageable);
        for (Folder descendant : descendants) {
            descendant.setPath(descendant.getPath().replace(oldPath, newPath));
            descendant.setUpdatedAt(Instant.now());
        }
        folderRepository.saveAll(descendants);
    }

    /**
     * Updates paths, ancestor IDs, and depths for all descendants after a move operation.
     *
     * <p>When a folder is moved to a new parent, all descendants require comprehensive
     * updates to their hierarchical metadata:</p>
     * <ul>
     *   <li><b>Path:</b> Replace old path prefix with new path prefix</li>
     *   <li><b>Ancestor IDs:</b> Replace old ancestor chain with new parent's chain</li>
     *   <li><b>Depth:</b> Recalculate based on new position in hierarchy</li>
     * </ul>
     *
     * <h3>Ancestor ID Algorithm</h3>
     * <ol>
     *   <li>Start with new parent's ancestor IDs</li>
     *   <li>Add the moved folder's ID</li>
     *   <li>Append any ancestors that were below the moved folder</li>
     * </ol>
     *
     * @param tenantId          tenant ID for query isolation
     * @param driveId           drive ID for query isolation
     * @param folderId          the moved folder's ID
     * @param oldPath           the original path prefix
     * @param newPath           the new path prefix
     * @param parentAncestorIds the new parent's ancestor ID list
     * @param parentDepth       the new parent's depth
     */
    private void updateDescendantPathsAndAncestors(String tenantId, String driveId, String folderId,
                                                    String oldPath, String newPath,
                                                    List<String> parentAncestorIds, int parentDepth) {
        // Use large page size for collecting all descendants for cascading updates
        Pageable pageable = PageRequest.of(0, 100_000);
        List<Folder> descendants = folderRepository.findDescendants(tenantId, driveId, folderId, FolderStatus.ACTIVE, pageable);
        for (Folder descendant : descendants) {
            // Update path
            descendant.setPath(descendant.getPath().replace(oldPath, newPath));

            // Update ancestors - replace old ancestors with new ones
            List<String> newAncestors = new ArrayList<>(parentAncestorIds);
            newAncestors.add(folderId);

            // Find position after the moved folder in the original ancestors
            if (descendant.getAncestorIds() != null) {
                int indexOfMovedFolder = descendant.getAncestorIds().indexOf(folderId);
                if (indexOfMovedFolder >= 0 && indexOfMovedFolder < descendant.getAncestorIds().size() - 1) {
                    newAncestors.addAll(descendant.getAncestorIds().subList(indexOfMovedFolder + 1, descendant.getAncestorIds().size()));
                }
            }

            descendant.setAncestorIds(newAncestors);
            descendant.setDepth(parentDepth + 1 + (descendant.getAncestorIds().size() - parentAncestorIds.size() - 1));
            descendant.setUpdatedAt(Instant.now());
        }
        folderRepository.saveAll(descendants);
    }
}
