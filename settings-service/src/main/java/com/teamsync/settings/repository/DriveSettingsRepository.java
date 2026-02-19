package com.teamsync.settings.repository;

import com.teamsync.settings.model.DriveSettings;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for drive settings.
 */
@Repository
public interface DriveSettingsRepository extends MongoRepository<DriveSettings, String> {

    /**
     * Find drive settings by tenant, user, and drive ID.
     */
    Optional<DriveSettings> findByTenantIdAndUserIdAndDriveId(String tenantId, String userId, String driveId);

    /**
     * Find all drive settings for a user with pagination.
     * SECURITY FIX (Round 14 #H44): Removed unbounded version, pagination is now required
     * to prevent memory exhaustion for users with many drive settings.
     */
    List<DriveSettings> findByTenantIdAndUserId(String tenantId, String userId, Pageable pageable);

    /**
     * Find drive settings with cursor-based pagination.
     * Returns settings with ID greater than cursor, ordered by ID.
     */
    @Query("{ 'tenantId': ?0, 'userId': ?1, '_id': { $gt: { $oid: ?2 } } }")
    List<DriveSettings> findByTenantIdAndUserIdWithCursor(String tenantId, String userId, String cursor, Pageable pageable);

    /**
     * Find drive settings for a user with pagination (first page).
     */
    List<DriveSettings> findByTenantIdAndUserIdOrderByIdAsc(String tenantId, String userId, Pageable pageable);

    /**
     * Count total drive settings for a user.
     */
    long countByTenantIdAndUserId(String tenantId, String userId);

    /**
     * Check if settings exist for a user's drive.
     */
    boolean existsByTenantIdAndUserIdAndDriveId(String tenantId, String userId, String driveId);

    /**
     * Delete settings for a specific drive.
     */
    void deleteByTenantIdAndUserIdAndDriveId(String tenantId, String userId, String driveId);

    /**
     * Delete all drive settings for a user.
     */
    void deleteByTenantIdAndUserId(String tenantId, String userId);

    /**
     * Delete all settings for a drive (when drive is deleted).
     */
    void deleteByTenantIdAndDriveId(String tenantId, String driveId);

    /**
     * Delete all settings for a tenant.
     */
    void deleteByTenantId(String tenantId);
}
