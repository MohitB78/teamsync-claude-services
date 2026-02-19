package com.teamsync.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * Notification entity representing a user notification.
 * Supports multiple delivery channels (email, push, in-app).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "notifications")
@CompoundIndexes({
        @CompoundIndex(name = "tenant_user_idx", def = "{'tenantId': 1, 'userId': 1}"),
        @CompoundIndex(name = "tenant_user_read_idx", def = "{'tenantId': 1, 'userId': 1, 'isRead': 1}"),
        @CompoundIndex(name = "tenant_user_archived_idx", def = "{'tenantId': 1, 'userId': 1, 'isArchived': 1}"),
        @CompoundIndex(name = "tenant_user_type_idx", def = "{'tenantId': 1, 'userId': 1, 'type': 1}"),
        @CompoundIndex(name = "tenant_resource_idx", def = "{'tenantId': 1, 'resourceType': 1, 'resourceId': 1}")
})
public class Notification {

    @Id
    private String id;

    @Indexed
    private String tenantId;

    @Indexed
    private String userId;

    /**
     * Notification title (short summary).
     */
    private String title;

    /**
     * Full notification message.
     */
    private String message;

    /**
     * Type of notification for filtering and display.
     */
    private NotificationType type;

    /**
     * Priority level affecting delivery and display.
     */
    @Builder.Default
    private NotificationPriority priority = NotificationPriority.NORMAL;

    /**
     * Category for grouping similar notifications.
     */
    private String category;

    // Link to related resource
    private String resourceType;  // document, folder, share, comment, workflow, etc.
    private String resourceId;
    private String resourceName;

    /**
     * URL path for navigation when notification is clicked.
     */
    private String actionUrl;

    /**
     * Additional action data (JSON-serializable).
     */
    private Map<String, Object> actionData;

    // Sender information
    private String senderId;
    private String senderName;
    private String senderAvatarUrl;

    // Recipient information (cached for email delivery)
    private String recipientEmail;

    // Read status
    @Builder.Default
    private Boolean isRead = false;
    private Instant readAt;

    // Archive status
    @Builder.Default
    private Boolean isArchived = false;
    private Instant archivedAt;

    // Delivery channel status
    @Builder.Default
    private Boolean sentEmail = false;
    private Instant emailSentAt;
    private String emailError;

    @Builder.Default
    private Boolean sentPush = false;
    private Instant pushSentAt;
    private String pushError;

    @Builder.Default
    private Boolean sentInApp = true;  // In-app is always delivered
    private Instant inAppSentAt;

    /**
     * Channels requested for this notification.
     */
    @Builder.Default
    private DeliveryChannels requestedChannels = new DeliveryChannels();

    // Batch/grouping support
    private String groupKey;  // For grouping similar notifications
    private Integer groupCount;  // Number of items in group

    // Audit timestamps
    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    /**
     * Auto-expire notifications after this time (TTL index).
     */
    @Indexed(expireAfterSeconds = 0)
    private Instant expiresAt;

    @Version
    private Long version;

    /**
     * Notification type categories.
     */
    public enum NotificationType {
        // Document events
        DOCUMENT_SHARED,
        DOCUMENT_UPLOADED,
        DOCUMENT_UPDATED,
        DOCUMENT_COMMENTED,
        DOCUMENT_DOWNLOADED,

        // Collaboration
        SHARE,
        SHARE_ACCEPTED,
        SHARE_REVOKED,
        COMMENT,
        MENTION,
        REPLY,

        // Workflow
        WORKFLOW_STARTED,
        WORKFLOW_COMPLETED,
        WORKFLOW_FAILED,
        WORKFLOW_APPROVAL_REQUIRED,
        WORKFLOW_APPROVED,
        WORKFLOW_REJECTED,

        // System
        SYSTEM,
        SECURITY_ALERT,
        QUOTA_WARNING,
        QUOTA_EXCEEDED,

        // User events
        USER_INVITED,
        USER_JOINED,
        REMINDER,

        // Generic
        INFO,
        WARNING,
        ERROR
    }

    /**
     * Notification priority levels.
     */
    public enum NotificationPriority {
        LOW,      // Can be batched, delayed
        NORMAL,   // Standard delivery
        HIGH,     // Immediate delivery preferred
        URGENT    // Must be delivered immediately via all channels
    }

    /**
     * Requested delivery channels for notification.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeliveryChannels {
        @Builder.Default
        private boolean email = false;
        @Builder.Default
        private boolean push = false;
        @Builder.Default
        private boolean inApp = true;
    }
}
