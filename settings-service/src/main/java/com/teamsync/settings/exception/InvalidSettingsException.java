package com.teamsync.settings.exception;

import com.teamsync.settings.dto.SettingsValidationResult;

import java.util.List;

/**
 * SECURITY: Exception thrown when settings validation fails.
 * Supports both single field errors and multiple validation errors.
 */
public class InvalidSettingsException extends RuntimeException {

    private final String field;
    private final List<SettingsValidationResult.SettingsError> errors;
    private final int appliedCount;

    public InvalidSettingsException(String message) {
        super(message);
        this.field = null;
        this.errors = List.of();
        this.appliedCount = 0;
    }

    public InvalidSettingsException(String field, String message) {
        super(String.format("Invalid setting '%s': %s", field, message));
        this.field = field;
        this.errors = List.of(new SettingsValidationResult.SettingsError(field, message));
        this.appliedCount = 0;
    }

    /**
     * SECURITY: Constructor for partial update failures with collected errors.
     */
    public InvalidSettingsException(SettingsValidationResult result) {
        super(formatMessage(result));
        this.field = null;
        this.errors = result.getErrors();
        this.appliedCount = result.getAppliedCount();
    }

    private static String formatMessage(SettingsValidationResult result) {
        if (result.getErrorCount() == 1) {
            var error = result.getErrors().get(0);
            return String.format("Invalid setting '%s': %s", error.field(), error.message());
        }
        return String.format("%d settings failed validation (applied %d successfully)",
                result.getErrorCount(), result.getAppliedCount());
    }

    public String getField() {
        return field;
    }

    public List<SettingsValidationResult.SettingsError> getErrors() {
        return errors;
    }

    public int getAppliedCount() {
        return appliedCount;
    }

    public boolean hasMultipleErrors() {
        return errors.size() > 1;
    }
}
