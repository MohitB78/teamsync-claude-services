package com.teamsync.team.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating a team member's role.
 * External members cannot be assigned roles other than EXTERNAL.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMemberRoleRequest {

    @NotBlank(message = "Role ID is required")
    private String roleId;
}
