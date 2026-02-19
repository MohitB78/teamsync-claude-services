package com.teamsync.team.repository;

import com.teamsync.team.model.PortalSession;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * Repository for portal sessions.
 */
@Repository
public interface PortalSessionRepository extends MongoRepository<PortalSession, String> {

    /**
     * Find session by refresh token hash.
     */
    Optional<PortalSession> findByRefreshTokenHashAndExpiresAtAfter(
            String refreshTokenHash, Instant now);

    /**
     * Find active session by user ID.
     */
    Optional<PortalSession> findByUserIdAndExpiresAtAfter(String userId, Instant now);

    /**
     * Delete all sessions for a user.
     */
    void deleteByUserId(String userId);

    /**
     * Delete expired sessions.
     */
    void deleteByExpiresAtBefore(Instant time);
}
