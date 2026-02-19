package com.teamsync.permission.dto;

import com.teamsync.common.model.Permission;
import com.teamsync.permission.model.DriveAssignment.AssignmentSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;

/**
 * DTO for drive assignment responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriveAssignmentDTO {

    private String id;
    private String tenantId;
    private String driveId;
    private String userId;
    private String roleId;
    private String roleName;
    private Set<Permission> permissions;
    private AssignmentSource source;
    private String assignedViaDepartment;
    private String assignedViaTeam;
    private String grantedBy;
    private Instant grantedAt;
    private Instant expiresAt;
    private Boolean isActive;
}
