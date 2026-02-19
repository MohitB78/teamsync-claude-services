package com.teamsync.settings.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Generic request for updating settings.
 * Supports partial updates via a map of key-value pairs.
 *
 * SECURITY FIX (Round 15 #M20): Added @Size constraints to prevent DoS attacks
 * via excessively large settings maps.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSettingsRequest {

    /**
     * Map of settings to update.
     * Keys use dot notation for nested properties, e.g.:
     * - "theme" -> "dark"
     * - "notifications.emailEnabled" -> false
     * - "branding.primaryColor" -> "#ff0000"
     *
     * SECURITY: Limited to 100 settings per request to prevent DoS.
     */
    @NotNull(message = "Settings map is required")
    @Size(max = 100, message = "Cannot update more than 100 settings at once")
    private Map<String, Object> settings;
}
