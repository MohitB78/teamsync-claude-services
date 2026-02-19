package com.teamsync.permission.dto;

import com.teamsync.common.model.Permission;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request to check if a user has a specific permission on a drive.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionCheckRequest {

    @NotBlank(message = "User ID is required")
    private String userId;

    @NotBlank(message = "Drive ID is required")
    private String driveId;

    /**
     * Optional: specific permission to check.
     * If null, returns all permissions the user has.
     */
    private Permission requiredPermission;

    /**
     * Optional: user's team memberships for team-based access
     */
    private List<String> teamIds;

    /**
     * Optional: user's department for department-based access
     */
    private String departmentId;
}
