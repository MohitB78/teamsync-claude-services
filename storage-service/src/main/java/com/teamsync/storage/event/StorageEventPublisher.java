package com.teamsync.storage.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Async event publisher for storage audit events.
 *
 * Events are published asynchronously to Kafka to avoid blocking request threads.
 * This is a fire-and-forget pattern - failures are logged but don't affect the calling operation.
 *
 * Using @Async with virtual threads (eventPublisherExecutor) ensures minimal overhead
 * while decoupling event publishing from the request path.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StorageEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String STORAGE_EVENTS_TOPIC = "teamsync.storage.events";

    /**
     * Publish a storage event asynchronously.
     * Fire-and-forget - failures are logged but don't affect the calling operation.
     *
     * @param eventType   The type of storage event (UPLOAD_COMPLETED, FILE_DELETED, etc.)
     * @param tenantId    The tenant ID
     * @param driveId     The drive ID
     * @param userId      The user ID who performed the action
     * @param bucket      The storage bucket
     * @param storageKey  The storage key/path
     * @param fileSize    The file size in bytes (nullable)
     * @param outcome     The outcome of the operation (SUCCESS, FAILURE)
     */
    @Async("eventPublisherExecutor")
    public void publishStorageEvent(String eventType, String tenantId, String driveId, String userId,
                                     String bucket, String storageKey, Long fileSize, String outcome) {
        try {
            Map<String, Object> event = new HashMap<>(10);
            event.put("eventType", eventType);
            event.put("tenantId", tenantId);
            event.put("driveId", driveId);
            event.put("userId", userId);
            event.put("bucket", bucket);
            event.put("storageKey", storageKey);
            event.put("fileSize", fileSize != null ? fileSize : 0L);
            event.put("outcome", outcome);
            event.put("timestamp", Instant.now().toString());

            kafkaTemplate.send(STORAGE_EVENTS_TOPIC, storageKey, event);
            log.debug("Published storage event: {} for {}/{}", eventType, bucket, storageKey);
        } catch (Exception e) {
            // Log but don't throw - fire-and-forget pattern
            log.warn("Failed to publish storage event: {} for {}/{} - {}",
                    eventType, bucket, storageKey, e.getMessage());
        }
    }

    /**
     * Publish an upload completed event.
     */
    @Async("eventPublisherExecutor")
    public void publishUploadCompleted(String tenantId, String driveId, String userId,
                                        String bucket, String storageKey, long fileSize) {
        publishStorageEvent("UPLOAD_COMPLETED", tenantId, driveId, userId, bucket, storageKey, fileSize, "SUCCESS");
    }

    /**
     * Publish an upload cancelled event.
     */
    @Async("eventPublisherExecutor")
    public void publishUploadCancelled(String tenantId, String driveId, String userId,
                                        String bucket, String storageKey, long fileSize) {
        publishStorageEvent("UPLOAD_CANCELLED", tenantId, driveId, userId, bucket, storageKey, fileSize, "SUCCESS");
    }

    /**
     * Publish a file deleted event.
     */
    @Async("eventPublisherExecutor")
    public void publishFileDeleted(String tenantId, String driveId, String userId,
                                    String bucket, String storageKey, long fileSize) {
        publishStorageEvent("FILE_DELETED", tenantId, driveId, userId, bucket, storageKey, fileSize, "SUCCESS");
    }

    /**
     * Publish a file downloaded event.
     */
    @Async("eventPublisherExecutor")
    public void publishFileDownloaded(String tenantId, String driveId, String userId,
                                       String bucket, String storageKey, long fileSize) {
        publishStorageEvent("FILE_DOWNLOADED", tenantId, driveId, userId, bucket, storageKey, fileSize, "SUCCESS");
    }

    /**
     * Publish a file copied event.
     */
    @Async("eventPublisherExecutor")
    public void publishFileCopied(String tenantId, String driveId, String userId,
                                   String bucket, String storageKey, long fileSize) {
        publishStorageEvent("FILE_COPIED", tenantId, driveId, userId, bucket, storageKey, fileSize, "SUCCESS");
    }
}
