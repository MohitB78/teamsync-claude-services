package com.teamsync.permission.dto;

import com.teamsync.common.model.Permission;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;

/**
 * DTO for drive role responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriveRoleDTO {

    private String id;
    private String tenantId;
    private String driveId;
    private String name;
    private String description;
    private Set<Permission> permissions;
    private Boolean isSystemRole;
    private Integer priority;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;

    /**
     * Number of users assigned to this role (populated when needed)
     */
    private Long assignmentCount;
}
