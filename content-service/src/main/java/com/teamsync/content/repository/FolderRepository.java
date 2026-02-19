package com.teamsync.content.repository;

import com.teamsync.content.model.Folder;
import com.teamsync.content.model.Folder.FolderStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Meta;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface FolderRepository extends MongoRepository<Folder, String> {

    // Find by ID with tenant and drive isolation
    Optional<Folder> findByIdAndTenantIdAndDriveId(String id, String tenantId, String driveId);

    Optional<Folder> findByIdAndTenantId(String id, String tenantId);

    /**
     * Find folders in parent folder with pagination.
     * SECURITY FIX (Round 14 #H40): Added pagination to prevent memory exhaustion.
     */
    List<Folder> findByTenantIdAndDriveIdAndParentIdAndStatus(
            String tenantId, String driveId, String parentId, FolderStatus status, Pageable pageable);

    // Find folders in parent with cursor pagination
    // SECURITY FIX (Round 15 #M6): 5s timeout for API-facing queries
    @Meta(maxExecutionTimeMs = 5000)
    @Query("{ 'tenantId': ?0, 'driveId': ?1, 'parentId': ?2, 'status': ?3, '_id': { $gt: ?4 } }")
    List<Folder> findByTenantIdAndDriveIdAndParentIdAndStatusAfterCursor(
            String tenantId, String driveId, String parentId, FolderStatus status, String cursor, Pageable pageable);

    /**
     * Find root level folders (parentId is null) with pagination.
     * SECURITY FIX (Round 14 #H40): Added pagination to prevent memory exhaustion.
     */
    @Query("{ 'tenantId': ?0, 'driveId': ?1, 'parentId': null, 'status': ?2 }")
    List<Folder> findRootFolders(String tenantId, String driveId, FolderStatus status, Pageable pageable);

    // Find by path
    Optional<Folder> findByTenantIdAndDriveIdAndPath(String tenantId, String driveId, String path);

    /**
     * Find all descendants (folders containing this folder's ID in ancestorIds) with pagination.
     * SECURITY FIX (Round 14 #H40): Added pagination to prevent memory exhaustion
     * for deeply nested folder hierarchies.
     * SECURITY FIX (Round 15 #M6): 5s timeout for API-facing queries.
     */
    @Meta(maxExecutionTimeMs = 5000)
    @Query("{ 'tenantId': ?0, 'driveId': ?1, 'ancestorIds': ?2, 'status': ?3 }")
    List<Folder> findDescendants(String tenantId, String driveId, String ancestorId, FolderStatus status, Pageable pageable);

    /**
     * Find all descendant IDs (for bulk operations) with pagination.
     * SECURITY FIX (Round 14 #H40): Added pagination to prevent memory exhaustion.
     */
    @Query(value = "{ 'tenantId': ?0, 'driveId': ?1, 'ancestorIds': ?2 }", fields = "{ '_id': 1 }")
    List<Folder> findDescendantIds(String tenantId, String driveId, String ancestorId, Pageable pageable);

    /**
     * Find starred folders with pagination.
     * SECURITY FIX (Round 14 #H40): Added pagination to prevent memory exhaustion.
     */
    List<Folder> findByTenantIdAndDriveIdAndIsStarredAndStatus(
            String tenantId, String driveId, Boolean isStarred, FolderStatus status, Pageable pageable);

    // Count folders in parent
    long countByTenantIdAndDriveIdAndParentIdAndStatus(
            String tenantId, String driveId, String parentId, FolderStatus status);

    // Count all folders in drive
    long countByTenantIdAndDriveIdAndStatus(String tenantId, String driveId, FolderStatus status);

    // Check if name exists in parent folder
    boolean existsByTenantIdAndDriveIdAndParentIdAndNameAndStatusNot(
            String tenantId, String driveId, String parentId, String name, FolderStatus status);

    // Search by name
    // SECURITY FIX (Round 15 #M6): 5s timeout for API-facing queries
    @Meta(maxExecutionTimeMs = 5000)
    @Query("{ 'tenantId': ?0, 'driveId': ?1, 'name': { $regex: ?2, $options: 'i' }, 'status': ?3 }")
    List<Folder> searchByName(String tenantId, String driveId, String namePattern, FolderStatus status, Pageable pageable);

    /**
     * Find trashed folders for cleanup with pagination.
     * SECURITY FIX (Round 14 #H40): Added pagination to prevent memory exhaustion
     * when processing large numbers of trashed folders.
     */
    @Query("{ 'tenantId': ?0, 'status': 'TRASHED', 'updatedAt': { $lt: ?1 } }")
    List<Folder> findTrashedFoldersOlderThan(String tenantId, Instant cutoffDate, Pageable pageable);

    /**
     * Find all trashed folders for a user across all their drives with pagination.
     * Used by the trash page to display all trashed items for the current user.
     * SECURITY FIX: 5s timeout for API-facing queries.
     */
    @Meta(maxExecutionTimeMs = 5000)
    @Query("{ 'tenantId': ?0, 'ownerId': ?1, 'status': 'TRASHED' }")
    List<Folder> findTrashedFoldersByOwner(String tenantId, String ownerId, Pageable pageable);

    /**
     * Count trashed folders for a user across all drives.
     */
    long countByTenantIdAndOwnerIdAndStatus(String tenantId, String ownerId, FolderStatus status);

    // ==================== NEW METHODS FOR CONTENT SERVICE ====================

    // Increment/decrement document count in a folder
    @Query("{ '_id': ?0, 'tenantId': ?1, 'driveId': ?2 }")
    @Update("{ '$inc': { 'documentCount': ?3 } }")
    void incrementDocumentCount(String folderId, String tenantId, String driveId, int delta);

    // Increment/decrement folder count in a parent folder
    @Query("{ '_id': ?0, 'tenantId': ?1, 'driveId': ?2 }")
    @Update("{ '$inc': { 'folderCount': ?3 } }")
    void incrementFolderCount(String folderId, String tenantId, String driveId, int delta);

    // Increment total size in a folder
    @Query("{ '_id': ?0, 'tenantId': ?1, 'driveId': ?2 }")
    @Update("{ '$inc': { 'totalSize': ?3 } }")
    void incrementTotalSize(String folderId, String tenantId, String driveId, long delta);

    /**
     * SECURITY FIX: Atomic update of both document count and total size.
     * Prevents race condition where folder statistics could become inconsistent
     * if separate incrementDocumentCount and incrementTotalSize calls are interleaved.
     */
    @Query("{ '_id': ?0, 'tenantId': ?1, 'driveId': ?2 }")
    @Update("{ '$inc': { 'documentCount': ?3, 'totalSize': ?4 }, '$set': { 'updatedAt': ?5 } }")
    void atomicUpdateDocumentStats(String folderId, String tenantId, String driveId,
                                    int documentCountDelta, long totalSizeDelta, Instant updatedAt);

    /**
     * SECURITY FIX: Atomic update for folder trash/restore including all stats.
     */
    @Query("{ '_id': ?0, 'tenantId': ?1, 'driveId': ?2 }")
    @Update("{ '$inc': { 'documentCount': ?3, 'folderCount': ?4, 'totalSize': ?5 }, '$set': { 'updatedAt': ?6 } }")
    void atomicUpdateAllStats(String folderId, String tenantId, String driveId,
                               int documentCountDelta, int folderCountDelta, long totalSizeDelta, Instant updatedAt);

    // Bulk update status for multiple folders (for cascading trash/restore)
    @Query("{ '_id': { $in: ?0 }, 'tenantId': ?1, 'driveId': ?2 }")
    @Update("{ '$set': { 'status': ?3, 'updatedAt': ?4 } }")
    void updateStatusByIdIn(List<String> folderIds, String tenantId, String driveId, FolderStatus status, Instant updatedAt);

    // Delete folders by IDs (for cascading permanent delete)
    @Query(value = "{ '_id': { $in: ?0 }, 'tenantId': ?1, 'driveId': ?2 }", delete = true)
    void deleteByIdIn(List<String> folderIds, String tenantId, String driveId);

    // ==================== OPTIMIZED CONTENT LISTING METHODS ====================

    // Sorted by name with pagination (uses tenant_drive_parent_status_name_idx)
    @Query("{ 'tenantId': ?0, 'driveId': ?1, 'parentId': ?2, 'status': ?3 }")
    List<Folder> findByTenantIdAndDriveIdAndParentIdAndStatusSorted(
            String tenantId, String driveId, String parentId, FolderStatus status, Pageable pageable);

    // Root folders sorted by name (uses tenant_drive_parent_status_name_idx with null parentId)
    @Query("{ 'tenantId': ?0, 'driveId': ?1, 'parentId': null, 'status': ?2 }")
    List<Folder> findRootFoldersSorted(String tenantId, String driveId, FolderStatus status, Pageable pageable);

    // Cursor pagination with (name, _id) compound cursor for stable ordering
    // Fetches items after cursor position: name > cursorName OR (name == cursorName AND _id > cursorId)
    @Query("{ 'tenantId': ?0, 'driveId': ?1, 'parentId': ?2, 'status': ?3, " +
           "$or: [ { 'name': { $gt: ?4 } }, { 'name': ?4, '_id': { $gt: ?5 } } ] }")
    List<Folder> findByParentIdSortedAfterCursor(
            String tenantId, String driveId, String parentId, FolderStatus status,
            String cursorName, String cursorId, Pageable pageable);

    // Root folders with cursor pagination
    @Query("{ 'tenantId': ?0, 'driveId': ?1, 'parentId': null, 'status': ?2, " +
           "$or: [ { 'name': { $gt: ?3 } }, { 'name': ?3, '_id': { $gt: ?4 } } ] }")
    List<Folder> findRootFoldersSortedAfterCursor(
            String tenantId, String driveId, FolderStatus status,
            String cursorName, String cursorId, Pageable pageable);

    /**
     * SECURITY FIX (Round 9): Check if a folder contains another folder in its ancestry.
     * Used to prevent circular references during folder move operations.
     * This direct DB query protects against TOCTOU race conditions.
     *
     * @param id the folder ID to check
     * @param ancestorId the potential ancestor folder ID
     * @return true if ancestorId is in the folder's ancestorIds array
     */
    boolean existsByIdAndAncestorIdsContaining(String id, String ancestorId);
}
