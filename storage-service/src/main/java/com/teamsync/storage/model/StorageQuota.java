package com.teamsync.storage.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Tracks storage quota usage for a drive.
 *
 * SECURITY FIX (Round 15 #M33): Added @Version for optimistic locking to prevent
 * race conditions in concurrent quota updates (e.g., simultaneous uploads).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "storage_quotas")
@CompoundIndexes({
        @CompoundIndex(name = "tenant_drive_idx", def = "{'tenantId': 1, 'driveId': 1}", unique = true),
        @CompoundIndex(name = "tenant_idx", def = "{'tenantId': 1}")
})
public class StorageQuota {

    @Id
    private String id;

    /**
     * Version field for optimistic locking.
     * Critical for concurrent upload/delete operations.
     */
    @Version
    private Long version;

    private String tenantId;
    private String driveId;

    // Quota limits in bytes
    private Long quotaLimit;          // Max storage allowed
    private Long usedStorage;         // Current storage used
    private Long reservedStorage;     // Storage reserved for pending uploads

    // File count limits
    private Long maxFileCount;
    private Long currentFileCount;

    // File size limits
    private Long maxFileSizeBytes;    // Max size per file

    // Quotas by storage tier
    private Long hotStorageUsed;
    private Long warmStorageUsed;
    private Long coldStorageUsed;
    private Long archiveStorageUsed;

    // Audit
    private Instant createdAt;
    private Instant updatedAt;
    private String lastUpdatedBy;
}
