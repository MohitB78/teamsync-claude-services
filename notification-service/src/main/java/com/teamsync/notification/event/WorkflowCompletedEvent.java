package com.teamsync.notification.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * SECURITY FIX: Typed DTO for workflow completed Kafka events.
 * Replaces unsafe Map<String, Object> deserialization to prevent injection attacks.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowCompletedEvent {

    @NotBlank(message = "Event ID is required")
    private String eventId;

    @NotBlank(message = "Tenant ID is required")
    private String tenantId;

    @NotBlank(message = "Workflow ID is required")
    private String workflowId;

    @NotBlank(message = "Workflow name is required")
    private String workflowName;

    @NotBlank(message = "Status is required")
    @Pattern(regexp = "^(SUCCESS|FAILED|CANCELLED)$", message = "Status must be SUCCESS, FAILED, or CANCELLED")
    private String status;

    @NotBlank(message = "Initiator ID is required")
    private String initiatorId;

    private String resourceType;

    private String resourceId;

    private String resourceName;

    private String errorMessage;

    private Instant timestamp;
}
