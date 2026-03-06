package com.teamsync.audit.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.Map;

/**
 * Kafka event for audit logging.
 * Maps to audit_events table in ImmuDB.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuditEvent {

    // Core audit fields (matching audit_events table)
     String id;                  // Maps to 'id' column
     String tenantId;            // Maps to 'tenant_id' column
     String userId;              // Maps to 'user_id' column
     String eventId;           // Maps to 'EventId' column
     String userName;            // Maps to 'user_name' column
     String action;              // Maps to 'action' column - see AuditConstants for values
     String resourceType;        // Maps to 'resource_type' column - see AuditConstants for values
     String resourceId;          // Maps to 'resource_id' column
     String eventType;           // Maps to 'eventType' column
     String result;              // Maps to 'result' column - see AuditConstants for values
     Map<String, Object> details;    // Maps to 'details' JSON column - contains before/after states, failureReason, etc.
     Map<String, Object> context;    // Maps to 'context' JSON column - contains ipAddress, userAgent, requestId, PII flags, etc.
     Instant eventTime;          // Maps to 'event_time' column
     String hashChain;           // Maps to 'hash_chain' column
}
