package com.teamsync.notification.event;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * SECURITY FIX: Typed DTO for sharing created Kafka events.
 * Replaces unsafe Map<String, Object> deserialization to prevent injection attacks.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SharingCreatedEvent {

    @NotBlank(message = "Event ID is required")
    private String eventId;

    @NotBlank(message = "Tenant ID is required")
    private String tenantId;

    @NotBlank(message = "Recipient ID is required")
    private String recipientId;

    @NotBlank(message = "Resource type is required")
    private String resourceType;

    @NotBlank(message = "Resource ID is required")
    private String resourceId;

    private String resourceName;

    @NotBlank(message = "Shared by ID is required")
    private String sharedById;

    private String sharedByName;

    private String sharedByAvatarUrl;

    private String permissionLevel;

    private Instant timestamp;
}
