package com.teamsync.content.repository;

import com.teamsync.content.model.Document;
import com.teamsync.content.model.Document.DocumentStatus;
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
public interface DocumentRepository extends MongoRepository<Document, String> {

    // Find by ID with tenant and drive isolation
    Optional<Document> findByIdAndTenantIdAndDriveId(String id, String tenantId, String driveId);

    Optional<Document> findByIdAndTenantId(String id, String tenantId);

    /**
     * Find documents in folder with pagination.
     * SECURITY FIX (Round 14 #H38): Added pagination to prevent memory exhaustion
     * for folders with many documents.
     */
    List<Document> findByTenantIdAndDriveIdAndFolderIdAndStatus(
            String tenantId, String driveId, String folderId, DocumentStatus status, Pageable pageable);

    // Find documents in folder with cursor pagination
    // SECURITY FIX (Round 15 #M6): 5s timeout for API-facing queries
    @Meta(maxExecutionTimeMs = 5000)
    @Query("{ 'tenantId': ?0, 'driveId': ?1, 'folderId': ?2, 'status': ?3, '_id': { $gt: ?4 } }")
    List<Document> findByTenantIdAndDriveIdAndFolderIdAndStatusAfterCursor(
            String tenantId, String driveId, String folderId, DocumentStatus status, String cursor, Pageable pageable);

    /**
     * Find root level documents (folderId is null) with pagination.
     * SECURITY FIX (Round 14 #H38): Added pagination to prevent memory exhaustion.
     */
    @Query("{ 'tenantId': ?0, 'driveId': ?1, 'folderId': null, 'status': ?2 }")
    List<Document> findRootDocuments(String tenantId, String driveId, DocumentStatus status, Pageable pageable);

    /**
     * Find starred documents with pagination.
     * SECURITY FIX (Round 13 #9, Round 14 #H38): Removed unbounded version,
     * pagination is now required to prevent memory exhaustion.
     */
    List<Document> findByTenantIdAndDriveIdAndIsStarredAndStatus(
            String tenantId, String driveId, Boolean isStarred, DocumentStatus status, Pageable pageable);

    /**
     * Find by document type with pagination.
     * SECURITY FIX (Round 14 #H38): Added pagination to prevent memory exhaustion.
     */
    List<Document> findByTenantIdAndDriveIdAndDocumentTypeIdAndStatus(
            String tenantId, String driveId, String documentTypeId, DocumentStatus status, Pageable pageable);

    /**
     * Find by tags with pagination.
     * SECURITY FIX (Round 14 #H38): Added pagination to prevent memory exhaustion.
     */
    @Query("{ 'tenantId': ?0, 'driveId': ?1, 'tags': { $in: ?2 }, 'status': ?3 }")
    List<Document> findByTenantIdAndDriveIdAndTagsAndStatus(
            String tenantId, String driveId, List<String> tags, DocumentStatus status, Pageable pageable);

    // Find recently accessed
    List<Document> findByTenantIdAndOwnerIdAndStatusOrderByAccessedAtDesc(
            String tenantId, String ownerId, DocumentStatus status, Pageable pageable);

    // Count documents in drive
    long countByTenantIdAndDriveIdAndStatus(String tenantId, String driveId, DocumentStatus status);

    // Count documents in folder
    long countByTenantIdAndDriveIdAndFolderIdAndStatus(
            String tenantId, String driveId, String folderId, DocumentStatus status);

    // Check if name exists in folder
    boolean existsByTenantIdAndDriveIdAndFolderIdAndNameAndStatusNot(
            String tenantId, String driveId, String folderId, String name, DocumentStatus status);

    /**
     * Find by storage key with tenant isolation.
     * SECURITY FIX: Added tenantId parameter to prevent cross-tenant file access.
     * The old findByStorageKey method was a critical vulnerability allowing any user
     * to access documents from other tenants if they knew the storage key.
     */
    Optional<Document> findByStorageKeyAndTenantId(String storageKey, String tenantId);

    /**
     * @deprecated Use {@link #findByStorageKeyAndTenantId(String, String)} instead.
     * This method lacks tenant isolation and will be removed in a future release.
     * SECURITY: Do not use - allows cross-tenant file access.
     */
    @Deprecated(forRemoval = true)
    Optional<Document> findByStorageKey(String storageKey);

    // SECURITY: Check if document exists with tenant/drive isolation and storage key
    // Used to verify download token claims against actual database records
    boolean existsByTenantIdAndDriveIdAndStorageKey(String tenantId, String driveId, String storageKey);

    /**
     * Find trashed documents for cleanup with pagination.
     * SECURITY FIX (Round 14 #H39): Added pagination to prevent memory exhaustion
     * when processing large numbers of trashed documents.
     * SECURITY FIX (Round 15 #M6): 15s timeout for batch/cleanup jobs.
     */
    @Meta(maxExecutionTimeMs = 15000)
    @Query("{ 'tenantId': ?0, 'status': 'TRASHED', 'updatedAt': { $lt: ?1 } }")
    List<Document> findTrashedDocumentsOlderThan(String tenantId, Instant cutoffDate, Pageable pageable);

    /**
     * Find all trashed documents for a user in a drive with pagination.
     * Used by the trash page to display items that can be restored.
     * SECURITY FIX: 5s timeout for API-facing queries.
     */
    @Meta(maxExecutionTimeMs = 5000)
    @Query("{ 'tenantId': ?0, 'driveId': ?1, 'status': 'TRASHED' }")
    List<Document> findTrashedDocumentsByDrive(String tenantId, String driveId, Pageable pageable);

    /**
     * Find all trashed documents for a user across all their drives with pagination.
     * Used by the trash page to display all trashed items for the current user.
     * SECURITY FIX: 5s timeout for API-facing queries.
     */
    @Meta(maxExecutionTimeMs = 5000)
    @Query("{ 'tenantId': ?0, 'ownerId': ?1, 'status': 'TRASHED' }")
    List<Document> findTrashedDocumentsByOwner(String tenantId, String ownerId, Pageable pageable);

    /**
     * Count trashed documents for a user across all drives.
     */
    long countByTenantIdAndOwnerIdAndStatus(String tenantId, String ownerId, DocumentStatus status);

    // Search by name (case-insensitive)
    // SECURITY FIX (Round 15 #M6): 5s timeout for API-facing queries
    @Meta(maxExecutionTimeMs = 5000)
    @Query("{ 'tenantId': ?0, 'driveId': ?1, 'name': { $regex: ?2, $options: 'i' }, 'status': ?3 }")
    List<Document> searchByName(String tenantId, String driveId, String namePattern, DocumentStatus status, Pageable pageable);

    // ==================== NEW METHODS FOR CONTENT SERVICE ====================

    // Find documents in multiple folders (for cascading operations)
    @Query("{ 'tenantId': ?1, 'driveId': ?2, 'folderId': { $in: ?0 }, 'status': ?3 }")
    List<Document> findByFolderIdInAndTenantIdAndDriveIdAndStatus(
            List<String> folderIds, String tenantId, String driveId, DocumentStatus status);

    // Bulk update status for documents in folders (for cascading trash/restore)
    @Query("{ 'folderId': { $in: ?0 }, 'tenantId': ?1, 'driveId': ?2 }")
    @Update("{ '$set': { 'status': ?3, 'updatedAt': ?4 } }")
    void updateStatusByFolderIdIn(List<String> folderIds, String tenantId, String driveId, DocumentStatus status, Instant updatedAt);

    // Delete documents by folder IDs (for cascading permanent delete)
    @Query(value = "{ 'folderId': { $in: ?0 }, 'tenantId': ?1, 'driveId': ?2 }", delete = true)
    void deleteByFolderIdIn(List<String> folderIds, String tenantId, String driveId);

    // Find all documents in a folder (for folder deletion - get storage keys for cleanup)
    @Query(value = "{ 'folderId': { $in: ?0 }, 'tenantId': ?1, 'driveId': ?2 }", fields = "{ '_id': 1, 'storageKey': 1, 'storageBucket': 1 }")
    List<Document> findStorageInfoByFolderIdIn(List<String> folderIds, String tenantId, String driveId);

    // Calculate total size of documents in folder
    @Query(value = "{ 'folderId': ?0, 'tenantId': ?1, 'driveId': ?2, 'status': 'ACTIVE' }", fields = "{ 'fileSize': 1 }")
    List<Document> findFileSizesByFolderId(String folderId, String tenantId, String driveId);

    // ==================== UPLOAD FLOW METHODS ====================

    // Find PENDING_UPLOAD document by ID, tenant, drive, and status
    Optional<Document> findByIdAndTenantIdAndDriveIdAndStatus(
            String id, String tenantId, String driveId, DocumentStatus status);

    // Find document by ID, tenant, drive, and status in list (for download)
    Optional<Document> findByIdAndTenantIdAndDriveIdAndStatusIn(
            String id, String tenantId, String driveId, List<DocumentStatus> statuses);

    /**
     * Find stale PENDING_UPLOAD documents for cleanup job (per-tenant).
     * SECURITY FIX (Round 14 #H39): Added tenant filter and pagination to prevent memory exhaustion
     * and ensure tenant isolation in cleanup operations.
     */
    @Query("{ 'tenantId': ?0, 'status': 'PENDING_UPLOAD', 'createdAt': { $lt: ?1 } }")
    List<Document> findStalePendingDocumentsByTenant(String tenantId, Instant cutoff, Pageable pageable);

    /**
     * Find stale PENDING_UPLOAD documents across all tenants for scheduled cleanup job.
     * This method is intended for system-level cleanup jobs only.
     * SECURITY FIX (Round 15): Added pagination to prevent memory exhaustion.
     * SECURITY FIX (Round 15 #M6): 15s timeout for batch/cleanup jobs.
     */
    @Meta(maxExecutionTimeMs = 15000)
    @Query("{ 'status': 'PENDING_UPLOAD', 'createdAt': { $lt: ?0 } }")
    List<Document> findStalePendingDocuments(Instant cutoff, Pageable pageable);

    // Find by upload session ID (for correlation with Storage Service)
    Optional<Document> findByUploadSessionIdAndTenantId(String uploadSessionId, String tenantId);

    // ==================== OPTIMIZED CONTENT LISTING METHODS ====================

    // Sorted by name with pagination (uses tenant_drive_folder_status_name_idx)
    @Query("{ 'tenantId': ?0, 'driveId': ?1, 'folderId': ?2, 'status': ?3 }")
    List<Document> findByTenantIdAndDriveIdAndFolderIdAndStatusSorted(
            String tenantId, String driveId, String folderId, DocumentStatus status, Pageable pageable);

    // Root documents sorted by name (uses tenant_drive_folder_status_name_idx with null folderId)
    @Query("{ 'tenantId': ?0, 'driveId': ?1, 'folderId': null, 'status': ?2 }")
    List<Document> findRootDocumentsSorted(String tenantId, String driveId, DocumentStatus status, Pageable pageable);

    // Cursor pagination with (name, _id) compound cursor for stable ordering
    // Fetches items after cursor position: name > cursorName OR (name == cursorName AND _id > cursorId)
    // SECURITY FIX (Round 15 #M6): 5s timeout for API-facing queries
    @Meta(maxExecutionTimeMs = 5000)
    @Query("{ 'tenantId': ?0, 'driveId': ?1, 'folderId': ?2, 'status': ?3, " +
           "$or: [ { 'name': { $gt: ?4 } }, { 'name': ?4, '_id': { $gt: ?5 } } ] }")
    List<Document> findByFolderIdSortedAfterCursor(
            String tenantId, String driveId, String folderId, DocumentStatus status,
            String cursorName, String cursorId, Pageable pageable);

    // Root documents with cursor pagination
    @Query("{ 'tenantId': ?0, 'driveId': ?1, 'folderId': null, 'status': ?2, " +
           "$or: [ { 'name': { $gt: ?3 } }, { 'name': ?3, '_id': { $gt: ?4 } } ] }")
    List<Document> findRootDocumentsSortedAfterCursor(
            String tenantId, String driveId, DocumentStatus status,
            String cursorName, String cursorId, Pageable pageable);

    // ==================== BATCH COUNT METHODS (Phase 2 Optimization) ====================

    // Count documents in multiple folders at once (fixes N+1 query in getFolderContentsStats)
    @Query(value = "{ 'tenantId': ?0, 'driveId': ?1, 'folderId': { $in: ?2 }, 'status': ?3 }", count = true)
    long countByTenantIdAndDriveIdAndFolderIdInAndStatus(
            String tenantId, String driveId, List<String> folderIds, DocumentStatus status);

    // ==================== ATOMIC LOCK METHODS (SECURITY FIX) ====================

    /**
     * Atomically acquire a lock on a document if it's not already locked.
     * SECURITY FIX: Prevents race condition where two users could both check isLocked=false
     * and then both try to lock, resulting in one user's lock being overwritten.
     *
     * Uses MongoDB's findAndModify with conditional update:
     * - Only updates if isLocked=false (or isLocked is null)
     * - Returns the modified document if lock was acquired
     * - Returns null if document was already locked or doesn't exist
     *
     * @param documentId The document ID
     * @param tenantId The tenant ID
     * @param driveId The drive ID
     * @param userId The user attempting to acquire the lock
     * @param lockedAt The timestamp for the lock
     * @return UpdateResult with modifiedCount=1 if lock acquired, 0 if failed
     */
    @Query("{ '_id': ?0, 'tenantId': ?1, 'driveId': ?2, '$or': [{'isLocked': false}, {'isLocked': null}] }")
    @Update("{ '$set': { 'isLocked': true, 'lockedBy': ?3, 'lockedAt': ?4, 'updatedAt': ?4 } }")
    long atomicAcquireLock(String documentId, String tenantId, String driveId, String userId, Instant lockedAt);

    /**
     * Atomically release a lock on a document if it's locked by the specified user.
     * SECURITY FIX: Prevents race condition where lock could be released by wrong user
     * or released multiple times.
     *
     * @param documentId The document ID
     * @param tenantId The tenant ID
     * @param driveId The drive ID
     * @param userId The user who owns the lock
     * @param updatedAt The timestamp for the update
     * @return 1 if lock was released, 0 if document not found or not locked by this user
     */
    @Query("{ '_id': ?0, 'tenantId': ?1, 'driveId': ?2, 'isLocked': true, 'lockedBy': ?3 }")
    @Update("{ '$set': { 'isLocked': false, 'lockedBy': null, 'lockedAt': null, 'updatedAt': ?4 } }")
    long atomicReleaseLock(String documentId, String tenantId, String driveId, String userId, Instant updatedAt);
}
