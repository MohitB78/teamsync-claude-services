package com.teamsync.notification.exception;

/**
 * Exception thrown when user attempts to access notifications they don't own.
 */
public class UnauthorizedAccessException extends RuntimeException {

    private final String userId;
    private final String resourceId;
    private final String resourceType;

    public UnauthorizedAccessException(String message) {
        super(message);
        this.userId = null;
        this.resourceId = null;
        this.resourceType = null;
    }

    public UnauthorizedAccessException(String userId, String resourceType, String resourceId) {
        super(String.format("User %s is not authorized to access %s: %s",
                userId, resourceType, resourceId));
        this.userId = userId;
        this.resourceId = resourceId;
        this.resourceType = resourceType;
    }

    public String getUserId() {
        return userId;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getResourceType() {
        return resourceType;
    }
}
