package com.teamsync.permission.event.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Event published when a new team is created.
 * Consumed by Permission Manager to create the team drive.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamCreatedEvent {

    @NotBlank(message = "Event ID is required")
    private String eventId;

    @NotBlank(message = "Tenant ID is required")
    private String tenantId;

    @NotBlank(message = "Team ID is required")
    private String teamId;

    @NotBlank(message = "Team name is required")
    private String teamName;

    @NotBlank(message = "Owner ID is required")
    private String ownerId;

    /**
     * Optional quota limit for the team drive in bytes.
     * If null, the drive has unlimited quota.
     */
    private Long quotaBytes;

    private Instant timestamp;
}
