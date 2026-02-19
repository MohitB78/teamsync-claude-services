package com.teamsync.common.model;

/**
 * Types of drives in TeamSync.
 * Each drive type has different ownership and access control semantics.
 */
public enum DriveType {
    /** User's personal drive (full access to owner) */
    PERSONAL,

    /** Department shared drive (RBAC controlled via department membership) */
    DEPARTMENT,

    /** Team shared drive (RBAC controlled via team membership and roles) */
    TEAM
}
