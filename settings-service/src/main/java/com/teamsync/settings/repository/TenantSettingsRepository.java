package com.teamsync.settings.repository;

import com.teamsync.settings.model.TenantSettings;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for tenant settings.
 */
@Repository
public interface TenantSettingsRepository extends MongoRepository<TenantSettings, String> {

    /**
     * Find tenant settings by tenant ID.
     */
    Optional<TenantSettings> findByTenantId(String tenantId);

    /**
     * Check if settings exist for a tenant.
     */
    boolean existsByTenantId(String tenantId);

    /**
     * Delete settings for a tenant.
     */
    void deleteByTenantId(String tenantId);
}
