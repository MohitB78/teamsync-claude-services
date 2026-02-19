package com.teamsync.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * User notification preferences.
 * Controls which notifications users receive and through which channels.
 *
 * SECURITY FIX (Round 15 #M36): Added @Version for optimistic locking to prevent
 * race conditions in concurrent preference updates.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "notification_preferences")
@CompoundIndexes({
        @CompoundIndex(name = "tenant_user_idx", def = "{'tenantId': 1, 'userId': 1}", unique = true)
})
public class NotificationPreference {

    @Id
    private String id;

    /**
     * Version field for optimistic locking.
     */
    @Version
    private Long version;

    private String tenantId;
    private String userId;

    /**
     * Global email notifications enabled.
     */
    @Builder.Default
    private Boolean emailEnabled = true;

    /**
     * Global push notifications enabled.
     */
    @Builder.Default
    private Boolean pushEnabled = true;

    /**
     * Global in-app notifications enabled.
     */
    @Builder.Default
    private Boolean inAppEnabled = true;

    /**
     * Only notify for @mentions (reduces noise).
     */
    @Builder.Default
    private Boolean mentionsOnly = false;

    /**
     * Email digest settings.
     */
    @Builder.Default
    private DigestSettings digestSettings = new DigestSettings();

    /**
     * Quiet hours - no notifications during these times.
     */
    @Builder.Default
    private QuietHours quietHours = new QuietHours();

    /**
     * Per-type notification preferences.
     * Key: NotificationType name, Value: channel preferences
     */
    @Builder.Default
    private Map<String, TypePreference> typePreferences = new HashMap<>();

    /**
     * Muted resources (documents, folders) - no notifications.
     */
    @Builder.Default
    private Map<String, MutedResource> mutedResources = new HashMap<>();

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    /**
     * Digest email settings.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DigestSettings {
        @Builder.Default
        private Boolean enabled = false;

        @Builder.Default
        private DigestFrequency frequency = DigestFrequency.DAILY;

        /**
         * Preferred send time in HH:mm format (user's timezone).
         */
        @Builder.Default
        private String sendTime = "09:00";

        /**
         * User's timezone for digest scheduling.
         */
        @Builder.Default
        private String timezone = "UTC";

        /**
         * Include low priority notifications in digest only.
         */
        @Builder.Default
        private Boolean lowPriorityToDigest = true;
    }

    /**
     * Quiet hours configuration.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuietHours {
        @Builder.Default
        private Boolean enabled = false;

        /**
         * Start time in HH:mm format.
         */
        @Builder.Default
        private String startTime = "22:00";

        /**
         * End time in HH:mm format.
         */
        @Builder.Default
        private String endTime = "08:00";

        /**
         * User's timezone.
         */
        @Builder.Default
        private String timezone = "UTC";

        /**
         * Allow urgent notifications during quiet hours.
         */
        @Builder.Default
        private Boolean allowUrgent = true;
    }

    /**
     * Per-type preference settings.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TypePreference {
        @Builder.Default
        private boolean enabled = true;
        @Builder.Default
        private boolean email = true;
        @Builder.Default
        private boolean push = true;
        @Builder.Default
        private boolean inApp = true;
    }

    /**
     * Muted resource configuration.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MutedResource {
        private String resourceType;  // document, folder
        private String resourceId;
        private Instant mutedAt;
        private Instant muteExpiresAt;  // null = permanent
    }

    public enum DigestFrequency {
        DAILY,
        WEEKLY,
        NEVER
    }

    /**
     * Check if a notification type is enabled.
     */
    public boolean isTypeEnabled(Notification.NotificationType type) {
        TypePreference pref = typePreferences.get(type.name());
        return pref == null || pref.isEnabled();
    }

    /**
     * Check if email is enabled for a notification type.
     */
    public boolean isEmailEnabledForType(Notification.NotificationType type) {
        if (!emailEnabled) return false;
        TypePreference pref = typePreferences.get(type.name());
        return pref == null || pref.isEmail();
    }

    /**
     * Check if push is enabled for a notification type.
     */
    public boolean isPushEnabledForType(Notification.NotificationType type) {
        if (!pushEnabled) return false;
        TypePreference pref = typePreferences.get(type.name());
        return pref == null || pref.isPush();
    }

    /**
     * Check if in-app is enabled for a notification type.
     */
    public boolean isInAppEnabledForType(Notification.NotificationType type) {
        if (!inAppEnabled) return false;
        TypePreference pref = typePreferences.get(type.name());
        return pref == null || pref.isInApp();
    }

    /**
     * Check if a resource is muted.
     */
    public boolean isResourceMuted(String resourceType, String resourceId) {
        String key = resourceType + ":" + resourceId;
        MutedResource muted = mutedResources.get(key);
        if (muted == null) return false;
        if (muted.getMuteExpiresAt() != null && Instant.now().isAfter(muted.getMuteExpiresAt())) {
            return false;
        }
        return true;
    }
}
