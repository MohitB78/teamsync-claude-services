package com.teamsync.audit.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * Result of verifying the audit trail for a resource (document, folder, etc.).
 */
@Data
@Builder
public class ResourceAuditVerification {

    private String resourceType;
    private String resourceId;
    private int totalEvents;
    private int verifiedEvents;
    private boolean allEventsVerified;
    private boolean hashChainIntact;
    private Instant oldestEvent;
    private Instant newestEvent;
    private List<String> failedEventIds;
    private Instant verifiedAt;
}
