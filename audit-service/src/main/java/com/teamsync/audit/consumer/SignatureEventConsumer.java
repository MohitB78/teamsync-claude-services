package com.teamsync.audit.consumer;

import com.teamsync.audit.dto.SignatureAuditEvent;
import com.teamsync.audit.model.AuditLog;
import com.teamsync.audit.model.ImmutableAuditRecord;
import com.teamsync.audit.repository.AuditLogRepository;
import com.teamsync.audit.service.ImmutableAuditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Kafka consumer for signature audit events.
 * Writes ALL signature events to ImmuDB for legal compliance.
 *
 * Unlike AuditEventConsumer which filters for high-value events,
 * this consumer stores ALL signature events because they are
 * legally required for e-signature compliance.
 */
@Component
@Slf4j
public class SignatureEventConsumer {

    private final Optional<ImmutableAuditService> immutableAuditService;
    private final AuditLogRepository auditLogRepository;

    @Autowired
    public SignatureEventConsumer(
            @Autowired(required = false) ImmutableAuditService immutableAuditService,
            AuditLogRepository auditLogRepository) {
        this.immutableAuditService = Optional.ofNullable(immutableAuditService);
        this.auditLogRepository = auditLogRepository;

        if (this.immutableAuditService.isEmpty()) {
            log.warn("ImmuDB service not available - signature events will only be stored in MongoDB");
        }
    }

    @KafkaListener(
            topics = "signature-events",
            groupId = "signature-service-group",
            containerFactory = "signatureAuditKafkaListenerContainerFactory"
    )
    public void consumeSignatureEvent(SignatureAuditEvent event) {
        if (event == null ) {
            log.warn("Received null or invalid signature audit event");
            return;
        }

        try {
            log.info("Processing signature audit event: id={}, type={}, request={}, actor={}",
                    event.getEventId(), event.getEventType(), event.getRequestId(), event.getActorEmail());

            // 1. Write to ImmuDB if available (primary - tamper-proof)
            ImmutableAuditRecord record;
            if (immutableAuditService.isPresent()) {
                record = immutableAuditService.get().writeSignatureAuditEvent(event);
                log.info("Signature audit event written to ImmuDB: id={}, immuTxId={}",
                        event.getEventId(), record.getImmudbTransactionId());
            } else {
                // Create a placeholder record for MongoDB-only mode
                record = ImmutableAuditRecord.builder()
                        .eventId(event.getEventId())
                        .verified(false)
                        .verifiedAt(Instant.now())
                        .build();
                log.info("Signature audit event processed (MongoDB only): id={}",
                        event.getEventId());
            }

            // 2. Write to MongoDB (secondary for ImmuDB mode, primary if ImmuDB disabled)
            syncToMongoDB(event, record);

        } catch (Exception e) {
            log.error("Failed to process signature audit event: id={}", event.getEventId(), e);
            throw e;
        }
    }

    /**
     * Sync signature event to MongoDB for fast queries.
     */
    private void syncToMongoDB(SignatureAuditEvent event, ImmutableAuditRecord record) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .eventId(event.getEventId())
                    .tenantId(event.getTenantId())
                    .userId(event.getActorId())
                    .userName(event.getActorName())
                    .action("SIGNATURE_" + event.getEventType())
                    .resourceType("SIGNATURE_REQUEST")
                    .resourceId(event.getRequestId())
                    .resourceName(event.getDescription())
                    .ipAddress(event.getIpAddress())
                    .userAgent(event.getUserAgent())
                    .sessionId(event.getSessionId())
                    .outcome("SUCCESS")
                    .timestamp(Instant.now())
                    .immudbTransactionId(record.getImmudbTransactionId())
                    .immudbHashChain(record.getHashChain())
                    .verified(true)
                    .signatureRequestId(event.getRequestId())
                    .signatureEventType(event.getEventType())
                    .signatureActorType(event.getActorType())
                    .signatureActorEmail(event.getActorEmail())
                    .metadata(buildMetadata(event))
                    .build();

            auditLogRepository.save(auditLog);
            log.debug("Signature audit event synced to MongoDB: {}", event.getEventId());

        } catch (Exception e) {
            log.error("Failed to sync signature audit event to MongoDB: {}", event.getEventId(), e);
            // Don't fail the entire operation - ImmuDB write succeeded
        }
    }

    private Map<String, Object> buildMetadata(SignatureAuditEvent event) {
        return Map.of(
                "documentId", event.getDocumentId() != null ? event.getDocumentId() : "",
                "actorType", event.getActorType() != null ? event.getActorType() : "",
                "actorEmail", event.getActorEmail() != null ? event.getActorEmail() : "",
                "eventMetadata", event.getMetadata() != null ? event.getMetadata() : Map.of()
        );
    }
}
