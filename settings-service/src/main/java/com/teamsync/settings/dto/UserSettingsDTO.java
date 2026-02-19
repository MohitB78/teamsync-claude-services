package com.teamsync.settings.dto;

import com.teamsync.settings.model.UserSettings;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO for user settings response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSettingsDTO {

    private String id;
    private String tenantId;
    private String userId;
    private String theme;
    private String language;
    private String timezone;
    private String dateFormat;
    private String timeFormat;
    private String defaultLandingPage;
    private boolean openDocumentsInNewTab;
    private NotificationSettingsDTO notifications;
    private DocumentViewSettingsDTO documentView;
    private AccessibilitySettingsDTO accessibility;
    private ViewerPreferencesDTO viewerPreferences;
    private Instant updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationSettingsDTO {
        private boolean emailEnabled;
        private boolean pushEnabled;
        private boolean inAppEnabled;
        private boolean mentionsOnly;
        private boolean dailyDigest;
        private String digestTime;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentViewSettingsDTO {
        private String defaultView;
        private String sortBy;
        private String sortOrder;
        private boolean showHiddenFiles;
        private int itemsPerPage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccessibilitySettingsDTO {
        private boolean highContrast;
        private boolean reducedMotion;
        private String fontSize;
        private boolean screenReaderOptimized;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ViewerPreferencesDTO {
        private String pdf;
        private String office;
        private String image;
    }

    public static UserSettingsDTO fromEntity(UserSettings entity) {
        if (entity == null) return null;

        UserSettingsDTO.NotificationSettingsDTO notifications = null;
        if (entity.getNotifications() != null) {
            notifications = NotificationSettingsDTO.builder()
                    .emailEnabled(entity.getNotifications().isEmailEnabled())
                    .pushEnabled(entity.getNotifications().isPushEnabled())
                    .inAppEnabled(entity.getNotifications().isInAppEnabled())
                    .mentionsOnly(entity.getNotifications().isMentionsOnly())
                    .dailyDigest(entity.getNotifications().isDailyDigest())
                    .digestTime(entity.getNotifications().getDigestTime())
                    .build();
        }

        UserSettingsDTO.DocumentViewSettingsDTO documentView = null;
        if (entity.getDocumentView() != null) {
            documentView = DocumentViewSettingsDTO.builder()
                    .defaultView(entity.getDocumentView().getDefaultView())
                    .sortBy(entity.getDocumentView().getSortBy())
                    .sortOrder(entity.getDocumentView().getSortOrder())
                    .showHiddenFiles(entity.getDocumentView().isShowHiddenFiles())
                    .itemsPerPage(entity.getDocumentView().getItemsPerPage())
                    .build();
        }

        UserSettingsDTO.AccessibilitySettingsDTO accessibility = null;
        if (entity.getAccessibility() != null) {
            accessibility = AccessibilitySettingsDTO.builder()
                    .highContrast(entity.getAccessibility().isHighContrast())
                    .reducedMotion(entity.getAccessibility().isReducedMotion())
                    .fontSize(entity.getAccessibility().getFontSize())
                    .screenReaderOptimized(entity.getAccessibility().isScreenReaderOptimized())
                    .build();
        }

        UserSettingsDTO.ViewerPreferencesDTO viewerPreferences = null;
        if (entity.getViewerPreferences() != null) {
            viewerPreferences = ViewerPreferencesDTO.builder()
                    .pdf(entity.getViewerPreferences().getPdf())
                    .office(entity.getViewerPreferences().getOffice())
                    .image(entity.getViewerPreferences().getImage())
                    .build();
        }

        return UserSettingsDTO.builder()
                .id(entity.getId())
                .tenantId(entity.getTenantId())
                .userId(entity.getUserId())
                .theme(entity.getTheme())
                .language(entity.getLanguage())
                .timezone(entity.getTimezone())
                .dateFormat(entity.getDateFormat())
                .timeFormat(entity.getTimeFormat())
                .defaultLandingPage(entity.getDefaultLandingPage())
                .openDocumentsInNewTab(entity.isOpenDocumentsInNewTab())
                .notifications(notifications)
                .documentView(documentView)
                .accessibility(accessibility)
                .viewerPreferences(viewerPreferences)
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
