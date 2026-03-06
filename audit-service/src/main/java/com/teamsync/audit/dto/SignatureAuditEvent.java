package com.teamsync.audit.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

/**
 * Kafka event for signature audit logging.
 * Published to teamsync.signature.events topic for ImmuDB storage.
 * Maps to signature_audit_events table in ImmuDB.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SignatureAuditEvent {

    // Core fields (matching signature_audit_events table)
    String id;              // Maps to 'id' column
    String tenantId;        // Maps to 'tenant_id' column
    String requestId;       // Maps to 'request_id' column
    String eventId;
    String documentId;      // Maps to 'document_id' column

    // Actor information
    String actorId;         // Maps to 'actor_id' column
    String actorEmail;      // Maps to 'actor_email' column
    String actorName;       // Maps to 'actor_name' column
    String actorType;       // Maps to 'actor_type' column - see SignatureAuditConstants for values

    // Event details
    String eventType;       // Maps to 'event_type' column - see SignatureAuditConstants for values
    String description;     // Maps to 'description' column
    String metadata;        // Maps to 'metadata' column (VARCHAR[8192])

    // Security context
    String ipAddress;       // Maps to 'ip_address' column
    String userAgent;       // Maps to 'user_agent' column
    String sessionId;       // Maps to 'session_id' column

    // Timestamp and verification
    Instant eventTime;      // Maps to 'event_time' column
    String hashChain;       // Maps to 'hash_chain' column
}
