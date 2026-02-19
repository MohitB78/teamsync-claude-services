package com.teamsync.activity.dto;

import com.teamsync.activity.model.Activity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * DTO for Activity responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityDTO {

    private String id;
    private String tenantId;
    private String driveId;

    // Who performed the action
    private String userId;
    private String userName;
    private String userEmail;
    private String userAvatarUrl;

    // What action was performed
    private String action;
    private String actionDescription;

    // On what resource
    private String resourceType;
    private String resourceId;
    private String resourceName;

    // Additional context
    private Map<String, Object> metadata;
    private Map<String, Object> changes;

    // Timestamp
    private Instant createdAt;

    /**
     * Convert Activity entity to DTO.
     */
    public static ActivityDTO fromEntity(Activity activity) {
        return ActivityDTO.builder()
                .id(activity.getId())
                .tenantId(activity.getTenantId())
                .driveId(activity.getDriveId())
                .userId(activity.getUserId())
                .userName(activity.getUserName())
                .userEmail(activity.getUserEmail())
                .action(activity.getAction() != null ? activity.getAction().name() : null)
                .actionDescription(activity.getActionDescription())
                .resourceType(activity.getResourceType())
                .resourceId(activity.getResourceId())
                .resourceName(activity.getResourceName())
                .metadata(activity.getMetadata())
                .changes(activity.getChanges())
                .createdAt(activity.getCreatedAt())
                .build();
    }
}
