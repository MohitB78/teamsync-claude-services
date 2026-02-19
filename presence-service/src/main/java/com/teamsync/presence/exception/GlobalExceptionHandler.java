package com.teamsync.presence.exception;

import com.teamsync.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for the Presence Service.
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
@RestControllerAdvice("presenceExceptionHandler")
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(DocumentFullException.class)
    public ResponseEntity<ApiResponse<Void>> handleDocumentFull(DocumentFullException ex) {
        // SECURITY FIX (Round 12): Log details server-side, return generic message
        log.warn("Document full: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.<Void>builder()
                        .success(false)
                        .error("Document has reached maximum participant limit")
                        .code("DOCUMENT_FULL")
                        .build());
    }

    @ExceptionHandler(ParticipantNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleParticipantNotFound(ParticipantNotFoundException ex) {
        // SECURITY FIX (Round 12): Log details server-side, return generic message
        log.warn("Participant not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.<Void>builder()
                        .success(false)
                        .error("Participant not found in this document")
                        .code("PARTICIPANT_NOT_FOUND")
                        .build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {
        // SECURITY FIX (Round 12): Log details server-side, don't expose field names to client
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        log.warn("Validation failed: {}", errors);

        return ResponseEntity.badRequest()
                .body(ApiResponse.<Void>builder()
                        .success(false)
                        .error("One or more fields failed validation")
                        .code("VALIDATION_ERROR")
                        .build());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeader(MissingRequestHeaderException ex) {
        // SECURITY FIX (Round 12): Don't expose header names to clients
        log.warn("Missing header: {}", ex.getHeaderName());
        return ResponseEntity.badRequest()
                .body(ApiResponse.<Void>builder()
                        .success(false)
                        .error("A required header is missing")
                        .code("MISSING_HEADER")
                        .build());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        // SECURITY FIX (Round 12): Don't expose internal error details to clients
        log.warn("Illegal argument: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.<Void>builder()
                        .success(false)
                        .error("Invalid request data provided")
                        .code("INVALID_ARGUMENT")
                        .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unexpected error: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.<Void>builder()
                        .success(false)
                        .error("An unexpected error occurred")
                        .code("INTERNAL_ERROR")
                        .build());
    }
}
