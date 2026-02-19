package com.teamsync.signing.repository;

import com.teamsync.signing.model.SignatureRequest;
import com.teamsync.signing.model.SignatureRequest.SignatureRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for SignatureRequest entities.
 */
@Repository
public interface SignatureRequestRepository extends MongoRepository<SignatureRequest, String> {

    /**
     * Find a request by ID and tenant.
     */
    Optional<SignatureRequest> findByIdAndTenantId(String id, String tenantId);

    /**
     * Find all requests for a tenant.
     */
    Page<SignatureRequest> findByTenantId(String tenantId, Pageable pageable);

    /**
     * Find requests by sender (internal user).
     */
    Page<SignatureRequest> findByTenantIdAndSenderId(String tenantId, String senderId, Pageable pageable);

    /**
     * Find requests by sender and status.
     */
    Page<SignatureRequest> findByTenantIdAndSenderIdAndStatusIn(
            String tenantId, String senderId, List<SignatureRequestStatus> statuses, Pageable pageable);

    /**
     * Find a request by signer's token hash.
     * This is the primary method for portal signing - validates the token.
     */
    @Query("{ 'signers.accessTokenHash': ?0, 'status': { $in: ['PENDING', 'IN_PROGRESS', 'COMPLETED'] } }")
    Optional<SignatureRequest> findBySignerAccessTokenHash(String tokenHash);

    /**
     * Find requests by tenant and status.
     */
    Page<SignatureRequest> findByTenantIdAndStatus(String tenantId, SignatureRequestStatus status, Pageable pageable);

    /**
     * Find a request by download token hash (for completed document download).
     */
    @Query("{ 'downloadTokenHash': ?0, 'status': 'COMPLETED', 'downloadTokenExpiresAt': { $gt: ?1 } }")
    Optional<SignatureRequest> findByDownloadToken(String tokenHash, Instant now);

    /**
     * Find pending requests by signer email.
     */
    @Query("{ 'signers.email': ?0, 'status': { $in: ['PENDING', 'IN_PROGRESS'] } }")
    List<SignatureRequest> findPendingBySignerEmail(String email);

    /**
     * Find requests that have expired but not yet marked as expired.
     */
    @Query("{ 'status': { $in: ['PENDING', 'IN_PROGRESS'] }, 'expiresAt': { $lt: ?0 } }")
    List<SignatureRequest> findExpiredRequests(Instant now);

    /**
     * Find requests needing reminders.
     * Returns requests that are pending/in_progress and expire within the given days.
     */
    @Query("{ 'status': { $in: ['PENDING', 'IN_PROGRESS'] }, 'expiresAt': { $gt: ?0, $lt: ?1 } }")
    List<SignatureRequest> findRequestsNeedingReminder(Instant now, Instant reminderThreshold);

    /**
     * Count requests by tenant and status.
     */
    long countByTenantIdAndStatus(String tenantId, SignatureRequestStatus status);

    /**
     * Count requests by sender and status.
     */
    long countByTenantIdAndSenderIdAndStatus(String tenantId, String senderId, SignatureRequestStatus status);
}
