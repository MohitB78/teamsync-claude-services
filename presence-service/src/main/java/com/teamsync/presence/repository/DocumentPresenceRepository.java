package com.teamsync.presence.repository;

import com.teamsync.presence.config.PresenceProperties;
import com.teamsync.presence.model.DocumentParticipant;
import com.teamsync.presence.model.DocumentPresence;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
@Slf4j
public class DocumentPresenceRepository {

    private final RedisTemplate<String, Object> redisTemplate;
    private final PresenceProperties presenceProperties;

    private static final Duration DOCUMENT_TTL = Duration.ofHours(24);
    private static final Duration PARTICIPANT_TTL = Duration.ofHours(2);

    public void saveDocument(DocumentPresence document) {
        String key = DocumentPresence.buildRedisKey(document.getTenantId(), document.getDocumentId());
        redisTemplate.opsForValue().set(key, document, DOCUMENT_TTL);

        // Add to active documents set
        String activeDocsKey = DocumentPresence.buildActiveDocumentsSetKey(document.getTenantId());
        redisTemplate.opsForSet().add(activeDocsKey, document.getDocumentId());
        redisTemplate.expire(activeDocsKey, DOCUMENT_TTL);

        log.debug("Saved document presence: {} in tenant: {}", document.getDocumentId(), document.getTenantId());
    }

    public Optional<DocumentPresence> findDocument(String tenantId, String documentId) {
        String key = DocumentPresence.buildRedisKey(tenantId, documentId);
        Object result = redisTemplate.opsForValue().get(key);

        if (result instanceof DocumentPresence) {
            return Optional.of((DocumentPresence) result);
        }
        return Optional.empty();
    }

    public void addParticipant(DocumentParticipant participant) {
        String key = DocumentParticipant.buildRedisKey(
                participant.getTenantId(),
                participant.getDocumentId(),
                participant.getUserId()
        );
        redisTemplate.opsForValue().set(key, participant, PARTICIPANT_TTL);

        // Add to document participants sets
        String viewersKey = DocumentPresence.buildViewersSetKey(participant.getTenantId(), participant.getDocumentId());
        String editorsKey = DocumentPresence.buildEditorsSetKey(participant.getTenantId(), participant.getDocumentId());

        redisTemplate.opsForSet().add(viewersKey, participant.getUserId());
        redisTemplate.expire(viewersKey, DOCUMENT_TTL);

        if (participant.isEditor()) {
            redisTemplate.opsForSet().add(editorsKey, participant.getUserId());
            redisTemplate.expire(editorsKey, DOCUMENT_TTL);
        }

        // Track which documents user is in
        String userDocsKey = DocumentParticipant.buildUserDocumentsSetKey(
                participant.getTenantId(),
                participant.getUserId()
        );
        redisTemplate.opsForSet().add(userDocsKey, participant.getDocumentId());
        redisTemplate.expire(userDocsKey, DOCUMENT_TTL);

        log.debug("Added participant {} to document {} (mode: {})",
                participant.getUserId(), participant.getDocumentId(), participant.getMode());
    }

    public void updateParticipant(DocumentParticipant participant) {
        String key = DocumentParticipant.buildRedisKey(
                participant.getTenantId(),
                participant.getDocumentId(),
                participant.getUserId()
        );
        redisTemplate.opsForValue().set(key, participant, PARTICIPANT_TTL);

        log.debug("Updated participant {} in document {}", participant.getUserId(), participant.getDocumentId());
    }

    public Optional<DocumentParticipant> findParticipant(String tenantId, String documentId, String userId) {
        String key = DocumentParticipant.buildRedisKey(tenantId, documentId, userId);
        Object result = redisTemplate.opsForValue().get(key);

        if (result instanceof DocumentParticipant) {
            return Optional.of((DocumentParticipant) result);
        }
        return Optional.empty();
    }

    public void removeParticipant(String tenantId, String documentId, String userId) {
        String key = DocumentParticipant.buildRedisKey(tenantId, documentId, userId);
        redisTemplate.delete(key);

        // Remove from sets
        String viewersKey = DocumentPresence.buildViewersSetKey(tenantId, documentId);
        String editorsKey = DocumentPresence.buildEditorsSetKey(tenantId, documentId);
        String userDocsKey = DocumentParticipant.buildUserDocumentsSetKey(tenantId, userId);

        redisTemplate.opsForSet().remove(viewersKey, userId);
        redisTemplate.opsForSet().remove(editorsKey, userId);
        redisTemplate.opsForSet().remove(userDocsKey, documentId);

        // Clean up document if no more participants
        Long viewerCount = redisTemplate.opsForSet().size(viewersKey);
        if (viewerCount == null || viewerCount == 0) {
            cleanupDocument(tenantId, documentId);
        }

        log.debug("Removed participant {} from document {}", userId, documentId);
    }

    /**
     * Maximum number of participants to return to prevent memory exhaustion.
     * SECURITY FIX (Round 14 #M2): Added limit to prevent DoS.
     */
    private static final int MAX_PARTICIPANTS_PER_DOCUMENT = 500;

    /**
     * Find all participants for a document.
     * SECURITY FIX (Round 14 #M2): Changed from N+1 query pattern to batch multiGet
     * to prevent Redis round-trip amplification attacks.
     */
    public List<DocumentParticipant> findAllParticipants(String tenantId, String documentId) {
        String viewersKey = DocumentPresence.buildViewersSetKey(tenantId, documentId);
        Set<Object> userIds = redisTemplate.opsForSet().members(viewersKey);

        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }

        // SECURITY: Limit to prevent memory exhaustion from very large sets
        List<String> userIdList = userIds.stream()
                .map(Object::toString)
                .limit(MAX_PARTICIPANTS_PER_DOCUMENT)
                .toList();

        if (userIdList.size() < userIds.size()) {
            log.warn("SECURITY: Document {} has {} participants, limited to {}",
                    documentId, userIds.size(), MAX_PARTICIPANTS_PER_DOCUMENT);
        }

        // SECURITY FIX: Use multiGet instead of N individual calls
        List<String> keys = userIdList.stream()
                .map(userId -> DocumentParticipant.buildRedisKey(tenantId, documentId, userId))
                .toList();

        List<Object> results = redisTemplate.opsForValue().multiGet(keys);

        if (results == null) {
            return Collections.emptyList();
        }

        return results.stream()
                .filter(obj -> obj instanceof DocumentParticipant)
                .map(obj -> (DocumentParticipant) obj)
                .toList();
    }

    public List<DocumentParticipant> findViewers(String tenantId, String documentId) {
        return findAllParticipants(tenantId, documentId).stream()
                .filter(DocumentParticipant::isViewer)
                .collect(Collectors.toList());
    }

    /**
     * Find all editors for a document.
     * SECURITY FIX (Round 14 #M2): Changed from N+1 query pattern to batch multiGet.
     */
    public List<DocumentParticipant> findEditors(String tenantId, String documentId) {
        String editorsKey = DocumentPresence.buildEditorsSetKey(tenantId, documentId);
        Set<Object> userIds = redisTemplate.opsForSet().members(editorsKey);

        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }

        // SECURITY: Limit editors (same as participants limit)
        List<String> userIdList = userIds.stream()
                .map(Object::toString)
                .limit(MAX_PARTICIPANTS_PER_DOCUMENT)
                .toList();

        // SECURITY FIX: Use multiGet instead of N individual calls
        List<String> keys = userIdList.stream()
                .map(userId -> DocumentParticipant.buildRedisKey(tenantId, documentId, userId))
                .toList();

        List<Object> results = redisTemplate.opsForValue().multiGet(keys);

        if (results == null) {
            return Collections.emptyList();
        }

        return results.stream()
                .filter(obj -> obj instanceof DocumentParticipant)
                .map(obj -> (DocumentParticipant) obj)
                .toList();
    }

    public int countViewers(String tenantId, String documentId) {
        String key = DocumentPresence.buildViewersSetKey(tenantId, documentId);
        Long size = redisTemplate.opsForSet().size(key);
        return size != null ? size.intValue() : 0;
    }

    public int countEditors(String tenantId, String documentId) {
        String key = DocumentPresence.buildEditorsSetKey(tenantId, documentId);
        Long size = redisTemplate.opsForSet().size(key);
        return size != null ? size.intValue() : 0;
    }

    public Set<String> findDocumentsByUser(String tenantId, String userId) {
        String key = DocumentParticipant.buildUserDocumentsSetKey(tenantId, userId);
        Set<Object> docIds = redisTemplate.opsForSet().members(key);

        if (docIds == null || docIds.isEmpty()) {
            return Collections.emptySet();
        }

        return docIds.stream()
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    public Set<String> findActiveDocuments(String tenantId) {
        String key = DocumentPresence.buildActiveDocumentsSetKey(tenantId);
        Set<Object> docIds = redisTemplate.opsForSet().members(key);

        if (docIds == null || docIds.isEmpty()) {
            return Collections.emptySet();
        }

        return docIds.stream()
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    public void removeUserFromAllDocuments(String tenantId, String userId) {
        Set<String> documentIds = findDocumentsByUser(tenantId, userId);
        for (String documentId : documentIds) {
            removeParticipant(tenantId, documentId, userId);
        }
        log.debug("Removed user {} from all documents in tenant {}", userId, tenantId);
    }

    public List<DocumentParticipant> findIdleParticipants(String tenantId, String documentId, int idleThresholdSeconds) {
        return findAllParticipants(tenantId, documentId).stream()
                .filter(p -> p.isIdle(idleThresholdSeconds))
                .collect(Collectors.toList());
    }

    public Set<String> getUsedColors(String tenantId, String documentId) {
        return findAllParticipants(tenantId, documentId).stream()
                .map(DocumentParticipant::getAssignedColor)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private void cleanupDocument(String tenantId, String documentId) {
        String docKey = DocumentPresence.buildRedisKey(tenantId, documentId);
        String viewersKey = DocumentPresence.buildViewersSetKey(tenantId, documentId);
        String editorsKey = DocumentPresence.buildEditorsSetKey(tenantId, documentId);
        String activeDocsKey = DocumentPresence.buildActiveDocumentsSetKey(tenantId);

        redisTemplate.delete(docKey);
        redisTemplate.delete(viewersKey);
        redisTemplate.delete(editorsKey);
        redisTemplate.opsForSet().remove(activeDocsKey, documentId);

        log.debug("Cleaned up document presence for document {} in tenant {}", documentId, tenantId);
    }
}
