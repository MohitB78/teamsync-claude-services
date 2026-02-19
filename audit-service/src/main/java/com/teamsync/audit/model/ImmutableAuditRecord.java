package com.teamsync.audit.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Represents the result of writing an audit event to ImmuDB.
 * Contains verification proof information.
 */
@Data
@Builder
public class ImmutableAuditRecord {

    /**
     * The event ID (matches the original AuditEvent.eventId).
     */
    private String eventId;

    /**
     * ImmuDB transaction ID for this write operation.
     * Used for verification and proof generation.
     */
    private long immudbTransactionId;

    /**
     * SHA-256 hash chain linking this event to previous events.
     * Format: SHA256(previousHashChain + eventData)
     */
    private String hashChain;

    /**
     * The hash chain of the previous event (for verification).
     */
    private String previousHashChain;

    /**
     * Timestamp when the event was verified and written.
     */
    private Instant verifiedAt;

    /**
     * Whether the write was verified by ImmuDB's cryptographic proof.
     */
    private boolean verified;

    /**
     * Error message if verification failed.
     */
    private String errorMessage;

    /**
     * The raw event data that was hashed (for verification).
     */
    private String eventData;
}
