package com.teamsync.signing.repository;

import com.teamsync.signing.model.SignatureEvent;
import com.teamsync.signing.model.SignatureEvent.SignatureEventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository for SignatureEvent audit trail.
 */
@Repository
public interface SignatureEventRepository extends MongoRepository<SignatureEvent, String> {

    /**
     * Find all events for a signature request, ordered by timestamp.
     */
    List<SignatureEvent> findByRequestIdOrderByTimestampAsc(String requestId);

    /**
     * Find all events for a signature request (paginated).
     */
    Page<SignatureEvent> findByRequestId(String requestId, Pageable pageable);

    /**
     * Find events for a tenant (recent first).
     */
    Page<SignatureEvent> findByTenantIdOrderByTimestampDesc(String tenantId, Pageable pageable);

    /**
     * Find events by type for a request.
     */
    List<SignatureEvent> findByRequestIdAndEventType(String requestId, SignatureEventType eventType);

    /**
     * Find events by actor email.
     */
    Page<SignatureEvent> findByActorEmailOrderByTimestampDesc(String actorEmail, Pageable pageable);

    /**
     * Find events in a time range.
     */
    @Query("{ 'tenantId': ?0, 'timestamp': { $gte: ?1, $lte: ?2 } }")
    List<SignatureEvent> findByTenantIdAndTimestampBetween(
            String tenantId, Instant startTime, Instant endTime);

    /**
     * Count events by type for a request.
     */
    long countByRequestIdAndEventType(String requestId, SignatureEventType eventType);
}
