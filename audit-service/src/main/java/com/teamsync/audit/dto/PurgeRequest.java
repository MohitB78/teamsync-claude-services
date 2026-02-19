package com.teamsync.audit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Duration;

/**
 * Request for purging audit logs.
 */
@Data
public class PurgeRequest {

    /**
     * Retention period - events older than this will be purged.
     * Format: ISO-8601 duration (e.g., "P30D" for 30 days, "P365D" for 1 year)
     */
    @NotNull
    private Duration retentionPeriod;

    /**
     * Reason for the purge operation (required for compliance).
     */
    @NotBlank
    @Size(min = 10, max = 500)
    private String reason;

    /**
     * Whether this is a preview (dry run) or actual execution.
     */
    private boolean preview = false;

    /**
     * Confirmation code (required for non-preview purge).
     * Must match a code returned from the preview operation.
     */
    private String confirmationCode;
}
