package com.teamsync.settings.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * User-specific settings and preferences.
 * Each user has one settings document per tenant.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "user_settings")
@CompoundIndexes({
    @CompoundIndex(name = "tenant_user_idx", def = "{'tenantId': 1, 'userId': 1}", unique = true)
})
public class UserSettings {

    @Id
    private String id;

    private String tenantId;
    private String userId;

    // UI Preferences
    @Builder.Default
    private String theme = "light";

    @Builder.Default
    private String language = "en";

    @Builder.Default
    private String timezone = "UTC";

    @Builder.Default
    private String dateFormat = "YYYY-MM-DD";

    @Builder.Default
    private String timeFormat = "24h";

    // Notification Preferences
    @Builder.Default
    private NotificationSettings notifications = new NotificationSettings();

    // Document View Preferences
    @Builder.Default
    private DocumentViewSettings documentView = new DocumentViewSettings();

    // Accessibility Settings
    @Builder.Default
    private AccessibilitySettings accessibility = new AccessibilitySettings();

    // Application Preferences
    @Builder.Default
    private String defaultLandingPage = "/drive";

    @Builder.Default
    private boolean openDocumentsInNewTab = false;

    // Viewer Preferences
    @Builder.Default
    private ViewerPreferences viewerPreferences = new ViewerPreferences();

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationSettings {
        @Builder.Default
        private boolean emailEnabled = true;

        @Builder.Default
        private boolean pushEnabled = true;

        @Builder.Default
        private boolean inAppEnabled = true;

        @Builder.Default
        private boolean mentionsOnly = false;

        @Builder.Default
        private boolean dailyDigest = false;

        @Builder.Default
        private String digestTime = "09:00";
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentViewSettings {
        @Builder.Default
        private String defaultView = "grid";

        @Builder.Default
        private String sortBy = "name";

        @Builder.Default
        private String sortOrder = "asc";

        @Builder.Default
        private boolean showHiddenFiles = false;

        @Builder.Default
        private int itemsPerPage = 50;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccessibilitySettings {
        @Builder.Default
        private boolean highContrast = false;

        @Builder.Default
        private boolean reducedMotion = false;

        @Builder.Default
        private String fontSize = "medium";

        @Builder.Default
        private boolean screenReaderOptimized = false;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ViewerPreferences {
        @Builder.Default
        private String pdf = "teamsync"; // 'react-pdf' | 'teamsync'

        @Builder.Default
        private String office = "teamsync-editor"; // 'collabora' | 'onlyoffice' | 'microsoft365' | 'teamsync-office' | 'teamsync-editor'

        @Builder.Default
        private String image = "default"; // 'default'
    }
}
