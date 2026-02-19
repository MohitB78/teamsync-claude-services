package com.teamsync.notification.dto;

import com.teamsync.notification.model.Notification;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * DTO for notification responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDTO {

    private String id;
    private String title;
    private String message;
    private String type;
    private String priority;
    private String category;

    // Resource link
    private String resourceType;
    private String resourceId;
    private String resourceName;
    private String actionUrl;
    private Map<String, Object> actionData;

    // Sender
    private String senderId;
    private String senderName;
    private String senderAvatarUrl;

    // Status
    private boolean read;
    private Instant readAt;
    private boolean archived;

    // Grouping
    private String groupKey;
    private Integer groupCount;

    // Timestamps
    private Instant createdAt;
    private Instant expiresAt;

    /**
     * Convert entity to DTO.
     */
    public static NotificationDTO fromEntity(Notification entity) {
        if (entity == null) return null;

        return NotificationDTO.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .message(entity.getMessage())
                .type(entity.getType() != null ? entity.getType().name() : null)
                .priority(entity.getPriority() != null ? entity.getPriority().name() : "NORMAL")
                .category(entity.getCategory())
                .resourceType(entity.getResourceType())
                .resourceId(entity.getResourceId())
                .resourceName(entity.getResourceName())
                .actionUrl(entity.getActionUrl())
                .actionData(entity.getActionData())
                .senderId(entity.getSenderId())
                .senderName(entity.getSenderName())
                .senderAvatarUrl(entity.getSenderAvatarUrl())
                .read(Boolean.TRUE.equals(entity.getIsRead()))
                .readAt(entity.getReadAt())
                .archived(Boolean.TRUE.equals(entity.getIsArchived()))
                .groupKey(entity.getGroupKey())
                .groupCount(entity.getGroupCount())
                .createdAt(entity.getCreatedAt())
                .expiresAt(entity.getExpiresAt())
                .build();
    }
}
