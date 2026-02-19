package com.teamsync.audit.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * Comprehensive integrity report for a tenant's audit trail.
 */
@Data
@Builder
public class IntegrityReport {

    private String tenantId;

    /**
     * Time range of the report.
     */
    private Instant startTime;
    private Instant endTime;

    /**
     * Total number of events in the time range.
     */
    private long totalEvents;

    /**
     * Number of events verified in ImmuDB.
     */
    private long immudbVerifiedEvents;

    /**
     * Number of events in MongoDB mirror.
     */
    private long mongodbEvents;

    /**
     * Whether the hash chain is intact for the entire time range.
     */
    private boolean hashChainIntact;

    /**
     * Number of hash chain verification failures.
     */
    private int hashChainFailures;

    /**
     * Breakdown by action type.
     */
    private Map<String, Long> eventsByAction;

    /**
     * Breakdown by outcome.
     */
    private Map<String, Long> eventsByOutcome;

    /**
     * Number of purge operations in the time range.
     */
    private int purgeOperations;

    /**
     * Total events purged in the time range.
     */
    private long eventsPurged;

    /**
     * Overall integrity status.
     */
    private String integrityStatus;

    /**
     * When the report was generated.
     */
    private Instant generatedAt;

    /**
     * Integrity status values.
     */
    public static final String STATUS_INTACT = "INTACT";
    public static final String STATUS_COMPROMISED = "COMPROMISED";
    public static final String STATUS_PARTIAL = "PARTIAL";
    public static final String STATUS_UNKNOWN = "UNKNOWN";
}
