package com.teamsync.signing.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.Map;

/**
 * SignatureEvent provides an immutable audit trail for all signing actions.
 *
 * Every action in the signing process is logged with:
 * - Who performed the action (actor)
 * - What action was performed (eventType)
 * - Security context (IP, user agent)
 * - Timestamp
 *
 * This audit trail is critical for legal validity of electronic signatures.
 */
@Document(collection = "signature_events")
@CompoundIndexes({
        @CompoundIndex(name = "tenant_request_idx", def = "{'tenantId': 1, 'requestId': 1}"),
        @CompoundIndex(name = "tenant_time_idx", def = "{'tenantId': 1, 'timestamp': -1}"),
        @CompoundIndex(name = "request_time_idx", def = "{'requestId': 1, 'timestamp': 1}"),
        @CompoundIndex(name = "actor_idx", def = "{'actorEmail': 1, 'timestamp': -1}")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignatureEvent {

    @Id
    private String id;

    @Field("tenantId")
    private String tenantId;

    @Field("requestId")
    private String requestId;

    @Field("documentId")
    private String documentId;

    // Actor (who performed the action)
    @Field("actorId")
    private String actorId;  // User ID or signer ID

    @Field("actorEmail")
    private String actorEmail;

    @Field("actorName")
    private String actorName;

    @Field("actorType")
    private ActorType actorType;

    // Event details
    @Field("eventType")
    private SignatureEventType eventType;

    @Field("description")
    private String description;

    @Field("metadata")
    private Map<String, Object> metadata;  // Event-specific data

    // Security info
    @Field("ipAddress")
    private String ipAddress;

    @Field("userAgent")
    private String userAgent;

    @Field("sessionId")
    private String sessionId;

    // Timestamp
    @Field("timestamp")
    private Instant timestamp;

    /**
     * Actor type in the signing process.
     */
    public enum ActorType {
        SENDER,   // Internal user who sent the request
        SIGNER,   // External user signing
        SYSTEM    // Automated events (reminders, expiration)
    }

    /**
     * All possible event types in the signing process.
     */
    public enum SignatureEventType {
        // Request lifecycle
        REQUEST_CREATED,
        REQUEST_SENT,
        REQUEST_VOIDED,
        REQUEST_EXPIRED,
        REQUEST_COMPLETED,
        ALL_SIGNATURES_COLLECTED,

        // Signer actions
        DOCUMENT_VIEWED,
        SIGNATURE_APPLIED,
        FIELD_FILLED,
        DOCUMENT_SIGNED,
        SIGNATURE_DECLINED,
        SIGNER_DECLINED,

        // Notifications
        REMINDER_SENT,
        NOTIFICATION_DELIVERED,
        NOTIFICATION_FAILED,

        // Access
        ACCESS_TOKEN_GENERATED,
        ACCESS_TOKEN_USED,
        ACCESS_TOKEN_EXPIRED,
        INVALID_ACCESS_ATTEMPT,

        // Document
        DOCUMENT_DOWNLOADED,
        SIGNED_DOCUMENT_GENERATED
    }

    /**
     * Builder helper to create an event with current timestamp.
     */
    public static SignatureEventBuilder createEvent(String tenantId, String requestId) {
        return SignatureEvent.builder()
                .tenantId(tenantId)
                .requestId(requestId)
                .timestamp(Instant.now());
    }
}
