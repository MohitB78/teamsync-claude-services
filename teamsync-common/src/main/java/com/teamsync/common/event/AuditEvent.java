package com.teamsync.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Kafka event for audit logging.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEvent {

    private String eventId;
    private String tenantId;
    private String userId;
    private String userName;
    private String action;              // CREATE, READ, UPDATE, DELETE, SHARE, DOWNLOAD, LOGIN, LOGOUT
    private String resourceType;        // DOCUMENT, FOLDER, DRIVE, TEAM, PROJECT, USER, ROLE
    private String resourceId;
    private String resourceName;
    private String driveId;
    private Map<String, Object> before; // State before change
    private Map<String, Object> after;  // State after change
    private String ipAddress;
    private String userAgent;
    private String sessionId;
    private String requestId;
    private String outcome;             // SUCCESS, FAILURE, DENIED
    private String failureReason;
    private Instant timestamp;

    // PII tracking
    private boolean piiAccessed;
    private boolean sensitiveDataAccessed;
    private String dataClassification;

    // Action types
    public static final String ACTION_CREATE = "CREATE";
    public static final String ACTION_READ = "READ";
    public static final String ACTION_UPDATE = "UPDATE";
    public static final String ACTION_DELETE = "DELETE";
    public static final String ACTION_SHARE = "SHARE";
    public static final String ACTION_DOWNLOAD = "DOWNLOAD";
    public static final String ACTION_UPLOAD = "UPLOAD";
    public static final String ACTION_LOGIN = "LOGIN";
    public static final String ACTION_LOGOUT = "LOGOUT";
    public static final String ACTION_PERMISSION_CHANGE = "PERMISSION_CHANGE";

    // Resource types
    public static final String RESOURCE_DOCUMENT = "DOCUMENT";
    public static final String RESOURCE_FOLDER = "FOLDER";
    public static final String RESOURCE_DRIVE = "DRIVE";
    public static final String RESOURCE_TEAM = "TEAM";
    public static final String RESOURCE_PROJECT = "PROJECT";
    public static final String RESOURCE_USER = "USER";
    public static final String RESOURCE_ROLE = "ROLE";

    // Outcomes
    public static final String OUTCOME_SUCCESS = "SUCCESS";
    public static final String OUTCOME_FAILURE = "FAILURE";
    public static final String OUTCOME_DENIED = "DENIED";
}
