package com.teamsync.notification.exception;

/**
 * Exception thrown when notification delivery fails.
 */
public class NotificationDeliveryException extends RuntimeException {

    private final String notificationId;
    private final String channel;
    private final String errorCode;

    public NotificationDeliveryException(String message) {
        super(message);
        this.notificationId = null;
        this.channel = null;
        this.errorCode = null;
    }

    public NotificationDeliveryException(String notificationId, String channel, String message) {
        super(String.format("Failed to deliver notification %s via %s: %s",
                notificationId, channel, message));
        this.notificationId = notificationId;
        this.channel = channel;
        this.errorCode = null;
    }

    public NotificationDeliveryException(String notificationId, String channel, String message, Throwable cause) {
        super(String.format("Failed to deliver notification %s via %s: %s",
                notificationId, channel, message), cause);
        this.notificationId = notificationId;
        this.channel = channel;
        this.errorCode = null;
    }

    public NotificationDeliveryException(String notificationId, String channel, String errorCode, String message) {
        super(String.format("Failed to deliver notification %s via %s [%s]: %s",
                notificationId, channel, errorCode, message));
        this.notificationId = notificationId;
        this.channel = channel;
        this.errorCode = errorCode;
    }

    public String getNotificationId() {
        return notificationId;
    }

    public String getChannel() {
        return channel;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
