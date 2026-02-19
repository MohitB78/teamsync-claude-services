package com.teamsync.team.service;

import com.teamsync.common.exception.BadRequestException;
import com.teamsync.common.exception.ResourceNotFoundException;
import com.teamsync.common.exception.UnauthorizedException;
import com.teamsync.team.dto.portal.*;
import com.teamsync.team.model.*;
import com.teamsync.team.model.Team.TeamMember;
import com.teamsync.team.model.Team.MemberType;
import com.teamsync.team.model.Team.MemberStatus;
import com.teamsync.team.model.Team.TeamStatus;
import com.teamsync.team.model.TeamInvitation.InvitationStatus;
import com.teamsync.team.repository.*;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for portal authentication using magic links.
 * External users don't have Zitadel accounts - they use email-based magic links.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PortalAuthService {

    private final MagicLinkTokenRepository magicLinkTokenRepository;
    private final PortalSessionRepository portalSessionRepository;
    private final ExternalUserRepository externalUserRepository;
    private final TeamInvitationRepository teamInvitationRepository;
    private final TeamRepository teamRepository;
    private final TeamRoleRepository teamRoleRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${teamsync.portal.jwt.secret}")
    private String jwtSecret;

    @Value("${teamsync.portal.jwt.access-token-expiry:900}")
    private long accessTokenExpiry; // 15 minutes

    @Value("${teamsync.portal.jwt.refresh-token-expiry:604800}")
    private long refreshTokenExpiry; // 7 days

    @Value("${teamsync.portal.magic-link.expiry:900}")
    private long magicLinkExpiry; // 15 minutes

    @Value("${teamsync.portal.magic-link.max-active:3}")
    private int maxActiveMagicLinks;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Request a magic link to be sent to an email address.
     */
    @Transactional
    public void requestMagicLink(String tenantId, MagicLinkRequest request) {
        String email = request.getEmail().toLowerCase().trim();

        // Rate limiting - check active tokens
        long activeTokens = magicLinkTokenRepository.countByTenantIdAndEmailAndUsedFalseAndExpiresAtAfter(
                tenantId, email, Instant.now());

        if (activeTokens >= maxActiveMagicLinks) {
            throw new BadRequestException("Too many active magic link requests. Please wait before requesting another.");
        }

        // If invitation token provided, verify it's valid
        String invitationId = null;
        if (request.getInvitationToken() != null && !request.getInvitationToken().isBlank()) {
            String invitationTokenHash = hashToken(request.getInvitationToken());
            TeamInvitation invitation = teamInvitationRepository.findByTokenHashAndStatusAndExpiresAtAfter(
                    invitationTokenHash, InvitationStatus.PENDING, Instant.now())
                    .orElseThrow(() -> new BadRequestException("Invalid or expired invitation"));

            if (!invitation.getEmail().equalsIgnoreCase(email)) {
                throw new BadRequestException("Email does not match invitation");
            }

            invitationId = invitation.getId();
        } else {
            // No invitation - verify user has access to at least one team
            ExternalUser existingUser = externalUserRepository.findByTenantIdAndEmail(tenantId, email)
                    .orElse(null);

            if (existingUser == null) {
                // User doesn't exist - they need an invitation first
                throw new BadRequestException("No account found. Please request an invitation from your team.");
            }
        }

        // Generate magic link token
        String token = generateSecureToken();
        String tokenHash = hashToken(token);

        MagicLinkToken magicLinkToken = MagicLinkToken.builder()
                .tenantId(tenantId)
                .email(email)
                .tokenHash(tokenHash)
                .invitationId(invitationId)
                .expiresAt(Instant.now().plus(magicLinkExpiry, ChronoUnit.SECONDS))
                .used(false)
                .createdAt(Instant.now())
                .build();

        magicLinkTokenRepository.save(magicLinkToken);

        // Publish event to send email (handled by notification service)
        Map<String, Object> emailEvent = Map.of(
                "type", "MAGIC_LINK",
                "tenantId", tenantId,
                "email", email,
                "token", token, // Plain token for email
                "expiresAt", magicLinkToken.getExpiresAt().toString()
        );
        kafkaTemplate.send("teamsync.notifications.email", emailEvent);

        log.info("Magic link requested for email: {} in tenant: {}", email, tenantId);
    }

    /**
     * Verify a magic link token and return auth tokens.
     */
    @Transactional
    public PortalAuthResponse verifyMagicLink(String tenantId, VerifyMagicLinkRequest request) {
        String tokenHash = hashToken(request.getToken());

        MagicLinkToken magicLinkToken = magicLinkTokenRepository
                .findByTokenHashAndUsedFalseAndExpiresAtAfter(tokenHash, Instant.now())
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired magic link"));

        if (!magicLinkToken.getTenantId().equals(tenantId)) {
            throw new UnauthorizedException("Invalid magic link");
        }

        // Mark token as used
        magicLinkToken.setUsed(true);
        magicLinkToken.setUsedAt(Instant.now());
        magicLinkTokenRepository.save(magicLinkToken);

        // Get or create external user
        ExternalUser user = getOrCreateExternalUser(tenantId, magicLinkToken.getEmail());

        // Process invitation if this magic link was for an invitation
        if (magicLinkToken.getInvitationId() != null) {
            processInvitation(magicLinkToken.getInvitationId(), user);
        }

        // Get user's team access
        List<PortalSession.TeamAccess> teamAccess = getTeamAccess(tenantId, user.getId());

        // Generate tokens
        String accessToken = generateAccessToken(user, teamAccess);
        String refreshToken = generateSecureToken();
        String refreshTokenHash = hashToken(refreshToken);

        // Create portal session
        PortalSession session = PortalSession.builder()
                .tenantId(tenantId)
                .userId(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .refreshTokenHash(refreshTokenHash)
                .teamAccess(teamAccess)
                .expiresAt(Instant.now().plus(refreshTokenExpiry, ChronoUnit.SECONDS))
                .createdAt(Instant.now())
                .lastActiveAt(Instant.now())
                .build();

        portalSessionRepository.save(session);

        // Update user login stats
        user.setLastLoginAt(Instant.now());
        user.setLoginCount(user.getLoginCount() + 1);
        externalUserRepository.save(user);

        log.info("Magic link verified for user: {} in tenant: {}", user.getEmail(), tenantId);

        return PortalAuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(accessTokenExpiry)
                .user(mapToUserDTO(user, teamAccess))
                .build();
    }

    /**
     * Refresh access token using refresh token.
     */
    @Transactional
    public PortalAuthResponse refreshToken(String tenantId, RefreshTokenRequest request) {
        String refreshTokenHash = hashToken(request.getRefreshToken());

        PortalSession session = portalSessionRepository
                .findByRefreshTokenHashAndExpiresAtAfter(refreshTokenHash, Instant.now())
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired refresh token"));

        if (!session.getTenantId().equals(tenantId)) {
            throw new UnauthorizedException("Invalid refresh token");
        }

        ExternalUser user = externalUserRepository.findById(session.getUserId())
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        // Refresh team access (might have changed)
        List<PortalSession.TeamAccess> teamAccess = getTeamAccess(tenantId, user.getId());

        // Generate new tokens
        String newAccessToken = generateAccessToken(user, teamAccess);
        String newRefreshToken = generateSecureToken();
        String newRefreshTokenHash = hashToken(newRefreshToken);

        // Update session
        session.setRefreshTokenHash(newRefreshTokenHash);
        session.setTeamAccess(teamAccess);
        session.setLastActiveAt(Instant.now());
        session.setExpiresAt(Instant.now().plus(refreshTokenExpiry, ChronoUnit.SECONDS));
        portalSessionRepository.save(session);

        log.debug("Token refreshed for user: {}", user.getEmail());

        return PortalAuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .expiresIn(accessTokenExpiry)
                .user(mapToUserDTO(user, teamAccess))
                .build();
    }

    /**
     * Get current user info from access token.
     */
    public PortalUserDTO getCurrentUser(String accessToken) {
        Claims claims = parseAccessToken(accessToken);

        String tenantId = claims.get("tenantId", String.class);
        String userId = claims.getSubject();

        ExternalUser user = externalUserRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        List<PortalSession.TeamAccess> teamAccess = getTeamAccess(tenantId, userId);

        return mapToUserDTO(user, teamAccess);
    }

    /**
     * Parse and validate access token.
     */
    public Claims parseAccessToken(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            throw new UnauthorizedException("Invalid access token");
        }
    }

    /**
     * Verify invitation token (public endpoint).
     */
    public TeamInvitation getInvitationByToken(String token) {
        String tokenHash = hashToken(token);

        return teamInvitationRepository.findByTokenHashAndStatusAndExpiresAtAfter(
                tokenHash, InvitationStatus.PENDING, Instant.now())
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found or expired"));
    }

    // ========================================
    // Private Helper Methods
    // ========================================

    private ExternalUser getOrCreateExternalUser(String tenantId, String email) {
        return externalUserRepository.findByTenantIdAndEmail(tenantId, email)
                .orElseGet(() -> {
                    ExternalUser newUser = ExternalUser.builder()
                            .tenantId(tenantId)
                            .email(email)
                            .displayName(extractDisplayName(email))
                            .status(ExternalUser.ExternalUserStatus.ACTIVE)
                            .createdAt(Instant.now())
                            .loginCount(0)
                            .build();
                    return externalUserRepository.save(newUser);
                });
    }

    private void processInvitation(String invitationId, ExternalUser user) {
        TeamInvitation invitation = teamInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found"));

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            return; // Already processed
        }

        // Add user to team
        Team team = teamRepository.findById(invitation.getTeamId())
                .orElseThrow(() -> new ResourceNotFoundException("Team not found"));

        // Get the EXTERNAL role for external users (system roles have ID = constant)
        TeamRole externalRole = teamRoleRepository.findByTenantIdAndTeamIdAndName(
                invitation.getTenantId(), invitation.getTeamId(), "EXTERNAL")
                .or(() -> teamRoleRepository.findSystemRoleById(
                        invitation.getTenantId(), TeamRole.ROLE_ID_EXTERNAL))
                .orElseThrow(() -> new ResourceNotFoundException("External role not found"));

        TeamMember newMember = TeamMember.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .memberType(MemberType.EXTERNAL)
                .roleId(externalRole.getId())
                .roleName(externalRole.getName())
                .permissions(externalRole.getPermissions().stream()
                        .map(TeamPermission::name)
                        .collect(Collectors.toSet()))
                .joinedAt(Instant.now())
                .invitedBy(invitation.getInvitedById())
                .status(MemberStatus.ACTIVE)
                .build();

        team.getMembers().add(newMember);
        team.setMemberCount(team.getMemberCount() + 1);
        teamRepository.save(team);

        // Update invitation status
        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation.setRespondedAt(Instant.now());
        teamInvitationRepository.save(invitation);

        // Publish event
        kafkaTemplate.send("teamsync.teams.member_added", Map.of(
                "teamId", team.getId(),
                "tenantId", team.getTenantId(),
                "userId", user.getId(),
                "memberType", "EXTERNAL",
                "roleId", externalRole.getId()
        ));

        log.info("External user {} joined team {} via invitation", user.getEmail(), team.getName());
    }

    private List<PortalSession.TeamAccess> getTeamAccess(String tenantId, String userId) {
        List<Team> teams = teamRepository.findByTenantIdAndMembersUserId(tenantId, userId);

        return teams.stream()
                .map(team -> {
                    TeamMember member = team.getMembers().stream()
                            .filter(m -> m.getUserId().equals(userId))
                            .findFirst()
                            .orElse(null);

                    if (member == null) {
                        return null;
                    }

                    return PortalSession.TeamAccess.builder()
                            .teamId(team.getId())
                            .teamName(team.getName())
                            .roleId(member.getRoleId())
                            .roleName(member.getRoleName())
                            .permissions(member.getPermissions())
                            .build();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private String generateAccessToken(ExternalUser user, List<PortalSession.TeamAccess> teamAccess) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

        Map<String, Object> claims = new HashMap<>();
        claims.put("tenantId", user.getTenantId());
        claims.put("email", user.getEmail());
        claims.put("displayName", user.getDisplayName());
        claims.put("type", "EXTERNAL");
        claims.put("teams", teamAccess.stream()
                .map(ta -> Map.of(
                        "teamId", ta.getTeamId(),
                        "permissions", ta.getPermissions()))
                .collect(Collectors.toList()));

        return Jwts.builder()
                .subject(user.getId())
                .claims(claims)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(accessTokenExpiry, ChronoUnit.SECONDS)))
                .signWith(key)
                .compact();
    }

    private String generateSecureToken() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private String extractDisplayName(String email) {
        String localPart = email.split("@")[0];
        // Convert user.name to User Name
        return Arrays.stream(localPart.split("[._-]"))
                .map(s -> s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }

    private PortalUserDTO mapToUserDTO(ExternalUser user, List<PortalSession.TeamAccess> teamAccess) {
        return PortalUserDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .teams(teamAccess.stream()
                        .map(ta -> PortalUserDTO.PortalTeamAccessDTO.builder()
                                .teamId(ta.getTeamId())
                                .teamName(ta.getTeamName())
                                .roleName(ta.getRoleName())
                                .permissions(new ArrayList<>(ta.getPermissions()))
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }
}
