package com.teamsync.team.service;

import com.teamsync.common.context.TenantContext;
import com.teamsync.common.exception.AccessDeniedException;
import com.teamsync.common.exception.ResourceNotFoundException;
import com.teamsync.team.dto.CreateTeamRoleRequest;
import com.teamsync.team.dto.TeamRoleDTO;
import com.teamsync.team.dto.UpdateTeamRoleRequest;
import com.teamsync.team.model.Team;
import com.teamsync.team.model.TeamPermission;
import com.teamsync.team.model.TeamRole;
import com.teamsync.team.repository.TeamRepository;
import com.teamsync.team.repository.TeamRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for managing team roles and permissions.
 *
 * Responsibilities:
 * - System role initialization
 * - Custom role CRUD
 * - Permission management
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TeamRoleService {

    private final TeamRoleRepository roleRepository;
    private final TeamRepository teamRepository;

    // ============== SYSTEM ROLES ==============

    /**
     * Ensure system roles exist for a tenant.
     * Called when first team is created in a tenant.
     */
    @Transactional
    public void ensureSystemRoles(String tenantId) {
        List<TeamRole> existingRoles = roleRepository.findByTenantIdAndIsSystemRoleTrue(tenantId);

        if (existingRoles.isEmpty()) {
            log.info("Creating system roles for tenant: {}", tenantId);

            List<TeamRole> systemRoles = List.of(
                    TeamRole.createOwnerRole(tenantId),
                    TeamRole.createAdminRole(tenantId),
                    TeamRole.createManagerRole(tenantId),
                    TeamRole.createMemberRole(tenantId),
                    TeamRole.createGuestRole(tenantId),
                    TeamRole.createExternalRole(tenantId)
            );

            roleRepository.saveAll(systemRoles);
            log.info("Created {} system roles for tenant: {}", systemRoles.size(), tenantId);
        }
    }

    /**
     * Get all system roles for a tenant.
     */
    public List<TeamRoleDTO> getSystemRoles() {
        String tenantId = TenantContext.getTenantId();

        return roleRepository.findSystemRoles(tenantId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    // ============== ROLE QUERIES ==============

    /**
     * Get all roles available for a team (system + custom).
     */
    public List<TeamRoleDTO> getRolesForTeam(String teamId) {
        String tenantId = TenantContext.getTenantId();

        return roleRepository.findRolesForTeam(tenantId, teamId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get a specific role.
     */
    public TeamRoleDTO getRole(String roleId) {
        String tenantId = TenantContext.getTenantId();

        TeamRole role = roleRepository.findByIdAndTenantId(roleId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleId));

        return mapToDTO(role);
    }

    /**
     * Get the default role for new members.
     */
    public TeamRole getDefaultRole(String teamId) {
        String tenantId = TenantContext.getTenantId();

        // Check for team-specific default first
        List<TeamRole> defaults = roleRepository.findDefaultRoles(tenantId, teamId);

        // Prefer team-specific default over system default
        return defaults.stream()
                .filter(r -> teamId.equals(r.getTeamId()))
                .findFirst()
                .orElseGet(() -> defaults.stream()
                        .filter(r -> r.getTeamId() == null)
                        .findFirst()
                        .orElseGet(() -> roleRepository.findSystemRoleById(tenantId, TeamRole.ROLE_ID_MEMBER)
                                .orElseThrow(() -> new IllegalStateException("Member role not found"))));
    }

    // ============== CUSTOM ROLE CRUD ==============

    /**
     * Create a custom role for a team.
     */
    @Transactional
    public TeamRoleDTO createRole(String teamId, CreateTeamRoleRequest request) {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();

        // Verify team exists and user has permission
        Team team = teamRepository.findByIdAndTenantId(teamId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));

        verifyRoleManagePermission(team, userId);

        // Check for duplicate name
        if (roleRepository.existsByTenantIdAndTeamIdAndName(tenantId, teamId, request.getName())) {
            throw new IllegalArgumentException("Role name already exists: " + request.getName());
        }

        // Validate permissions - no permissions that user doesn't have
        Set<TeamPermission> userPermissions = getUserPermissions(team, userId);
        for (TeamPermission perm : request.getPermissions()) {
            if (!userPermissions.contains(perm)) {
                throw new AccessDeniedException("Cannot grant permission you don't have: " + perm);
            }
        }

        TeamRole role = TeamRole.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .name(request.getName())
                .description(request.getDescription())
                .color(request.getColor())
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 50)
                .permissions(request.getPermissions())
                .isSystemRole(false)
                .isDefault(false)
                .isExternalOnly(false)
                .createdBy(userId)
                .createdAt(Instant.now())
                .build();

        role = roleRepository.save(role);

        // Add to team's custom role list
        if (team.getCustomRoleIds() == null) {
            team.setCustomRoleIds(new java.util.ArrayList<>());
        }
        team.getCustomRoleIds().add(role.getId());
        teamRepository.save(team);

        log.info("Created custom role: {} in team: {}", role.getName(), teamId);

        return mapToDTO(role);
    }

    /**
     * Update a custom role.
     */
    @Transactional
    public TeamRoleDTO updateRole(String teamId, String roleId, UpdateTeamRoleRequest request) {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();

        // Verify team exists and user has permission
        Team team = teamRepository.findByIdAndTenantId(teamId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));

        verifyRoleManagePermission(team, userId);

        // Get role
        TeamRole role = roleRepository.findByIdAndTenantId(roleId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleId));

        // Cannot modify system roles
        if (role.isSystem()) {
            throw new IllegalArgumentException("Cannot modify system roles");
        }

        // Must belong to this team
        if (!teamId.equals(role.getTeamId())) {
            throw new AccessDeniedException("Role does not belong to this team");
        }

        // Update fields
        if (request.getName() != null && !request.getName().equals(role.getName())) {
            if (roleRepository.existsByTenantIdAndTeamIdAndName(tenantId, teamId, request.getName())) {
                throw new IllegalArgumentException("Role name already exists: " + request.getName());
            }
            role.setName(request.getName());
        }

        if (request.getDescription() != null) {
            role.setDescription(request.getDescription());
        }
        if (request.getColor() != null) {
            role.setColor(request.getColor());
        }
        if (request.getDisplayOrder() != null) {
            role.setDisplayOrder(request.getDisplayOrder());
        }

        if (request.getPermissions() != null) {
            // Validate permissions
            Set<TeamPermission> userPermissions = getUserPermissions(team, userId);
            for (TeamPermission perm : request.getPermissions()) {
                if (!userPermissions.contains(perm)) {
                    throw new AccessDeniedException("Cannot grant permission you don't have: " + perm);
                }
            }
            role.setPermissions(request.getPermissions());
        }

        role.setUpdatedAt(Instant.now());
        role = roleRepository.save(role);

        // TODO: Update denormalized permissions in team members

        log.info("Updated role: {} in team: {}", role.getName(), teamId);

        return mapToDTO(role);
    }

    /**
     * Delete a custom role.
     */
    @Transactional
    public void deleteRole(String teamId, String roleId) {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();

        // Verify team exists and user has permission
        Team team = teamRepository.findByIdAndTenantId(teamId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));

        verifyRoleManagePermission(team, userId);

        // Get role
        TeamRole role = roleRepository.findByIdAndTenantId(roleId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleId));

        // Cannot delete system roles
        if (role.isSystem()) {
            throw new IllegalArgumentException("Cannot delete system roles");
        }

        // Must belong to this team
        if (!teamId.equals(role.getTeamId())) {
            throw new AccessDeniedException("Role does not belong to this team");
        }

        // Check if any members have this role
        boolean hasMembers = team.getMembers().stream()
                .anyMatch(m -> roleId.equals(m.getRoleId()) && m.getStatus() == Team.MemberStatus.ACTIVE);

        if (hasMembers) {
            throw new IllegalArgumentException("Cannot delete role that has members assigned");
        }

        // Remove from team's custom role list
        if (team.getCustomRoleIds() != null) {
            team.getCustomRoleIds().remove(roleId);
            teamRepository.save(team);
        }

        roleRepository.delete(role);

        log.info("Deleted role: {} from team: {}", role.getName(), teamId);
    }

    // ============== HELPER METHODS ==============

    private void verifyRoleManagePermission(Team team, String userId) {
        Team.TeamMember member = team.getMembers().stream()
                .filter(m -> m.getUserId().equals(userId) && m.getStatus() == Team.MemberStatus.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("Not a team member"));

        if (!member.getPermissions().contains(TeamPermission.ROLE_CREATE.name()) &&
            !member.getPermissions().contains(TeamPermission.ROLE_EDIT.name())) {
            throw new AccessDeniedException("Permission denied: ROLE_EDIT");
        }
    }

    private Set<TeamPermission> getUserPermissions(Team team, String userId) {
        return team.getMembers().stream()
                .filter(m -> m.getUserId().equals(userId) && m.getStatus() == Team.MemberStatus.ACTIVE)
                .findFirst()
                .map(m -> m.getPermissions().stream()
                        .map(TeamPermission::valueOf)
                        .collect(Collectors.toSet()))
                .orElse(Set.of());
    }

    private TeamRoleDTO mapToDTO(TeamRole role) {
        return TeamRoleDTO.builder()
                .id(role.getId())
                .tenantId(role.getTenantId())
                .teamId(role.getTeamId())
                .name(role.getName())
                .description(role.getDescription())
                .color(role.getColor())
                .displayOrder(role.getDisplayOrder())
                .permissions(role.getPermissions())
                .isSystemRole(role.getIsSystemRole())
                .isDefault(role.getIsDefault())
                .isExternalOnly(role.getIsExternalOnly())
                .createdAt(role.getCreatedAt())
                .updatedAt(role.getUpdatedAt())
                .build();
    }
}
