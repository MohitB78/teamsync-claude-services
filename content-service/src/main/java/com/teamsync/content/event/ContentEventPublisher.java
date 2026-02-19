package com.teamsync.content.event;

import com.teamsync.common.context.TenantContext;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Unified event publisher for content (folders and documents).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ContentEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${teamsync.kafka.topics.content-events:teamsync.content.events}")
    private String contentEventsTopic;

    /**
     * Publish a folder event
     */
    public void publishFolderEvent(String folderId, EventType eventType, String tenantId, String driveId) {
        ContentEvent event = ContentEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .contentType(ContentType.FOLDER)
                .contentId(folderId)
                .eventType(eventType)
                .tenantId(tenantId)
                .driveId(driveId)
                .userId(TenantContext.getUserId())
                .timestamp(Instant.now())
                .build();

        try {
            kafkaTemplate.send(contentEventsTopic, folderId, event);
            log.debug("Published folder event: {} for folder: {}", eventType, folderId);
        } catch (Exception e) {
            log.error("Failed to publish folder event: {}", e.getMessage());
        }
    }

    /**
     * Publish a document event
     */
    public void publishDocumentEvent(String documentId, EventType eventType, String tenantId, String driveId) {
        ContentEvent event = ContentEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .contentType(ContentType.DOCUMENT)
                .contentId(documentId)
                .eventType(eventType)
                .tenantId(tenantId)
                .driveId(driveId)
                .userId(TenantContext.getUserId())
                .timestamp(Instant.now())
                .build();

        try {
            kafkaTemplate.send(contentEventsTopic, documentId, event);
            log.debug("Published document event: {} for document: {}", eventType, documentId);
        } catch (Exception e) {
            log.error("Failed to publish document event: {}", e.getMessage());
        }
    }

    /**
     * Content types
     */
    public enum ContentType {
        FOLDER,
        DOCUMENT
    }

    /**
     * Event types
     */
    public enum EventType {
        CREATED,
        UPDATED,
        TRASHED,
        RESTORED,
        DELETED,
        MOVED,
        LOCKED,
        UNLOCKED
    }

    /**
     * Unified content event
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContentEvent {
        private String eventId;
        private ContentType contentType;
        private String contentId;
        private EventType eventType;
        private String tenantId;
        private String driveId;
        private String userId;
        private Instant timestamp;
    }
}
