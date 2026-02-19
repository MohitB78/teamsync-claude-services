package com.teamsync.notification.exception;

/**
 * Exception thrown when notification data is invalid.
 */
public class InvalidNotificationException extends RuntimeException {

    private final String field;
    private final String reason;

    public InvalidNotificationException(String message) {
        super(message);
        this.field = null;
        this.reason = message;
    }

    public InvalidNotificationException(String field, String reason) {
        super(String.format("Invalid notification field '%s': %s", field, reason));
        this.field = field;
        this.reason = reason;
    }

    public String getField() {
        return field;
    }

    public String getReason() {
        return reason;
    }
}
