package com.teamsync.presence.job;

import com.teamsync.common.context.TenantContext;
import com.teamsync.presence.config.PresenceProperties;
import com.teamsync.presence.service.DocumentPresenceService;
import com.teamsync.presence.service.UserPresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Scheduled job for cleaning up expired presence data.
 *
 * SECURITY FIX (Round 15 #C7-C9): All scheduled methods now clear TenantContext
 * in finally blocks to prevent context leakage in virtual thread pools.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PresenceCleanupJob {

    private final UserPresenceService userPresenceService;
    private final DocumentPresenceService documentPresenceService;
    private final PresenceProperties presenceProperties;

    /**
     * Clean up expired user presences.
     * Runs every minute by default.
     * Users who haven't sent a heartbeat within the timeout period are marked offline.
     *
     * SECURITY FIX (Round 15 #C7): Clear TenantContext after execution.
     */
    @Scheduled(fixedRateString = "${teamsync.presence.cleanup-interval-seconds:60}000")
    public void cleanupExpiredPresences() {
        Instant start = Instant.now();
        log.debug("Starting expired presence cleanup job");

        try {
            userPresenceService.cleanupExpiredPresences();
            log.debug("Expired presence cleanup completed in {}ms",
                    Instant.now().toEpochMilli() - start.toEpochMilli());
        } catch (Exception e) {
            log.error("Error during expired presence cleanup: {}", e.getMessage(), e);
        } finally {
            // SECURITY FIX (Round 15 #C7): Always clear TenantContext to prevent leakage
            TenantContext.clear();
        }
    }

    /**
     * Mark idle users as AWAY.
     * Runs every 30 seconds.
     * Users who haven't been active (mouse/keyboard) are marked as AWAY.
     *
     * SECURITY FIX (Round 15 #C8): Clear TenantContext after execution.
     */
    @Scheduled(fixedRate = 30000)
    public void markIdleUsersAway() {
        log.debug("Starting idle user check job");

        try {
            userPresenceService.markIdleUsersAway();
        } catch (Exception e) {
            log.error("Error during idle user check: {}", e.getMessage(), e);
        } finally {
            // SECURITY FIX (Round 15 #C8): Always clear TenantContext to prevent leakage
            TenantContext.clear();
        }
    }

    /**
     * Clean up idle document participants.
     * Runs every 2 minutes.
     * Participants who are inactive in documents are marked idle or removed.
     *
     * SECURITY FIX (Round 15 #C9): Clear TenantContext after execution.
     */
    @Scheduled(fixedRate = 120000)
    public void cleanupIdleDocumentParticipants() {
        log.debug("Starting idle document participant cleanup job");

        try {
            documentPresenceService.cleanupIdleParticipants();
        } catch (Exception e) {
            log.error("Error during idle document participant cleanup: {}", e.getMessage(), e);
        } finally {
            // SECURITY FIX (Round 15 #C9): Always clear TenantContext to prevent leakage
            TenantContext.clear();
        }
    }
}
