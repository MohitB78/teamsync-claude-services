package com.teamsync.content.controller;

import com.teamsync.common.dto.ApiResponse;
import com.teamsync.common.dto.CursorPage;
import com.teamsync.content.dto.folder.*;
import com.teamsync.content.service.ContentOrchestrationService;
import com.teamsync.content.service.FolderService;
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
import java.util.Map;

/**
 * REST controller for folder management operations in the TeamSync content service.
 *
 * <h2>Overview</h2>
 * <p>This controller provides hierarchical folder management including:
 * <ul>
 *   <li>CRUD operations for folders</li>
 *   <li>Folder hierarchy navigation (tree view)</li>
 *   <li>Move operations with hierarchy recalculation</li>
 *   <li>Cascading operations (trash/restore/delete affects all contents)</li>
 *   <li>Quick access features (starring)</li>
 * </ul>
 *
 * <h2>Folder Hierarchy</h2>
 * <p>Folders use a materialized path pattern for efficient queries:
 * <ul>
 *   <li>{@code ancestorIds} - Array of all ancestor folder IDs (for containment queries)</li>
 *   <li>{@code path} - Full path string (e.g., "/Documents/Projects/Q4")</li>
 *   <li>{@code depth} - Nesting level (0 for root folders)</li>
 *   <li>{@code parentId} - Direct parent reference (null for root)</li>
 * </ul>
 *
 * <h2>Cascading Operations</h2>
 * <p>Folder operations cascade to all contents:
 * <ul>
 *   <li><b>Trash</b>: Folder and all subfolders/documents move to trash</li>
 *   <li><b>Restore</b>: Folder and all contents restore together</li>
 *   <li><b>Delete</b>: Permanent deletion of folder tree and all files</li>
 * </ul>
 *
 * <h2>Security Model</h2>
 * <p>Folder access inherits drive-level RBAC:
 * <ul>
 *   <li>READ - View folders and navigate hierarchy</li>
 *   <li>WRITE - Create/rename/move folders</li>
 *   <li>DELETE - Move folders to trash</li>
 * </ul>
 *
 * <h2>Uniqueness Constraints</h2>
 * <p>Folders enforce uniqueness via compound indexes:
 * <ul>
 *   <li>Unique path within tenant+drive: {@code (tenantId, driveId, path)}</li>
 *   <li>Unique name within parent: {@code (tenantId, driveId, parentId, name)}</li>
 * </ul>
 *
 * @author TeamSync Platform Team
 * @version 1.0.0
 * @since Spring Boot 4.0.0
 * @see FolderService
 * @see ContentOrchestrationService
 *
 * SECURITY FIX (Round 14 #H29): Added @Validated and path variable validation
 * to prevent injection attacks via malicious IDs.
 *
 * SECURITY FIX (Round 15 #H1): Added @PreAuthorize("isAuthenticated()") to all endpoints
 * for defense-in-depth. Even though the API Gateway validates tokens, controller-level
 * authorization provides an additional security layer.
 */
@RestController
@RequestMapping("/api/folders")
@RequiredArgsConstructor
@Validated
@Slf4j
@PreAuthorize("isAuthenticated()")
public class FolderController {

    /**
     * SECURITY FIX (Round 14 #H29): Valid ID pattern for path variables.
     */
    private static final String VALID_ID_PATTERN = "^[a-zA-Z0-9-]{1,64}$";

    private final FolderService folderService;
    private final ContentOrchestrationService contentOrchestrationService;

    /**
     * Retrieves a folder by its unique identifier.
     *
     * @param folderId the folder ID (MongoDB ObjectId)
     * @return 200 OK with folder data including hierarchy info
     * @throws com.teamsync.common.exception.ResourceNotFoundException if folder not found
     *
     * SECURITY FIX (Round 14 #H29): Added path variable validation.
     */
    @GetMapping("/{folderId}")
    public ResponseEntity<ApiResponse<FolderDTO>> getFolder(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid folder ID format")
            String folderId) {
        log.debug("GET /api/folders/{}", folderId);

        FolderDTO folder = folderService.getFolder(folderId);

        return ResponseEntity.ok(ApiResponse.<FolderDTO>builder()
                .success(true)
                .data(folder)
                .message("Folder retrieved successfully")
                .build());
    }

    /**
     * Lists folders within a parent folder using cursor-based pagination.
     *
     * <p>Returns folders sorted by name (alphabetically). Use {@code parentId=null}
     * or omit for root-level folders in the drive.
     *
     * @param parentId the parent folder ID (null for root-level)
     * @param cursor   pagination cursor from previous response
     * @param limit    maximum items per page (default 50, max 100)
     * @return 200 OK with paginated folder list
     *
     * SECURITY FIX (Round 14 #H29): Added query parameter validation.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<CursorPage<FolderDTO>>> listFolders(
            @RequestParam(required = false)
            @Size(max = 64, message = "Parent ID must not exceed 64 characters")
            @Pattern(regexp = "^[a-zA-Z0-9-]*$", message = "Invalid parent ID format")
            String parentId,
            @RequestParam(required = false)
            @Size(max = 256, message = "Cursor must not exceed 256 characters")
            String cursor,
            @RequestParam(defaultValue = "50")
            @Min(value = 1, message = "Limit must be at least 1")
            @Max(value = 100, message = "Limit must not exceed 100")
            int limit) {

        log.debug("GET /api/folders - parentId: {}, cursor: {}, limit: {}", parentId, cursor, limit);

        // SECURITY FIX (Round 9): Validate limit to prevent negative values and enforce max
        limit = Math.max(1, Math.min(limit, 100));
        CursorPage<FolderDTO> folders = folderService.listFolders(parentId, cursor, limit);

        return ResponseEntity.ok(ApiResponse.<CursorPage<FolderDTO>>builder()
                .success(true)
                .data(folders)
                .message("Folders retrieved successfully")
                .build());
    }

    /**
     * Creates a new folder in the drive.
     *
     * <p>The folder's hierarchy metadata is automatically calculated:
     * <ul>
     *   <li>{@code path} - Full path from root</li>
     *   <li>{@code ancestorIds} - All ancestor folder IDs</li>
     *   <li>{@code depth} - Nesting level</li>
     * </ul>
     *
     * <p><b>Validation:</b> Returns 409 Conflict if a folder with the same
     * name already exists in the target parent folder.
     *
     * @param request the folder creation request with name and optional parentId
     * @return 201 Created with the new folder data
     * @throws IllegalStateException if duplicate name exists in parent
     */
    @PostMapping
    public ResponseEntity<ApiResponse<FolderDTO>> createFolder(
            @Valid @RequestBody CreateFolderRequest request) {

        log.info("POST /api/folders - name: {}", request.getName());

        FolderDTO folder = folderService.createFolder(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<FolderDTO>builder()
                        .success(true)
                        .data(folder)
                        .message("Folder created successfully")
                        .build());
    }

    /**
     * Updates folder metadata (name, description, color, etc.).
     *
     * <p><b>Note:</b> Renaming a folder updates its path and the paths of all
     * descendant folders. Use the move endpoint to change parent.
     *
     * @param folderId the folder to update
     * @param request  the update request with fields to change
     * @return 200 OK with updated folder data
     * @throws com.teamsync.common.exception.ResourceNotFoundException if folder not found
     * @throws IllegalStateException if new name conflicts with sibling
     */
    /**
     * SECURITY FIX (Round 14 #H29): Added path variable validation.
     */
    @PatchMapping("/{folderId}")
    public ResponseEntity<ApiResponse<FolderDTO>> updateFolder(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid folder ID format")
            String folderId,
            @Valid @RequestBody UpdateFolderRequest request) {

        log.info("PATCH /api/folders/{}", folderId);

        FolderDTO folder = folderService.updateFolder(folderId, request);

        return ResponseEntity.ok(ApiResponse.<FolderDTO>builder()
                .success(true)
                .data(folder)
                .message("Folder updated successfully")
                .build());
    }

    /**
     * Moves a folder to a different parent folder.
     *
     * <p>This operation recalculates hierarchy metadata for the folder and all
     * descendants:
     * <ul>
     *   <li>Updates {@code parentId} to new parent</li>
     *   <li>Recalculates {@code path}, {@code ancestorIds}, {@code depth}</li>
     *   <li>Propagates path changes to all descendant folders</li>
     * </ul>
     *
     * <p><b>Validation:</b>
     * <ul>
     *   <li>Cannot move folder into itself or its descendants (cycle detection)</li>
     *   <li>Cannot create duplicate name in target parent</li>
     * </ul>
     *
     * @param folderId the folder to move
     * @param request  the move request with new parentId (null for root)
     * @return 200 OK with updated folder data
     * @throws IllegalStateException if move would create cycle or name conflict
     */
    /**
     * SECURITY FIX (Round 14 #H29): Added path variable validation.
     */
    @PostMapping("/{folderId}/move")
    public ResponseEntity<ApiResponse<FolderDTO>> moveFolder(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid folder ID format")
            String folderId,
            @Valid @RequestBody MoveFolderRequest request) {

        log.info("POST /api/folders/{}/move", folderId);

        FolderDTO folder = folderService.moveFolder(folderId, request);

        return ResponseEntity.ok(ApiResponse.<FolderDTO>builder()
                .success(true)
                .data(folder)
                .message("Folder moved successfully")
                .build());
    }

    /**
     * Moves a folder and all its contents to trash (cascading soft delete).
     *
     * <p><b>Cascading behavior:</b> This operation affects the entire folder subtree:
     * <ul>
     *   <li>The folder itself is marked as TRASHED</li>
     *   <li>All subfolders recursively are marked as TRASHED</li>
     *   <li>All documents in the folder tree are marked as TRASHED</li>
     *   <li>All items retain 30-day retention in trash</li>
     * </ul>
     *
     * <p>Use {@code POST /{folderId}/restore} to restore the entire tree.
     *
     * @param folderId the folder to trash (along with all contents)
     * @return 200 OK on success
     * @throws com.teamsync.common.exception.ResourceNotFoundException if folder not found
     * @see #restoreFolder for restore operation
     *
     * SECURITY FIX (Round 14 #H29): Added path variable validation.
     */
    @DeleteMapping("/{folderId}")
    public ResponseEntity<ApiResponse<Void>> trashFolder(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid folder ID format")
            String folderId) {
        log.info("DELETE /api/folders/{} (trash)", folderId);

        // Use orchestration service for cascading trash
        contentOrchestrationService.trashFolderWithContents(folderId);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Folder and contents moved to trash")
                .build());
    }

    /**
     * Permanently deletes a folder and all its contents (cascading hard delete).
     *
     * <p><b>WARNING: This operation is irreversible.</b> It permanently deletes:
     * <ul>
     *   <li>The folder record from MongoDB</li>
     *   <li>All subfolder records recursively</li>
     *   <li>All document records and their files from storage</li>
     *   <li>All version history for affected documents</li>
     * </ul>
     *
     * <p>Storage quota is decremented for all deleted files.
     *
     * @param folderId the folder to permanently delete
     * @return 200 OK on success
     * @throws com.teamsync.common.exception.ResourceNotFoundException if folder not found
     *
     * SECURITY FIX (Round 14 #H29): Added path variable validation.
     */
    @DeleteMapping("/{folderId}/permanent")
    public ResponseEntity<ApiResponse<Void>> deleteFolder(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid folder ID format")
            String folderId) {
        log.info("DELETE /api/folders/{}/permanent", folderId);

        // Use orchestration service for cascading delete
        contentOrchestrationService.deleteFolderWithContents(folderId);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Folder and contents permanently deleted")
                .build());
    }

    /**
     * Restores a folder and all its contents from trash (cascading restore).
     *
     * <p><b>Cascading behavior:</b> Restores the entire folder subtree:
     * <ul>
     *   <li>The folder status changes from TRASHED to ACTIVE</li>
     *   <li>All subfolders recursively are restored</li>
     *   <li>All documents in the folder tree are restored</li>
     * </ul>
     *
     * <p><b>Name conflicts:</b> If a folder/document with the same name now exists
     * in the original location, timestamps are appended to restored items.
     *
     * @param folderId the trashed folder to restore
     * @return 200 OK with the restored folder data
     * @throws com.teamsync.common.exception.ResourceNotFoundException if folder not found
     * @throws IllegalStateException if folder is not in TRASHED status
     *
     * SECURITY FIX (Round 14 #H29): Added path variable validation.
     */
    @PostMapping("/{folderId}/restore")
    public ResponseEntity<ApiResponse<FolderDTO>> restoreFolder(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid folder ID format")
            String folderId) {
        log.info("POST /api/folders/{}/restore", folderId);

        // Use orchestration service for cascading restore
        FolderDTO folder = contentOrchestrationService.restoreFolderWithContents(folderId);

        return ResponseEntity.ok(ApiResponse.<FolderDTO>builder()
                .success(true)
                .data(folder)
                .message("Folder and contents restored successfully")
                .build());
    }

    /**
     * Toggles the starred status of a folder.
     *
     * <p>Starred folders appear in the user's "Starred" collection for quick access.
     *
     * @param folderId the folder to star/unstar
     * @param body     JSON object with {@code starred} boolean (default true)
     * @return 200 OK with updated folder data
     *
     * SECURITY FIX (Round 14 #H29): Added path variable validation.
     */
    @PostMapping("/{folderId}/star")
    public ResponseEntity<ApiResponse<FolderDTO>> toggleStar(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid folder ID format")
            String folderId,
            @RequestBody Map<String, Boolean> body) {

        log.info("POST /api/folders/{}/star", folderId);

        boolean starred = body.getOrDefault("starred", true);
        UpdateFolderRequest request = UpdateFolderRequest.builder()
                .isStarred(starred)
                .build();

        FolderDTO folder = folderService.updateFolder(folderId, request);

        return ResponseEntity.ok(ApiResponse.<FolderDTO>builder()
                .success(true)
                .data(folder)
                .message(starred ? "Folder starred" : "Folder unstarred")
                .build());
    }

    /**
     * Retrieves the folder tree structure for navigation UI.
     *
     * <p>Returns a hierarchical tree starting from the specified parent (or root).
     * Each node includes folder info and its immediate children, up to the
     * specified depth.
     *
     * <p><b>Performance:</b> Tree depth is limited to prevent expensive queries.
     * For deep navigation, use incremental loading by fetching subtrees as needed.
     *
     * @param parentId the starting folder (null for root)
     * @param maxDepth maximum tree depth to return (default 2, max 5)
     * @return 200 OK with folder tree structure
     *
     * SECURITY FIX (Round 14 #H29): Added query parameter validation.
     */
    @GetMapping("/tree")
    public ResponseEntity<ApiResponse<List<FolderTreeNode>>> getFolderTree(
            @RequestParam(required = false)
            @Size(max = 64, message = "Parent ID must not exceed 64 characters")
            @Pattern(regexp = "^[a-zA-Z0-9-]*$", message = "Invalid parent ID format")
            String parentId,
            @RequestParam(defaultValue = "2")
            @Min(value = 1, message = "Max depth must be at least 1")
            @Max(value = 5, message = "Max depth must not exceed 5")
            int maxDepth) {

        log.debug("GET /api/folders/tree - parentId: {}, maxDepth: {}", parentId, maxDepth);

        maxDepth = Math.min(maxDepth, 5);
        List<FolderTreeNode> tree = folderService.getFolderTree(parentId, maxDepth);

        return ResponseEntity.ok(ApiResponse.<List<FolderTreeNode>>builder()
                .success(true)
                .data(tree)
                .message("Folder tree retrieved successfully")
                .build());
    }

    /**
     * Retrieves all starred folders for the current user in the current drive.
     *
     * @return 200 OK with list of starred folders
     */
    @GetMapping("/starred")
    public ResponseEntity<ApiResponse<List<FolderDTO>>> getStarredFolders() {
        log.debug("GET /api/folders/starred");

        List<FolderDTO> folders = folderService.getStarredFolders();

        return ResponseEntity.ok(ApiResponse.<List<FolderDTO>>builder()
                .success(true)
                .data(folders)
                .message("Starred folders retrieved successfully")
                .build());
    }

    /**
     * Searches folders by name within the current drive.
     *
     * <p>Performs case-insensitive partial match on folder names.
     * Only returns ACTIVE folders (excludes TRASHED).
     *
     * @param q     the search query (minimum 1 character)
     * @param limit maximum results to return (default 50, max 100)
     * @return 200 OK with matching folders
     *
     * SECURITY FIX (Round 14 #H29): Added query parameter validation.
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<FolderDTO>>> searchFolders(
            @RequestParam
            @NotBlank(message = "Search query is required")
            @Size(min = 1, max = 100, message = "Search query must be between 1 and 100 characters")
            @Pattern(regexp = "^[\\w\\s\\-\\.]+$", message = "Search query contains invalid characters")
            String q,
            @RequestParam(defaultValue = "50")
            @Min(value = 1, message = "Limit must be at least 1")
            @Max(value = 100, message = "Limit must not exceed 100")
            int limit) {

        log.debug("GET /api/folders/search - query: {}, limit: {}", q, limit);

        // SECURITY FIX (Round 9): Validate limit to prevent negative values and enforce max
        limit = Math.max(1, Math.min(limit, 100));
        List<FolderDTO> folders = folderService.searchFolders(q, limit);

        return ResponseEntity.ok(ApiResponse.<List<FolderDTO>>builder()
                .success(true)
                .data(folders)
                .message("Search completed successfully")
                .build());
    }

    /**
     * Returns the total count of active folders in the current drive.
     *
     * @return 200 OK with folder count
     */
    @GetMapping("/count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getFolderCount() {
        log.debug("GET /api/folders/count");

        long count = folderService.getFolderCount();

        return ResponseEntity.ok(ApiResponse.<Map<String, Long>>builder()
                .success(true)
                .data(Map.of("count", count))
                .message("Folder count retrieved successfully")
                .build());
    }

    /**
     * Retrieves statistics about folder contents.
     *
     * <p>Returns counts and metadata about the folder's immediate and nested contents:
     * <ul>
     *   <li>Total subfolder count</li>
     *   <li>Total document count</li>
     *   <li>Total storage size</li>
     *   <li>Breakdown by document type</li>
     * </ul>
     *
     * @param folderId the folder to get statistics for
     * @return 200 OK with folder statistics
     * @throws com.teamsync.common.exception.ResourceNotFoundException if folder not found
     *
     * SECURITY FIX (Round 14 #H29): Added path variable validation.
     */
    @GetMapping("/{folderId}/stats")
    public ResponseEntity<ApiResponse<ContentOrchestrationService.FolderContentsStats>> getFolderStats(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid folder ID format")
            String folderId) {

        log.debug("GET /api/folders/{}/stats", folderId);

        ContentOrchestrationService.FolderContentsStats stats = contentOrchestrationService.getFolderContentsStats(folderId);

        return ResponseEntity.ok(ApiResponse.<ContentOrchestrationService.FolderContentsStats>builder()
                .success(true)
                .data(stats)
                .message("Folder statistics retrieved successfully")
                .build());
    }
}
