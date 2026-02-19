package com.teamsync.settings.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SECURITY: Typed request DTO for updating user settings.
 * Replaces unsafe Map<String, Object> to prevent NoSQL injection and
 * ensure all inputs are validated.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserSettingsRequest {

    @Pattern(regexp = "^(light|dark|system)$", message = "Theme must be 'light', 'dark', or 'system'")
    private String theme;

    @Pattern(regexp = "^[a-z]{2}(-[A-Z]{2})?$", message = "Language must be a valid locale (e.g., 'en', 'en-US')")
    private String language;

    @Pattern(regexp = "^[A-Za-z_/]+$", message = "Timezone must be a valid IANA timezone identifier")
    private String timezone;

    @Pattern(regexp = "^(YYYY-MM-DD|MM/DD/YYYY|DD/MM/YYYY|DD\\.MM\\.YYYY)$",
             message = "Date format must be one of: YYYY-MM-DD, MM/DD/YYYY, DD/MM/YYYY, DD.MM.YYYY")
    private String dateFormat;

    @Pattern(regexp = "^(HH:mm|hh:mm a|HH:mm:ss)$",
             message = "Time format must be one of: HH:mm, hh:mm a, HH:mm:ss")
    private String timeFormat;

    @Pattern(regexp = "^(dashboard|documents|recent|starred)$",
             message = "Default landing page must be one of: dashboard, documents, recent, starred")
    private String defaultLandingPage;

    private Boolean openDocumentsInNewTab;

    @Valid
    private NotificationSettingsUpdate notifications;

    @Valid
    private DocumentViewSettingsUpdate documentView;

    @Valid
    private AccessibilitySettingsUpdate accessibility;

    @Valid
    private ViewerPreferencesUpdate viewerPreferences;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationSettingsUpdate {
        private Boolean emailEnabled;
        private Boolean pushEnabled;
        private Boolean inAppEnabled;
        private Boolean mentionsOnly;
        private Boolean dailyDigest;

        @Pattern(regexp = "^([01]?[0-9]|2[0-3]):[0-5][0-9]$",
                 message = "Digest time must be in HH:mm format")
        private String digestTime;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentViewSettingsUpdate {
        @Pattern(regexp = "^(grid|list|compact)$", message = "Default view must be 'grid', 'list', or 'compact'")
        private String defaultView;

        @Pattern(regexp = "^(name|date|size|type)$", message = "Sort by must be 'name', 'date', 'size', or 'type'")
        private String sortBy;

        @Pattern(regexp = "^(asc|desc)$", message = "Sort order must be 'asc' or 'desc'")
        private String sortOrder;

        private Boolean showHiddenFiles;

        @Min(value = 10, message = "Items per page must be at least 10")
        @Max(value = 100, message = "Items per page must not exceed 100")
        private Integer itemsPerPage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccessibilitySettingsUpdate {
        private Boolean highContrast;
        private Boolean reducedMotion;

        @Pattern(regexp = "^(small|medium|large|extra-large)$",
                 message = "Font size must be 'small', 'medium', 'large', or 'extra-large'")
        private String fontSize;

        private Boolean screenReaderOptimized;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ViewerPreferencesUpdate {
        @Pattern(regexp = "^(native|collabora|onlyoffice)$",
                 message = "PDF viewer must be 'native', 'collabora', or 'onlyoffice'")
        private String pdf;

        @Pattern(regexp = "^(collabora|onlyoffice)$",
                 message = "Office viewer must be 'collabora' or 'onlyoffice'")
        private String office;

        @Pattern(regexp = "^(native|lightbox)$",
                 message = "Image viewer must be 'native' or 'lightbox'")
        private String image;
    }
}
