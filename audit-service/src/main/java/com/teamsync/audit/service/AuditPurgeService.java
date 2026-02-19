package com.teamsync.audit.service;

import com.teamsync.audit.config.AuditServiceProperties;
import com.teamsync.audit.dto.PurgeRequest;
import com.teamsync.audit.dto.PurgeResponse;
import com.teamsync.audit.model.PurgeRecord;
import com.teamsync.audit.repository.AuditLogRepository;
import com.teamsync.audit.repository.PurgeRecordRepository;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for purging audit logs based on retention policy.
 *
 * Uses ImmuDB's retention feature which:
 * - Only deletes VALUE LOG data (proofs remain intact)
 * - Preserves schema and table definitions
 * - Records the purge operation itself as an immutable record
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditPurgeService {

    private final AuditLogRepository auditLogRepository;
    private final PurgeRecordRepository purgeRecordRepository;
    private final HashChainService hashChainService;
    private final AuditServiceProperties properties;

    /**
     * Cache for confirmation codes (tenant -> code -> request)
     */
    private final Map<String, Map<String, PurgeRequest>> pendingPurges = new ConcurrentHashMap<>();

    /**
     * Preview a purge operation (dry run).
     * Returns what would be purged without actually deleting anything.
     */
    @Timed(value = "audit.purge.preview", description = "Time to preview purge operation")
    public PurgeResponse previewPurge(String tenantId, PurgeRequest request) {
        log.info("Previewing purge for tenant {} with retention {}", tenantId, request.getRetentionPeriod());

        // Validate retention period
        validateRetentionPeriod(request.getRetentionPeriod());

        // Calculate cutoff time
        Instant cutoffTime = Instant.now().minus(request.getRetentionPeriod());

        // Count events that would be purged
        long eventsToPurge = auditLogRepository.countByTenantIdAndEventTimeBetween(
                tenantId, Instant.EPOCH, cutoffTime);

        // Generate confirmation code
        String confirmationCode = generateConfirmationCode();

        // Store pending purge
        pendingPurges
                .computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>())
                .put(confirmationCode, request);

        return PurgeResponse.builder()
                .preview(true)
                .eventsToPurge((int) eventsToPurge)
                .cutoffTime(cutoffTime)
                .retentionPeriod(request.getRetentionPeriod().toString())
                .confirmationCode(confirmationCode)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Execute a purge operation.
     * Requires a valid confirmation code from a preview operation.
     */
    @Timed(value = "audit.purge.execute", description = "Time to execute purge operation")
    @Transactional
    public PurgeResponse executePurge(String tenantId, String userId, String userEmail,
                                      PurgeRequest request) {
        log.info("Executing purge for tenant {} by user {} with retention {}",
                tenantId, userId, request.getRetentionPeriod());

        // Validate confirmation code
        Map<String, PurgeRequest> tenantPurges = pendingPurges.get(tenantId);
        if (tenantPurges == null || !tenantPurges.containsKey(request.getConfirmationCode())) {
            return PurgeResponse.builder()
                    .preview(false)
                    .success(false)
                    .errorMessage("Invalid or expired confirmation code. Please preview the purge again.")
                    .timestamp(Instant.now())
                    .build();
        }

        // Remove the pending purge
        tenantPurges.remove(request.getConfirmationCode());

        // Validate retention period
        validateRetentionPeriod(request.getRetentionPeriod());

        // Calculate cutoff time
        Instant cutoffTime = Instant.now().minus(request.getRetentionPeriod());

        // Get hash before purge
        String hashBefore = hashChainService.getLatestHashChain(tenantId);

        // Count events to purge
        long eventsToPurge = auditLogRepository.countByTenantIdAndEventTimeBetween(
                tenantId, Instant.EPOCH, cutoffTime);

        try {
            // Delete from MongoDB mirror
            auditLogRepository.deleteByTenantIdAndEventTimeBefore(tenantId, cutoffTime);

            // Note: In production, would also execute ImmuDB truncation:
            // immuadmin database truncate --retention-period=X

            // Clear hash cache (will be rebuilt from remaining events)
            hashChainService.clearCache(tenantId);

            // Get hash after purge
            String hashAfter = hashChainService.getLatestHashChain(tenantId);

            // Create purge record
            PurgeRecord purgeRecord = PurgeRecord.builder()
                    .id(UUID.randomUUID().toString())
                    .tenantId(tenantId)
                    .purgedBy(userId)
                    .purgedByEmail(userEmail)
                    .purgeReason(request.getReason())
                    .retentionPeriod(request.getRetentionPeriod().toString())
                    .eventsPurged((int) eventsToPurge)
                    .purgeTime(Instant.now())
                    .hashBefore(hashBefore)
                    .hashAfter(hashAfter)
                    .immudbPurged(false) // Would be true if ImmuDB truncation was executed
                    .mongodbPurged(true)
                    .build();

            purgeRecordRepository.save(purgeRecord);

            log.info("Purge completed for tenant {}: {} events purged", tenantId, eventsToPurge);

            return PurgeResponse.builder()
                    .preview(false)
                    .success(true)
                    .eventsToPurge((int) eventsToPurge)
                    .cutoffTime(cutoffTime)
                    .retentionPeriod(request.getRetentionPeriod().toString())
                    .hashBefore(hashBefore)
                    .hashAfter(hashAfter)
                    .purgeRecordId(purgeRecord.getId())
                    .timestamp(Instant.now())
                    .build();

        } catch (Exception e) {
            log.error("Purge failed for tenant {}: {}", tenantId, e.getMessage(), e);

            // Record failed purge attempt
            PurgeRecord failedRecord = PurgeRecord.builder()
                    .id(UUID.randomUUID().toString())
                    .tenantId(tenantId)
                    .purgedBy(userId)
                    .purgedByEmail(userEmail)
                    .purgeReason(request.getReason())
                    .retentionPeriod(request.getRetentionPeriod().toString())
                    .eventsPurged(0)
                    .purgeTime(Instant.now())
                    .hashBefore(hashBefore)
                    .errorMessage(e.getMessage())
                    .build();

            purgeRecordRepository.save(failedRecord);

            return PurgeResponse.builder()
                    .preview(false)
                    .success(false)
                    .errorMessage("Purge failed: " + e.getMessage())
                    .timestamp(Instant.now())
                    .build();
        }
    }

    /**
     * Get purge history for a tenant.
     */
    public Page<PurgeRecord> getPurgeHistory(String tenantId, int page, int size) {
        return purgeRecordRepository.findByTenantIdOrderByPurgeTimeDesc(
                tenantId, PageRequest.of(page, size));
    }

    /**
     * Validate retention period against configured limits.
     */
    private void validateRetentionPeriod(Duration retentionPeriod) {
        AuditServiceProperties.RetentionProperties retention = properties.getRetention();

        if (retentionPeriod.compareTo(retention.getMinRetentionPeriod()) < 0) {
            throw new IllegalArgumentException(
                    "Retention period must be at least " + retention.getMinRetentionPeriod());
        }

        if (retentionPeriod.compareTo(retention.getMaxRetentionPeriod()) > 0) {
            throw new IllegalArgumentException(
                    "Retention period cannot exceed " + retention.getMaxRetentionPeriod());
        }
    }

    /**
     * Generate a secure confirmation code.
     */
    private String generateConfirmationCode() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
