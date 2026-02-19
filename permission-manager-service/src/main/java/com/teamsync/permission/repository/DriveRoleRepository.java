package com.teamsync.permission.repository;

import com.teamsync.permission.model.DriveRole;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DriveRoleRepository extends MongoRepository<DriveRole, String> {

    /**
     * Find role by ID and tenant
     */
    Optional<DriveRole> findByIdAndTenantId(String id, String tenantId);

    /**
     * Find role by name (tenant-wide template)
     */
    Optional<DriveRole> findByTenantIdAndNameAndDriveIdIsNull(String tenantId, String name);

    /**
     * Find role by name for specific drive
     */
    Optional<DriveRole> findByTenantIdAndDriveIdAndName(String tenantId, String driveId, String name);

    /**
     * Find all tenant-wide role templates
     */
    List<DriveRole> findByTenantIdAndDriveIdIsNull(String tenantId);

    /**
     * Find all roles for a specific drive (including tenant templates)
     */
    List<DriveRole> findByTenantIdAndDriveIdIsNullOrTenantIdAndDriveId(
            String tenantId1, String tenantId2, String driveId);

    /**
     * Find all system roles for a tenant
     */
    List<DriveRole> findByTenantIdAndIsSystemRoleTrue(String tenantId);

    /**
     * Find custom roles for a drive
     */
    List<DriveRole> findByTenantIdAndDriveIdAndIsSystemRoleFalse(String tenantId, String driveId);

    /**
     * Check if role name already exists
     */
    boolean existsByTenantIdAndDriveIdAndName(String tenantId, String driveId, String name);

    /**
     * Delete custom roles for a drive
     */
    void deleteByTenantIdAndDriveIdAndIsSystemRoleFalse(String tenantId, String driveId);

    /**
     * Batch fetch roles by IDs within a tenant.
     * SECURITY FIX (Round 14 #H34): Added tenant filter to prevent cross-tenant role information disclosure.
     * The old method without tenant filter has been removed as it was a security vulnerability.
     */
    List<DriveRole> findByTenantIdAndIdIn(String tenantId, List<String> ids);

    /**
     * SECURITY (Round 7): Check if a role exists and belongs to a specific tenant.
     * Used to prevent cross-tenant role manipulation attacks.
     */
    boolean existsByIdAndTenantId(String id, String tenantId);
}
