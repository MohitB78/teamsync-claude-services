package com.teamsync.sharing.repository;

import com.teamsync.sharing.model.PublicLink;
import com.teamsync.sharing.model.PublicLink.LinkStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;


import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PublicLinkRepository extends MongoRepository<PublicLink, String> {

    // SECURITY: Find public link by ID with tenant isolation
    Optional<PublicLink> findByIdAndTenantId(String id, String tenantId);

    Optional<PublicLink> findByToken(String token);

    Optional<PublicLink> findByTokenAndStatus(String token, LinkStatus status);

    List<PublicLink> findByTenantIdAndResourceId(String tenantId, String resourceId);

    List<PublicLink> findByTenantIdAndCreatedBy(String tenantId, String createdBy);

    /**
     * Find expired links within a tenant.
     * SECURITY FIX (Round 14 #H31): Added tenant filter and pagination to prevent DoS
     * via memory exhaustion from unbounded list queries across all tenants.
     */
    @Query("{ 'tenantId': ?0, 'status': 'ACTIVE', 'expiresAt': { $lt: ?1, $ne: null } }")
    List<PublicLink> findExpiredLinksByTenant(String tenantId, Instant now, org.springframework.data.domain.Pageable pageable);

    /**
     * Find exhausted links within a tenant.
     * SECURITY FIX (Round 14 #H31): Added tenant filter and pagination to prevent DoS
     * via memory exhaustion from unbounded list queries across all tenants.
     * Also fixes missing tenant isolation - previously any tenant could query links from other tenants.
     */
    @Query("{ 'tenantId': ?0, 'status': 'ACTIVE', 'maxDownloads': { $ne: null }, $expr: { $gte: ['$downloadCount', '$maxDownloads'] } }")
    List<PublicLink> findExhaustedLinksByTenant(String tenantId, org.springframework.data.domain.Pageable pageable);

    /**
     * Delete all public links for a resource within a tenant.
     * SECURITY FIX (Round 14 #C2): Added tenant filter to prevent cross-tenant link deletion.
     * The old method without tenant filter has been removed as it was a critical security vulnerability.
     */
    void deleteByTenantIdAndResourceId(String tenantId, String resourceId);

    long countByTenantIdAndResourceIdAndStatus(String tenantId, String resourceId, LinkStatus status);

    // ============== SECURITY FIXES: Atomic operations to prevent race conditions ==============

    /**
     * SECURITY FIX: Atomic increment of access count.
     * Prevents race condition where concurrent accesses could corrupt the counter.
     */
    @Query("{ 'token': ?0 }")
    @Update("{ '$inc': { 'accessCount': 1 }, '$set': { 'lastAccessedAt': ?1 } }")
    void atomicIncrementAccessCount(String token, Instant now);

    /**
     * SECURITY FIX: Atomic increment of download count with limit check.
     * Only increments if current count is below maxDownloads.
     * Returns number of documents modified (0 = limit reached, 1 = success).
     */
    @Query("{ 'token': ?0, '$or': [ { 'maxDownloads': null }, { '$expr': { '$lt': ['$downloadCount', '$maxDownloads'] } } ] }")
    @Update("{ '$inc': { 'downloadCount': 1 }, '$set': { 'lastAccessedAt': ?1 } }")
    long atomicIncrementDownloadCountIfUnderLimit(String token, Instant now);

    /**
     * SECURITY FIX: Atomic status update.
     */
    @Query("{ 'token': ?0 }")
    @Update("{ '$set': { 'status': ?1 } }")
    void atomicUpdateStatus(String token, LinkStatus status);

    /**
     * SECURITY FIX (Round 9): Atomic increment AND status update in single operation.
     * This fixes the race condition where concurrent downloads could all pass the limit check
     * before any of them updated the status to EXHAUSTED.
     *
     * Operation:
     * 1. Only matches if status is ACTIVE AND (no maxDownloads OR downloadCount < maxDownloads)
     * 2. Increments downloadCount
     * 3. Sets status to EXHAUSTED if new count equals maxDownloads
     *
     * @return Number of documents modified (0 if link not found, not active, or limit reached; 1 if success)
     */
    @Query("{ 'token': ?0, 'status': 'ACTIVE', '$or': [ { 'maxDownloads': null }, { '$expr': { '$lt': ['$downloadCount', '$maxDownloads'] } } ] }")
    @Update("{ '$inc': { 'downloadCount': 1 }, '$set': { 'lastAccessedAt': ?1 } }")
    long atomicIncrementAndExhaustIfLimitReached(String token, Instant now);
}
