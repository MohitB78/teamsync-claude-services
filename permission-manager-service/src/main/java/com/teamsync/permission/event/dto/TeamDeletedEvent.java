package com.teamsync.permission.event.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Event published when a team is deleted or archived.
 * Consumed by Permission Manager to archive the team drive
 * and revoke all member access.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamDeletedEvent {

    @NotBlank(message = "Event ID is required")
    private String eventId;

    @NotBlank(message = "Tenant ID is required")
    private String tenantId;

    @NotBlank(message = "Team ID is required")
    private String teamId;

    /**
     * Whether this is a permanent deletion or just archival.
     * If archived, the drive content is preserved.
     */
    private Boolean isPermanent;

    private Instant timestamp;
}
