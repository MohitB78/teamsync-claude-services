package com.teamsync.audit.exception;

/**
 * Exception thrown when ImmuDB detects potential tampering.
 * This is a critical security event that should trigger alerts.
 */
public class TamperDetectedException extends RuntimeException {

    public TamperDetectedException(String message) {
        super(message);
    }

    public TamperDetectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
