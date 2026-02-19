package com.teamsync.signing.service;

import com.teamsync.common.config.KafkaTopics;
import com.teamsync.common.event.SignatureAuditEvent;
import com.teamsync.signing.model.SignatureEvent;
import com.teamsync.signing.model.SignatureEvent.ActorType;
import com.teamsync.signing.model.SignatureEvent.SignatureEventType;
import com.teamsync.signing.repository.SignatureEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Service for logging signature events (audit trail).
 *
 * All signing actions are logged immutably for legal compliance.
 * Events include IP address and user agent for security auditing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SignatureEventService {

    private final SignatureEventRepository eventRepository;
    private final KafkaTemplate<String, SignatureAuditEvent> kafkaTemplate;

    /**
     * Log a signature event.
     *
     * @param tenantId Tenant ID
     * @param requestId Signature request ID
     * @param eventType Type of event
     * @param actorType Who performed the action
     * @param actorId Actor's ID
     * @param actorEmail Actor's email
     * @param actorName Actor's name
     * @param description Human-readable description
     * @param ipAddress Client IP address
     * @param userAgent Client user agent
     * @param metadata Additional event-specific data
     * @return The created event
     */
    public SignatureEvent logEvent(
            String tenantId,
            String requestId,
            SignatureEventType eventType,
            ActorType actorType,
            String actorId,
            String actorEmail,
            String actorName,
            String description,
            String ipAddress,
            String userAgent,
            Map<String, Object> metadata) {

        SignatureEvent event = SignatureEvent.builder()
                .tenantId(tenantId)
                .requestId(requestId)
                .eventType(eventType)
                .actorType(actorType)
                .actorId(actorId)
                .actorEmail(actorEmail)
                .actorName(actorName)
                .description(description)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .metadata(metadata)
                .timestamp(Instant.now())
                .build();

        SignatureEvent saved = eventRepository.save(event);

        log.info("Signature event logged: type={}, request={}, actor={}, ip={}",
                eventType, requestId, actorEmail, ipAddress);

        // Publish to Kafka for ImmuDB audit storage
        publishToKafka(saved);

        return saved;
    }

    /**
     * Publish signature event to Kafka for ImmuDB audit storage.
     */
    private void publishToKafka(SignatureEvent event) {
        try {
            SignatureAuditEvent auditEvent = SignatureAuditEvent.builder()
                    .eventId(event.getId())
                    .tenantId(event.getTenantId())
                    .requestId(event.getRequestId())
                    .documentId(event.getDocumentId())
                    .actorId(event.getActorId())
                    .actorEmail(event.getActorEmail())
                    .actorName(event.getActorName())
                    .actorType(event.getActorType() != null ? event.getActorType().name() : null)
                    .eventType(event.getEventType() != null ? event.getEventType().name() : null)
                    .description(event.getDescription())
                    .metadata(event.getMetadata())
                    .ipAddress(event.getIpAddress())
                    .userAgent(event.getUserAgent())
                    .sessionId(event.getSessionId())
                    .timestamp(event.getTimestamp())
                    .build();

            kafkaTemplate.send(KafkaTopics.SIGNATURE_AUDIT_EVENTS, event.getRequestId(), auditEvent)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish signature audit event to Kafka: eventId={}, error={}",
                                    event.getId(), ex.getMessage());
                        } else {
                            log.debug("Signature audit event published to Kafka: eventId={}, partition={}",
                                    event.getId(), result.getRecordMetadata().partition());
                        }
                    });
        } catch (Exception e) {
            // Don't fail the main operation if Kafka publishing fails
            log.error("Error publishing signature audit event to Kafka: eventId={}", event.getId(), e);
        }
    }

    /**
     * Log a request lifecycle event (created, sent, voided, etc.).
     */
    public SignatureEvent logRequestEvent(
            String tenantId,
            String requestId,
            SignatureEventType eventType,
            String senderId,
            String senderEmail,
            String senderName,
            String description,
            String ipAddress,
            String userAgent) {

        return logEvent(tenantId, requestId, eventType, ActorType.SENDER,
                senderId, senderEmail, senderName, description, ipAddress, userAgent, null);
    }

    /**
     * Log a signer action event (viewed, signed, declined).
     */
    public SignatureEvent logSignerEvent(
            String tenantId,
            String requestId,
            SignatureEventType eventType,
            String signerId,
            String signerEmail,
            String signerName,
            String description,
            String ipAddress,
            String userAgent,
            Map<String, Object> metadata) {

        return logEvent(tenantId, requestId, eventType, ActorType.SIGNER,
                signerId, signerEmail, signerName, description, ipAddress, userAgent, metadata);
    }

    /**
     * Log a system event (expiration, reminder).
     */
    public SignatureEvent logSystemEvent(
            String tenantId,
            String requestId,
            SignatureEventType eventType,
            String description,
            Map<String, Object> metadata) {

        return logEvent(tenantId, requestId, eventType, ActorType.SYSTEM,
                "SYSTEM", "system@teamsync.local", "System", description, null, null, metadata);
    }

    /**
     * Get all events for a signature request (for audit trail display).
     */
    public List<SignatureEvent> getEventsForRequest(String requestId) {
        return eventRepository.findByRequestIdOrderByTimestampAsc(requestId);
    }

    /**
     * Get events for a request (paginated).
     */
    public Page<SignatureEvent> getEventsForRequest(String requestId, Pageable pageable) {
        return eventRepository.findByRequestId(requestId, pageable);
    }

    /**
     * Get events for a tenant (for admin audit view).
     */
    public Page<SignatureEvent> getEventsForTenant(String tenantId, Pageable pageable) {
        return eventRepository.findByTenantIdOrderByTimestampDesc(tenantId, pageable);
    }

    /**
     * Count specific events for a request.
     */
    public long countEvents(String requestId, SignatureEventType eventType) {
        return eventRepository.countByRequestIdAndEventType(requestId, eventType);
    }
}
