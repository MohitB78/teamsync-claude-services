package com.teamsync.team.dto;

import com.teamsync.team.model.TeamPermission;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;

/**
 * Data Transfer Object for TeamRole entity.
 * Used for API responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamRoleDTO {

    private String id;
    private String tenantId;
    private String teamId;

    private String name;
    private String description;
    private String color;
    private Integer displayOrder;

    private Set<TeamPermission> permissions;

    private Boolean isSystemRole;
    private Boolean isDefault;
    private Boolean isExternalOnly;

    private Instant createdAt;
    private Instant updatedAt;
}
