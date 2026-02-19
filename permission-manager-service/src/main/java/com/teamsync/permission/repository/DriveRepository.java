package com.teamsync.permission.repository;

import com.teamsync.common.model.DriveType;
import com.teamsync.permission.model.Drive;
import com.teamsync.permission.model.Drive.DriveStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

@Repository
public interface DriveRepository extends MongoRepository<Drive, String> {

    /**
     * Find drive by ID and tenant
     */
    Optional<Drive> findByIdAndTenantId(String id, String tenantId);

    /**
     * Find personal drive for a user
     */
    Optional<Drive> findByTenantIdAndOwnerIdAndType(String tenantId, String ownerId, DriveType type);

    /**
     * Find department drive
     */
    Optional<Drive> findByTenantIdAndDepartmentId(String tenantId, String departmentId);

    /**
     * Find all drives for a tenant with pagination.
     * SECURITY FIX (Round 14 #H41): Added pagination to prevent memory exhaustion
     * for tenants with many drives.
     */
    List<Drive> findByTenantIdAndStatus(String tenantId, DriveStatus status, Pageable pageable);

    /**
     * Find all department drives for a tenant with pagination.
     * SECURITY FIX (Round 14 #H41): Added pagination to prevent memory exhaustion.
     */
    List<Drive> findByTenantIdAndTypeAndStatus(String tenantId, DriveType type, DriveStatus status, Pageable pageable);

    /**
     * Check if drive exists
     */
    boolean existsByIdAndTenantId(String id, String tenantId);

    /**
     * Update storage usage atomically
     */
    @Query("{ '_id': ?0, 'tenantId': ?1 }")
    @Update("{ '$inc': { 'usedBytes': ?2 } }")
    void incrementUsedBytes(String driveId, String tenantId, long bytes);

    /**
     * Find drives approaching quota (>80% used) with pagination.
     * SECURITY FIX (Round 14 #H41): Added pagination to prevent memory exhaustion.
     */
    @Query("{ 'tenantId': ?0, 'quotaBytes': { $ne: null }, " +
            "$expr: { $gt: [ '$usedBytes', { $multiply: [ '$quotaBytes', 0.8 ] } ] } }")
    List<Drive> findDrivesApproachingQuota(String tenantId, Pageable pageable);

    /**
     * Find drives exceeding quota with pagination.
     * SECURITY FIX (Round 14 #H41): Added pagination to prevent memory exhaustion.
     */
    @Query("{ 'tenantId': ?0, 'quotaBytes': { $ne: null }, " +
            "$expr: { $gte: [ '$usedBytes', '$quotaBytes' ] } }")
    List<Drive> findDrivesExceedingQuota(String tenantId, Pageable pageable);

    /**
     * SECURITY (Round 7): Check if a department drive exists for the given tenant and department.
     * Used to validate that a department belongs to a tenant before allowing bulk operations.
     */
    boolean existsByTenantIdAndDepartmentId(String tenantId, String departmentId);

    /**
     * SECURITY FIX (Round 12): Check if any drives exist for a tenant.
     * Used by Kafka event listeners to validate tenant existence before processing events.
     * This prevents cross-tenant attacks via malicious event injection.
     */
    boolean existsByTenantId(String tenantId);
}
