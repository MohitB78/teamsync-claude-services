package com.teamsync.audit.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * Audit statistics for admin dashboard.
 */
@Data
@Builder
public class AuditStats {

    /**
     * Total number of audit events.
     */
    private long totalEvents;

    /**
     * Number of events verified in ImmuDB.
     */
    private long verifiedEvents;

    /**
     * Number of events pending verification.
     */
    private long pendingEvents;

    /**
     * Event counts by action type.
     */
    private Map<String, Long> byAction;

    /**
     * Event counts by outcome.
     */
    private Map<String, Long> byOutcome;

    /**
     * Event counts by resource type.
     */
    private Map<String, Long> byResourceType;

    /**
     * Events in the last 24 hours.
     */
    private long last24Hours;

    /**
     * Events in the last 7 days.
     */
    private long last7Days;

    /**
     * Events in the last 30 days.
     */
    private long last30Days;

    /**
     * Number of purge operations.
     */
    private long purgeOperations;

    /**
     * Total events purged.
     */
    private long totalPurged;

    /**
     * When these stats were generated.
     */
    private Instant generatedAt;
}
