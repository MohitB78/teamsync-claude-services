package com.teamsync.audit.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * MongoDB document for audit logs (fast query mirror).
 *
 * This is a mirror of the ImmuDB audit_events table,
 * optimized for fast queries and complex filtering.
 * The source of truth is ImmuDB - this is for read performance.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "audit_logs")
@CompoundIndexes({
    @CompoundIndex(name = "tenant_idx", def = "{'tenantId': 1}"),
    @CompoundIndex(name = "tenant_user_idx", def = "{'tenantId': 1, 'userId': 1}"),
    @CompoundIndex(name = "tenant_resource_idx", def = "{'tenantId': 1, 'resourceType': 1, 'resourceId': 1}"),
    @CompoundIndex(name = "tenant_action_idx", def = "{'tenantId': 1, 'action': 1}"),
    @CompoundIndex(name = "tenant_time_idx", def = "{'tenantId': 1, 'eventTime': -1}"),
    @CompoundIndex(name = "created_idx", def = "{'eventTime': -1}")
})
public class AuditLog {

    @Id
    private String id;

    private String tenantId;
    private String driveId;

    // Who performed the action
    private String userId;
    private String userName;

    // What action was performed
    private String action;
    private String resourceType;
    private String resourceId;
    private String resourceName;

    // State changes
    private Map<String, Object> before;
    private Map<String, Object> after;

    // Request context
    private String ipAddress;
    private String userAgent;
    private String sessionId;
    private String requestId;

    // Outcome
    private String outcome;
    private String failureReason;

    // PII/Compliance tracking
    private boolean piiAccessed;
    private boolean sensitiveDataAccessed;
    private String dataClassification;

    // Timestamp
    private Instant eventTime;

    // ImmuDB verification
    private Long immudbTransactionId;
    private String immudbHashChain;
    private String hashChain;  // Keep for backward compatibility
    private boolean immudbVerified;
    private boolean verified;  // Keep for backward compatibility
    private Instant verifiedAt;

    // Signature-specific fields (for signature audit events)
    private String signatureRequestId;
    private String signatureEventType;
    private String signatureActorType;
    private String signatureActorEmail;

    // Generic metadata for flexible event data
    private Map<String, Object> metadata;

    // Alias for eventId
    private String eventId;

    // Alias for eventTime
    private Instant timestamp;
}
