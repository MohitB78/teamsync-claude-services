package com.teamsync.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Kafka event for document lifecycle events.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentEvent {

    private String eventId;
    private String eventType;  // CREATED, UPDATED, DELETED, UPLOADED, MOVED, COPIED, SHARED
    private String tenantId;
    private String driveId;
    private String documentId;
    private String folderId;
    private String userId;
    private String documentName;
    private String contentType;
    private Long fileSize;
    private String storageKey;
    private Map<String, Object> metadata;
    private Instant timestamp;

    // Event types as constants (for backward compatibility)
    public static final String CREATED = "CREATED";
    public static final String UPDATED = "UPDATED";
    public static final String DELETED = "DELETED";
    public static final String UPLOADED = "UPLOADED";
    public static final String MOVED = "MOVED";
    public static final String COPIED = "COPIED";
    public static final String SHARED = "SHARED";
    public static final String RESTORED = "RESTORED";
    public static final String PERMANENTLY_DELETED = "PERMANENTLY_DELETED";
    public static final String VERSION_CREATED = "VERSION_CREATED";
    public static final String METADATA_UPDATED = "METADATA_UPDATED";

    /**
     * Enum for type-safe event types.
     */
    public enum EventType {
        CREATED,
        UPDATED,
        DELETED,
        UPLOADED,
        MOVED,
        COPIED,
        SHARED,
        RESTORED,
        TRASHED,
        PERMANENTLY_DELETED,
        VERSION_CREATED,
        METADATA_UPDATED,
        DOWNLOADED,
        PREVIEWED
    }
}
