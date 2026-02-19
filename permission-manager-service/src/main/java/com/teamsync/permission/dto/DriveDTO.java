package com.teamsync.permission.dto;

import com.teamsync.common.model.DriveType;
import com.teamsync.permission.model.Drive.DriveSettings;
import com.teamsync.permission.model.Drive.DriveStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO for drive responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriveDTO {

    private String id;
    private String tenantId;
    private String name;
    private String description;
    private DriveType type;
    private String ownerId;
    private String departmentId;
    private Long quotaBytes;
    private Long usedBytes;
    private Double usagePercent;
    private String defaultRoleId;
    private DriveStatus status;
    private DriveSettings settings;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;

    /**
     * User count (populated when needed)
     */
    private Long userCount;
}
