package com.teamsync.audit.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Response for purge operation.
 */
@Data
@Builder
public class PurgeResponse {

    /**
     * Whether this is a preview response or actual execution result.
     */
    private boolean preview;

    /**
     * Number of events that will be / were purged.
     */
    private int eventsToPurge;

    /**
     * Cutoff time - events before this will be / were purged.
     */
    private Instant cutoffTime;

    /**
     * Retention period that was applied.
     */
    private String retentionPeriod;

    /**
     * Confirmation code for executing the purge (only in preview mode).
     */
    private String confirmationCode;

    /**
     * Whether the purge was successful (only in execution mode).
     */
    private Boolean success;

    /**
     * Hash chain value before purge.
     */
    private String hashBefore;

    /**
     * Hash chain value after purge.
     */
    private String hashAfter;

    /**
     * Purge record ID (only in execution mode).
     */
    private String purgeRecordId;

    /**
     * Error message if purge failed.
     */
    private String errorMessage;

    /**
     * Timestamp of the operation.
     */
    private Instant timestamp;
}
