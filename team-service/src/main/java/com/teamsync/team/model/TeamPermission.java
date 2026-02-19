package com.teamsync.team.model;

/**
 * Granular permissions for team-level access control.
 * These permissions are assigned to roles and determine what actions
 * a team member can perform within a team.
 *
 * Content permissions map to Drive-level permissions for the team drive.
 * External users are restricted to: CONTENT_VIEW, CONTENT_UPLOAD, CONTENT_VERSION_CREATE,
 * TASK_VIEW, TASK_COMMENT only.
 */
public enum TeamPermission {

    // ========================================
    // Team Management
    // ========================================

    /** View team details, settings, and basic info */
    TEAM_VIEW,

    /** Edit team name, description, settings */
    TEAM_EDIT,

    /** Delete or archive the team */
    TEAM_DELETE,

    /** Transfer team ownership to another member */
    TEAM_TRANSFER_OWNERSHIP,

    // ========================================
    // Member Management
    // ========================================

    /** View member list and member details */
    MEMBER_VIEW,

    /** Invite internal (tenant) users to the team */
    MEMBER_INVITE,

    /** Invite external (non-tenant) users to the team */
    MEMBER_INVITE_EXTERNAL,

    /** Remove members from the team */
    MEMBER_REMOVE,

    /** Change member roles (assign different role) */
    MEMBER_ROLE_ASSIGN,

    // ========================================
    // Role Management
    // ========================================

    /** View roles and their permissions */
    ROLE_VIEW,

    /** Create new custom roles for this team */
    ROLE_CREATE,

    /** Edit custom role permissions */
    ROLE_EDIT,

    /** Delete custom roles (system roles cannot be deleted) */
    ROLE_DELETE,

    // ========================================
    // Content Permissions (Map to Drive Permissions)
    // ========================================

    /** View and download documents and folders (maps to Drive.READ) */
    CONTENT_VIEW,

    /** Upload new documents to the team drive */
    CONTENT_UPLOAD,

    /** Create a new version of an existing document (external users allowed) */
    CONTENT_VERSION_CREATE,

    /** Edit existing documents in-place (INTERNAL USERS ONLY) */
    CONTENT_EDIT,

    /** Delete documents (move to trash) */
    CONTENT_DELETE,

    /** Share documents with other users/teams */
    CONTENT_SHARE,

    /** Create folders in the team drive */
    CONTENT_CREATE_FOLDER,

    /** Move documents and folders within the team drive */
    CONTENT_MOVE,

    /** Manage document comments */
    CONTENT_COMMENT,

    // ========================================
    // Task Permissions
    // ========================================

    /** View tasks in the team */
    TASK_VIEW,

    /** Create new tasks */
    TASK_CREATE,

    /** Edit task details (title, description, priority, due date) */
    TASK_EDIT,

    /** Delete tasks */
    TASK_DELETE,

    /** Assign tasks to team members */
    TASK_ASSIGN,

    /** Update task status (allowed for assignee even without TASK_EDIT) */
    TASK_UPDATE_STATUS,

    /** Add comments to tasks */
    TASK_COMMENT,

    // ========================================
    // Activity & Audit
    // ========================================

    /** View team activity feed and audit logs */
    ACTIVITY_VIEW,

    // ========================================
    // Settings
    // ========================================

    /** Manage team settings (notifications, integrations) */
    SETTINGS_MANAGE;

    /**
     * Checks if this permission is allowed for external users.
     * External users have very restricted permissions.
     *
     * @return true if external users can have this permission
     */
    public boolean isAllowedForExternalUsers() {
        return switch (this) {
            case CONTENT_VIEW,
                 CONTENT_UPLOAD,
                 CONTENT_VERSION_CREATE,
                 TASK_VIEW,
                 TASK_COMMENT,
                 TEAM_VIEW,
                 MEMBER_VIEW,
                 ACTIVITY_VIEW -> true;
            default -> false;
        };
    }

    /**
     * Gets the corresponding Drive permission for content permissions.
     *
     * @return the Drive permission name, or null if not a content permission
     */
    public String toDrivePermission() {
        return switch (this) {
            case CONTENT_VIEW -> "READ";
            case CONTENT_UPLOAD, CONTENT_VERSION_CREATE, CONTENT_EDIT,
                 CONTENT_CREATE_FOLDER, CONTENT_MOVE, CONTENT_COMMENT -> "WRITE";
            case CONTENT_DELETE -> "DELETE";
            case CONTENT_SHARE -> "SHARE";
            default -> null;
        };
    }
}
