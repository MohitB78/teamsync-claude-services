package com.teamsync.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a service or external dependency is temporarily unavailable.
 * Returns HTTP 503 Service Unavailable.
 */
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class ServiceUnavailableException extends RuntimeException {

    private final String serviceName;

    public ServiceUnavailableException(String message) {
        super(message);
        this.serviceName = null;
    }

    public ServiceUnavailableException(String serviceName, String message) {
        super(String.format("%s: %s", serviceName, message));
        this.serviceName = serviceName;
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
        this.serviceName = null;
    }

    public String getServiceName() {
        return serviceName;
    }
}
