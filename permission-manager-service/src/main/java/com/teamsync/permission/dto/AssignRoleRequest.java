package com.teamsync.permission.dto;

import com.teamsync.permission.model.DriveAssignment.AssignmentSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Request to assign a role to a user for a drive.
 *
 * SECURITY FIX (Round 15 #M18): Added @Size constraints to prevent DoS attacks.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignRoleRequest {

    @NotBlank(message = "User ID is required")
    @Size(max = 64, message = "User ID must not exceed 64 characters")
    private String userId;

    @NotBlank(message = "Drive ID is required")
    @Size(max = 64, message = "Drive ID must not exceed 64 characters")
    private String driveId;

    @NotBlank(message = "Role ID is required")
    @Size(max = 64, message = "Role ID must not exceed 64 characters")
    private String roleId;

    /**
     * How the assignment is being created
     */
    @Builder.Default
    private AssignmentSource source = AssignmentSource.DIRECT;

    /**
     * Department ID if source is DEPARTMENT
     */
    @Size(max = 64, message = "Department ID must not exceed 64 characters")
    private String departmentId;

    /**
     * Team ID if source is TEAM
     */
    @Size(max = 64, message = "Team ID must not exceed 64 characters")
    private String teamId;

    /**
     * Optional expiration date
     */
    private Instant expiresAt;
}
