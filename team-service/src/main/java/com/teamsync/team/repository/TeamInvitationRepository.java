package com.teamsync.team.repository;

import com.teamsync.team.model.TeamInvitation;
import com.teamsync.team.model.TeamInvitation.InvitationStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for TeamInvitation entities.
 */
@Repository
public interface TeamInvitationRepository extends MongoRepository<TeamInvitation, String> {

    // ============== SINGLE INVITATION QUERIES ==============

    /**
     * Find invitation by ID with tenant isolation.
     */
    Optional<TeamInvitation> findByIdAndTenantId(String id, String tenantId);

    /**
     * Find invitation by token (for acceptance via magic link).
     */
    Optional<TeamInvitation> findByToken(String token);

    /**
     * Find invitation by token hash.
     */
    Optional<TeamInvitation> findByTokenHash(String tokenHash);

    /**
     * Find invitation by token hash with status and expiry check.
     */
    Optional<TeamInvitation> findByTokenHashAndStatusAndExpiresAtAfter(
            String tokenHash, InvitationStatus status, Instant now);

    /**
     * Find invitation by ID with tenant and team isolation.
     */
    Optional<TeamInvitation> findByIdAndTenantIdAndTeamId(String id, String tenantId, String teamId);

    /**
     * Find pending invitation for email in a team.
     */
    Optional<TeamInvitation> findByTenantIdAndTeamIdAndEmailAndStatus(
            String tenantId, String teamId, String email, InvitationStatus status);

    /**
     * Check if invitation exists for email in team.
     */
    boolean existsByTenantIdAndTeamIdAndEmailAndStatus(
            String tenantId, String teamId, String email, InvitationStatus status);

    // ============== LIST QUERIES ==============

    /**
     * Find all pending invitations for a team.
     */
    List<TeamInvitation> findByTenantIdAndTeamIdAndStatus(
            String tenantId, String teamId, InvitationStatus status);

    /**
     * Find all pending invitations for a team with pagination.
     */
    List<TeamInvitation> findByTenantIdAndTeamIdAndStatus(
            String tenantId, String teamId, InvitationStatus status, Pageable pageable);

    /**
     * Find all pending invitations for an email (across all teams).
     */
    List<TeamInvitation> findByTenantIdAndEmailAndStatus(
            String tenantId, String email, InvitationStatus status);

    /**
     * Find all pending invitations for a user ID (internal users).
     */
    List<TeamInvitation> findByTenantIdAndUserIdAndStatus(
            String tenantId, String userId, InvitationStatus status);

    /**
     * Find invitations sent by a user.
     */
    List<TeamInvitation> findByTenantIdAndInvitedByIdAndStatus(
            String tenantId, String invitedById, InvitationStatus status, Pageable pageable);

    // ============== EXPIRED INVITATIONS ==============

    /**
     * Find pending invitations that have expired.
     */
    @Query("{ 'status': 'PENDING', 'expiresAt': { $lt: ?0 } }")
    List<TeamInvitation> findExpiredInvitations(Instant now);

    /**
     * Count expired invitations (for cleanup job monitoring).
     */
    @Query(value = "{ 'status': 'PENDING', 'expiresAt': { $lt: ?0 } }", count = true)
    long countExpiredInvitations(Instant now);

    // ============== COUNTS ==============

    /**
     * Count pending invitations for a team.
     */
    long countByTenantIdAndTeamIdAndStatus(String tenantId, String teamId, InvitationStatus status);

    /**
     * Count pending invitations for a user/email.
     */
    long countByTenantIdAndEmailAndStatus(String tenantId, String email, InvitationStatus status);
}
