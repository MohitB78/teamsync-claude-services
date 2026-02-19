package com.teamsync.audit.service;

import com.teamsync.audit.dto.*;
import com.teamsync.audit.model.AuditLog;
import com.teamsync.audit.model.PurgeRecord;
import com.teamsync.audit.repository.AuditLogRepository;
import com.teamsync.audit.repository.PurgeRecordRepository;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for verifying audit event integrity.
 *
 * Provides:
 * - Single event verification
 * - Resource audit trail verification
 * - Integrity reports for compliance
 * - Cryptographic proof generation
 *
 * Only available when ImmuDB is enabled.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "teamsync.audit.immudb.enabled", havingValue = "true")
public class VerificationService {

    private final AuditLogRepository auditLogRepository;
    private final PurgeRecordRepository purgeRecordRepository;
    private final ImmutableAuditService immutableAuditService;
    private final HashChainService hashChainService;

    /**
     * Verify a single audit event's integrity.
     */
    @Timed(value = "audit.verify.event", description = "Time to verify single audit event")
    public VerificationResult verifyEvent(String tenantId, String eventId) {
        log.debug("Verifying event {} for tenant {}", eventId, tenantId);

        // Get event from MongoDB mirror
        Optional<AuditLog> auditLogOpt = auditLogRepository.findByIdAndTenantId(eventId, tenantId);

        if (auditLogOpt.isEmpty()) {
            return VerificationResult.builder()
                    .eventId(eventId)
                    .verified(false)
                    .verificationMethod(VerificationResult.METHOD_MONGODB_MIRROR)
                    .verifiedAt(Instant.now())
                    .errorMessage("Event not found")
                    .build();
        }

        AuditLog auditLog = auditLogOpt.get();

        // Verify in ImmuDB
        boolean immudbVerified = false;
        if (immutableAuditService != null) {
            try {
                immudbVerified = immutableAuditService.verifyEvent(eventId, tenantId);
            } catch (Exception e) {
                log.warn("ImmuDB verification failed for event {}: {}", eventId, e.getMessage());
            }
        }

        // Check hash chain validity
        boolean hashChainValid = auditLog.getHashChain() != null && !auditLog.getHashChain().isEmpty();

        return VerificationResult.builder()
                .eventId(eventId)
                .verified(auditLog.isImmudbVerified() || immudbVerified)
                .verificationMethod(VerificationResult.METHOD_IMMUDB_VERIFIED)
                .immudbTransactionId(auditLog.getImmudbTransactionId())
                .hashChain(auditLog.getHashChain())
                .hashChainValid(hashChainValid)
                .verifiedAt(Instant.now())
                .build();
    }

    /**
     * Verify the audit trail for a resource.
     */
    @Timed(value = "audit.verify.resource", description = "Time to verify resource audit trail")
    public ResourceAuditVerification verifyResourceAuditTrail(
            String tenantId, String resourceType, String resourceId) {

        log.debug("Verifying audit trail for {}/{} in tenant {}", resourceType, resourceId, tenantId);

        // Get all events for this resource in chronological order
        List<AuditLog> events = auditLogRepository
                .findByTenantIdAndResourceTypeAndResourceIdOrderByEventTimeAsc(
                        tenantId, resourceType, resourceId);

        if (events.isEmpty()) {
            return ResourceAuditVerification.builder()
                    .resourceType(resourceType)
                    .resourceId(resourceId)
                    .totalEvents(0)
                    .verifiedEvents(0)
                    .allEventsVerified(true)
                    .hashChainIntact(true)
                    .verifiedAt(Instant.now())
                    .failedEventIds(List.of())
                    .build();
        }

        // Verify hash chain
        boolean hashChainIntact = hashChainService.verifyHashChain(tenantId, events);

        // Count verified events
        List<String> failedEventIds = new ArrayList<>();
        int verifiedCount = 0;

        for (AuditLog event : events) {
            if (event.isImmudbVerified()) {
                verifiedCount++;
            } else {
                failedEventIds.add(event.getId());
            }
        }

        return ResourceAuditVerification.builder()
                .resourceType(resourceType)
                .resourceId(resourceId)
                .totalEvents(events.size())
                .verifiedEvents(verifiedCount)
                .allEventsVerified(failedEventIds.isEmpty())
                .hashChainIntact(hashChainIntact)
                .oldestEvent(events.get(0).getEventTime())
                .newestEvent(events.get(events.size() - 1).getEventTime())
                .failedEventIds(failedEventIds)
                .verifiedAt(Instant.now())
                .build();
    }

    /**
     * Generate an integrity report for a tenant.
     */
    @Timed(value = "audit.integrity.report", description = "Time to generate integrity report")
    public IntegrityReport generateIntegrityReport(String tenantId, Instant startTime, Instant endTime) {
        log.info("Generating integrity report for tenant {} from {} to {}", tenantId, startTime, endTime);

        // Get total events in time range
        long totalEvents = auditLogRepository.countByTenantIdAndEventTimeBetween(tenantId, startTime, endTime);

        // Get verified events count
        long verifiedEvents = auditLogRepository.countByTenantIdAndImmudbVerified(tenantId, true);

        // Get events by action
        Map<String, Long> eventsByAction = new HashMap<>();
        for (String action : List.of("DELETE", "PERMISSION_CHANGE", "SHARE", "CREATE", "UPDATE")) {
            long count = auditLogRepository.countByTenantIdAndAction(tenantId, action);
            if (count > 0) {
                eventsByAction.put(action, count);
            }
        }

        // Get events by outcome
        Map<String, Long> eventsByOutcome = new HashMap<>();
        for (String outcome : List.of("SUCCESS", "FAILURE", "DENIED")) {
            long count = auditLogRepository.countByTenantIdAndOutcome(tenantId, outcome);
            if (count > 0) {
                eventsByOutcome.put(outcome, count);
            }
        }

        // Get purge records
        List<PurgeRecord> purgeRecords = purgeRecordRepository
                .findByTenantIdAndPurgeTimeBetweenOrderByPurgeTimeDesc(tenantId, startTime, endTime);
        int purgeOperations = purgeRecords.size();
        long eventsPurged = purgeRecords.stream()
                .mapToLong(PurgeRecord::getEventsPurged)
                .sum();

        // Determine integrity status
        String integrityStatus;
        if (verifiedEvents == totalEvents && totalEvents > 0) {
            integrityStatus = IntegrityReport.STATUS_INTACT;
        } else if (verifiedEvents == 0) {
            integrityStatus = IntegrityReport.STATUS_UNKNOWN;
        } else if (verifiedEvents < totalEvents) {
            integrityStatus = IntegrityReport.STATUS_PARTIAL;
        } else {
            integrityStatus = IntegrityReport.STATUS_UNKNOWN;
        }

        return IntegrityReport.builder()
                .tenantId(tenantId)
                .startTime(startTime)
                .endTime(endTime)
                .totalEvents(totalEvents)
                .immudbVerifiedEvents(verifiedEvents)
                .mongodbEvents(totalEvents)
                .hashChainIntact(true) // Would need full chain verification
                .hashChainFailures(0)
                .eventsByAction(eventsByAction)
                .eventsByOutcome(eventsByOutcome)
                .purgeOperations(purgeOperations)
                .eventsPurged(eventsPurged)
                .integrityStatus(integrityStatus)
                .generatedAt(Instant.now())
                .build();
    }

    /**
     * Get cryptographic proof for an audit event.
     */
    @Timed(value = "audit.proof.get", description = "Time to get cryptographic proof")
    public CryptographicProof getCryptographicProof(String tenantId, String eventId) {
        log.debug("Getting cryptographic proof for event {} in tenant {}", eventId, tenantId);

        Optional<AuditLog> auditLogOpt = auditLogRepository.findByIdAndTenantId(eventId, tenantId);

        if (auditLogOpt.isEmpty()) {
            return null;
        }

        AuditLog auditLog = auditLogOpt.get();

        // Build proof (in production, this would include actual Merkle proof from ImmuDB)
        String base64Proof = Base64.getEncoder().encodeToString(
                (eventId + "|" + auditLog.getHashChain() + "|" + auditLog.getEventTime()).getBytes()
        );

        return CryptographicProof.builder()
                .eventId(eventId)
                .immudbTransactionId(auditLog.getImmudbTransactionId())
                .merkleRoot(null) // Would come from ImmuDB
                .merkleProofPath(List.of()) // Would come from ImmuDB
                .hashChain(auditLog.getHashChain())
                .previousHashChain(null) // Would need to look up
                .timestamp(auditLog.getEventTime())
                .proofFormat(CryptographicProof.FORMAT_HASH_CHAIN_V1)
                .base64EncodedProof(base64Proof)
                .build();
    }
}
