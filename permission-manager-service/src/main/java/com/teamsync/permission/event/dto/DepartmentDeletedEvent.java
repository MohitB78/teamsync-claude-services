package com.teamsync.permission.event.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * SECURITY FIX: Typed DTO for department deleted Kafka events.
 * Replaces unsafe Map<String, Object> deserialization to prevent injection attacks.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentDeletedEvent {

    @NotBlank(message = "Event ID is required")
    private String eventId;

    @NotBlank(message = "Tenant ID is required")
    private String tenantId;

    @NotBlank(message = "Department ID is required")
    private String departmentId;

    private Instant timestamp;
}
