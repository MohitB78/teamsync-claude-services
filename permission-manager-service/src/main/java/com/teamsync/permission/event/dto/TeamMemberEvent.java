package com.teamsync.permission.event.dto;

import com.teamsync.common.model.Permission;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;

/**
 * SECURITY FIX: Typed DTO for team member added/removed Kafka events.
 * Replaces unsafe Map<String, Object> deserialization to prevent injection attacks.
 *
 * Published by Team Service when a member is added to or removed from a team.
 * Consumed by Permission Manager to grant/revoke team drive access.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamMemberEvent {

    @NotBlank(message = "Event ID is required")
    private String eventId;

    @NotBlank(message = "Tenant ID is required")
    private String tenantId;

    @NotBlank(message = "Team ID is required")
    private String teamId;

    @NotBlank(message = "User ID is required")
    private String userId;

    /**
     * The role ID assigned to the member.
     * Used to look up permissions for drive access.
     */
    private String roleId;

    /**
     * Denormalized role name for logging.
     */
    private String roleName;

    /**
     * The permissions granted by the role.
     * Mapped to drive permissions for team drive access.
     */
    private Set<Permission> permissions;

    /**
     * Whether this is an external user.
     * External users have restricted permissions.
     */
    private Boolean isExternal;

    private Instant timestamp;
}
