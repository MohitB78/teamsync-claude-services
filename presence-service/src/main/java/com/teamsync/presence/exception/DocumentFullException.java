package com.teamsync.presence.exception;

public class DocumentFullException extends RuntimeException {

    public DocumentFullException(String message) {
        super(message);
    }

    public DocumentFullException(String message, Throwable cause) {
        super(message, cause);
    }
}
