package com.teamsync.audit.model;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Record of a purge operation for compliance tracking.
 * This is also stored in ImmuDB as an immutable record.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Document(collection = "purge_records")
@CompoundIndexes({
        @CompoundIndex(name = "tenant_time_idx", def = "{'tenantId': 1, 'purgeTime': -1}")
})
public class PurgeRecord {

    @Id
    String id;
    String tenantId;
    String purgedBy; // User ID of the admin who executed the purge.
    String purgedByEmail;  // User email for audit trail.
    String purgeReason; // Reason provided for the purge operation.
    String retentionPeriod; // Retention period that was applied (e.g., "365d")
    int eventsPurged; // Number of events that were purged.
    Instant purgeTime; // Timestamp of the purge operation.
    boolean immudbPurged; // Whether the purge was applied to ImmuDB.
    boolean mongodbPurged; // Whether the purge was applied to MongoDB mirror.
    String errorMessage;   // Any error message if the purge partially failed.

    /**
     * Hash chain value before the purge.
     * Used to verify the audit trail was intact before purging.
     */
    String hashBefore;

    /**
     * Hash chain value after the purge.
     * Used to continue the hash chain after purge.
     */
    String hashAfter;
}
