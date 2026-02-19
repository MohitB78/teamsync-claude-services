package com.teamsync.settings.service;

import com.teamsync.common.config.KafkaTopics;
import com.teamsync.settings.config.CacheConfig;
import com.teamsync.settings.dto.TenantSettingsDTO;
import com.teamsync.settings.event.SettingsChangedEvent;
import com.teamsync.settings.model.TenantSettings;
import com.teamsync.settings.repository.TenantSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Service for managing tenant (organization) settings.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantSettingsService {

    private final TenantSettingsRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final SettingsMapper settingsMapper;

    /**
     * Get tenant settings, creating defaults if they don't exist.
     */
    @Transactional
    public TenantSettingsDTO getTenantSettings(String tenantId) {
        log.debug("Getting tenant settings for tenant: {}", tenantId);

        TenantSettings settings = repository.findByTenantId(tenantId)
                .orElseGet(() -> createDefaultTenantSettings(tenantId));

        return TenantSettingsDTO.fromEntity(settings);
    }

    /**
     * Update tenant settings with partial update support.
     * Evicts all feature flag and tenant settings caches for this tenant.
     */
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.FEATURE_FLAGS_CACHE, allEntries = true),
            @CacheEvict(value = CacheConfig.TENANT_SETTINGS_CACHE, key = "#tenantId")
    })
    public TenantSettingsDTO updateTenantSettings(String tenantId, Map<String, Object> updates) {
        log.info("Updating tenant settings for tenant: {}", tenantId);

        TenantSettings settings = repository.findByTenantId(tenantId)
                .orElseGet(() -> createDefaultTenantSettings(tenantId));

        // Apply updates
        settingsMapper.applyTenantSettingsUpdates(settings, updates);

        // Save
        TenantSettings saved = repository.save(settings);
        log.info("Tenant settings updated for tenant: {}", tenantId);

        // Publish event
        publishSettingsChangedEvent(tenantId, updates);

        return TenantSettingsDTO.fromEntity(saved);
    }

    /**
     * Reset tenant settings to defaults.
     * Evicts all caches for this tenant.
     */
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.FEATURE_FLAGS_CACHE, allEntries = true),
            @CacheEvict(value = CacheConfig.TENANT_SETTINGS_CACHE, key = "#tenantId")
    })
    public TenantSettingsDTO resetTenantSettings(String tenantId) {
        log.info("Resetting tenant settings for tenant: {}", tenantId);

        repository.deleteByTenantId(tenantId);
        TenantSettings settings = createDefaultTenantSettings(tenantId);

        return TenantSettingsDTO.fromEntity(settings);
    }

    /**
     * Get specific feature flag status.
     * Cached in Redis with 5-minute TTL.
     */
    @Cacheable(value = CacheConfig.FEATURE_FLAGS_CACHE, key = "#tenantId + ':' + #feature")
    public boolean isFeatureEnabled(String tenantId, String feature) {
        log.debug("Cache miss for feature flag: tenant={}, feature={}", tenantId, feature);
        TenantSettings settings = repository.findByTenantId(tenantId).orElse(null);
        if (settings == null || settings.getFeatures() == null) {
            return true; // Default to enabled
        }

        var features = settings.getFeatures();
        return switch (feature) {
            case "aiMetadataExtraction" -> features.isAiMetadataExtraction();
            case "docuTalkChat" -> features.isDocuTalkChat();
            case "officeEditing" -> features.isOfficeEditing();
            case "advancedSearch" -> features.isAdvancedSearch();
            case "workflowAutomation" -> features.isWorkflowAutomation();
            case "auditLogging" -> features.isAuditLogging();
            case "realTimeCollaboration" -> features.isRealTimeCollaboration();
            case "customDocumentTypes" -> features.isCustomDocumentTypes();
            default -> true;
        };
    }

    /**
     * Get storage quota for new users in this tenant.
     */
    public long getDefaultQuotaBytes(String tenantId) {
        TenantSettings settings = repository.findByTenantId(tenantId).orElse(null);
        if (settings == null || settings.getStorage() == null) {
            return 10L * 1024 * 1024 * 1024; // 10GB default
        }
        return settings.getStorage().getDefaultQuotaBytes();
    }

    /**
     * Check if public sharing is allowed for this tenant.
     */
    public boolean isPublicSharingAllowed(String tenantId) {
        TenantSettings settings = repository.findByTenantId(tenantId).orElse(null);
        if (settings == null || settings.getSecurity() == null) {
            return true; // Default to allowed
        }
        return settings.getSecurity().isAllowPublicSharing();
    }

    private TenantSettings createDefaultTenantSettings(String tenantId) {
        log.debug("Creating default tenant settings for tenant: {}", tenantId);

        TenantSettings settings = TenantSettings.builder()
                .tenantId(tenantId)
                .build();

        return repository.save(settings);
    }

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_BASE_DELAY_MS = 1000;
    private static final double JITTER_FACTOR = 0.3; // 30% jitter

    private void publishSettingsChangedEvent(String tenantId, Map<String, Object> changes) {
        SettingsChangedEvent event = SettingsChangedEvent.builder()
                .tenantId(tenantId)
                .settingsType("TENANT")
                .changedFields(changes.keySet().stream().toList())
                .build();

        publishWithRetry(KafkaTopics.SETTINGS_TENANT_UPDATED, tenantId, event, 0);
    }

    /**
     * Calculate retry delay with exponential backoff and jitter.
     * SECURITY FIX (Round 14 #M9): Added jitter to prevent thundering herd when
     * multiple requests fail and retry simultaneously.
     */
    private long calculateRetryDelay(int attempt) {
        long baseDelay = RETRY_BASE_DELAY_MS * (1L << attempt); // Exponential: 1s, 2s, 4s
        long jitter = (long) (baseDelay * JITTER_FACTOR * ThreadLocalRandom.current().nextDouble());
        return baseDelay + jitter;
    }

    private void publishWithRetry(String topic, String key, Object event, int attempt) {
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    long delayMs = calculateRetryDelay(attempt);
                    log.warn("Failed to publish tenant event (attempt {}/{}): {}. Retrying in {}ms...",
                            attempt + 1, MAX_RETRY_ATTEMPTS, ex.getMessage(), delayMs);
                    // SECURITY FIX: Exponential backoff with jitter to prevent thundering herd
                    CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS)
                            .execute(() -> publishWithRetry(topic, key, event, attempt + 1));
                } else {
                    log.error("Failed to publish tenant event after {} attempts. Event lost: topic={}, key={}",
                            MAX_RETRY_ATTEMPTS, topic, key, ex);
                }
            } else {
                log.debug("Successfully published tenant event to topic: {}, partition: {}, offset: {}",
                        topic, result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
            }
        });
    }
}
