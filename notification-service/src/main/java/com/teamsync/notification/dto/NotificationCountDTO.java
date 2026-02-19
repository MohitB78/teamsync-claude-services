package com.teamsync.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * DTO for notification counts.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationCountDTO {

    /**
     * Total unread count.
     */
    private long unreadCount;

    /**
     * Count by type.
     */
    private Map<String, Long> countByType;

    /**
     * Count by priority.
     */
    private Map<String, Long> countByPriority;

    /**
     * Has urgent unread notifications.
     */
    private boolean hasUrgent;
}
