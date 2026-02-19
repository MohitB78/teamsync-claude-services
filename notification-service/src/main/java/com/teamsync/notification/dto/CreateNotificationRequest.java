package com.teamsync.notification.dto;

import com.teamsync.notification.model.Notification;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Request to create a new notification.
 *
 * SECURITY FIX (Round 15 #M10-M12): Added @Size constraints to prevent DoS attacks
 * via excessively long strings in title, message, and URL fields.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateNotificationRequest {

    /**
     * Target user IDs to receive the notification.
     * SECURITY FIX (Round 15 #M10): Limit number of recipients to prevent mass notification attacks.
     */
    @NotNull(message = "User IDs are required")
    @Size(max = 1000, message = "Cannot notify more than 1000 users at once")
    private List<String> userIds;

    /**
     * Notification title.
     * SECURITY FIX (Round 15 #M11): Added size constraint.
     */
    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    private String title;

    /**
     * Full notification message.
     * SECURITY FIX (Round 15 #M12): Added size constraint.
     */
    @NotBlank(message = "Message is required")
    @Size(max = 4000, message = "Message must not exceed 4000 characters")
    private String message;

    /**
     * Notification type.
     */
    @NotNull(message = "Type is required")
    private Notification.NotificationType type;

    /**
     * Priority level.
     */
    @Builder.Default
    private Notification.NotificationPriority priority = Notification.NotificationPriority.NORMAL;

    /**
     * Optional category for grouping.
     */
    @Size(max = 64, message = "Category must not exceed 64 characters")
    private String category;

    // Resource linking
    @Size(max = 64, message = "Resource type must not exceed 64 characters")
    private String resourceType;
    @Size(max = 64, message = "Resource ID must not exceed 64 characters")
    private String resourceId;
    @Size(max = 255, message = "Resource name must not exceed 255 characters")
    private String resourceName;

    // Action
    @Size(max = 2000, message = "Action URL must not exceed 2000 characters")
    private String actionUrl;
    /**
     * SECURITY FIX (Round 14 #M21): Added @Size constraint to prevent DoS via large payloads.
     */
    @Size(max = 20, message = "Action data must not exceed 20 entries")
    private Map<String, Object> actionData;

    // Sender (populated from JWT if not provided)
    @Size(max = 64, message = "Sender ID must not exceed 64 characters")
    private String senderId;
    @Size(max = 255, message = "Sender name must not exceed 255 characters")
    private String senderName;
    @Size(max = 2000, message = "Sender avatar URL must not exceed 2000 characters")
    private String senderAvatarUrl;

    // Delivery channels
    @Builder.Default
    private boolean sendEmail = false;
    @Builder.Default
    private boolean sendPush = false;
    @Builder.Default
    private boolean sendInApp = true;

    // Grouping
    @Size(max = 255, message = "Group key must not exceed 255 characters")
    private String groupKey;

    // Expiration
    private Instant expiresAt;
}
