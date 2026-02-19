package com.teamsync.team.service;

import com.teamsync.common.context.TenantContext;
import com.teamsync.common.exception.AccessDeniedException;
import com.teamsync.common.exception.ResourceNotFoundException;
import com.teamsync.team.dto.TeamMemberDTO;
import com.teamsync.team.dto.UpdateMemberRoleRequest;
import com.teamsync.team.model.Team;
import com.teamsync.team.model.TeamPermission;
import com.teamsync.team.model.TeamRole;
import com.teamsync.team.repository.TeamRepository;
import com.teamsync.team.repository.TeamRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for managing team members.
 *
 * Responsibilities:
 * - Member listing and filtering
 * - Role assignment
 * - Member removal
 * - Permission checks
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TeamMemberService {

    private final TeamRepository teamRepository;
    private final TeamRoleRepository roleRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // ============== MEMBER QUERIES ==============

    /**
     * Get all members of a team.
     */
    public List<TeamMemberDTO> getMembers(String teamId) {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();

        Team team = teamRepository.findByIdAndTenantId(teamId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));

        verifyMemberViewPermission(team, userId);

        return team.getMembers().stream()
                .filter(m -> m.getStatus() == Team.MemberStatus.ACTIVE)
                .map(m -> mapToDTO(m, team))
                .collect(Collectors.toList());
    }

    /**
     * Get a specific member's details.
     */
    public TeamMemberDTO getMember(String teamId, String memberId) {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();

        Team team = teamRepository.findByIdAndTenantId(teamId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));

        verifyMemberViewPermission(team, userId);

        Team.TeamMember member = team.getMembers().stream()
                .filter(m -> m.getUserId().equals(memberId) && m.getStatus() == Team.MemberStatus.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Member not found: " + memberId));

        return mapToDTO(member, team);
    }

    // ============== ROLE ASSIGNMENT ==============

    /**
     * Update a member's role.
     * External members cannot be assigned roles other than EXTERNAL.
     */
    @Transactional
    public TeamMemberDTO updateMemberRole(String teamId, String memberId, UpdateMemberRoleRequest request) {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();

        Team team = teamRepository.findByIdAndTenantId(teamId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));

        verifyRoleAssignPermission(team, userId);

        // Find member
        Team.TeamMember member = team.getMembers().stream()
                .filter(m -> m.getUserId().equals(memberId) && m.getStatus() == Team.MemberStatus.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Member not found: " + memberId));

        // Get the new role
        TeamRole newRole = roleRepository.findByIdAndTenantId(request.getRoleId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + request.getRoleId()));

        // External members can only have EXTERNAL role
        if (member.getMemberType() == Team.MemberType.EXTERNAL) {
            if (!newRole.getIsExternalOnly()) {
                throw new IllegalArgumentException("External members can only be assigned the EXTERNAL role");
            }
        }

        // Cannot assign external-only role to internal members
        if (member.getMemberType() == Team.MemberType.INTERNAL && newRole.getIsExternalOnly()) {
            throw new IllegalArgumentException("Cannot assign external-only role to internal members");
        }

        // Cannot demote owner (unless transferring ownership)
        if (team.getOwnerId().equals(memberId) && !TeamRole.ROLE_ID_OWNER.equals(newRole.getName())) {
            throw new IllegalArgumentException("Cannot change owner's role. Transfer ownership first.");
        }

        // Update member
        String oldRoleId = member.getRoleId();
        member.setRoleId(newRole.getId());
        member.setRoleName(newRole.getName());
        member.setPermissions(newRole.getPermissions().stream()
                .map(TeamPermission::name)
                .collect(Collectors.toSet()));

        team.setUpdatedAt(Instant.now());
        teamRepository.save(team);

        // Publish event for permission manager
        publishMemberRoleChanged(team, member, oldRoleId, newRole);

        log.info("Updated role for member {} in team {} from {} to {}",
                memberId, teamId, oldRoleId, newRole.getName());

        return mapToDTO(member, team);
    }

    // ============== MEMBER REMOVAL ==============

    /**
     * Remove a member from the team.
     */
    @Transactional
    public void removeMember(String teamId, String memberId) {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();

        Team team = teamRepository.findByIdAndTenantId(teamId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));

        verifyMemberRemovePermission(team, userId);

        // Find member
        Team.TeamMember member = team.getMembers().stream()
                .filter(m -> m.getUserId().equals(memberId) && m.getStatus() == Team.MemberStatus.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Member not found: " + memberId));

        // Cannot remove owner
        if (team.getOwnerId().equals(memberId)) {
            throw new IllegalArgumentException("Cannot remove team owner. Transfer ownership first.");
        }

        // Mark as removed (soft delete)
        member.setStatus(Team.MemberStatus.REMOVED);
        team.setMemberCount(team.getMemberCount() - 1);
        team.setUpdatedAt(Instant.now());
        teamRepository.save(team);

        // Publish event for permission manager to revoke access
        publishMemberRemoved(team, member);

        log.info("Removed member {} from team {}", memberId, teamId);
    }

    /**
     * Leave a team (self-removal).
     */
    @Transactional
    public void leaveTeam(String teamId) {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();

        Team team = teamRepository.findByIdAndTenantId(teamId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));

        // Owners cannot leave - must transfer ownership first
        if (team.getOwnerId().equals(userId)) {
            throw new IllegalArgumentException("Team owner cannot leave. Transfer ownership first.");
        }

        // Find member
        Team.TeamMember member = team.getMembers().stream()
                .filter(m -> m.getUserId().equals(userId) && m.getStatus() == Team.MemberStatus.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("Not a team member"));

        // Mark as removed
        member.setStatus(Team.MemberStatus.REMOVED);
        team.setMemberCount(team.getMemberCount() - 1);
        team.setUpdatedAt(Instant.now());
        teamRepository.save(team);

        // Publish event for permission manager to revoke access
        publishMemberRemoved(team, member);

        log.info("User {} left team {}", userId, teamId);
    }

    // ============== HELPER METHODS ==============

    private void verifyMemberViewPermission(Team team, String userId) {
        Team.TeamMember member = team.getMembers().stream()
                .filter(m -> m.getUserId().equals(userId) && m.getStatus() == Team.MemberStatus.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("Not a team member"));

        if (!member.getPermissions().contains(TeamPermission.MEMBER_VIEW.name())) {
            throw new AccessDeniedException("Permission denied: MEMBER_VIEW");
        }
    }

    private void verifyRoleAssignPermission(Team team, String userId) {
        Team.TeamMember member = team.getMembers().stream()
                .filter(m -> m.getUserId().equals(userId) && m.getStatus() == Team.MemberStatus.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("Not a team member"));

        if (!member.getPermissions().contains(TeamPermission.MEMBER_ROLE_ASSIGN.name())) {
            throw new AccessDeniedException("Permission denied: MEMBER_ROLE_ASSIGN");
        }
    }

    private void verifyMemberRemovePermission(Team team, String userId) {
        Team.TeamMember member = team.getMembers().stream()
                .filter(m -> m.getUserId().equals(userId) && m.getStatus() == Team.MemberStatus.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("Not a team member"));

        if (!member.getPermissions().contains(TeamPermission.MEMBER_REMOVE.name())) {
            throw new AccessDeniedException("Permission denied: MEMBER_REMOVE");
        }
    }

    private TeamMemberDTO mapToDTO(Team.TeamMember member, Team team) {
        return TeamMemberDTO.builder()
                .userId(member.getUserId())
                .email(member.getEmail())
                .displayName(member.getEmail()) // Use email as display name
                .avatar(null) // Avatar not stored on TeamMember
                .memberType(member.getMemberType())
                .status(member.getStatus())
                .roleId(member.getRoleId())
                .roleName(member.getRoleName())
                .permissions(member.getPermissions())
                .joinedAt(member.getJoinedAt())
                .invitedBy(member.getInvitedBy())
                .lastActiveAt(member.getLastActiveAt())
                .isOwner(team.getOwnerId().equals(member.getUserId()))
                .build();
    }

    private void publishMemberRoleChanged(Team team, Team.TeamMember member, String oldRoleId, TeamRole newRole) {
        // The permission manager will update drive assignments
        kafkaTemplate.send("teamsync.teams.member_role_changed", team.getId(),
                java.util.Map.of(
                        "tenantId", team.getTenantId(),
                        "teamId", team.getId(),
                        "userId", member.getUserId(),
                        "oldRoleId", oldRoleId,
                        "newRoleId", newRole.getId(),
                        "newRoleName", newRole.getName(),
                        "permissions", newRole.getPermissions().stream()
                                .map(TeamPermission::name)
                                .collect(Collectors.toSet())
                ));
    }

    private void publishMemberRemoved(Team team, Team.TeamMember member) {
        kafkaTemplate.send("teamsync.teams.member_removed", team.getId(),
                java.util.Map.of(
                        "tenantId", team.getTenantId(),
                        "teamId", team.getId(),
                        "userId", member.getUserId(),
                        "email", member.getEmail(),
                        "memberType", member.getMemberType().name()
                ));
    }
}
