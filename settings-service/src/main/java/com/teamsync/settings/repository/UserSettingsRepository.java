package com.teamsync.settings.repository;

import com.teamsync.settings.model.UserSettings;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for user settings.
 */
@Repository
public interface UserSettingsRepository extends MongoRepository<UserSettings, String> {

    /**
     * Find user settings by tenant and user ID.
     */
    Optional<UserSettings> findByTenantIdAndUserId(String tenantId, String userId);

    /**
     * Check if settings exist for a user.
     */
    boolean existsByTenantIdAndUserId(String tenantId, String userId);

    /**
     * Delete all settings for a user.
     */
    void deleteByTenantIdAndUserId(String tenantId, String userId);

    /**
     * Delete all settings for a tenant.
     */
    void deleteByTenantId(String tenantId);
}
