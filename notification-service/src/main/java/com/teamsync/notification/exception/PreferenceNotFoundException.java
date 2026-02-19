package com.teamsync.notification.exception;

/**
 * Exception thrown when notification preferences are not found.
 */
public class PreferenceNotFoundException extends RuntimeException {

    private final String tenantId;
    private final String userId;

    public PreferenceNotFoundException(String tenantId, String userId) {
        super(String.format("Notification preferences not found for tenant: %s, user: %s",
                tenantId, userId));
        this.tenantId = tenantId;
        this.userId = userId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getUserId() {
        return userId;
    }
}
