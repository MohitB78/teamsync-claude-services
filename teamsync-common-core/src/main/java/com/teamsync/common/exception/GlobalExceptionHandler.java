package com.teamsync.common.exception;

import com.teamsync.common.dto.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.stream.Collectors;

/**
 * Core exception handler with common exceptions.
 * Services that use Spring Data should add their own handler for OptimisticLockingFailureException.
 *
 * <p><strong>SECURITY FIX (Round 8):</strong> Exception handlers now return generic error messages
 * to clients instead of raw exception messages. This prevents information disclosure via:
 * <ul>
 *   <li>Internal path/field name exposure</li>
 *   <li>Database schema leakage</li>
 *   <li>Business logic enumeration</li>
 * </ul>
 *
 * <p>Detailed error information is logged server-side for debugging but never exposed to clients.
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        // SECURITY FIX (Round 8): Return generic message, don't expose internal resource names
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("The requested resource was not found", "RESOURCE_NOT_FOUND"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("You don't have permission to access this resource", "ACCESS_DENIED"));
    }

    /**
     * SECURITY FIX (Round 14 #H46): No longer exposes validation error details.
     * Previously, validation messages (which could include field names and constraints)
     * were returned to clients. Now we return only a generic message.
     * Full details are logged server-side for debugging.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationErrors(MethodArgumentNotValidException ex) {
        // Log full details server-side
        String internalErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("Validation error: {}", internalErrors);

        // Return generic message to clients
        int errorCount = ex.getBindingResult().getFieldErrors().size();
        String message = errorCount == 1
                ? "1 validation error"
                : errorCount + " validation errors";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(message, "VALIDATION_ERROR"));
    }

    /**
     * SECURITY FIX (Round 14 #H47): No longer exposes constraint violation paths.
     * Previously, validation messages were returned to clients which could reveal
     * internal field naming. Now we return only a generic message.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        // SECURITY FIX: Log full property paths internally only
        String internalErrors = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining(", "));
        log.warn("Constraint violation: {}", internalErrors);

        // Return generic message to clients - don't expose field names or constraint types
        int errorCount = ex.getConstraintViolations().size();
        String message = errorCount == 1
                ? "1 validation error"
                : errorCount + " validation errors";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(message, "VALIDATION_ERROR"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        // SECURITY FIX (Round 8): Don't expose internal argument validation details
        // IllegalArgumentException messages often contain parameter names and values
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Invalid request parameters", "BAD_REQUEST"));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException ex) {
        log.warn("Illegal state: {}", ex.getMessage());
        // SECURITY FIX (Round 8): Don't expose internal state details
        // IllegalStateException messages often reveal business logic or state machine details
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("The requested operation cannot be performed in the current state", "CONFLICT"));
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidToken(InvalidTokenException ex) {
        log.warn("Invalid token: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Invalid or expired token", "INVALID_TOKEN"));
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleServiceUnavailable(ServiceUnavailableException ex) {
        log.error("Service unavailable: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error("Service temporarily unavailable. Please try again later.", "SERVICE_UNAVAILABLE"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred", "INTERNAL_ERROR"));
    }
}
