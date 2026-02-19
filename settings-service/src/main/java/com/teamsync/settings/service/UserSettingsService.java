package com.teamsync.settings.service;

import com.teamsync.common.config.KafkaTopics;
import com.teamsync.settings.dto.UserSettingsDTO;
import com.teamsync.settings.event.SettingsChangedEvent;
import com.teamsync.settings.model.UserSettings;
import com.teamsync.settings.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Service for managing user settings.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserSettingsService {

    private final UserSettingsRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final SettingsMapper settingsMapper;

    /**
     * Get user settings, creating defaults if they don't exist.
     */
    @Transactional
    public UserSettingsDTO getUserSettings(String tenantId, String userId) {
        log.debug("Getting user settings for tenant: {}, user: {}", tenantId, userId);

        UserSettings settings = repository.findByTenantIdAndUserId(tenantId, userId)
                .orElseGet(() -> createDefaultUserSettings(tenantId, userId));

        return UserSettingsDTO.fromEntity(settings);
    }

    /**
     * Update user settings with partial update support.
     */
    @Transactional
    public UserSettingsDTO updateUserSettings(String tenantId, String userId, Map<String, Object> updates) {
        log.info("Updating user settings for tenant: {}, user: {}", tenantId, userId);

        UserSettings settings = repository.findByTenantIdAndUserId(tenantId, userId)
                .orElseGet(() -> createDefaultUserSettings(tenantId, userId));

        // Apply updates
        settingsMapper.applyUserSettingsUpdates(settings, updates);

        // Save
        UserSettings saved = repository.save(settings);
        log.info("User settings updated for user: {}", userId);

        // Publish event
        publishSettingsChangedEvent(tenantId, userId, "USER", updates);

        return UserSettingsDTO.fromEntity(saved);
    }

    /**
     * Reset user settings to defaults.
     */
    @Transactional
    public UserSettingsDTO resetUserSettings(String tenantId, String userId) {
        log.info("Resetting user settings for tenant: {}, user: {}", tenantId, userId);

        repository.deleteByTenantIdAndUserId(tenantId, userId);
        UserSettings settings = createDefaultUserSettings(tenantId, userId);

        return UserSettingsDTO.fromEntity(settings);
    }

    private UserSettings createDefaultUserSettings(String tenantId, String userId) {
        log.debug("Creating default user settings for tenant: {}, user: {}", tenantId, userId);

        UserSettings settings = UserSettings.builder()
                .tenantId(tenantId)
                .userId(userId)
                .build();

        return repository.save(settings);
    }

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000;

    private void publishSettingsChangedEvent(String tenantId, String userId, String settingsType, Map<String, Object> changes) {
        SettingsChangedEvent event = SettingsChangedEvent.builder()
                .tenantId(tenantId)
                .userId(userId)
                .settingsType(settingsType)
                .changedFields(changes.keySet().stream().toList())
                .build();

        publishWithRetry(KafkaTopics.SETTINGS_USER_UPDATED, userId, event, 0);
    }

    private void publishWithRetry(String topic, String key, Object event, int attempt) {
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    log.warn("Failed to publish event (attempt {}/{}): {}. Retrying in {}ms...",
                            attempt + 1, MAX_RETRY_ATTEMPTS, ex.getMessage(), RETRY_DELAY_MS);
                    // Schedule retry with exponential backoff
                    CompletableFuture.delayedExecutor(RETRY_DELAY_MS * (attempt + 1), TimeUnit.MILLISECONDS)
                            .execute(() -> publishWithRetry(topic, key, event, attempt + 1));
                } else {
                    log.error("Failed to publish event after {} attempts. Event lost: topic={}, key={}",
                            MAX_RETRY_ATTEMPTS, topic, key, ex);
                    // In production, consider persisting failed events to a dead-letter table
                    // for manual retry or alerting
                }
            } else {
                log.debug("Successfully published event to topic: {}, partition: {}, offset: {}",
                        topic, result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
            }
        });
    }
}
