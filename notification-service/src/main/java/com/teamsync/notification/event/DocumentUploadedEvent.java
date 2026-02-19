package com.teamsync.notification.event;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * SECURITY FIX: Typed DTO for document uploaded Kafka events.
 * Replaces unsafe Map<String, Object> deserialization to prevent injection attacks.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentUploadedEvent {

    @NotBlank(message = "Event ID is required")
    private String eventId;

    @NotBlank(message = "Tenant ID is required")
    private String tenantId;

    @NotBlank(message = "Drive ID is required")
    private String driveId;

    private String folderId;

    @NotBlank(message = "Document ID is required")
    private String documentId;

    private String documentName;

    private String contentType;

    private Long fileSize;

    @NotBlank(message = "Uploaded by ID is required")
    private String uploadedById;

    private String uploadedByName;

    private Instant timestamp;
}
