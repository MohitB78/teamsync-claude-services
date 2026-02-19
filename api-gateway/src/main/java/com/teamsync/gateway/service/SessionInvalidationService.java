package com.teamsync.gateway.service;

import com.teamsync.gateway.config.BffProperties;
import com.teamsync.gateway.model.BffSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.session.data.redis.ReactiveRedisIndexedSessionRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for invalidating sessions during backchannel logout.
 *
 * <p>SECURITY: This service enables Single Logout (SLO) by invalidating all TeamSync
 * sessions associated with a Zitadel session when the user logs out from Zitadel.
 *
 * <p>How it works:
 * <ol>
 *   <li>Zitadel calls /bff/auth/backchannel-logout with logout_token containing the Zitadel session ID</li>
 *   <li>This service scans all Redis sessions to find those with matching zitadelSessionId</li>
 *   <li>Matching sessions are invalidated (deleted from Redis)</li>
 * </ol>
 *
 * <p>Note: This implementation scans all sessions which may be slow for large deployments.
 * Consider using a Redis secondary index (zitadelSessionId → sessionId mapping) for production
 * scale if backchannel logout latency becomes an issue.
 *
 * @see BffSession#getZitadelSessionId()
 */
@Service
@ConditionalOnProperty(name = "teamsync.bff.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class SessionInvalidationService {

    private final ReactiveRedisTemplate<String, Object> bffSessionRedisTemplate;
    private final BffProperties bffProperties;

    /**
     * Invalidate all sessions associated with a Zitadel session ID.
     *
     * <p>This method is called during backchannel logout to implement Single Logout (SLO).
     * It finds and invalidates all TeamSync sessions that were created from the specified
     * Zitadel session.
     *
     * @param zitadelSessionId The Zitadel session ID from the logout_token
     * @return Mono containing the number of sessions invalidated
     */
    public Mono<Integer> invalidateSessionsByZitadelId(String zitadelSessionId) {
        if (zitadelSessionId == null || zitadelSessionId.isBlank()) {
            log.warn("Backchannel logout called with null/blank zitadelSessionId");
            return Mono.just(0);
        }

        String redisNamespace = bffProperties.session().redisNamespace();
        // Spring Session Redis uses pattern: {namespace}:sessions:{sessionId}
        String sessionPattern = redisNamespace + ":sessions:*";

        log.info("Starting backchannel logout for Zitadel session: {}", zitadelSessionId);

        AtomicInteger invalidatedCount = new AtomicInteger(0);

        return bffSessionRedisTemplate.keys(sessionPattern)
            .flatMap(sessionKey -> {
                // Extract session ID from key
                String sessionId = extractSessionId(sessionKey, redisNamespace);
                if (sessionId == null) {
                    return Mono.empty();
                }

                // Get the session data and check if it matches
                return bffSessionRedisTemplate.opsForHash()
                    .get(sessionKey, "sessionAttr:" + BffSession.SESSION_KEY)
                    .flatMap(sessionData -> {
                        if (sessionData instanceof BffSession bffSession) {
                            if (zitadelSessionId.equals(bffSession.getZitadelSessionId())) {
                                log.debug("Found matching session to invalidate: {}", sessionId);
                                // Delete the session
                                return bffSessionRedisTemplate.delete(sessionKey)
                                    .doOnSuccess(deleted -> {
                                        if (deleted > 0) {
                                            invalidatedCount.incrementAndGet();
                                            log.info("Invalidated session {} for user {} during backchannel logout",
                                                sessionId, bffSession.getEmail());
                                        }
                                    })
                                    .thenReturn(sessionId);
                            }
                        }
                        return Mono.empty();
                    });
            })
            .collectList()
            .map(invalidatedSessions -> {
                int count = invalidatedCount.get();
                log.info("Backchannel logout complete for Zitadel session {}: {} sessions invalidated",
                    zitadelSessionId, count);
                return count;
            })
            .onErrorResume(e -> {
                log.error("Error during backchannel logout for Zitadel session {}: {}",
                    zitadelSessionId, e.getMessage(), e);
                return Mono.just(0);
            });
    }

    /**
     * Invalidate all sessions for a specific user ID.
     *
     * <p>This can be used for administrative actions like:
     * <ul>
     *   <li>Force logout of a user</li>
     *   <li>Session cleanup after account deletion</li>
     *   <li>Security incident response</li>
     * </ul>
     *
     * @param userId The Zitadel user ID (subject claim)
     * @return Mono containing the number of sessions invalidated
     */
    public Mono<Integer> invalidateSessionsByUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            log.warn("Session invalidation called with null/blank userId");
            return Mono.just(0);
        }

        String redisNamespace = bffProperties.session().redisNamespace();
        String sessionPattern = redisNamespace + ":sessions:*";

        log.info("Starting session invalidation for user: {}", userId);

        AtomicInteger invalidatedCount = new AtomicInteger(0);

        return bffSessionRedisTemplate.keys(sessionPattern)
            .flatMap(sessionKey -> {
                return bffSessionRedisTemplate.opsForHash()
                    .get(sessionKey, "sessionAttr:" + BffSession.SESSION_KEY)
                    .flatMap(sessionData -> {
                        if (sessionData instanceof BffSession bffSession) {
                            if (userId.equals(bffSession.getUserId())) {
                                log.debug("Found session to invalidate for user {}", userId);
                                return bffSessionRedisTemplate.delete(sessionKey)
                                    .doOnSuccess(deleted -> {
                                        if (deleted > 0) {
                                            invalidatedCount.incrementAndGet();
                                        }
                                    })
                                    .thenReturn(true);
                            }
                        }
                        return Mono.empty();
                    });
            })
            .collectList()
            .map(results -> {
                int count = invalidatedCount.get();
                log.info("Session invalidation complete for user {}: {} sessions invalidated", userId, count);
                return count;
            })
            .onErrorResume(e -> {
                log.error("Error invalidating sessions for user {}: {}", userId, e.getMessage(), e);
                return Mono.just(0);
            });
    }

    /**
     * Clear ALL sessions from Redis.
     *
     * <p>ADMIN USE ONLY: This method deletes all BFF sessions without attempting to read them.
     * Use this for:
     * <ul>
     *   <li>Clearing corrupted sessions that cannot be deserialized</li>
     *   <li>Emergency session cleanup</li>
     *   <li>Post-deployment cleanup after schema changes</li>
     * </ul>
     *
     * <p>All users will be required to re-authenticate after this operation.
     *
     * @return Mono containing the number of sessions cleared
     */
    public Mono<Long> clearAllSessions() {
        String redisNamespace = bffProperties.session().redisNamespace();
        // Spring Session Redis uses pattern: {namespace}:sessions:{sessionId}
        String sessionPattern = redisNamespace + ":sessions:*";

        log.warn("ADMIN: Starting bulk session cleanup - all users will need to re-authenticate");

        return bffSessionRedisTemplate.keys(sessionPattern)
            .collectList()
            .flatMap(keys -> {
                if (keys.isEmpty()) {
                    log.info("No sessions found to clear");
                    return Mono.just(0L);
                }

                log.info("Found {} sessions to clear", keys.size());

                // Delete all session keys
                return bffSessionRedisTemplate.delete(Flux.fromIterable(keys))
                    .doOnSuccess(count -> log.warn("ADMIN: Cleared {} sessions from Redis", count));
            })
            .onErrorResume(e -> {
                log.error("Error clearing sessions: {}", e.getMessage(), e);
                return Mono.just(0L);
            });
    }

    /**
     * Extract session ID from Redis key.
     *
     * @param key Redis key in format "{namespace}:sessions:{sessionId}"
     * @param namespace The session namespace
     * @return Session ID or null if key format is invalid
     */
    private String extractSessionId(String key, String namespace) {
        String prefix = namespace + ":sessions:";
        if (key.startsWith(prefix)) {
            return key.substring(prefix.length());
        }
        return null;
    }
}
