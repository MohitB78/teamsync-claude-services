package com.teamsync.common.feature;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Service;

/**
 * Centralized feature flag service that reads flags from Config Server.
 *
 * Feature flags are defined in config-repo/application.yml under the 'features' key
 * and can be updated at runtime via Spring Cloud Bus refresh.
 *
 * Usage:
 * <pre>
 * {@code
 * @Autowired
 * private FeatureFlagService featureFlags;
 *
 * if (featureFlags.isAiChatEnabled()) {
 *     // AI chat feature logic
 * }
 * }
 * </pre>
 *
 * To update flags:
 * 1. Modify features in config-repo/application.yml
 * 2. POST /actuator/busrefresh to broadcast changes
 * 3. All services automatically receive updated flags
 */
@Service
@RefreshScope
@Slf4j
@Getter
public class FeatureFlagService {

    // Document Management Features
    @Value("${features.ai-metadata-extraction:true}")
    private boolean aiMetadataExtractionEnabled;

    @Value("${features.ocr-processing:true}")
    private boolean ocrProcessingEnabled;

    @Value("${features.virus-scanning:true}")
    private boolean virusScanningEnabled;

    @Value("${features.thumbnail-generation:true}")
    private boolean thumbnailGenerationEnabled;

    // Collaboration Features
    @Value("${features.real-time-presence:true}")
    private boolean realTimePresenceEnabled;

    @Value("${features.wopi-editing:false}")
    private boolean wopiEditingEnabled;

    @Value("${features.version-history:true}")
    private boolean versionHistoryEnabled;

    @Value("${features.comments-enabled:true}")
    private boolean commentsEnabled;

    // Search Features
    @Value("${features.semantic-search:false}")
    private boolean semanticSearchEnabled;

    @Value("${features.elasticsearch-enabled:true}")
    private boolean elasticsearchEnabled;

    @Value("${features.full-text-search:true}")
    private boolean fullTextSearchEnabled;

    // AI Features
    @Value("${features.ai-chat-enabled:true}")
    private boolean aiChatEnabled;

    @Value("${features.document-summarization:true}")
    private boolean documentSummarizationEnabled;

    @Value("${features.smart-categorization:false}")
    private boolean smartCategorizationEnabled;

    // Security Features
    @Value("${features.mfa-required:false}")
    private boolean mfaRequired;

    @Value("${features.audit-logging:true}")
    private boolean auditLoggingEnabled;

    @Value("${features.rate-limiting:true}")
    private boolean rateLimitingEnabled;

    // Platform Features
    @Value("${features.maintenance-mode:false}")
    private boolean maintenanceMode;

    @Value("${features.debug-mode:false}")
    private boolean debugMode;

    @Value("${features.beta-features:false}")
    private boolean betaFeaturesEnabled;

    /**
     * Check if a feature is enabled by name.
     * Useful for dynamic feature flag checks.
     *
     * @param featureName the name of the feature flag
     * @return true if enabled, false otherwise
     */
    public boolean isFeatureEnabled(String featureName) {
        return switch (featureName) {
            case "ai-metadata-extraction" -> aiMetadataExtractionEnabled;
            case "ocr-processing" -> ocrProcessingEnabled;
            case "virus-scanning" -> virusScanningEnabled;
            case "thumbnail-generation" -> thumbnailGenerationEnabled;
            case "real-time-presence" -> realTimePresenceEnabled;
            case "wopi-editing" -> wopiEditingEnabled;
            case "version-history" -> versionHistoryEnabled;
            case "comments-enabled" -> commentsEnabled;
            case "semantic-search" -> semanticSearchEnabled;
            case "elasticsearch-enabled" -> elasticsearchEnabled;
            case "full-text-search" -> fullTextSearchEnabled;
            case "ai-chat-enabled" -> aiChatEnabled;
            case "document-summarization" -> documentSummarizationEnabled;
            case "smart-categorization" -> smartCategorizationEnabled;
            case "mfa-required" -> mfaRequired;
            case "audit-logging" -> auditLoggingEnabled;
            case "rate-limiting" -> rateLimitingEnabled;
            case "maintenance-mode" -> maintenanceMode;
            case "debug-mode" -> debugMode;
            case "beta-features" -> betaFeaturesEnabled;
            default -> {
                log.warn("Unknown feature flag requested: {}", featureName);
                yield false;
            }
        };
    }
}
