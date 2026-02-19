package com.teamsync.team.repository;

import com.teamsync.team.model.Team;
import com.teamsync.team.model.Team.TeamStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Team entities.
 * All queries MUST filter by tenantId for multi-tenant isolation.
 */
@Repository
public interface TeamRepository extends MongoRepository<Team, String> {

    // ============== SINGLE TEAM QUERIES ==============

    /**
     * Find team by ID with tenant isolation.
     */
    Optional<Team> findByIdAndTenantId(String id, String tenantId);

    /**
     * Find team by name with tenant isolation.
     */
    Optional<Team> findByNameAndTenantId(String name, String tenantId);

    /**
     * Check if team exists.
     */
    boolean existsByIdAndTenantId(String id, String tenantId);

    /**
     * Check if team name is taken.
     */
    boolean existsByNameAndTenantId(String name, String tenantId);

    // ============== LIST QUERIES ==============

    /**
     * Find all active teams in a tenant.
     */
    List<Team> findByTenantIdAndStatus(String tenantId, TeamStatus status);

    /**
     * Find all teams owned by a user.
     */
    List<Team> findByTenantIdAndOwnerIdAndStatus(String tenantId, String ownerId, TeamStatus status);

    /**
     * Find teams where user is a member.
     */
    @Query("{ 'tenantId': ?0, 'status': ?1, 'members': { $elemMatch: { 'userId': ?2, 'status': 'ACTIVE' } } }")
    List<Team> findByTenantIdAndStatusAndMembersUserId(String tenantId, TeamStatus status, String userId);

    /**
     * Find teams where user is a member (any status).
     */
    @Query("{ 'tenantId': ?0, 'members': { $elemMatch: { 'userId': ?1, 'status': 'ACTIVE' } } }")
    List<Team> findByTenantIdAndMembersUserId(String tenantId, String userId);

    /**
     * Find teams where user is a member with pagination.
     * Note: Pageable parameter handles pagination automatically.
     */
    @Query("{ 'tenantId': ?0, 'status': ?1, 'members': { $elemMatch: { 'userId': ?2, 'status': 'ACTIVE' } } }")
    List<Team> findByTenantIdAndStatusAndMembersUserIdPaged(
            String tenantId, TeamStatus status, String userId, Pageable pageable);

    // ============== CURSOR-BASED PAGINATION ==============

    /**
     * Find teams with cursor-based pagination (for infinite scroll).
     */
    @Query("{ 'tenantId': ?0, 'status': ?1, '_id': { $gt: ?2 } }")
    List<Team> findByTenantIdAndStatusAfterCursor(
            String tenantId, TeamStatus status, String cursor, Pageable pageable);

    /**
     * Find teams where user is member with cursor.
     */
    @Query("{ 'tenantId': ?0, 'status': ?1, 'members.userId': ?2, '_id': { $gt: ?3 } }")
    List<Team> findMemberTeamsAfterCursor(
            String tenantId, TeamStatus status, String userId, String cursor, Pageable pageable);

    // ============== SEARCH ==============

    /**
     * Search teams by name (case-insensitive partial match).
     */
    @Query("{ 'tenantId': ?0, 'status': ?1, 'name': { $regex: ?2, $options: 'i' } }")
    List<Team> searchByName(String tenantId, TeamStatus status, String namePattern, Pageable pageable);

    /**
     * Search public teams (for join requests).
     */
    @Query("{ 'tenantId': ?0, 'status': 'ACTIVE', 'visibility': 'PUBLIC', 'name': { $regex: ?1, $options: 'i' } }")
    List<Team> searchPublicTeams(String tenantId, String namePattern, Pageable pageable);

    // ============== COUNTS ==============

    /**
     * Count teams where user is a member.
     */
    @Query(value = "{ 'tenantId': ?0, 'status': ?1, 'members.userId': ?2 }", count = true)
    long countMemberTeams(String tenantId, TeamStatus status, String userId);

    /**
     * Count teams owned by user.
     */
    long countByTenantIdAndOwnerIdAndStatus(String tenantId, String ownerId, TeamStatus status);

    // ============== HIERARCHY (Phase 5) ==============

    /**
     * Find child teams (sub-teams/workstreams) for a parent team.
     */
    List<Team> findByTenantIdAndParentTeamIdAndStatus(String tenantId, String parentTeamId, TeamStatus status);

    /**
     * Batch find teams by IDs (for resolving parent teams efficiently).
     * Used to avoid N+1 queries when loading team hierarchies.
     */
    @Query("{ 'tenantId': ?0, '_id': { $in: ?1 } }")
    List<Team> findByTenantIdAndIdIn(String tenantId, List<String> ids);

    /**
     * Batch find child teams for multiple parent teams.
     * Used to avoid N+1 queries when loading team lists with children.
     */
    @Query("{ 'tenantId': ?0, 'parentTeamId': { $in: ?1 }, 'status': ?2 }")
    List<Team> findByTenantIdAndParentTeamIdInAndStatus(String tenantId, List<String> parentTeamIds, TeamStatus status);
}
