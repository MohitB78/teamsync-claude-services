package com.teamsync.audit.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Duration;
import java.time.Instant;

/**
 * Record of a purge operation for compliance tracking.
 * This is also stored in ImmuDB as an immutable record.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "purge_records")
@CompoundIndexes({
    @CompoundIndex(name = "tenant_time_idx", def = "{'tenantId': 1, 'purgeTime': -1}")
})
public class PurgeRecord {

    @Id
    private String id;

    private String tenantId;

    /**
     * User ID of the admin who executed the purge.
     */
    private String purgedBy;

    /**
     * User email for audit trail.
     */
    private String purgedByEmail;

    /**
     * Reason provided for the purge operation.
     */
    private String purgeReason;

    /**
     * Retention period that was applied (e.g., "365d").
     */
    private String retentionPeriod;

    /**
     * Number of events that were purged.
     */
    private int eventsPurged;

    /**
     * Timestamp of the purge operation.
     */
    private Instant purgeTime;

    /**
     * Hash chain value before the purge.
     * Used to verify the audit trail was intact before purging.
     */
    private String hashBefore;

    /**
     * Hash chain value after the purge.
     * Used to continue the hash chain after purge.
     */
    private String hashAfter;

    /**
     * Whether the purge was applied to ImmuDB.
     */
    private boolean immudbPurged;

    /**
     * Whether the purge was applied to MongoDB mirror.
     */
    private boolean mongodbPurged;

    /**
     * Any error message if the purge partially failed.
     */
    private String errorMessage;
}
