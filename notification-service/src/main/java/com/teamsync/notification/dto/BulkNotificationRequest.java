package com.teamsync.notification.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request for bulk notification operations.
 *
 * SECURITY FIX (Round 7): Added validation constraints to prevent mass assignment
 * and ensure proper input validation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkNotificationRequest {

    /**
     * List of notification IDs to operate on.
     * Limited to 100 IDs per request to prevent DoS.
     */
    @NotEmpty(message = "At least one notification ID is required")
    @Size(max = 100, message = "Cannot process more than 100 notifications at once")
    private List<String> notificationIds;

    /**
     * Operation type.
     */
    @NotNull(message = "Operation type is required")
    private BulkOperation operation;

    public enum BulkOperation {
        MARK_READ,
        MARK_UNREAD,
        ARCHIVE,
        UNARCHIVE,
        DELETE
    }
}
