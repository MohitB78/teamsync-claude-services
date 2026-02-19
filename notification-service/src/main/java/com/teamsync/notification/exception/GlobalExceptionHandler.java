package com.teamsync.notification.exception;

import com.teamsync.common.dto.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception handler for the Notification Service.
 * Uses explicit bean name to avoid conflict with teamsync-common-core's GlobalExceptionHandler.
 *
 * SECURITY FIX (Round 12): Replaced raw exception messages with generic messages.
 * Raw exception messages can leak internal details such as:
 * - Database field names and structure
 * - Internal paths and configuration
 * - Business logic implementation details
 *
 * All exception details are logged server-side for debugging while returning
 * safe, generic messages to API clients.
 */
@RestControllerAdvice("notificationExceptionHandler")
@Slf4j
public class GlobalExceptionHandler {

    // ==================== Custom Exceptions ====================

    @ExceptionHandler(NotificationNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotificationNotFound(NotificationNotFoundException ex) {
        // SECURITY FIX (Round 12): Log details server-side, return generic message
        log.warn("Notification not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.<Void>builder()
                        .success(false)
                        .error("The requested notification was not found")
                        .code("NOTIFICATION_NOT_FOUND")
                        .build());
    }

    @ExceptionHandler(PreferenceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handlePreferenceNotFound(PreferenceNotFoundException ex) {
        // SECURITY FIX (Round 12): Log details server-side, return generic message
        log.warn("Preferences not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.<Void>builder()
                        .success(false)
                        .error("Notification preferences not found")
                        .code("PREFERENCE_NOT_FOUND")
                        .build());
    }

    @ExceptionHandler(InvalidNotificationException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidNotification(InvalidNotificationException ex) {
        // SECURITY FIX (Round 12): Log details server-side, return generic message
        log.warn("Invalid notification: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Void>builder()
                        .success(false)
                        .error("Invalid notification data provided")
                        .code("INVALID_NOTIFICATION")
                        .build());
    }

    @ExceptionHandler(NotificationDeliveryException.class)
    public ResponseEntity<ApiResponse<Void>> handleDeliveryFailure(NotificationDeliveryException ex) {
        log.error("Notification delivery failed: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.<Void>builder()
                        .success(false)
                        .error("Failed to deliver notification. Please try again later.")
                        .code("DELIVERY_FAILED")
                        .build());
    }

    @ExceptionHandler(UnauthorizedAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorizedAccess(UnauthorizedAccessException ex) {
        log.warn("Unauthorized access attempt: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.<Void>builder()
                        .success(false)
                        .error("You are not authorized to access this resource")
                        .code("UNAUTHORIZED_ACCESS")
                        .build());
    }

    // ==================== Security Exceptions ====================

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(AuthenticationException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.<Void>builder()
                        .success(false)
                        .error("Authentication required")
                        .code("AUTHENTICATION_REQUIRED")
                        .build());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.<Void>builder()
                        .success(false)
                        .error("Access denied")
                        .code("ACCESS_DENIED")
                        .build());
    }

    // ==================== Validation Exceptions ====================

    /**
     * SECURITY FIX (Round 14 #H46): Field names are no longer exposed in validation errors.
     * Previously, field names were returned in the response data, which could reveal
     * internal schema structure to attackers for reconnaissance.
     * Now, we only return generic validation messages to clients while logging
     * full details server-side for debugging.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException ex) {
        // Log full details server-side for debugging
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid value",
                        (first, second) -> first
                ));
        log.warn("Validation failed for fields: {}", errors);

        // Return generic message without field names to clients
        int errorCount = errors.size();
        String message = errorCount == 1
                ? "1 validation error"
                : errorCount + " validation errors";

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Void>builder()
                        .success(false)
                        .error(message)
                        .code("VALIDATION_ERROR")
                        .build());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException ex) {
        // SECURITY FIX (Round 12): Log details server-side, don't expose field names to client
        // Field names can reveal internal schema structure for reconnaissance
        Map<String, String> errors = new HashMap<>();
        ex.getConstraintViolations().forEach(violation -> {
            String field = violation.getPropertyPath().toString();
            String message = violation.getMessage();
            errors.put(field, message);
        });

        log.warn("Constraint violation: {}", errors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Void>builder()
                        .success(false)
                        .error("One or more fields failed validation")
                        .code("CONSTRAINT_VIOLATION")
                        .build());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeader(MissingRequestHeaderException ex) {
        // SECURITY FIX (Round 12): Don't expose header names to clients
        log.warn("Missing required header: {}", ex.getHeaderName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Void>builder()
                        .success(false)
                        .error("A required header is missing")
                        .code("MISSING_HEADER")
                        .build());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(MissingServletRequestParameterException ex) {
        // SECURITY FIX (Round 12): Don't expose parameter names to clients
        log.warn("Missing required parameter: {}", ex.getParameterName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Void>builder()
                        .success(false)
                        .error("A required parameter is missing")
                        .code("MISSING_PARAMETER")
                        .build());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        // SECURITY FIX (Round 12): Don't expose parameter names or values to clients
        String message = String.format("Invalid value '%s' for parameter '%s'",
                ex.getValue(), ex.getName());
        log.warn("Type mismatch: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Void>builder()
                        .success(false)
                        .error("Invalid parameter value provided")
                        .code("TYPE_MISMATCH")
                        .build());
    }

    // ==================== HTTP Exceptions ====================

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableMessage(HttpMessageNotReadableException ex) {
        log.warn("Malformed request body: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Void>builder()
                        .success(false)
                        .error("Malformed request body")
                        .code("INVALID_REQUEST_BODY")
                        .build());
    }

    /**
     * SECURITY FIX (Round 14 #C24): Removed content type from error response.
     * Previously exposed the actual unsupported media type which could help
     * attackers fingerprint the API and understand accepted content types.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        log.warn("Unsupported media type: {}", ex.getContentType());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.<Void>builder()
                        .success(false)
                        .error("Unsupported media type")
                        .code("UNSUPPORTED_MEDIA_TYPE")
                        .build());
    }

    /**
     * SECURITY FIX (Round 14 #C25): Removed HTTP method from error response.
     * Previously exposed the actual method which could help attackers
     * enumerate which methods are supported for each endpoint.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("Method not allowed: {}", ex.getMethod());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.<Void>builder()
                        .success(false)
                        .error("Method not allowed")
                        .code("METHOD_NOT_ALLOWED")
                        .build());
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoHandlerFound(NoHandlerFoundException ex) {
        log.warn("Endpoint not found: {} {}", ex.getHttpMethod(), ex.getRequestURL());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.<Void>builder()
                        .success(false)
                        .error("Endpoint not found")
                        .code("ENDPOINT_NOT_FOUND")
                        .build());
    }

    // ==================== Catch-All ====================

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        // SECURITY FIX (Round 12): Don't expose internal error details to clients
        log.warn("Illegal argument: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Void>builder()
                        .success(false)
                        .error("Invalid request data provided")
                        .code("INVALID_ARGUMENT")
                        .build());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException ex) {
        // SECURITY FIX (Round 12): Don't expose internal state details to clients
        log.error("Illegal state: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.<Void>builder()
                        .success(false)
                        .error("Operation cannot be completed in current state")
                        .code("INVALID_STATE")
                        .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unexpected error occurred", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.<Void>builder()
                        .success(false)
                        .error("An unexpected error occurred. Please try again later.")
                        .code("INTERNAL_ERROR")
                        .build());
    }
}
