package com.teamsync.notification.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request to update notification preferences.
 * Uses partial update pattern.
 *
 * SECURITY FIX (Round 15 #M26): Added @Size constraint to prevent DoS attacks.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePreferenceRequest {

    /**
     * Partial update map with dot notation support.
     * Examples:
     * - "emailEnabled" -> false
     * - "digestSettings.enabled" -> true
     * - "typePreferences.COMMENT.email" -> false
     *
     * SECURITY: Limited to 50 preferences per request to prevent DoS.
     */
    @NotNull(message = "Preferences map is required")
    @Size(max = 50, message = "Cannot update more than 50 preferences at once")
    private Map<String, Object> preferences;
}
