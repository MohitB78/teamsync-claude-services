package com.teamsync.common.permission;

import com.teamsync.common.model.Permission;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for permission checks.
 * Used by services to check if a user has permission on a drive.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionCheckRequest {

    private String userId;
    private String driveId;
    private Permission requiredPermission;
    private List<String> teamIds;
    private String departmentId;
}
