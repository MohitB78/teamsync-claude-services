package com.teamsync.settings.exception;

import com.teamsync.common.dto.ApiResponse;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Exception handler for settings-specific exceptions.
 *
 * <p><strong>SECURITY FIX (Round 8):</strong> Exception handlers now return generic error messages
 * instead of raw exception messages to prevent information disclosure about:
 * <ul>
 *   <li>Internal setting key names</li>
 *   <li>Validation rules and constraints</li>
 *   <li>Settings schema structure</li>
 * </ul>
 */
@ControllerAdvice
@Slf4j
public class SettingsExceptionHandler {

    @ExceptionHandler(SettingsNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleSettingsNotFound(SettingsNotFoundException ex) {
        log.warn("Settings not found: {}", ex.getMessage());
        // SECURITY FIX (Round 8): Don't expose which settings were requested
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("The requested settings were not found", "SETTINGS_NOT_FOUND"));
    }

    /**
     * SECURITY FIX (Round 8): Handle settings validation errors without exposing internal details.
     * Field names and detailed error messages are logged server-side only.
     * Clients receive a count of errors without revealing the settings schema.
     */
    @ExceptionHandler(InvalidSettingsException.class)
    public ResponseEntity<ApiResponse<Object>> handleInvalidSettings(InvalidSettingsException ex) {
        // Log full details internally for debugging
        log.warn("SECURITY: Invalid settings: {}", ex.getMessage());
        if (ex.hasMultipleErrors()) {
            ex.getErrors().forEach(e ->
                    log.debug("Settings validation error - field: {}, message: {}", e.field(), e.message()));
        }

        // SECURITY FIX (Round 8): Don't expose field names or detailed validation messages
        // This prevents attackers from enumerating valid settings keys
        if (ex.hasMultipleErrors()) {
            var errorDetails = java.util.Map.of(
                    "message", "Some settings could not be applied due to validation errors",
                    "appliedCount", ex.getAppliedCount(),
                    "errorCount", ex.getErrors().size()
                    // NOTE: Removed 'errors' array that exposed field names
            );
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.<Object>builder()
                            .success(false)
                            .error("Some settings could not be applied due to validation errors")
                            .code("INVALID_SETTINGS")
                            .data(errorDetails)
                            .build());
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Object>builder()
                        .success(false)
                        .error("Invalid settings value provided")
                        .code("INVALID_SETTINGS")
                        .build());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("You don't have permission to modify these settings", "ACCESS_DENIED"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Invalid argument: {}", ex.getMessage());
        // SECURITY FIX (Round 8): Don't expose internal argument details
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Invalid request parameters", "INVALID_ARGUMENT"));
    }

    @ExceptionHandler(RequestNotPermitted.class)
    public ResponseEntity<ApiResponse<Void>> handleRateLimitExceeded(RequestNotPermitted ex) {
        log.warn("Rate limit exceeded: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse.error("Rate limit exceeded. Please try again later.", "RATE_LIMIT_EXCEEDED"));
    }

    /**
     * SECURITY FIX (Round 14 #H23): Field names are no longer exposed in validation errors.
     * Previously, field names and their validation messages were returned directly to clients,
     * which could reveal internal field naming and validation rules.
     * Now, we only return a count of errors while logging full details server-side.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        // Log full details server-side for debugging
        String internalDetails = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(java.util.stream.Collectors.joining(", "));
        log.warn("Validation error: {}", internalDetails);

        // Return generic message without field names to clients
        int errorCount = ex.getBindingResult().getFieldErrors().size();
        String message = errorCount == 1
                ? "1 validation error"
                : errorCount + " validation errors";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(message, "VALIDATION_ERROR"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unexpected error in settings service", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred", "INTERNAL_ERROR"));
    }
}
