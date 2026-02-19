package com.teamsync.gateway.service;

import com.teamsync.gateway.model.BffSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for publishing login events to Kafka/Redpanda.
 *
 * <p>On successful login, publishes a {@code user.logged_in} event that triggers
 * the Permission Manager service to warm the user's cache (pre-load DriveAssignments).
 *
 * <p>Event flow:
 * <pre>
 * BFF Login → Kafka: teamsync.users.logged_in → Permission Manager
 *                                                    ↓
 *                                          warmUserCache(userId)
 *                                                    ↓
 *                                          Pre-load DriveAssignments to Redis
 * </pre>
 *
 * @see com.teamsync.gateway.controller.BffAuthController
 */
@Service
@ConditionalOnProperty(name = "teamsync.bff.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class LoginEventPublisher {

    private static final String TOPIC = "teamsync.users.logged_in";
    private static final Duration PUBLISH_TIMEOUT = Duration.ofSeconds(3);

    private final KafkaSender<String, Object> kafkaSender;

    /**
     * Publish a login event for the given session.
     *
     * <p>This event is consumed by Permission Manager to warm the user's cache.
     *
     * @param session The BFF session created on login
     * @return Mono completing when the event is sent
     */
    public Mono<Void> publishLoginEvent(BffSession session) {
        Map<String, Object> event = new HashMap<>();
        event.put("userId", session.getUserId());
        event.put("email", session.getEmail());
        event.put("tenantId", session.getTenantId());
        event.put("roles", session.getRoles());
        event.put("isSuperAdmin", session.isSuperAdmin());
        event.put("isOrgAdmin", session.isOrgAdmin());
        event.put("isDepartmentAdmin", session.isDepartmentAdmin());
        event.put("timestamp", Instant.now().toString());
        event.put("source", "bff-gateway");
        event.put("clientIp", session.getClientIp());

        ProducerRecord<String, Object> record = new ProducerRecord<>(
            TOPIC,
            session.getUserId(),  // Key: userId for partitioning
            event
        );

        SenderRecord<String, Object, String> senderRecord = SenderRecord.create(
            record,
            session.getUserId()  // Correlation metadata
        );

        // Use timeout to prevent blocking the login response if Kafka is slow/unavailable
        return kafkaSender.send(Mono.just(senderRecord))
            .timeout(PUBLISH_TIMEOUT)
            .doOnNext(result -> {
                if (result.exception() != null) {
                    log.error("Failed to publish login event for user {}: {}",
                        session.getUserId(), result.exception().getMessage());
                } else {
                    log.info("Published login event for user: {} to topic: {} partition: {} offset: {}",
                        session.getUserId(),
                        TOPIC,
                        result.recordMetadata().partition(),
                        result.recordMetadata().offset());
                }
            })
            .doOnError(e -> log.warn("Login event publish timed out or failed for user {}: {} (login continues)",
                session.getUserId(), e.getMessage()))
            .onErrorResume(e -> {
                // Don't fail the login if event publishing fails
                log.warn("Login event publishing failed, continuing with login: {}", e.getMessage());
                return Mono.empty();
            })
            .then();
    }

    /**
     * Publish a logout event for the given session.
     *
     * <p>This can be used to invalidate cache entries in Permission Manager.
     *
     * @param session The BFF session being invalidated
     * @return Mono completing when the event is sent
     */
    public Mono<Void> publishLogoutEvent(BffSession session) {
        Map<String, Object> event = new HashMap<>();
        event.put("userId", session.getUserId());
        event.put("email", session.getEmail());
        event.put("tenantId", session.getTenantId());
        event.put("timestamp", Instant.now().toString());
        event.put("source", "bff-gateway");
        event.put("eventType", "logout");

        ProducerRecord<String, Object> record = new ProducerRecord<>(
            "teamsync.users.logged_out",
            session.getUserId(),
            event
        );

        SenderRecord<String, Object, String> senderRecord = SenderRecord.create(
            record,
            session.getUserId()
        );

        // Use timeout to prevent blocking the logout response if Kafka is slow/unavailable
        return kafkaSender.send(Mono.just(senderRecord))
            .timeout(PUBLISH_TIMEOUT)
            .doOnNext(result -> {
                if (result.exception() == null) {
                    log.debug("Published logout event for user: {}", session.getUserId());
                }
            })
            .onErrorResume(e -> {
                log.warn("Failed to publish logout event: {}", e.getMessage());
                return Mono.empty();
            })
            .then();
    }
}
