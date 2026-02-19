package com.teamsync.common.config;

/**
 * Constants for Kafka topic names.
 */
public final class KafkaTopics {

    private KafkaTopics() {}

    // Document events
    public static final String DOCUMENTS_UPLOADED = "teamsync.documents.uploaded";
    public static final String DOCUMENTS_CREATED = "teamsync.documents.created";
    public static final String DOCUMENTS_UPDATED = "teamsync.documents.updated";
    public static final String DOCUMENTS_DELETED = "teamsync.documents.deleted";
    public static final String DOCUMENTS_METADATA_UPDATED = "teamsync.documents.metadata.updated";
    public static final String DOCUMENTS_TEXT_EXTRACTED = "teamsync.documents.text.extracted";

    // Folder events
    public static final String FOLDERS_CREATED = "teamsync.folders.created";
    public static final String FOLDERS_UPDATED = "teamsync.folders.updated";
    public static final String FOLDERS_DELETED = "teamsync.folders.deleted";

    // Drive events
    public static final String DRIVES_CREATED = "teamsync.drives.created";
    public static final String DRIVES_UPDATED = "teamsync.drives.updated";

    // Sharing events
    public static final String SHARING_CREATED = "teamsync.sharing.created";
    public static final String SHARING_REVOKED = "teamsync.sharing.revoked";

    // Notification events
    public static final String NOTIFICATIONS_SEND = "teamsync.notifications.send";

    // Audit events
    public static final String AUDIT_EVENTS = "teamsync.audit.events";
    public static final String SIGNATURE_AUDIT_EVENTS = "teamsync.signature.events";

    // Storage events (for quota sync when Kafka is re-enabled)
    public static final String STORAGE_FILE_UPLOADED = "teamsync.storage.file.uploaded";
    public static final String STORAGE_FILE_DELETED = "teamsync.storage.file.deleted";
    public static final String STORAGE_QUOTA_UPDATED = "teamsync.storage.quota.updated";

    // Workflow events
    public static final String WORKFLOW_STEP_EXECUTE = "teamsync.workflow.step.execute";
    public static final String WORKFLOW_COMPLETED = "teamsync.workflow.completed";

    // Settings events
    public static final String SETTINGS_USER_UPDATED = "teamsync.settings.user.updated";
    public static final String SETTINGS_TENANT_UPDATED = "teamsync.settings.tenant.updated";
    public static final String SETTINGS_DRIVE_UPDATED = "teamsync.settings.drive.updated";

    // AccessArc integration events
    public static final String ACCESSARC_DEPARTMENTS_CREATED = "accessarc.departments.created";
    public static final String ACCESSARC_DEPARTMENTS_DELETED = "accessarc.departments.deleted";
    public static final String ACCESSARC_USERS_DEPARTMENT_ASSIGNED = "accessarc.users.department_assigned";
    public static final String ACCESSARC_USERS_DEPARTMENT_REMOVED = "accessarc.users.department_removed";
}
