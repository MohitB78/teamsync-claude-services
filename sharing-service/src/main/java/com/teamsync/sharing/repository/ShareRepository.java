package com.teamsync.sharing.repository;

import com.teamsync.sharing.model.Share;
import com.teamsync.sharing.model.Share.ResourceType;
import com.teamsync.sharing.model.Share.ShareeType;
import org.springframework.data.mongodb.repository.Meta;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShareRepository extends MongoRepository<Share, String> {

    // SECURITY: Find share by ID with tenant isolation
    Optional<Share> findByIdAndTenantId(String id, String tenantId);

    /**
     * Find shares for a resource with pagination.
     * SECURITY FIX (Round 14 #M1): Added pagination to prevent memory exhaustion DoS.
     * A heavily shared resource could return thousands of shares without pagination.
     * SECURITY FIX (Round 15 #M6): 5s timeout for API-facing queries.
     */
    @Meta(maxExecutionTimeMs = 5000)
    List<Share> findByTenantIdAndResourceId(String tenantId, String resourceId, org.springframework.data.domain.Pageable pageable);

    /**
     * @deprecated Use paginated version instead to prevent DoS attacks.
     */
    @Deprecated(forRemoval = true)
    default List<Share> findByTenantIdAndResourceId(String tenantId, String resourceId) {
        return findByTenantIdAndResourceId(tenantId, resourceId,
                org.springframework.data.domain.PageRequest.of(0, 1000));
    }

    // Find shares with specific user
    List<Share> findByTenantIdAndSharedWithIdAndSharedWithType(
            String tenantId, String sharedWithId, ShareeType sharedWithType);

    /**
     * Find specific share.
     * SECURITY FIX (Round 14 #C5): Added tenant filter to prevent cross-tenant information disclosure.
     */
    Optional<Share> findByTenantIdAndResourceIdAndSharedWithId(String tenantId, String resourceId, String sharedWithId);

    /**
     * Find shares created by user with pagination.
     * SECURITY FIX (Round 14 #M1): Added pagination to prevent memory exhaustion DoS.
     */
    List<Share> findByTenantIdAndSharedById(String tenantId, String sharedById, org.springframework.data.domain.Pageable pageable);

    /**
     * Find shares owned by user with pagination.
     * SECURITY FIX (Round 14 #M1): Added pagination to prevent memory exhaustion DoS.
     */
    List<Share> findByTenantIdAndOwnerId(String tenantId, String ownerId, org.springframework.data.domain.Pageable pageable);

    // Find all shares for user (as sharee) across resources
    // SECURITY FIX (Round 15 #M6): 5s timeout for API-facing queries
    @Meta(maxExecutionTimeMs = 5000)
    @Query("{ 'tenantId': ?0, '$or': [ " +
            "{ 'sharedWithId': ?1, 'sharedWithType': 'USER' }, " +
            "{ 'sharedWithId': { $in: ?2 }, 'sharedWithType': 'TEAM' }, " +
            "{ 'sharedWithId': ?3, 'sharedWithType': 'DEPARTMENT' }, " +
            "{ 'sharedWithType': 'EVERYONE' } ] }")
    List<Share> findSharesForUser(String tenantId, String userId, List<String> teamIds, String departmentId);

    // Check if user has access to resource
    // SECURITY FIX (Round 15 #M6): 5s timeout for permission checks (critical path)
    @Meta(maxExecutionTimeMs = 5000)
    @Query(value = "{ 'tenantId': ?0, 'resourceId': ?1, '$or': [ " +
            "{ 'sharedWithId': ?2, 'sharedWithType': 'USER' }, " +
            "{ 'sharedWithId': { $in: ?3 }, 'sharedWithType': 'TEAM' }, " +
            "{ 'sharedWithId': ?4, 'sharedWithType': 'DEPARTMENT' }, " +
            "{ 'sharedWithType': 'EVERYONE' } ] }", exists = true)
    boolean hasAccessToResource(String tenantId, String resourceId, String userId, List<String> teamIds, String departmentId);

    /**
     * Find expired shares within a tenant.
     * SECURITY FIX (Round 14 #H13): Added pagination to prevent memory exhaustion DoS.
     */
    @Query("{ 'tenantId': ?0, 'expiresAt': { $lt: ?1, $ne: null } }")
    List<Share> findExpiredSharesByTenant(String tenantId, Instant now, org.springframework.data.domain.Pageable pageable);

    /**
     * Delete all shares for a resource within a tenant.
     * SECURITY FIX (Round 14 #C1): Added tenant filter to prevent cross-tenant share deletion.
     * The old method without tenant filter has been removed as it was a critical security vulnerability.
     */
    void deleteByTenantIdAndResourceId(String tenantId, String resourceId);

    // Count shares for a resource
    long countByTenantIdAndResourceId(String tenantId, String resourceId);
}
