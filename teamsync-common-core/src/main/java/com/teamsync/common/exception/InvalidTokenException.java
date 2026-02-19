package com.teamsync.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a token validation fails.
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class InvalidTokenException extends RuntimeException {

    private final String tokenType;

    public InvalidTokenException(String message) {
        super(message);
        this.tokenType = null;
    }

    public InvalidTokenException(String message, String tokenType) {
        super(message);
        this.tokenType = tokenType;
    }

    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
        this.tokenType = null;
    }

    public String getTokenType() {
        return tokenType;
    }
}
