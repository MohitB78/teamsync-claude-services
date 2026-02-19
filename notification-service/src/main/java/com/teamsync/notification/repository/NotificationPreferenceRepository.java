package com.teamsync.notification.repository;

import com.teamsync.notification.model.NotificationPreference;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * Repository for notification preferences.
 */
@Repository
public interface NotificationPreferenceRepository extends MongoRepository<NotificationPreference, String> {

    /**
     * Find preferences for a user.
     */
    Optional<NotificationPreference> findByTenantIdAndUserId(String tenantId, String userId);

    /**
     * Check if preferences exist for a user.
     */
    boolean existsByTenantIdAndUserId(String tenantId, String userId);

    /**
     * Find all preferences for a tenant with pagination.
     * SECURITY FIX (Round 14 #H44): Added pagination to prevent memory exhaustion.
     */
    List<NotificationPreference> findByTenantId(String tenantId, Pageable pageable);

    /**
     * Find users with digest enabled with pagination.
     * SECURITY FIX (Round 14 #H44): Added tenant filter and pagination to prevent memory exhaustion
     * and ensure tenant isolation.
     */
    @Query("{ 'tenantId': ?0, 'digestSettings.enabled': true, 'digestSettings.frequency': ?1 }")
    List<NotificationPreference> findByTenantIdAndDigestFrequency(String tenantId, String frequency, Pageable pageable);

    /**
     * Find users with email enabled for a specific time (for digest scheduling) with pagination.
     * SECURITY FIX (Round 14 #H44): Added tenant filter and pagination to prevent memory exhaustion
     * and ensure tenant isolation.
     */
    @Query("{ 'tenantId': ?0, 'digestSettings.enabled': true, 'digestSettings.sendTime': ?1, 'emailEnabled': true }")
    List<NotificationPreference> findDigestRecipientsByTenant(String tenantId, String sendTime, Pageable pageable);

    /**
     * Delete preferences for a user.
     */
    void deleteByTenantIdAndUserId(String tenantId, String userId);

    /**
     * Delete all preferences for a tenant.
     */
    void deleteByTenantId(String tenantId);

    /**
     * Update email enabled status.
     */
    @Query("{ 'tenantId': ?0, 'userId': ?1 }")
    @Update("{ '$set': { 'emailEnabled': ?2 } }")
    long updateEmailEnabled(String tenantId, String userId, boolean enabled);

    /**
     * Update push enabled status.
     */
    @Query("{ 'tenantId': ?0, 'userId': ?1 }")
    @Update("{ '$set': { 'pushEnabled': ?2 } }")
    long updatePushEnabled(String tenantId, String userId, boolean enabled);

    /**
     * Add muted resource.
     */
    @Query("{ 'tenantId': ?0, 'userId': ?1 }")
    @Update("{ '$set': { 'mutedResources.?2': ?3 } }")
    long addMutedResource(String tenantId, String userId, String resourceKey,
                          NotificationPreference.MutedResource mutedResource);

    /**
     * Remove muted resource.
     */
    @Query("{ 'tenantId': ?0, 'userId': ?1 }")
    @Update("{ '$unset': { 'mutedResources.?2': '' } }")
    long removeMutedResource(String tenantId, String userId, String resourceKey);
}
