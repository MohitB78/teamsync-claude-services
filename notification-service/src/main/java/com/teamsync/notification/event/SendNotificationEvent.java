package com.teamsync.notification.event;

import com.teamsync.notification.model.Notification;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Kafka event for sending notifications.
 * Published by other services to trigger notifications.
 *
 * SECURITY FIX (Round 14 #M11): Added validation annotations to prevent
 * malformed events from causing errors or resource exhaustion.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendNotificationEvent {

    /**
     * Unique event ID for deduplication.
     * SECURITY FIX (Round 14 #M12): Required for deduplication to prevent replay attacks.
     */
    @NotBlank(message = "Event ID is required for deduplication")
    @Size(max = 64, message = "Event ID must be at most 64 characters")
    private String eventId;

    /**
     * Tenant ID for multi-tenancy.
     */
    @NotBlank(message = "Tenant ID is required")
    @Size(max = 64, message = "Tenant ID must be at most 64 characters")
    private String tenantId;

    /**
     * Target user IDs.
     */
    @NotEmpty(message = "At least one user ID is required")
    @Size(max = 100, message = "Cannot send to more than 100 users at once")
    private List<@NotBlank @Size(max = 64) String> userIds;

    /**
     * Notification title.
     */
    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must be at most 200 characters")
    private String title;

    /**
     * Full message.
     */
    @NotBlank(message = "Message is required")
    @Size(max = 2000, message = "Message must be at most 2000 characters")
    private String message;

    /**
     * Notification type.
     */
    @NotBlank(message = "Type is required")
    @Size(max = 50, message = "Type must be at most 50 characters")
    private String type;

    /**
     * Priority level.
     */
    @Builder.Default
    private String priority = "NORMAL";

    /**
     * Category for grouping.
     */
    private String category;

    // Resource info
    private String resourceType;
    private String resourceId;
    private String resourceName;

    // Action
    private String actionUrl;
    private Map<String, Object> actionData;

    // Sender info
    private String senderId;
    private String senderName;
    private String senderAvatarUrl;

    // Delivery options
    @Builder.Default
    private boolean sendEmail = false;
    @Builder.Default
    private boolean sendPush = false;
    @Builder.Default
    private boolean sendInApp = true;

    // Grouping
    private String groupKey;

    // Expiration
    private Instant expiresAt;

    // Event metadata
    @Builder.Default
    private Instant timestamp = Instant.now();

    /**
     * Convert to NotificationType enum safely.
     */
    public Notification.NotificationType getNotificationType() {
        if (type == null) return Notification.NotificationType.INFO;
        try {
            return Notification.NotificationType.valueOf(type);
        } catch (IllegalArgumentException e) {
            return Notification.NotificationType.INFO;
        }
    }

    /**
     * Convert to NotificationPriority enum safely.
     */
    public Notification.NotificationPriority getNotificationPriority() {
        if (priority == null) return Notification.NotificationPriority.NORMAL;
        try {
            return Notification.NotificationPriority.valueOf(priority);
        } catch (IllegalArgumentException e) {
            return Notification.NotificationPriority.NORMAL;
        }
    }
}
