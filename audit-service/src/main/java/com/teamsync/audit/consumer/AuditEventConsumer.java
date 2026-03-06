package com.teamsync.audit.consumer;

import com.teamsync.audit.config.AuditServiceProperties;
import com.teamsync.audit.constants.AuditConstants;
import com.teamsync.audit.dto.AuditEvent;
import com.teamsync.audit.exception.TamperDetectedException;
import com.teamsync.audit.model.AuditLog;
import com.teamsync.audit.model.ImmutableAuditRecord;
import com.teamsync.audit.repository.AuditLogRepository;
import com.teamsync.audit.service.ImmutableAuditService;
import io.micrometer.core.annotation.Counted;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.Map;

/**
 * Kafka consumer for audit events.
 *
 * Filters for high-value events only (deletions, permission changes, shares, failures)
 * and writes them to ImmuDB for tamper-proof storage.
 *
 * Flow:
 * 1. Receive event from teamsync.audit.events topic
 * 2. Check if event is high-value (based on action or outcome)
 * 3. Write to ImmuDB (primary, verified)
 * 4. Sync to MongoDB (secondary, for fast queries)
 * 5. Acknowledge message
 */
@Component
@Slf4j
@Validated
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class AuditEventConsumer {

    private final Optional<ImmutableAuditService> immutableAuditService;
    private final AuditLogRepository auditLogRepository;
    private final AuditServiceProperties properties;


    /**
     * High-value actions that are always stored in ImmuDB.
     */
    private static final Set<String> HIGH_VALUE_ACTIONS = Set.of(
            AuditConstants.ACTION_DELETE,
            AuditConstants.ACTION_PERMISSION_CHANGE,
            AuditConstants.ACTION_SHARE
    );

    @KafkaListener(
            topics = "audit-events",
            groupId = "audit-service-group",
            containerFactory = "auditKafkaListenerContainerFactory"
    )
    @Counted(value = "audit.events.received", description = "Number of audit events received from Kafka")
    public void handleAuditEvent(@Payload AuditEvent event, Acknowledgment ack) {
        if (event == null) {
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

        // Include failures if configured (result field in AuditEvent maps to outcome)
        if (hvProps.isIncludeFailures() && AuditConstants.RESULT_FAILURE.equalsIgnoreCase(event.getResult())) {
            return true;
        }

        // Include denied if configured (result field in AuditEvent maps to outcome)
        if (hvProps.isIncludeDenied() && AuditConstants.RESULT_DENIED.equalsIgnoreCase(event.getResult())) {
            return true;
        }

        // Check if resource type should always be tracked
        if (hvProps.getAlwaysTrackResourceTypes().contains(event.getResourceType())) {
            return true;
        }

        return false;
    }

    /**
     * Sync audit event to MongoDB for fast queries.
     * Extracts nested data from details and context maps.
     */
    private void syncToMongoDB(AuditEvent event, ImmutableAuditRecord record) {
        if (!properties.getMongodbSync().isEnabled()) {
            return;
        }

        try {
            // Extract details map data
            Map<String, Object> details = event.getDetails() != null ? event.getDetails() : Map.of();
            Map<String, Object> before = details.get("before") instanceof Map ?
                    (Map<String, Object>) details.get("before") : null;
            Map<String, Object> after = details.get("after") instanceof Map ?
                    (Map<String, Object>) details.get("after") : null;
            String resourceName = details.get("resourceName") != null ?
                    details.get("resourceName").toString() : null;
            String failureReason = details.get("failureReason") != null ?
                    details.get("failureReason").toString() : null;

            // Extract context map data
            Map<String, Object> context = event.getContext() != null ? event.getContext() : Map.of();
            String ipAddress = context.get("ipAddress") != null ?
                    context.get("ipAddress").toString() : null;
            String userAgent = context.get("userAgent") != null ?
                    context.get("userAgent").toString() : null;
            String sessionId = context.get("sessionId") != null ?
                    context.get("sessionId").toString() : null;
            String requestId = context.get("requestId") != null ?
                    context.get("requestId").toString() : null;
            String driveId = context.get("driveId") != null ?
                    context.get("driveId").toString() : null;

            // Extract PII/compliance flags
            boolean piiAccessed = context.get("piiAccessed") instanceof Boolean ?
                    (Boolean) context.get("piiAccessed") : false;
            boolean sensitiveDataAccessed = context.get("sensitiveDataAccessed") instanceof Boolean ?
                    (Boolean) context.get("sensitiveDataAccessed") : false;
            String dataClassification = context.get("dataClassification") != null ?
                    context.get("dataClassification").toString() : null;

            AuditLog auditLog = AuditLog.builder()
                    .id(event.getEventId())
                    .eventId(event.getEventId())  // Alias for backward compatibility
                    .tenantId(event.getTenantId())
                    .driveId(driveId)
                    .userId(event.getUserId())
                    .userName(event.getUserName())
                    .action(event.getAction())
                    .resourceType(event.getResourceType())
                    .resourceId(event.getResourceId())
                    .resourceName(resourceName)
                    .before(before)
                    .after(after)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .sessionId(sessionId)
                    .requestId(requestId)
                    .outcome(event.getResult())  // result -> outcome mapping
                    .failureReason(failureReason)
                    .piiAccessed(piiAccessed)
                    .sensitiveDataAccessed(sensitiveDataAccessed)
                    .dataClassification(dataClassification)
                    .eventTime(event.getEventTime())
                    .timestamp(event.getEventTime())  // Alias for backward compatibility
                    .metadata(details)  // Store full details as metadata
                    .immudbTransactionId(record.getImmudbTransactionId())
                    .immudbHashChain(record.getHashChain())
                    .hashChain(record.getHashChain())  // Keep for backward compatibility
                    .immudbVerified(record.isVerified())
                    .verified(record.isVerified())  // Keep for backward compatibility
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
