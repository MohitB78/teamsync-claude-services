package com.teamsync.audit.repository;

import com.teamsync.audit.model.PurgeRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository for purge records.
 * Tracks all purge operations for compliance auditing.
 */
@Repository
public interface PurgeRecordRepository extends MongoRepository<PurgeRecord, String> {

    Page<PurgeRecord> findByTenantIdOrderByPurgeTimeDesc(String tenantId, Pageable pageable);

    List<PurgeRecord> findByTenantIdAndPurgeTimeBetweenOrderByPurgeTimeDesc(
            String tenantId, Instant startTime, Instant endTime);

    long countByTenantId(String tenantId);
}
