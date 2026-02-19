package com.teamsync.notification.exception;

/**
 * Exception thrown when a notification is not found.
 */
public class NotificationNotFoundException extends RuntimeException {

    private final String notificationId;
    private final String tenantId;
    private final String userId;

    public NotificationNotFoundException(String notificationId) {
        super("Notification not found: " + notificationId);
        this.notificationId = notificationId;
        this.tenantId = null;
        this.userId = null;
    }

    public NotificationNotFoundException(String notificationId, String tenantId, String userId) {
        super(String.format("Notification not found: %s for tenant: %s, user: %s",
                notificationId, tenantId, userId));
        this.notificationId = notificationId;
        this.tenantId = tenantId;
        this.userId = userId;
    }

    public String getNotificationId() {
        return notificationId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getUserId() {
        return userId;
    }
}
