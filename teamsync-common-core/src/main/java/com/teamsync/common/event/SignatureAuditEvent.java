package com.teamsync.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Kafka event for signature audit logging.
 * Published to teamsync.signature.events topic for ImmuDB storage.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignatureAuditEvent {

    private String eventId;
    private String tenantId;
    private String requestId;
    private String documentId;

    // Actor information
    private String actorId;
    private String actorEmail;
    private String actorName;
    private String actorType;  // SENDER, SIGNER, SYSTEM

    // Event details
    private String eventType;  // REQUEST_CREATED, DOCUMENT_SIGNED, etc.
    private String description;
    private Map<String, Object> metadata;

    // Security context
    private String ipAddress;
    private String userAgent;
    private String sessionId;

    // Timestamp
    private Instant timestamp;

    // Event type constants (matching SignatureEvent.SignatureEventType)
    public static final String EVENT_REQUEST_CREATED = "REQUEST_CREATED";
    public static final String EVENT_REQUEST_SENT = "REQUEST_SENT";
    public static final String EVENT_REQUEST_VOIDED = "REQUEST_VOIDED";
    public static final String EVENT_REQUEST_EXPIRED = "REQUEST_EXPIRED";
    public static final String EVENT_REQUEST_COMPLETED = "REQUEST_COMPLETED";
    public static final String EVENT_ALL_SIGNATURES_COLLECTED = "ALL_SIGNATURES_COLLECTED";
    public static final String EVENT_DOCUMENT_VIEWED = "DOCUMENT_VIEWED";
    public static final String EVENT_SIGNATURE_APPLIED = "SIGNATURE_APPLIED";
    public static final String EVENT_FIELD_FILLED = "FIELD_FILLED";
    public static final String EVENT_DOCUMENT_SIGNED = "DOCUMENT_SIGNED";
    public static final String EVENT_SIGNATURE_DECLINED = "SIGNATURE_DECLINED";
    public static final String EVENT_SIGNER_DECLINED = "SIGNER_DECLINED";
    public static final String EVENT_REMINDER_SENT = "REMINDER_SENT";
    public static final String EVENT_NOTIFICATION_DELIVERED = "NOTIFICATION_DELIVERED";
    public static final String EVENT_NOTIFICATION_FAILED = "NOTIFICATION_FAILED";
    public static final String EVENT_ACCESS_TOKEN_GENERATED = "ACCESS_TOKEN_GENERATED";
    public static final String EVENT_ACCESS_TOKEN_USED = "ACCESS_TOKEN_USED";
    public static final String EVENT_ACCESS_TOKEN_EXPIRED = "ACCESS_TOKEN_EXPIRED";
    public static final String EVENT_INVALID_ACCESS_ATTEMPT = "INVALID_ACCESS_ATTEMPT";
    public static final String EVENT_DOCUMENT_DOWNLOADED = "DOCUMENT_DOWNLOADED";
    public static final String EVENT_SIGNED_DOCUMENT_GENERATED = "SIGNED_DOCUMENT_GENERATED";

    // Actor type constants
    public static final String ACTOR_SENDER = "SENDER";
    public static final String ACTOR_SIGNER = "SIGNER";
    public static final String ACTOR_SYSTEM = "SYSTEM";
}
