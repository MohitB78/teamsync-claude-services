package com.teamsync.settings.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Event published when settings are changed.
 * Used for cache invalidation and audit logging.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettingsChangedEvent {

    private String tenantId;
    private String userId;
    private String driveId;

    /**
     * Type of settings: USER, TENANT, or DRIVE
     */
    private String settingsType;

    /**
     * List of field names that were changed.
     */
    private List<String> changedFields;

    @Builder.Default
    private Instant timestamp = Instant.now();
}
