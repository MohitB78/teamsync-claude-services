package com.teamsync.audit.consumer;

import com.teamsync.audit.config.AuditServiceProperties;
import com.teamsync.audit.exception.TamperDetectedException;
import com.teamsync.audit.model.AuditLog;
import com.teamsync.audit.model.ImmutableAuditRecord;
import com.teamsync.audit.repository.AuditLogRepository;
import com.teamsync.audit.service.ImmutableAuditService;
import com.teamsync.common.event.AuditEvent;
import io.micrometer.core.annotation.Counted;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * Kafka consumer for audit events.
 *
 * Filters for high-value events only (deletions, permission changes, shares, failures)
 * and writes them to ImmuDB for tamper-proof storage.
 *
 * Flow:
 * 1. Receive event from teamsync.audit.events topic
 * 2. Check if event is high-value (based on action or outcome)
 * 3. Deduplicate using Redis
 * 4. Write to ImmuDB (primary, verified)
 * 5. Sync to MongoDB (secondary, for fast queries)
 * 6. Acknowledge message
 */
@Component
@Slf4j
@Validated
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class AuditEventConsumer {

    private final Optional<ImmutableAuditService> immutableAuditService;
    private final AuditLogRepository auditLogRepository;
    private final StringRedisTemplate redisTemplate;
    private final AuditServiceProperties properties;

    @Autowired
    public AuditEventConsumer(
            @Autowired(required = false) ImmutableAuditService immutableAuditService,
            AuditLogRepository auditLogRepository,
            StringRedisTemplate redisTemplate,
            AuditServiceProperties properties) {
        this.immutableAuditService = Optional.ofNullable(immutableAuditService);
        this.auditLogRepository = auditLogRepository;
        this.redisTemplate = redisTemplate;
        this.properties = properties;

        if (this.immutableAuditService.isEmpty()) {
            log.warn("ImmuDB service not available - audit events will only be stored in MongoDB");
        }
    }

    /**
     * High-value actions that are always stored in ImmuDB.
     */
    private static final Set<String> HIGH_VALUE_ACTIONS = Set.of(
            AuditEvent.ACTION_DELETE,
            AuditEvent.ACTION_PERMISSION_CHANGE,
            AuditEvent.ACTION_SHARE
    );

    @KafkaListener(
            topics = "teamsync.audit.events",
            groupId = "audit-service",
            containerFactory = "auditKafkaListenerContainerFactory"
    )
    @Counted(value = "audit.events.received", description = "Number of audit events received from Kafka")
    public void handleAuditEvent(@Payload AuditEvent event, Acknowledgment ack) {
        if (event == null || event.getEventId() == null) {
            log.warn("Received null or invalid audit event");
            ack.acknowledge();
            return;
        }

        log.debug("Received audit event: eventId={}, action={}, resource={}/{}",
                event.getEventId(), event.getAction(), event.getResourceType(), event.getResourceId());

        try {
            // Check if this is a high-value event
            if (!isHighValueEvent(event)) {
                log.trace("Skipping non-high-value event: {}", event.getEventId());
                ack.acknowledge();
                return;
            }

            // Deduplication check
            if (isDuplicateEvent(event.getEventId())) {
                log.debug("Duplicate audit event skipped: {}", event.getEventId());
                ack.acknowledge();
                return;
            }

            // Write to ImmuDB if available (primary, verified)
            ImmutableAuditRecord record = null;
            if (immutableAuditService.isPresent()) {
                record = immutableAuditService.get().writeAuditEvent(event);
                log.info("Audit event persisted to ImmuDB: eventId={}, action={}, immudbVerified={}",
                        event.getEventId(), event.getAction(), record.isVerified());
            } else {
                // Create a placeholder record for MongoDB-only mode
                record = ImmutableAuditRecord.builder()
                        .eventId(event.getEventId())
                        .verified(false)
                        .verifiedAt(Instant.now())
                        .build();
                log.info("Audit event persisted (MongoDB only): eventId={}, action={}",
                        event.getEventId(), event.getAction());
            }

            // Sync to MongoDB (secondary for ImmuDB mode, primary if ImmuDB disabled)
            syncToMongoDB(event, record);

            ack.acknowledge();

        } catch (TamperDetectedException e) {
            // CRITICAL: ImmuDB verification failed - potential tampering
            log.error("CRITICAL: Tamper detected for event {}: {}", event.getEventId(), e.getMessage());
            // Don't acknowledge - this needs investigation
            // In production, trigger alerts here
            throw e;

        } catch (Exception e) {
            log.error("Failed to process audit event {}: {}", event.getEventId(), e.getMessage(), e);
            // Don't acknowledge - will be retried
            throw e;
        }
    }

    /**
     * Determine if an event is high-value and should be stored in ImmuDB.
     */
    private boolean isHighValueEvent(AuditEvent event) {
        AuditServiceProperties.HighValueEventsProperties hvProps = properties.getHighValueEvents();

        // Check if action is in the high-value list
        if (hvProps.getActions().contains(event.getAction())) {
            return true;
        }

        // Check configured actions
        if (HIGH_VALUE_ACTIONS.contains(event.getAction())) {
            return true;
        }

        // Include failures if configured
        if (hvProps.isIncludeFailures() && AuditEvent.OUTCOME_FAILURE.equals(event.getOutcome())) {
            return true;
        }

        // Include denied if configured
        if (hvProps.isIncludeDenied() && AuditEvent.OUTCOME_DENIED.equals(event.getOutcome())) {
            return true;
        }

        // Check if resource type should always be tracked
        if (hvProps.getAlwaysTrackResourceTypes().contains(event.getResourceType())) {
            return true;
        }

        return false;
    }

    /**
     * Check if event was already processed (deduplication).
     */
    private boolean isDuplicateEvent(String eventId) {
        if (!properties.getDeduplication().isEnabled()) {
            return false;
        }

        String key = properties.getDeduplication().getRedisPrefix() + eventId;
        Duration ttl = Duration.ofHours(properties.getDeduplication().getTtlHours());

        // setIfAbsent returns true if key was set (not duplicate)
        // returns false if key already exists (duplicate)
        Boolean wasSet = redisTemplate.opsForValue().setIfAbsent(key, "1", ttl);
        return Boolean.FALSE.equals(wasSet);
    }

    /**
     * Sync audit event to MongoDB for fast queries.
     */
    private void syncToMongoDB(AuditEvent event, ImmutableAuditRecord record) {
        if (!properties.getMongodbSync().isEnabled()) {
            return;
        }

        try {
            AuditLog auditLog = AuditLog.builder()
                    .id(event.getEventId())
                    .tenantId(event.getTenantId())
                    .driveId(event.getDriveId())
                    .userId(event.getUserId())
                    .userName(event.getUserName())
                    .action(event.getAction())
                    .resourceType(event.getResourceType())
                    .resourceId(event.getResourceId())
                    .resourceName(event.getResourceName())
                    .before(event.getBefore())
                    .after(event.getAfter())
                    .ipAddress(event.getIpAddress())
                    .userAgent(event.getUserAgent())
                    .sessionId(event.getSessionId())
                    .requestId(event.getRequestId())
                    .outcome(event.getOutcome())
                    .failureReason(event.getFailureReason())
                    .piiAccessed(event.isPiiAccessed())
                    .sensitiveDataAccessed(event.isSensitiveDataAccessed())
                    .dataClassification(event.getDataClassification())
                    .eventTime(event.getTimestamp())
                    .immudbTransactionId(record.getImmudbTransactionId())
                    .hashChain(record.getHashChain())
                    .immudbVerified(record.isVerified())
                    .verifiedAt(record.getVerifiedAt())
                    .build();

            auditLogRepository.save(auditLog);
            log.debug("Synced audit event to MongoDB: {}", event.getEventId());

        } catch (Exception e) {
            log.warn("Failed to sync audit event to MongoDB: {}", e.getMessage());
            // Don't fail the whole operation - ImmuDB is the source of truth
        }
    }
}
