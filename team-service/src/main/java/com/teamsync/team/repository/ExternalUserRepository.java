package com.teamsync.team.repository;

import com.teamsync.team.model.ExternalUser;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for external users.
 */
@Repository
public interface ExternalUserRepository extends MongoRepository<ExternalUser, String> {

    /**
     * Find external user by tenant and email.
     */
    Optional<ExternalUser> findByTenantIdAndEmail(String tenantId, String email);

    /**
     * Check if external user exists.
     */
    boolean existsByTenantIdAndEmail(String tenantId, String email);
}
