package com.teamsync.notification.consumer;

import com.teamsync.common.config.KafkaTopics;
import com.teamsync.notification.dto.CreateNotificationRequest;
import com.teamsync.notification.event.*;
import com.teamsync.notification.model.Notification;
import com.teamsync.notification.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Kafka consumer for notification events from other services.
 *
 * SECURITY FIX: Uses typed DTOs instead of Map<String, Object> to prevent
 * deserialization attacks and ensure all required fields are validated.
 *
 * SECURITY FIX (Round 15 #H18): Added event deduplication using Redis SETNX.
 * This prevents duplicate notifications from being created when Kafka messages
 * are redelivered due to consumer restarts or network issues.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Validated
public class NotificationEventConsumer {

    private final NotificationService notificationService;
    private final StringRedisTemplate redisTemplate;

    /**
     * SECURITY FIX (Round 15 #H18): Event deduplication TTL.
     * Events are deduplicated for 24 hours to prevent duplicate notifications.
     */
    private static final Duration EVENT_DEDUP_TTL = Duration.ofHours(24);

    /**
     * SECURITY FIX (Round 15 #H18): Redis key prefix for event deduplication.
     */
    private static final String EVENT_DEDUP_PREFIX = "teamsync:notification:event:dedup:";

    /**
     * SECURITY FIX: Pattern for validating resource IDs to prevent URL parameter pollution.
     * Only allows alphanumeric characters, hyphens, and underscores.
     */
    private static final Pattern SAFE_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9\\-_]+$");

    /**
     * Listen for notification send requests from other services.
     *
     * SECURITY FIX (Round 14 #M12): Added deduplication check using eventId.
     * SECURITY FIX: Added @Transactional for database consistency.
     */
    @Transactional
    @KafkaListener(
            topics = KafkaTopics.NOTIFICATIONS_SEND,
            groupId = "notification-service",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleSendNotification(@Payload @Valid SendNotificationEvent event) {
        log.info("Received notification event for {} users, type: {}, eventId: {}",
                event.getUserIds() != null ? event.getUserIds().size() : 0, event.getType(), event.getEventId());

        try {
            // SECURITY FIX (Round 14 #M12): Check for duplicate event
            if (isDuplicateEvent(event.getEventId(), "notification")) {
                return;
            }

            if (event.getUserIds() == null || event.getUserIds().isEmpty()) {
                log.warn("Received notification event with no user IDs");
                return;
            }

            // SECURITY FIX: Sanitize action URL to prevent parameter pollution
            String safeActionUrl = sanitizeActionUrl(event.getActionUrl());

            CreateNotificationRequest request = CreateNotificationRequest.builder()
                    .userIds(event.getUserIds())
                    .title(event.getTitle())
                    .message(event.getMessage())
                    .type(event.getNotificationType())
                    .priority(event.getNotificationPriority())
                    .category(event.getCategory())
                    .resourceType(event.getResourceType())
                    .resourceId(event.getResourceId())
                    .resourceName(event.getResourceName())
                    .actionUrl(safeActionUrl)
                    .actionData(event.getActionData())
                    .senderId(event.getSenderId())
                    .senderName(event.getSenderName())
                    .senderAvatarUrl(event.getSenderAvatarUrl())
                    .sendEmail(event.isSendEmail())
                    .sendPush(event.isSendPush())
                    .sendInApp(event.isSendInApp())
                    .groupKey(event.getGroupKey())
                    .expiresAt(event.getExpiresAt())
                    .build();

            notificationService.createNotifications(
                    event.getTenantId(),
                    request,
                    event.getSenderId(),
                    event.getSenderName()
            );

            log.info("Successfully processed notification event for {} users",
                    event.getUserIds().size());

        } catch (Exception e) {
            log.error("Failed to process notification event: {}", e.getMessage(), e);
            // Don't rethrow - we don't want to retry indefinitely
        }
    }

    /**
     * Listen for sharing events to create notifications.
     *
     * SECURITY FIX: Uses typed SharingCreatedEvent instead of Map<String, Object>
     * SECURITY FIX: Added @Transactional for database consistency.
     */
    @Transactional
    @KafkaListener(
            topics = KafkaTopics.SHARING_CREATED,
            groupId = "notification-service-sharing",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleSharingCreated(@Payload @Valid SharingCreatedEvent event) {
        log.debug("Received sharing created event: tenantId={}, recipientId={}, resourceId={}",
                event.getTenantId(), event.getRecipientId(), event.getResourceId());

        try {
            // SECURITY FIX: Build URL safely with validated and encoded resource ID
            String safeActionUrl = buildSafeResourceUrl("/drive", event.getResourceId());

            CreateNotificationRequest request = CreateNotificationRequest.builder()
                    .userIds(List.of(event.getRecipientId()))
                    .title(event.getSharedByName() + " shared a " + event.getResourceType() + " with you")
                    .message("\"" + sanitizeText(event.getResourceName()) + "\" has been shared with you")
                    .type(Notification.NotificationType.SHARE)
                    .priority(Notification.NotificationPriority.NORMAL)
                    .resourceType(event.getResourceType())
                    .resourceId(event.getResourceId())
                    .resourceName(event.getResourceName())
                    .actionUrl(safeActionUrl)
                    .senderId(event.getSharedById())
                    .senderName(event.getSharedByName())
                    .sendEmail(true)
                    .sendInApp(true)
                    .build();

            notificationService.createNotifications(event.getTenantId(), request,
                    event.getSharedById(), event.getSharedByName());

        } catch (Exception e) {
            log.error("Failed to process sharing event: {}", e.getMessage(), e);
        }
    }

    /**
     * Listen for document uploaded events.
     *
     * SECURITY FIX: Uses typed DocumentUploadedEvent instead of Map<String, Object>
     */
    @KafkaListener(
            topics = KafkaTopics.DOCUMENTS_UPLOADED,
            groupId = "notification-service-documents",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleDocumentUploaded(@Payload @Valid DocumentUploadedEvent event) {
        log.debug("Received document uploaded event: tenantId={}, documentId={}",
                event.getTenantId(), event.getDocumentId());

        // This would typically notify users watching the folder
        // Implementation depends on folder watching feature
    }

    /**
     * Listen for workflow events.
     *
     * SECURITY FIX: Uses typed WorkflowCompletedEvent instead of Map<String, Object>
     * SECURITY FIX: Added @Transactional for database consistency.
     */
    @Transactional
    @KafkaListener(
            topics = KafkaTopics.WORKFLOW_COMPLETED,
            groupId = "notification-service-workflow",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleWorkflowCompleted(@Payload @Valid WorkflowCompletedEvent event) {
        log.debug("Received workflow completed event: tenantId={}, workflowId={}, status={}",
                event.getTenantId(), event.getWorkflowId(), event.getStatus());

        try {
            Notification.NotificationType type = "SUCCESS".equals(event.getStatus()) ?
                    Notification.NotificationType.WORKFLOW_COMPLETED :
                    Notification.NotificationType.WORKFLOW_FAILED;

            String title = "SUCCESS".equals(event.getStatus()) ?
                    "Workflow \"" + sanitizeText(event.getWorkflowName()) + "\" completed" :
                    "Workflow \"" + sanitizeText(event.getWorkflowName()) + "\" failed";

            // SECURITY FIX: Build URL safely with validated and encoded resource ID
            String safeActionUrl = buildSafeResourceUrl("/workflows/history", event.getResourceId());

            CreateNotificationRequest request = CreateNotificationRequest.builder()
                    .userIds(List.of(event.getInitiatorId()))
                    .title(title)
                    .message("The workflow on \"" + sanitizeText(event.getResourceName()) + "\" has " +
                            ("SUCCESS".equals(event.getStatus()) ? "completed successfully" : "failed"))
                    .type(type)
                    .priority("SUCCESS".equals(event.getStatus()) ?
                            Notification.NotificationPriority.NORMAL :
                            Notification.NotificationPriority.HIGH)
                    .resourceType("document")
                    .resourceId(event.getResourceId())
                    .resourceName(event.getResourceName())
                    .actionUrl(safeActionUrl)
                    .sendEmail(true)
                    .sendInApp(true)
                    .build();

            notificationService.createNotifications(event.getTenantId(), request, "system", "TeamSync");

        } catch (Exception e) {
            log.error("Failed to process workflow event: {}", e.getMessage(), e);
        }
    }

    /**
     * Listen for storage quota warnings.
     *
     * SECURITY FIX: Uses typed StorageQuotaUpdatedEvent instead of Map<String, Object>
     * SECURITY FIX: Added @Transactional for database consistency.
     */
    @Transactional
    @KafkaListener(
            topics = KafkaTopics.STORAGE_QUOTA_UPDATED,
            groupId = "notification-service-storage",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleStorageQuotaUpdated(@Payload @Valid StorageQuotaUpdatedEvent event) {
        log.debug("Received storage quota event: tenantId={}, userId={}, percentage={}",
                event.getTenantId(), event.getUserId(), event.getUsedPercentage());

        try {
            double percentage = event.getUsedPercentage();

            // Warn at 80%, alert at 95%
            if (percentage >= 95) {
                createQuotaNotification(event.getTenantId(), event.getUserId(),
                        Notification.NotificationType.QUOTA_EXCEEDED,
                        Notification.NotificationPriority.URGENT,
                        "Storage quota exceeded",
                        "Your storage is " + Math.round(percentage) + "% full. Please free up space to continue uploading files.");
            } else if (percentage >= 80) {
                createQuotaNotification(event.getTenantId(), event.getUserId(),
                        Notification.NotificationType.QUOTA_WARNING,
                        Notification.NotificationPriority.HIGH,
                        "Storage quota warning",
                        "Your storage is " + Math.round(percentage) + "% full. Consider cleaning up unused files.");
            }

        } catch (Exception e) {
            log.error("Failed to process storage quota event: {}", e.getMessage(), e);
        }
    }

    private void createQuotaNotification(String tenantId, String userId,
                                          Notification.NotificationType type,
                                          Notification.NotificationPriority priority,
                                          String title, String message) {
        CreateNotificationRequest request = CreateNotificationRequest.builder()
                .userIds(List.of(userId))
                .title(title)
                .message(message)
                .type(type)
                .priority(priority)
                .actionUrl("/settings/storage")
                .sendEmail(true)
                .sendInApp(true)
                .groupKey("quota-warning-" + userId)  // Avoid duplicate warnings
                .build();

        notificationService.createNotifications(tenantId, request, "system", "TeamSync");
    }

    /**
     * SECURITY FIX: Validates and builds a safe URL with resource ID parameter.
     * Prevents URL parameter pollution by:
     * 1. Validating the resource ID format
     * 2. URL-encoding the resource ID
     * 3. Using a controlled URL structure
     *
     * @param basePath the base URL path (e.g., "/drive")
     * @param resourceId the resource ID to include as a parameter
     * @return a safely constructed URL
     */
    private String buildSafeResourceUrl(String basePath, String resourceId) {
        if (resourceId == null || resourceId.isBlank()) {
            return basePath;
        }

        // Validate resource ID format to prevent injection
        if (!SAFE_ID_PATTERN.matcher(resourceId).matches()) {
            log.warn("Invalid resource ID format detected, using base path only: {}", resourceId);
            return basePath;
        }

        // URL-encode the resource ID even though it passed validation
        String encodedId = URLEncoder.encode(resourceId, StandardCharsets.UTF_8);
        return basePath + "?resourceId=" + encodedId;
    }

    /**
     * SECURITY FIX: Sanitizes an existing action URL to prevent parameter pollution.
     */
    private String sanitizeActionUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }

        // Only allow internal paths
        if (!url.startsWith("/")) {
            log.warn("External URL detected in notification, blocking: {}", url);
            return null;
        }

        // Check for parameter pollution attempts
        if (url.contains("&") || url.split("\\?").length > 2) {
            log.warn("Potential URL parameter pollution detected: {}", url);
            // Extract just the base path and first parameter if present
            int queryStart = url.indexOf('?');
            if (queryStart > 0) {
                String basePath = url.substring(0, queryStart);
                String queryPart = url.substring(queryStart + 1);
                int ampersand = queryPart.indexOf('&');
                if (ampersand > 0) {
                    queryPart = queryPart.substring(0, ampersand);
                }
                return basePath + "?" + queryPart;
            }
        }

        return url;
    }

    /**
     * SECURITY FIX: Sanitizes text to prevent XSS in notifications.
     */
    private String sanitizeText(String text) {
        if (text == null) {
            return "";
        }
        // Remove any HTML/script tags
        return text.replaceAll("<[^>]*>", "")
                   .replaceAll("[<>\"']", "");
    }

    /**
     * SECURITY FIX (Round 15 #H18): Check if event was already processed.
     * Uses Redis SETNX (SET if Not eXists) for atomic deduplication.
     * This prevents duplicate notifications from Kafka message redelivery.
     *
     * @param eventId The unique event ID
     * @param eventType The type of event (for key namespacing)
     * @return true if this is a duplicate event (already processed), false if new
     */
    private boolean isDuplicateEvent(String eventId, String eventType) {
        if (eventId == null || eventId.isBlank()) {
            // Events without IDs cannot be deduplicated - allow processing
            return false;
        }

        String redisKey = EVENT_DEDUP_PREFIX + eventType + ":" + eventId;

        try {
            // SETNX: Set only if key doesn't exist, with TTL
            Boolean wasSet = redisTemplate.opsForValue().setIfAbsent(redisKey, "1", EVENT_DEDUP_TTL);

            if (Boolean.FALSE.equals(wasSet)) {
                // Key already existed - this is a duplicate
                log.debug("Duplicate {} event detected and skipped: {}", eventType, eventId);
                return true;
            }

            return false;
        } catch (Exception e) {
            // Redis failure - log error but allow processing (fail-open for availability)
            log.warn("Redis deduplication check failed for {} event {}: {}",
                    eventType, eventId, e.getMessage());
            return false;
        }
    }
}
