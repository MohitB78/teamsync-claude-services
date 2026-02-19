package com.teamsync.team.repository;

import com.teamsync.team.model.MagicLinkToken;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * Repository for magic link tokens.
 */
@Repository
public interface MagicLinkTokenRepository extends MongoRepository<MagicLinkToken, String> {

    /**
     * Find a token by its hash.
     */
    Optional<MagicLinkToken> findByTokenHashAndUsedFalseAndExpiresAtAfter(
            String tokenHash, Instant now);

    /**
     * Find unused token by email.
     */
    Optional<MagicLinkToken> findByTenantIdAndEmailAndUsedFalseAndExpiresAtAfter(
            String tenantId, String email, Instant now);

    /**
     * Delete expired tokens.
     */
    void deleteByExpiresAtBefore(Instant time);

    /**
     * Count active tokens for an email (rate limiting).
     */
    long countByTenantIdAndEmailAndUsedFalseAndExpiresAtAfter(
            String tenantId, String email, Instant now);
}
