package com.teamsync.audit.constants;

/**
 * Constants for audit events.
 */
public final class AuditConstants {

    private AuditConstants() {
        // Prevent instantiation
    }

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

    // Result values
    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_FAILURE = "FAILURE";
    public static final String RESULT_DENIED = "DENIED";
}
