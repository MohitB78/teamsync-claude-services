package com.teamsync.storage.repository;

import com.teamsync.storage.model.UploadSession;
import com.teamsync.storage.model.UploadSession.UploadStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface UploadSessionRepository extends MongoRepository<UploadSession, String> {

    Optional<UploadSession> findByIdAndTenantId(String id, String tenantId);

    Optional<UploadSession> findByIdAndTenantIdAndDriveId(String id, String tenantId, String driveId);

    /**
     * Find sessions by user and status with pagination.
     * SECURITY FIX (Round 14 #H37): Added pagination to prevent memory exhaustion.
     */
    List<UploadSession> findByTenantIdAndUserIdAndStatus(String tenantId, String userId, UploadStatus status, Pageable pageable);

    /**
     * Find sessions by drive and status with pagination.
     * SECURITY FIX (Round 14 #H37): Added pagination to prevent memory exhaustion.
     */
    List<UploadSession> findByTenantIdAndDriveIdAndStatus(String tenantId, String driveId, UploadStatus status, Pageable pageable);

    /**
     * Find expired sessions within a tenant.
     * SECURITY FIX (Round 14 #H37): Added tenant filter and pagination to prevent DoS
     * via memory exhaustion from unbounded list queries across all tenants.
     * Also fixes missing tenant isolation - expired sessions were queryable across tenants.
     */
    @Query("{ 'tenantId': ?0, 'status': { $in: ['INITIATED', 'IN_PROGRESS'] }, 'expiresAt': { $lt: ?1 } }")
    List<UploadSession> findExpiredSessionsByTenant(String tenantId, Instant now, Pageable pageable);

    // Count active uploads for user
    long countByTenantIdAndUserIdAndStatusIn(String tenantId, String userId, List<UploadStatus> statuses);
}
