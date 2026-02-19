package com.teamsync.team.dto;

import com.teamsync.team.model.TeamPermission;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Request DTO for updating an existing team role.
 * All fields are optional - only non-null fields are updated.
 * System roles cannot be modified.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTeamRoleRequest {

    @Size(min = 2, max = 50, message = "Role name must be between 2 and 50 characters")
    private String name;

    @Size(max = 200, message = "Description cannot exceed 200 characters")
    private String description;

    /**
     * Color for UI display (hex format).
     */
    private String color;

    /**
     * Sort order for display in role lists.
     */
    private Integer displayOrder;

    /**
     * Updated permissions for this role.
     * User can only grant permissions they themselves have.
     */
    private Set<TeamPermission> permissions;
}
