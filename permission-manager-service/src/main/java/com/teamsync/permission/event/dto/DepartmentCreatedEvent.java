package com.teamsync.permission.event.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * SECURITY FIX: Typed DTO for department created Kafka events.
 * Replaces unsafe Map<String, Object> deserialization to prevent injection attacks.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentCreatedEvent {

    @NotBlank(message = "Event ID is required")
    private String eventId;

    @NotBlank(message = "Tenant ID is required")
    private String tenantId;

    @NotBlank(message = "Department ID is required")
    private String departmentId;

    @NotBlank(message = "Department name is required")
    private String departmentName;

    private String parentDepartmentId;

    private Instant timestamp;
}
