package com.teamsync.content.repository;

import com.teamsync.content.model.DocumentVersion;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentVersionRepository extends MongoRepository<DocumentVersion, String> {

    /**
     * Find versions for a document with pagination.
     * SECURITY FIX (Round 14 #H42): Added pagination to prevent memory exhaustion
     * for documents with many versions.
     */
    List<DocumentVersion> findByDocumentIdOrderByVersionNumberDesc(String documentId, Pageable pageable);

    Optional<DocumentVersion> findByDocumentIdAndVersionNumber(String documentId, Integer versionNumber);

    Optional<DocumentVersion> findFirstByDocumentIdOrderByVersionNumberDesc(String documentId);

    long countByDocumentId(String documentId);

    /**
     * SECURITY FIX (Round 15 #H19): Added tenantId parameter for cross-tenant isolation.
     * Previous method deleteByDocumentId() lacked tenant filtering.
     */
    void deleteByTenantIdAndDocumentId(String tenantId, String documentId);

    /**
     * @deprecated Use {@link #deleteByTenantIdAndDocumentId(String, String)} instead
     * for proper tenant isolation.
     */
    @Deprecated(forRemoval = true)
    void deleteByDocumentId(String documentId);

    // ==================== NEW METHODS FOR CONTENT SERVICE ====================

    /**
     * Delete versions for multiple documents (for cascading delete).
     * SECURITY FIX (Round 15 #H20): Added tenantId filter for cross-tenant isolation.
     */
    @Query(value = "{ 'tenantId': ?0, 'documentId': { $in: ?1 } }", delete = true)
    void deleteByTenantIdAndDocumentIdIn(String tenantId, List<String> documentIds);

    /**
     * @deprecated Use {@link #deleteByTenantIdAndDocumentIdIn(String, List)} instead
     * for proper tenant isolation.
     */
    @Deprecated(forRemoval = true)
    @Query(value = "{ 'documentId': { $in: ?0 } }", delete = true)
    void deleteByDocumentIdIn(List<String> documentIds);

    /**
     * Find versions for multiple documents (to get storage keys for cleanup) with pagination.
     * SECURITY FIX: Added tenantId filter for cross-tenant isolation.
     * SECURITY FIX (Round 14 #H42): Added pagination to prevent memory exhaustion.
     */
    @Query(value = "{ 'tenantId': ?0, 'documentId': { $in: ?1 } }", fields = "{ '_id': 1, 'storageKey': 1, 'storageBucket': 1, 'documentId': 1 }")
    List<DocumentVersion> findStorageInfoByTenantIdAndDocumentIdIn(String tenantId, List<String> documentIds, Pageable pageable);

    /**
     * @deprecated Use {@link #findStorageInfoByTenantIdAndDocumentIdIn(String, List, Pageable)} instead
     * for proper tenant isolation.
     * SECURITY: Do not use - allows cross-tenant access.
     */
    @Deprecated(forRemoval = true)
    @Query(value = "{ 'documentId': { $in: ?0 } }", fields = "{ '_id': 1, 'storageKey': 1, 'storageBucket': 1, 'documentId': 1 }")
    List<DocumentVersion> findStorageInfoByDocumentIdIn(List<String> documentIds, Pageable pageable);

    /**
     * Find version by storage key with tenant isolation.
     * SECURITY FIX: Added tenantId parameter to prevent cross-tenant access.
     */
    Optional<DocumentVersion> findByStorageKeyAndTenantId(String storageKey, String tenantId);

    /**
     * @deprecated Use {@link #findByStorageKeyAndTenantId(String, String)} instead
     * for proper tenant isolation.
     * SECURITY: Do not use - allows cross-tenant access.
     */
    @Deprecated(forRemoval = true)
    Optional<DocumentVersion> findByStorageKey(String storageKey);
}
