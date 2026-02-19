package com.teamsync.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Event published for storage operations.
 * Used for asynchronous quota updates and audit logging.
 *
 * NOTE: Kafka is currently disabled due to Spring Boot 4.0.0 + Kafka 4.x
 * bytecode incompatibility. This event structure is prepared for when
 * Kafka is re-enabled with Spring Kafka 4.0.1+.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageEvent {

    private String eventId;
    private StorageEventType eventType;
    private String tenantId;
    private String driveId;
    private String userId;
    private String bucket;
    private String storageKey;
    private Long fileSize;
    private Instant timestamp;

    /**
     * Types of storage events.
     */
    public enum StorageEventType {
        FILE_UPLOADED,
        FILE_DELETED,
        FILE_COPIED,
        TIER_CHANGED,
        QUOTA_UPDATED
    }
}
