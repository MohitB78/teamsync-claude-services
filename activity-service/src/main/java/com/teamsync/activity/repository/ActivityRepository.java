package com.teamsync.activity.repository;

import com.teamsync.activity.model.Activity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.Instant;
import java.util.List;

/**
 * Repository for Activity entities.
 * Provides cursor-based pagination for efficient large dataset queries.
 */
public interface ActivityRepository extends MongoRepository<Activity, String> {

    /**
     * Find activities for a specific drive (team) with cursor-based pagination.
     * Results are sorted by createdAt descending (newest first).
     */
    @Query("{ 'tenantId': ?0, 'driveId': ?1, 'createdAt': { $lt: ?2 } }")
    List<Activity> findByTenantIdAndDriveIdAndCreatedAtBefore(
            String tenantId,
            String driveId,
            Instant cursor,
            Pageable pageable);

    /**
     * Find activities for a specific drive without cursor (first page).
     */
    List<Activity> findByTenantIdAndDriveIdOrderByCreatedAtDesc(
            String tenantId,
            String driveId,
            Pageable pageable);

    /**
     * Find activities for a specific document.
     */
    List<Activity> findByTenantIdAndDriveIdAndResourceIdOrderByCreatedAtDesc(
            String tenantId,
            String driveId,
            String resourceId,
            Pageable pageable);

    /**
     * Find activities for a specific user.
     */
    List<Activity> findByTenantIdAndUserIdOrderByCreatedAtDesc(
            String tenantId,
            String userId,
            Pageable pageable);

    /**
     * Count activities for a drive.
     */
    long countByTenantIdAndDriveId(String tenantId, String driveId);
}
