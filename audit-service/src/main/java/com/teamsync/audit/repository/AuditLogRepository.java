package com.teamsync.audit.repository;

import com.teamsync.audit.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for audit log mirror in MongoDB.
 * Provides fast query capabilities for the admin UI.
 */
@Repository
public interface AuditLogRepository extends MongoRepository<AuditLog, String> {

    // Basic tenant-scoped queries
    Page<AuditLog> findByTenantIdOrderByEventTimeDesc(String tenantId, Pageable pageable);

    Optional<AuditLog> findByIdAndTenantId(String id, String tenantId);

    // User activity queries
    Page<AuditLog> findByTenantIdAndUserIdOrderByEventTimeDesc(String tenantId, String userId, Pageable pageable);

    // Resource audit trail
    List<AuditLog> findByTenantIdAndResourceTypeAndResourceIdOrderByEventTimeAsc(
            String tenantId, String resourceType, String resourceId);

    Page<AuditLog> findByTenantIdAndResourceTypeAndResourceIdOrderByEventTimeDesc(
            String tenantId, String resourceType, String resourceId, Pageable pageable);

    // Action-based queries
    Page<AuditLog> findByTenantIdAndActionOrderByEventTimeDesc(
            String tenantId, String action, Pageable pageable);

    Page<AuditLog> findByTenantIdAndActionInOrderByEventTimeDesc(
            String tenantId, List<String> actions, Pageable pageable);

    // Outcome-based queries
    Page<AuditLog> findByTenantIdAndOutcomeOrderByEventTimeDesc(
            String tenantId, String outcome, Pageable pageable);

    // Time range queries
    Page<AuditLog> findByTenantIdAndEventTimeBetweenOrderByEventTimeDesc(
            String tenantId, Instant startTime, Instant endTime, Pageable pageable);

    // Complex search query
    @Query("{ 'tenantId': ?0, " +
           "$and: [" +
           "  { $or: [ { 'userId': { $regex: ?1, $options: 'i' } }, { ?1: null } ] }," +
           "  { $or: [ { 'action': ?2 }, { ?2: null } ] }," +
           "  { $or: [ { 'resourceType': ?3 }, { ?3: null } ] }," +
           "  { $or: [ { 'outcome': ?4 }, { ?4: null } ] }," +
           "  { 'eventTime': { $gte: ?5, $lte: ?6 } }" +
           "]}")
    Page<AuditLog> searchAuditLogs(
            String tenantId,
            String userIdPattern,
            String action,
            String resourceType,
            String outcome,
            Instant startTime,
            Instant endTime,
            Pageable pageable);

    // Count queries for statistics
    long countByTenantId(String tenantId);

    long countByTenantIdAndAction(String tenantId, String action);

    long countByTenantIdAndOutcome(String tenantId, String outcome);

    long countByTenantIdAndEventTimeBetween(String tenantId, Instant startTime, Instant endTime);

    // Purge query - delete events older than retention period
    void deleteByTenantIdAndEventTimeBefore(String tenantId, Instant cutoffTime);

    // Hash chain verification
    @Query(value = "{ 'tenantId': ?0 }", sort = "{ 'eventTime': -1 }")
    Optional<AuditLog> findLatestByTenantId(String tenantId);

    // Verification status queries
    long countByTenantIdAndImmudbVerified(String tenantId, boolean verified);
}
