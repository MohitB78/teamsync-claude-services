package com.teamsync.team.repository;

import com.teamsync.team.model.TeamRole;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for TeamRole entities.
 * Manages both system roles (tenant-wide) and custom team-specific roles.
 */
@Repository
public interface TeamRoleRepository extends MongoRepository<TeamRole, String> {

    // ============== SINGLE ROLE QUERIES ==============

    /**
     * Find role by ID with tenant isolation.
     */
    Optional<TeamRole> findByIdAndTenantId(String id, String tenantId);

    /**
     * Find role by name within a team.
     */
    Optional<TeamRole> findByTenantIdAndTeamIdAndName(String tenantId, String teamId, String name);

    /**
     * Find system role by ID (tenant-wide, no teamId).
     * System roles have their ID set to constants like OWNER, ADMIN, etc.
     */
    @Query("{ 'tenantId': ?0, 'teamId': null, '_id': ?1 }")
    Optional<TeamRole> findSystemRoleById(String tenantId, String roleId);

    /**
     * Find system role by name (tenant-wide, no teamId).
     * @deprecated Use findSystemRoleById instead - system roles are identified by ID constants
     */
    @Deprecated
    @Query("{ 'tenantId': ?0, 'teamId': null, 'name': ?1 }")
    Optional<TeamRole> findSystemRoleByName(String tenantId, String name);

    /**
     * Check if role exists.
     */
    boolean existsByIdAndTenantId(String id, String tenantId);

    /**
     * Check if role name is taken within a team.
     */
    boolean existsByTenantIdAndTeamIdAndName(String tenantId, String teamId, String name);

    // ============== LIST QUERIES ==============

    /**
     * Find all system roles (tenant-wide templates).
     */
    @Query("{ 'tenantId': ?0, 'teamId': null }")
    List<TeamRole> findSystemRoles(String tenantId);

    /**
     * Find all roles for a specific team (includes system roles and custom roles).
     */
    @Query("{ 'tenantId': ?0, $or: [ { 'teamId': null }, { 'teamId': ?1 } ] }")
    List<TeamRole> findRolesForTeam(String tenantId, String teamId);

    /**
     * Find custom roles for a specific team only (excludes system roles).
     */
    List<TeamRole> findByTenantIdAndTeamId(String tenantId, String teamId);

    /**
     * Find default role for a team (or system default if none set).
     */
    @Query("{ 'tenantId': ?0, $or: [ { 'teamId': ?1 }, { 'teamId': null } ], 'isDefault': true }")
    List<TeamRole> findDefaultRoles(String tenantId, String teamId);

    /**
     * Find all roles that are system roles.
     */
    List<TeamRole> findByTenantIdAndIsSystemRoleTrue(String tenantId);

    // ============== DELETION ==============

    /**
     * Delete custom roles for a team (used when team is deleted).
     * System roles are never deleted.
     */
    void deleteByTenantIdAndTeamIdAndIsSystemRoleFalse(String tenantId, String teamId);
}
