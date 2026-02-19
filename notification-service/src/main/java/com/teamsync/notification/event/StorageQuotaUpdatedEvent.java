package com.teamsync.notification.event;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * SECURITY FIX: Typed DTO for storage quota updated Kafka events.
 * Replaces unsafe Map<String, Object> deserialization to prevent injection attacks.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageQuotaUpdatedEvent {

    @NotBlank(message = "Event ID is required")
    private String eventId;

    @NotBlank(message = "Tenant ID is required")
    private String tenantId;

    @NotBlank(message = "User ID is required")
    private String userId;

    private String driveId;

    @NotNull(message = "Used storage is required")
    @Min(value = 0, message = "Used storage cannot be negative")
    private Long usedStorage;

    @NotNull(message = "Quota limit is required")
    @Min(value = 1, message = "Quota limit must be positive")
    private Long quotaLimit;

    @NotNull(message = "Used percentage is required")
    @Min(value = 0, message = "Used percentage cannot be negative")
    @Max(value = 200, message = "Used percentage cannot exceed 200") // Allow slight overage
    private Double usedPercentage;

    private Instant timestamp;
}
