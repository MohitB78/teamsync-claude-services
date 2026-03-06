package com.teamsync.audit.service;

import com.teamsync.audit.config.AuditServiceProperties;
import com.teamsync.audit.dto.AuditEvent;
import com.teamsync.audit.model.AuditLog;
import com.teamsync.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for computing and verifying hash chains.
 *
 * Hash chain format: SHA-256(previousHashChain + eventData)
 * This creates a tamper-evident chain where modifying any event
 * breaks the chain for all subsequent events.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HashChainService {

    private final AuditLogRepository auditLogRepository;
    private final AuditServiceProperties properties;

    /**
     * Genesis hash used for the first event in a chain.
     */
    private static final String GENESIS_HASH = "GENESIS";

    /**
     * Cache of last hash per tenant for chain continuity.
     * In production, this should be persisted to handle restarts.
     */
    private final Map<String, String> lastHashByTenant = new ConcurrentHashMap<>();

    /**
     * Compute the hash chain for an audit event.
     *
     * @param tenantId The tenant ID
     * @param event The audit event
     * @return The computed hash chain value
     */
    public String computeHashChain(String tenantId, AuditEvent event) {
        if (!properties.getVerification().isHashChainEnabled()) {
            return null;
        }

        // Get previous hash for this tenant
        String previousHash = getOrLoadPreviousHash(tenantId);

        // Build event data string for hashing
        String eventData = buildEventDataString(event);

        // Compute chain hash: SHA-256(previousHash + eventData)
        String chainHash = sha256(previousHash + "|" + eventData);

        // Update cache
        lastHashByTenant.put(tenantId, chainHash);

        log.debug("Computed hash chain for event {}: {} (prev: {})",
                event.getEventId(), chainHash.substring(0, 16) + "...", previousHash.substring(0, 8) + "...");

        return chainHash;
    }

    /**
     * Compute the hash chain for a signature audit event.
     */
    public String computeSignatureHashChain(String tenantId, String eventId, String requestId,
                                            String actorEmail, String eventType, Instant timestamp) {
        if (!properties.getVerification().isHashChainEnabled()) {
            return null;
        }

        // Use separate namespace for signature events
        String cacheKey = tenantId + ":sig";
        String previousHash = lastHashByTenant.getOrDefault(cacheKey, GENESIS_HASH);

        String eventData = String.join("|",
            eventId,
            tenantId,
            requestId,
            actorEmail,
            eventType,
            timestamp.toString()
        );

        String chainHash = sha256(previousHash + "|" + eventData);
        lastHashByTenant.put(cacheKey, chainHash);

        return chainHash;
    }

    /**
     * Verify the hash chain integrity for a list of audit records.
     *
     * @param tenantId The tenant ID
     * @param records List of audit logs in chronological order (oldest first)
     * @return true if the hash chain is intact, false if tampering detected
     */
    public boolean verifyHashChain(String tenantId, List<AuditLog> records) {
        if (records == null || records.isEmpty()) {
            return true;
        }

        String expectedHash = GENESIS_HASH;

        for (AuditLog record : records) {
            String eventData = buildEventDataStringFromLog(record);
            String computedHash = sha256(expectedHash + "|" + eventData);

            if (!computedHash.equals(record.getHashChain())) {
                log.error("TAMPER DETECTED: Hash chain broken at event {} for tenant {}",
                        record.getId(), tenantId);
                log.error("Expected hash: {}, Actual hash: {}", computedHash, record.getHashChain());
                return false;
            }

            expectedHash = computedHash;
        }

        log.debug("Hash chain verified for {} events in tenant {}", records.size(), tenantId);
        return true;
    }

    /**
     * Get the latest hash chain value for a tenant.
     */
    public String getLatestHashChain(String tenantId) {
        return getOrLoadPreviousHash(tenantId);
    }

    /**
     * Get or load the previous hash for a tenant.
     * If not in cache, loads from the database.
     */
    private String getOrLoadPreviousHash(String tenantId) {
        return lastHashByTenant.computeIfAbsent(tenantId, tid -> {
            // Load from MongoDB mirror
            return auditLogRepository.findLatestByTenantId(tid)
                    .map(AuditLog::getHashChain)
                    .orElse(GENESIS_HASH);
        });
    }

    /**
     * Build the event data string for hashing from an AuditEvent.
     */
    private String buildEventDataString(AuditEvent event) {
        return String.join("|",
            nullSafe(event.getEventId()),
            nullSafe(event.getTenantId()),
            nullSafe(event.getUserId()),
            nullSafe(event.getAction()),
            nullSafe(event.getResourceType()),
            nullSafe(event.getResourceId())
        );
    }

    /**
     * Build the event data string for verification from an AuditLog.
     */
    private String buildEventDataStringFromLog(AuditLog log) {
        return String.join("|",
            nullSafe(log.getId()),
            nullSafe(log.getTenantId()),
            nullSafe(log.getUserId()),
            nullSafe(log.getAction()),
            nullSafe(log.getResourceType()),
            nullSafe(log.getResourceId()),
            nullSafe(log.getOutcome()),
            log.getEventTime() != null ? log.getEventTime().toString() : ""
        );
    }

    /**
     * Compute SHA-256 hash of input string.
     */
    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }

    /**
     * Clear the hash cache for a tenant (used after purge operations).
     */
    public void clearCache(String tenantId) {
        lastHashByTenant.remove(tenantId);
        lastHashByTenant.remove(tenantId + ":sig");
    }
}
