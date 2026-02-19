package com.teamsync.content.job;

import com.teamsync.content.client.StorageServiceClient;
import com.teamsync.content.model.Document;
import com.teamsync.content.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Cleanup job for stale PENDING_UPLOAD documents.
 *
 * Safety net for uploads that were initialized but never completed.
 * Runs periodically to clean up orphaned documents and release storage quota.
 *
 * SECURITY FIX (Round 14 #H22): Added distributed lock to prevent concurrent execution
 * across multiple service instances. This prevents race conditions where multiple
 * instances might try to clean up the same documents simultaneously.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StalePendingDocumentCleanupJob {

    private final DocumentRepository documentRepository;
    private final StorageServiceClient storageServiceClient;
    private final StringRedisTemplate redisTemplate;

    /**
     * SECURITY FIX (Round 14 #H22): Redis key for distributed lock.
     */
    private static final String CLEANUP_LOCK_KEY = "teamsync:job:lock:pending-document-cleanup";

    /**
     * SECURITY FIX (Round 14 #H22): Lock duration - slightly longer than expected job duration.
     * If job takes longer, lock will expire and another instance might start,
     * but the pagination ensures they won't process the same documents.
     */
    private static final Duration LOCK_DURATION = Duration.ofMinutes(30);

    @Value("${teamsync.cleanup.pending-document-age-hours:24}")
    private int pendingDocumentAgeHours;

    /**
     * Clean up stale PENDING_UPLOAD documents.
     * Default schedule: Every 6 hours.
     *
     * SECURITY FIX (Round 14 #H22): Added distributed lock to prevent concurrent execution.
     * SECURITY FIX (Round 15 #C6): Clear TenantContext after scheduled job execution
     * to prevent context leakage in virtual thread pools.
     */
    @Scheduled(cron = "${teamsync.cleanup.pending-documents-cron:0 0 */6 * * *}")
    public void cleanupStalePendingDocuments() {
        // SECURITY FIX (Round 14 #H22): Try to acquire distributed lock
        Boolean lockAcquired = redisTemplate.opsForValue()
                .setIfAbsent(CLEANUP_LOCK_KEY, Instant.now().toString(), LOCK_DURATION);

        if (!Boolean.TRUE.equals(lockAcquired)) {
            log.info("Skipping stale pending document cleanup - another instance is running");
            return;
        }

        log.info("Starting stale pending document cleanup job (lock acquired)");

        try {
            Instant cutoff = Instant.now().minus(Duration.ofHours(pendingDocumentAgeHours));
            // SECURITY FIX (Round 15): Add pagination to prevent memory exhaustion
            Pageable pageable = PageRequest.of(0, 1000);
            List<Document> staleDocs = documentRepository.findStalePendingDocuments(cutoff, pageable);

            if (staleDocs.isEmpty()) {
                log.info("No stale pending documents found");
                return;
            }

            log.info("Found {} stale PENDING documents to clean up (older than {} hours)",
                    staleDocs.size(), pendingDocumentAgeHours);

            int successCount = 0;
            int failCount = 0;

            for (Document doc : staleDocs) {
                try {
                    cleanupDocument(doc);
                    successCount++;
                } catch (Exception e) {
                    log.error("Failed to cleanup pending document {}: {}", doc.getId(), e.getMessage());
                    failCount++;
                }
            }

            log.info("Stale pending document cleanup completed: {} succeeded, {} failed",
                    successCount, failCount);
        } finally {
            // SECURITY FIX (Round 14 #H22): Release the distributed lock
            try {
                redisTemplate.delete(CLEANUP_LOCK_KEY);
                log.debug("Released cleanup job lock");
            } catch (Exception e) {
                log.warn("Failed to release cleanup job lock: {}", e.getMessage());
                // Lock will auto-expire after LOCK_DURATION
            }

            // SECURITY FIX (Round 15 #C6): Always clear TenantContext to prevent leakage
            com.teamsync.common.context.TenantContext.clear();
        }
    }

    private void cleanupDocument(Document doc) {
        log.debug("Cleaning up stale pending document: {} (created: {})", doc.getId(), doc.getCreatedAt());

        // Cancel storage session if exists
        if (doc.getUploadSessionId() != null) {
            try {
                storageServiceClient.cancelUpload(doc.getUploadSessionId());
                log.debug("Cancelled storage session: {}", doc.getUploadSessionId());
            } catch (Exception e) {
                log.warn("Failed to cancel storage session {} for document {}: {}",
                        doc.getUploadSessionId(), doc.getId(), e.getMessage());
                // Continue with document deletion even if storage cancel fails
            }
        }

        // Delete the pending document
        documentRepository.delete(doc);
        log.info("Cleaned up stale pending document: {} (tenant: {}, drive: {})",
                doc.getId(), doc.getTenantId(), doc.getDriveId());
    }
}
