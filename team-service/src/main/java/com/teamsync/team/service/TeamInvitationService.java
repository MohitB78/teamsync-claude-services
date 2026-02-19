package com.teamsync.team.service;

import com.teamsync.common.context.TenantContext;
import com.teamsync.common.exception.AccessDeniedException;
import com.teamsync.common.exception.ResourceNotFoundException;
import com.teamsync.team.dto.CreateInvitationRequest;
import com.teamsync.team.dto.TeamInvitationDTO;
import com.teamsync.team.model.Team;
import com.teamsync.team.model.TeamInvitation;
import com.teamsync.team.model.TeamPermission;
import com.teamsync.team.model.TeamRole;
import com.teamsync.team.repository.TeamInvitationRepository;
import com.teamsync.team.repository.TeamRepository;
import com.teamsync.team.repository.TeamRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for managing team invitations.
 *
 * Responsibilities:
 * - Invitation creation and sending
 * - Invitation acceptance and decline
 * - Invitation lifecycle management
 * - External user portal token generation
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TeamInvitationService {

    private final TeamInvitationRepository invitationRepository;
    private final TeamRepository teamRepository;
    private final TeamRoleRepository roleRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final int INVITATION_EXPIRY_DAYS = 7;
    private static final int MAX_RESEND_COUNT = 3;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // ============== INVITATION QUERIES ==============

    /**
     * Get all pending invitations for a team.
     */
    public List<TeamInvitationDTO> getPendingInvitations(String teamId) {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();

        Team team = teamRepository.findByIdAndTenantId(teamId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));

        verifyMemberInvitePermission(team, userId);

        return invitationRepository.findByTenantIdAndTeamIdAndStatus(tenantId, teamId, TeamInvitation.InvitationStatus.PENDING)
                .stream()
                .map(inv -> mapToDTO(inv, team.getName()))
                .collect(Collectors.toList());
    }

    /**
     * Get invitation by token (for acceptance page).
     * Does not require authentication.
     */
    public TeamInvitationDTO getInvitationByToken(String token) {
        String tokenHash = hashToken(token);

        TeamInvitation invitation = invitationRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid or expired invitation"));

        if (invitation.getStatus() != TeamInvitation.InvitationStatus.PENDING) {
            throw new IllegalStateException("Invitation is no longer valid");
        }

        if (Instant.now().isAfter(invitation.getExpiresAt())) {
            invitation.setStatus(TeamInvitation.InvitationStatus.EXPIRED);
            invitationRepository.save(invitation);
            throw new IllegalStateException("Invitation has expired");
        }

        Team team = teamRepository.findById(invitation.getTeamId()).orElse(null);
        String teamName = team != null ? team.getName() : "Unknown Team";

        return mapToDTO(invitation, teamName);
    }

    // ============== INVITATION CREATION ==============

    /**
     * Create and send a team invitation.
     */
    @Transactional
    public TeamInvitationDTO createInvitation(String teamId, CreateInvitationRequest request) {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();

        Team team = teamRepository.findByIdAndTenantId(teamId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));

        // Check permissions
        if (request.getInviteeType() == TeamInvitation.InviteeType.EXTERNAL) {
            verifyExternalInvitePermission(team, userId);

            // Verify team allows external members
            if (!team.getAllowExternalMembers()) {
                throw new IllegalArgumentException("This team does not allow external members");
            }
        } else {
            verifyMemberInvitePermission(team, userId);
        }

        // Check for existing active invitation
        invitationRepository.findByTenantIdAndTeamIdAndEmailAndStatus(
                tenantId, teamId, request.getEmail(), TeamInvitation.InvitationStatus.PENDING
        ).ifPresent(existing -> {
            throw new IllegalArgumentException("An invitation is already pending for this email");
        });

        // Check if already a member
        boolean alreadyMember = team.getMembers().stream()
                .anyMatch(m -> m.getEmail().equalsIgnoreCase(request.getEmail()) &&
                               m.getStatus() == Team.MemberStatus.ACTIVE);
        if (alreadyMember) {
            throw new IllegalArgumentException("User is already a team member");
        }

        // Determine role (system roles have ID = constant like "EXTERNAL", "MEMBER")
        String roleId;
        String roleName;
        if (request.getInviteeType() == TeamInvitation.InviteeType.EXTERNAL) {
            // External users always get EXTERNAL role
            TeamRole externalRole = roleRepository.findSystemRoleById(tenantId, TeamRole.ROLE_ID_EXTERNAL)
                    .orElseThrow(() -> new IllegalStateException("External role not found"));
            roleId = externalRole.getId();
            roleName = externalRole.getName();
        } else if (request.getRoleId() != null) {
            TeamRole role = roleRepository.findByIdAndTenantId(request.getRoleId(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + request.getRoleId()));

            if (role.getIsExternalOnly()) {
                throw new IllegalArgumentException("Cannot assign external-only role to internal members");
            }

            roleId = role.getId();
            roleName = role.getName();
        } else {
            // Use team's default role
            TeamRole defaultRole = roleRepository.findSystemRoleById(tenantId, TeamRole.ROLE_ID_MEMBER)
                    .orElseThrow(() -> new IllegalStateException("Member role not found"));
            roleId = defaultRole.getId();
            roleName = defaultRole.getName();
        }

        // Generate secure token
        String token = generateToken();
        String tokenHash = hashToken(token);

        TeamInvitation invitation = TeamInvitation.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .email(request.getEmail())
                .inviteeType(request.getInviteeType())
                .roleId(roleId)
                .roleName(roleName)
                .token(null) // Don't store plain token
                .tokenHash(tokenHash)
                .status(TeamInvitation.InvitationStatus.PENDING)
                .invitedById(userId)
                .expiresAt(Instant.now().plus(INVITATION_EXPIRY_DAYS, ChronoUnit.DAYS))
                .resendCount(0)
                .createdAt(Instant.now())
                .build();

        invitation = invitationRepository.save(invitation);

        // Publish event to send invitation email
        publishInvitationCreated(invitation, team.getName(), token, request.getMessage());

        log.info("Created invitation for {} to team {} as {}",
                request.getEmail(), teamId, request.getInviteeType());

        TeamInvitationDTO dto = mapToDTO(invitation, team.getName());
        // Include token in response for caller to send (e.g., in email)
        return dto;
    }

    // ============== INVITATION ACCEPTANCE ==============

    /**
     * Accept an invitation using the token.
     */
    @Transactional
    public void acceptInvitation(String token, String acceptingUserId) {
        String tokenHash = hashToken(token);

        TeamInvitation invitation = invitationRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid invitation token"));

        if (invitation.getStatus() != TeamInvitation.InvitationStatus.PENDING) {
            throw new IllegalStateException("Invitation is no longer valid");
        }

        if (Instant.now().isAfter(invitation.getExpiresAt())) {
            invitation.setStatus(TeamInvitation.InvitationStatus.EXPIRED);
            invitationRepository.save(invitation);
            throw new IllegalStateException("Invitation has expired");
        }

        Team team = teamRepository.findById(invitation.getTeamId())
                .orElseThrow(() -> new ResourceNotFoundException("Team no longer exists"));

        // Get role details
        TeamRole role = roleRepository.findById(invitation.getRoleId())
                .orElseThrow(() -> new IllegalStateException("Role no longer exists"));

        // Add member to team
        Team.TeamMember newMember = Team.TeamMember.builder()
                .userId(acceptingUserId)
                .email(invitation.getEmail())
                .memberType(invitation.getInviteeType() == TeamInvitation.InviteeType.EXTERNAL
                        ? Team.MemberType.EXTERNAL : Team.MemberType.INTERNAL)
                .roleId(role.getId())
                .roleName(role.getName())
                .permissions(role.getPermissions().stream()
                        .map(TeamPermission::name)
                        .collect(Collectors.toSet()))
                .joinedAt(Instant.now())
                .invitedBy(invitation.getInvitedById())
                .status(Team.MemberStatus.ACTIVE)
                .build();

        team.getMembers().add(newMember);
        team.setMemberCount(team.getMemberCount() + 1);
        team.setUpdatedAt(Instant.now());
        teamRepository.save(team);

        // Update invitation status
        invitation.setStatus(TeamInvitation.InvitationStatus.ACCEPTED);
        invitation.setRespondedAt(Instant.now());
        invitationRepository.save(invitation);

        // Publish event for permission manager to grant access
        publishMemberAdded(team, newMember, role);

        log.info("Invitation accepted: {} joined team {} as {}",
                invitation.getEmail(), team.getId(), role.getName());
    }

    /**
     * Decline an invitation.
     */
    @Transactional
    public void declineInvitation(String token) {
        String tokenHash = hashToken(token);

        TeamInvitation invitation = invitationRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid invitation token"));

        if (invitation.getStatus() != TeamInvitation.InvitationStatus.PENDING) {
            throw new IllegalStateException("Invitation is no longer valid");
        }

        invitation.setStatus(TeamInvitation.InvitationStatus.DECLINED);
        invitation.setRespondedAt(Instant.now());
        invitationRepository.save(invitation);

        log.info("Invitation declined: {} for team {}",
                invitation.getEmail(), invitation.getTeamId());
    }

    // ============== INVITATION MANAGEMENT ==============

    /**
     * Cancel a pending invitation.
     */
    @Transactional
    public void cancelInvitation(String teamId, String invitationId) {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();

        Team team = teamRepository.findByIdAndTenantId(teamId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));

        verifyMemberInvitePermission(team, userId);

        TeamInvitation invitation = invitationRepository.findByIdAndTenantIdAndTeamId(invitationId, tenantId, teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found"));

        if (invitation.getStatus() != TeamInvitation.InvitationStatus.PENDING) {
            throw new IllegalStateException("Can only cancel pending invitations");
        }

        invitation.setStatus(TeamInvitation.InvitationStatus.CANCELLED);
        invitationRepository.save(invitation);

        log.info("Invitation cancelled: {} for team {}", invitation.getEmail(), teamId);
    }

    /**
     * Resend an invitation email.
     */
    @Transactional
    public void resendInvitation(String teamId, String invitationId) {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();

        Team team = teamRepository.findByIdAndTenantId(teamId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));

        verifyMemberInvitePermission(team, userId);

        TeamInvitation invitation = invitationRepository.findByIdAndTenantIdAndTeamId(invitationId, tenantId, teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found"));

        if (invitation.getStatus() != TeamInvitation.InvitationStatus.PENDING) {
            throw new IllegalStateException("Can only resend pending invitations");
        }

        if (invitation.getResendCount() >= MAX_RESEND_COUNT) {
            throw new IllegalStateException("Maximum resend limit reached");
        }

        // Generate new token
        String newToken = generateToken();
        invitation.setTokenHash(hashToken(newToken));
        invitation.setExpiresAt(Instant.now().plus(INVITATION_EXPIRY_DAYS, ChronoUnit.DAYS));
        invitation.setResendCount(invitation.getResendCount() + 1);
        invitationRepository.save(invitation);

        // Publish event to resend email
        publishInvitationCreated(invitation, team.getName(), newToken, null);

        log.info("Resent invitation {} to {}", invitationId, invitation.getEmail());
    }

    // ============== HELPER METHODS ==============

    private void verifyMemberInvitePermission(Team team, String userId) {
        Team.TeamMember member = team.getMembers().stream()
                .filter(m -> m.getUserId().equals(userId) && m.getStatus() == Team.MemberStatus.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("Not a team member"));

        if (!member.getPermissions().contains(TeamPermission.MEMBER_INVITE.name())) {
            throw new AccessDeniedException("Permission denied: MEMBER_INVITE");
        }
    }

    private void verifyExternalInvitePermission(Team team, String userId) {
        Team.TeamMember member = team.getMembers().stream()
                .filter(m -> m.getUserId().equals(userId) && m.getStatus() == Team.MemberStatus.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("Not a team member"));

        if (!member.getPermissions().contains(TeamPermission.MEMBER_INVITE_EXTERNAL.name())) {
            throw new AccessDeniedException("Permission denied: MEMBER_INVITE_EXTERNAL");
        }
    }

    private String generateToken() {
        byte[] tokenBytes = new byte[32];
        SECURE_RANDOM.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private TeamInvitationDTO mapToDTO(TeamInvitation invitation, String teamName) {
        return TeamInvitationDTO.builder()
                .id(invitation.getId())
                .tenantId(invitation.getTenantId())
                .teamId(invitation.getTeamId())
                .teamName(teamName)
                .email(invitation.getEmail())
                .inviteeType(invitation.getInviteeType())
                .roleId(invitation.getRoleId())
                .roleName(invitation.getRoleName())
                .status(invitation.getStatus())
                .invitedById(invitation.getInvitedById())
                .expiresAt(invitation.getExpiresAt())
                .resendCount(invitation.getResendCount())
                .createdAt(invitation.getCreatedAt())
                .respondedAt(invitation.getRespondedAt())
                .build();
    }

    private void publishInvitationCreated(TeamInvitation invitation, String teamName, String token, String message) {
        kafkaTemplate.send("teamsync.teams.invitation_sent", invitation.getId(), Map.of(
                "tenantId", invitation.getTenantId(),
                "teamId", invitation.getTeamId(),
                "teamName", teamName,
                "invitationId", invitation.getId(),
                "email", invitation.getEmail(),
                "inviteeType", invitation.getInviteeType().name(),
                "roleName", invitation.getRoleName(),
                "token", token,
                "expiresAt", invitation.getExpiresAt().toString(),
                "message", message != null ? message : ""
        ));
    }

    private void publishMemberAdded(Team team, Team.TeamMember member, TeamRole role) {
        Set<String> permissionNames = role.getPermissions().stream()
                .map(TeamPermission::name)
                .collect(Collectors.toSet());

        kafkaTemplate.send("teamsync.teams.member_added", team.getId(), Map.of(
                "tenantId", team.getTenantId(),
                "teamId", team.getId(),
                "userId", member.getUserId(),
                "email", member.getEmail(),
                "memberType", member.getMemberType().name(),
                "roleId", role.getId(),
                "roleName", role.getName(),
                "permissions", permissionNames,
                "isExternal", member.getMemberType() == Team.MemberType.EXTERNAL
        ));
    }
}
