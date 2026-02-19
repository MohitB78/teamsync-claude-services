package com.teamsync.signing.repository;

import com.teamsync.signing.model.SigningTemplate;
import com.teamsync.signing.model.SigningTemplate.TemplateStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for SigningTemplate entities.
 */
@Repository
public interface SigningTemplateRepository extends MongoRepository<SigningTemplate, String> {

    /**
     * Find a template by ID and tenant.
     */
    Optional<SigningTemplate> findByIdAndTenantId(String id, String tenantId);

    /**
     * Find all templates for a tenant.
     */
    Page<SigningTemplate> findByTenantId(String tenantId, Pageable pageable);

    /**
     * Find active templates for a tenant.
     */
    @Query("{ 'tenantId': ?0, 'status': 'ACTIVE' }")
    List<SigningTemplate> findActiveByTenantId(String tenantId);

    /**
     * Find templates by tenant and status.
     */
    Page<SigningTemplate> findByTenantIdAndStatus(String tenantId, TemplateStatus status, Pageable pageable);

    /**
     * Find template by tenant and name.
     */
    Optional<SigningTemplate> findByTenantIdAndName(String tenantId, String name);

    /**
     * Check if a template name exists for a tenant.
     */
    boolean existsByTenantIdAndName(String tenantId, String name);

    /**
     * Count templates by tenant and status.
     */
    long countByTenantIdAndStatus(String tenantId, TemplateStatus status);
}
