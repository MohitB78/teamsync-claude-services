package com.teamsync.settings.exception;

/**
 * Exception thrown when settings are not found.
 */
public class SettingsNotFoundException extends RuntimeException {

    public SettingsNotFoundException(String message) {
        super(message);
    }

    public SettingsNotFoundException(String settingsType, String identifier) {
        super(String.format("%s settings not found for: %s", settingsType, identifier));
    }
}
