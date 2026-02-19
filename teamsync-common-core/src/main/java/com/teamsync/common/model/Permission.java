package com.teamsync.common.model;

public enum Permission {
    READ,           // View documents and folders
    WRITE,          // Create/edit documents
    DELETE,         // Delete documents (to trash)
    SHARE,          // Share documents with other users/departments
    MANAGE_USERS,   // Add/remove users from roles in this drive
    MANAGE_ROLES    // Edit role permissions, create custom roles
}
