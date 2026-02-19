package com.teamsync.audit.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * Request for searching audit logs.
 */
@Data
public class AuditSearchRequest {

    /**
     * Filter by user ID (exact match or partial).
     */
    private String userId;

    /**
     * Filter by user name (partial match).
     */
    private String userName;

    /**
     * Filter by action type(s).
     */
    private List<String> actions;

    /**
     * Filter by resource type(s).
     */
    private List<String> resourceTypes;

    /**
     * Filter by specific resource ID.
     */
    private String resourceId;

    /**
     * Filter by outcome(s).
     */
    private List<String> outcomes;

    /**
     * Start time for date range filter.
     */
    private Instant startTime;

    /**
     * End time for date range filter.
     */
    private Instant endTime;

    /**
     * Full-text search query.
     */
    private String query;

    /**
     * Only include PII-accessed events.
     */
    private Boolean piiAccessedOnly;

    /**
     * Page number (0-based).
     */
    @Min(0)
    private int page = 0;

    /**
     * Page size.
     */
    @Min(1)
    @Max(100)
    private int size = 20;

    /**
     * Sort field.
     */
    private String sortBy = "eventTime";

    /**
     * Sort direction (asc/desc).
     */
    private String sortDirection = "desc";
}
