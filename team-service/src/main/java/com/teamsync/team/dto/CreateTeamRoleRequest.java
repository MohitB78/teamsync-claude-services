package com.teamsync.team.dto;

import com.teamsync.team.model.TeamPermission;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Request DTO for creating a custom team role.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTeamRoleRequest {

    @NotBlank(message = "Role name is required")
    @Size(min = 2, max = 50, message = "Role name must be between 2 and 50 characters")
    private String name;

    @Size(max = 200, message = "Description cannot exceed 200 characters")
    private String description;

    /**
     * Color for UI display (hex format, e.g., "#3B82F6").
     */
    private String color;

    /**
     * Sort order for display in role lists.
     * Lower numbers appear first.
     */
    private Integer displayOrder;

    /**
     * Permissions granted to this role.
     * User can only grant permissions they themselves have.
     */
    @NotEmpty(message = "At least one permission is required")
    private Set<TeamPermission> permissions;
}
