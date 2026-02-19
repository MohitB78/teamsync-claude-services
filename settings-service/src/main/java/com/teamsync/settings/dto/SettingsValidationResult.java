package com.teamsync.settings.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SECURITY: Result object for settings validation that collects errors
 * instead of silently ignoring them.
 */
public class SettingsValidationResult {

    private final List<SettingsError> errors = new ArrayList<>();
    private int appliedCount = 0;

    public void addError(String field, String message) {
        errors.add(new SettingsError(field, message));
    }

    public void incrementApplied() {
        appliedCount++;
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public List<SettingsError> getErrors() {
        return List.copyOf(errors);
    }

    public int getAppliedCount() {
        return appliedCount;
    }

    public int getErrorCount() {
        return errors.size();
    }

    public Map<String, Object> toMap() {
        return Map.of(
                "appliedCount", appliedCount,
                "errorCount", errors.size(),
                "errors", errors.stream()
                        .map(e -> Map.of("field", e.field(), "message", e.message()))
                        .toList()
        );
    }

    public record SettingsError(String field, String message) {}
}
