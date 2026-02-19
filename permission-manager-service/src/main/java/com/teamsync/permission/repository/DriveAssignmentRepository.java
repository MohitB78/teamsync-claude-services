package com.teamsync.permission.repository;

import com.teamsync.permission.model.DriveAssignment;
import com.teamsync.permission.model.DriveAssignment.AssignmentSource;
import org.springframework.data.mongodb.repository.Meta;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.teamsync.common.model.Permission;

@Repository
public interface DriveAssignmentRepository extends MongoRepository<DriveAssignment, String> {

    /**
     * PRIMARY LOOKUP: O(1) check if user has access to drive.
     * This is the most critical query for performance.
     * SECURITY FIX (Round 15 #M6): 5s timeout for permission checks (critical path).
     */
    @Meta(maxExecutionTimeMs = 5000)
    Optional<DriveAssignment> findByUserIdAndDriveIdAndIsActiveTrue(String userId, String driveId);

    /**
     * Find assignment by user, drive, and tenant
     */
    Optional<DriveAssignment> findByTenantIdAndUserIdAndDriveId(String tenantId, String userId, String driveId);

    /**
     * Find all active drives a user has access to
     */
    List<DriveAssignment> findByTenantIdAndUserIdAndIsActiveTrue(String tenantId, String userId);

    /**
     * Find all users with access to a drive (active only)
     */
    List<DriveAssignment> findByTenantIdAndDriveIdAndIsActiveTrue(String tenantId, String driveId);

    /**
     * Find all assignments for a drive (including inactive) for archival/cleanup.
     * Used when archiving department drives to get all affected users for cache invalidation.
     */
    List<DriveAssignment> findByTenantIdAndDriveId(String tenantId, String driveId);

    /**
     * Find assignments by role (for updating permissions when role changes)
     */
    List<DriveAssignment> findByTenantIdAndRoleId(String tenantId, String roleId);

    /**
     * Find assignments granted via department
     */
    List<DriveAssignment> findByTenantIdAndAssignedViaDepartmentAndIsActiveTrue(String tenantId, String departmentId);

    /**
     * Find assignments granted via team
     */
    List<DriveAssignment> findByTenantIdAndAssignedViaTeamAndIsActiveTrue(String tenantId, String teamId);

    /**
     * Find expired assignments within a tenant.
     * SECURITY FIX (Round 14 #H32): Added tenant filter and pagination to prevent DoS
     * via memory exhaustion from unbounded list queries across all tenants.
     * Also fixes missing tenant isolation - expired assignments were queryable across tenants.
     * SECURITY FIX (Round 15 #M6): 15s timeout for batch/cleanup jobs.
     */
    @Meta(maxExecutionTimeMs = 15000)
    @Query("{ 'tenantId': ?0, 'expiresAt': { $lt: ?1, $ne: null }, 'isActive': true }")
    List<DriveAssignment> findExpiredAssignmentsByTenant(String tenantId, Instant now, Pageable pageable);

    /**
     * Deactivate assignment
     */
    @Query("{ 'userId': ?0, 'driveId': ?1 }")
    @Update("{ '$set': { 'isActive': false, 'updatedAt': ?2 } }")
    void deactivateAssignment(String userId, String driveId, Instant updatedAt);

    /**
     * Bulk update permissions for a role within a tenant.
     * SECURITY FIX (Round 14 #C4): Added tenant filter to prevent cross-tenant privilege escalation.
     * The old method without tenant filter has been removed as it was a critical security vulnerability.
     */
    @Query("{ 'tenantId': ?0, 'roleId': ?1 }")
    @Update("{ '$set': { 'permissions': ?2, 'updatedAt': ?3 } }")
    void updatePermissionsByTenantIdAndRoleId(String tenantId, String roleId, Set<Permission> permissions, Instant updatedAt);

    /**
     * Deactivate all assignments from a department
     */
    @Query("{ 'tenantId': ?0, 'assignedViaDepartment': ?1 }")
    @Update("{ '$set': { 'isActive': false, 'updatedAt': ?2 } }")
    void deactivateByDepartment(String tenantId, String departmentId, Instant updatedAt);

    /**
     * Deactivate all assignments from a team
     */
    @Query("{ 'tenantId': ?0, 'assignedViaTeam': ?1 }")
    @Update("{ '$set': { 'isActive': false, 'updatedAt': ?2 } }")
    void deactivateByTeam(String tenantId, String teamId, Instant updatedAt);

    /**
     * Count users with access to a drive
     */
    long countByTenantIdAndDriveIdAndIsActiveTrue(String tenantId, String driveId);

    /**
     * Check if user has any assignment to the drive
     */
    boolean existsByUserIdAndDriveIdAndIsActiveTrue(String userId, String driveId);

    /**
     * Delete assignments for a drive within a tenant (when drive is deleted).
     * SECURITY FIX (Round 14 #C3): Added tenant filter to prevent cross-tenant permission deletion.
     * The old method without tenant filter has been removed as it was a critical security vulnerability.
     */
    void deleteByTenantIdAndDriveId(String tenantId, String driveId);

    /**
     * Find assignments by source type
     */
    List<DriveAssignment> findByTenantIdAndDriveIdAndSourceAndIsActiveTrue(
            String tenantId, String driveId, AssignmentSource source);

    /**
     * Find assignments for a specific user from a specific department.
     * Optimized: Filters at database level instead of in memory.
     */
    List<DriveAssignment> findByTenantIdAndUserIdAndAssignedViaDepartmentAndIsActiveTrue(
            String tenantId, String userId, String departmentId);

    // ============== CURSOR-BASED PAGINATION QUERIES ==============

    /**
     * Find all active drives a user has access to with cursor-based pagination.
     * Used for getUserDrives() to handle users with many drive assignments.
     * SECURITY FIX (Round 15 #M6): 5s timeout for API-facing queries.
     */
    @Meta(maxExecutionTimeMs = 5000)
    @Query("{ 'tenantId': ?0, 'userId': ?1, 'isActive': true, '_id': { $gt: ?2 } }")
    List<DriveAssignment> findByTenantIdAndUserIdAndIsActiveTrueAfterCursor(
            String tenantId, String userId, String cursor, Pageable pageable);

    /**
     * Find all active drives a user has access to (first page, no cursor).
     */
    List<DriveAssignment> findByTenantIdAndUserIdAndIsActiveTrueOrderByIdAsc(
            String tenantId, String userId, Pageable pageable);

    /**
     * Find all users with access to a drive with cursor-based pagination.
     * Used for getDriveUsers() to handle drives with many users.
     */
    @Query("{ 'tenantId': ?0, 'driveId': ?1, 'isActive': true, '_id': { $gt: ?2 } }")
    List<DriveAssignment> findByTenantIdAndDriveIdAndIsActiveTrueAfterCursor(
            String tenantId, String driveId, String cursor, Pageable pageable);

    /**
     * Find all users with access to a drive (first page, no cursor).
     */
    List<DriveAssignment> findByTenantIdAndDriveIdAndIsActiveTrueOrderByIdAsc(
            String tenantId, String driveId, Pageable pageable);

    /**
     * Count active assignments for a user (for pagination totalCount).
     */
    long countByTenantIdAndUserIdAndIsActiveTrue(String tenantId, String userId);

    /**
     * Check if user has any assignment with a specific permission.
     * Optimized for isTenantAdmin() check - stops at first match.
     */
    @Query("{ 'tenantId': ?0, 'userId': ?1, 'permissions': ?2, 'isActive': true }")
    boolean existsByTenantIdAndUserIdAndPermissionsContainingAndIsActiveTrue(
            String tenantId, String userId, Permission permission);
}
