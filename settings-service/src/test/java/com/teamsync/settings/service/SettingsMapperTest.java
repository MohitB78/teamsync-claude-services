package com.teamsync.settings.service;

import com.teamsync.settings.model.DriveSettings;
import com.teamsync.settings.model.TenantSettings;
import com.teamsync.settings.model.UserSettings;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SettingsMapper.
 * Tests partial update functionality with dot notation support.
 */
@DisplayName("Settings Mapper Tests")
class SettingsMapperTest {

    private SettingsMapper settingsMapper;

    @BeforeEach
    void setUp() {
        settingsMapper = new SettingsMapper();
    }

    @Nested
    @DisplayName("User Settings Update Tests")
    class UserSettingsUpdateTests {

        @Test
        @DisplayName("Should update top-level user settings")
        void applyUserSettingsUpdates_TopLevel() {
            // Given
            UserSettings settings = UserSettings.builder()
                    .theme("light")
                    .language("en")
                    .build();
            Map<String, Object> updates = Map.of(
                    "theme", "dark",
                    "language", "es"
            );

            // When
            settingsMapper.applyUserSettingsUpdates(settings, updates);

            // Then
            assertThat(settings.getTheme()).isEqualTo("dark");
            assertThat(settings.getLanguage()).isEqualTo("es");
        }

        @Test
        @DisplayName("Should update nested notification settings")
        void applyUserSettingsUpdates_NestedNotifications() {
            // Given
            UserSettings settings = UserSettings.builder().build();
            Map<String, Object> updates = Map.of(
                    "notifications.emailEnabled", false,
                    "notifications.pushEnabled", true,
                    "notifications.mentionsOnly", true
            );

            // When
            settingsMapper.applyUserSettingsUpdates(settings, updates);

            // Then
            assertThat(settings.getNotifications()).isNotNull();
            assertThat(settings.getNotifications().isEmailEnabled()).isFalse();
            assertThat(settings.getNotifications().isPushEnabled()).isTrue();
            assertThat(settings.getNotifications().isMentionsOnly()).isTrue();
        }

        @Test
        @DisplayName("Should update nested document view settings")
        void applyUserSettingsUpdates_NestedDocumentView() {
            // Given
            UserSettings settings = UserSettings.builder().build();
            Map<String, Object> updates = Map.of(
                    "documentView.defaultView", "list",
                    "documentView.sortBy", "date",
                    "documentView.itemsPerPage", 50
            );

            // When
            settingsMapper.applyUserSettingsUpdates(settings, updates);

            // Then
            assertThat(settings.getDocumentView()).isNotNull();
            assertThat(settings.getDocumentView().getDefaultView()).isEqualTo("list");
            assertThat(settings.getDocumentView().getSortBy()).isEqualTo("date");
            assertThat(settings.getDocumentView().getItemsPerPage()).isEqualTo(50);
        }

        @Test
        @DisplayName("Should update nested accessibility settings")
        void applyUserSettingsUpdates_NestedAccessibility() {
            // Given
            UserSettings settings = UserSettings.builder().build();
            Map<String, Object> updates = Map.of(
                    "accessibility.highContrast", true,
                    "accessibility.reducedMotion", true,
                    "accessibility.fontSize", "large"
            );

            // When
            settingsMapper.applyUserSettingsUpdates(settings, updates);

            // Then
            assertThat(settings.getAccessibility()).isNotNull();
            assertThat(settings.getAccessibility().isHighContrast()).isTrue();
            assertThat(settings.getAccessibility().isReducedMotion()).isTrue();
            assertThat(settings.getAccessibility().getFontSize()).isEqualTo("large");
        }

        @Test
        @DisplayName("Should handle boolean string conversion")
        void applyUserSettingsUpdates_BooleanStringConversion() {
            // Given
            UserSettings settings = UserSettings.builder().build();
            Map<String, Object> updates = Map.of(
                    "notifications.emailEnabled", "true",
                    "accessibility.highContrast", "false"
            );

            // When
            settingsMapper.applyUserSettingsUpdates(settings, updates);

            // Then
            assertThat(settings.getNotifications().isEmailEnabled()).isTrue();
            assertThat(settings.getAccessibility().isHighContrast()).isFalse();
        }

        @Test
        @DisplayName("Should handle integer string conversion")
        void applyUserSettingsUpdates_IntegerStringConversion() {
            // Given
            UserSettings settings = UserSettings.builder().build();
            Map<String, Object> updates = Map.of(
                    "documentView.itemsPerPage", "100"
            );

            // When
            settingsMapper.applyUserSettingsUpdates(settings, updates);

            // Then
            assertThat(settings.getDocumentView().getItemsPerPage()).isEqualTo(100);
        }
    }

    @Nested
    @DisplayName("Tenant Settings Update Tests")
    class TenantSettingsUpdateTests {

        @Test
        @DisplayName("Should update branding settings")
        void applyTenantSettingsUpdates_Branding() {
            // Given
            TenantSettings settings = TenantSettings.builder().build();
            Map<String, Object> updates = Map.of(
                    "branding.primaryColor", "#ff5500",
                    "branding.companyName", "Acme Corp",
                    "branding.customLoginPage", true
            );

            // When
            settingsMapper.applyTenantSettingsUpdates(settings, updates);

            // Then
            assertThat(settings.getBranding()).isNotNull();
            assertThat(settings.getBranding().getPrimaryColor()).isEqualTo("#ff5500");
            assertThat(settings.getBranding().getCompanyName()).isEqualTo("Acme Corp");
            assertThat(settings.getBranding().isCustomLoginPage()).isTrue();
        }

        @Test
        @DisplayName("Should update storage settings")
        void applyTenantSettingsUpdates_Storage() {
            // Given
            TenantSettings settings = TenantSettings.builder().build();
            Map<String, Object> updates = Map.of(
                    "storage.defaultQuotaBytes", 10737418240L, // 10GB
                    "storage.trashRetentionDays", 60,
                    "storage.autoVersioning", false
            );

            // When
            settingsMapper.applyTenantSettingsUpdates(settings, updates);

            // Then
            assertThat(settings.getStorage()).isNotNull();
            assertThat(settings.getStorage().getDefaultQuotaBytes()).isEqualTo(10737418240L);
            assertThat(settings.getStorage().getTrashRetentionDays()).isEqualTo(60);
            assertThat(settings.getStorage().isAutoVersioning()).isFalse();
        }

        @Test
        @DisplayName("Should update security settings")
        void applyTenantSettingsUpdates_Security() {
            // Given
            TenantSettings settings = TenantSettings.builder().build();
            Map<String, Object> updates = Map.of(
                    "security.mfaRequired", true,
                    "security.mfaEnforced", true,
                    "security.sessionTimeoutMinutes", 60,
                    "security.allowPublicSharing", false
            );

            // When
            settingsMapper.applyTenantSettingsUpdates(settings, updates);

            // Then
            assertThat(settings.getSecurity()).isNotNull();
            assertThat(settings.getSecurity().isMfaRequired()).isTrue();
            assertThat(settings.getSecurity().isMfaEnforced()).isTrue();
            assertThat(settings.getSecurity().getSessionTimeoutMinutes()).isEqualTo(60);
            assertThat(settings.getSecurity().isAllowPublicSharing()).isFalse();
        }

        @Test
        @DisplayName("Should update feature settings")
        void applyTenantSettingsUpdates_Features() {
            // Given
            TenantSettings settings = TenantSettings.builder().build();
            Map<String, Object> updates = Map.of(
                    "features.aiMetadataExtraction", true,
                    "features.docuTalkChat", true,
                    "features.officeEditing", false
            );

            // When
            settingsMapper.applyTenantSettingsUpdates(settings, updates);

            // Then
            assertThat(settings.getFeatures()).isNotNull();
            assertThat(settings.getFeatures().isAiMetadataExtraction()).isTrue();
            assertThat(settings.getFeatures().isDocuTalkChat()).isTrue();
            assertThat(settings.getFeatures().isOfficeEditing()).isFalse();
        }

        @Test
        @DisplayName("Should update list settings")
        void applyTenantSettingsUpdates_Lists() {
            // Given
            TenantSettings settings = TenantSettings.builder().build();
            Map<String, Object> updates = Map.of(
                    "storage.allowedFileTypes", List.of("pdf", "docx", "xlsx"),
                    "security.allowedIpRanges", List.of("10.0.0.0/8", "192.168.0.0/16")
            );

            // When
            settingsMapper.applyTenantSettingsUpdates(settings, updates);

            // Then
            assertThat(settings.getStorage().getAllowedFileTypes())
                    .containsExactly("pdf", "docx", "xlsx");
            assertThat(settings.getSecurity().getAllowedIpRanges())
                    .containsExactly("10.0.0.0/8", "192.168.0.0/16");
        }
    }

    @Nested
    @DisplayName("Drive Settings Update Tests")
    class DriveSettingsUpdateTests {

        @Test
        @DisplayName("Should update basic drive settings")
        void applyDriveSettingsUpdates_Basic() {
            // Given
            DriveSettings settings = DriveSettings.builder().build();
            Map<String, Object> updates = Map.of(
                    "defaultView", "list",
                    "sortBy", "modifiedAt",
                    "sortOrder", "desc"
            );

            // When
            settingsMapper.applyDriveSettingsUpdates(settings, updates);

            // Then
            assertThat(settings.getDefaultView()).isEqualTo("list");
            assertThat(settings.getSortBy()).isEqualTo("modifiedAt");
            assertThat(settings.getSortOrder()).isEqualTo("desc");
        }

        @Test
        @DisplayName("Should update boolean drive settings")
        void applyDriveSettingsUpdates_Booleans() {
            // Given
            DriveSettings settings = DriveSettings.builder().build();
            Map<String, Object> updates = Map.of(
                    "showHiddenFiles", true,
                    "showThumbnails", false,
                    "rememberLastFolder", true
            );

            // When
            settingsMapper.applyDriveSettingsUpdates(settings, updates);

            // Then
            assertThat(settings.isShowHiddenFiles()).isTrue();
            assertThat(settings.isShowThumbnails()).isFalse();
            assertThat(settings.isRememberLastFolder()).isTrue();
        }

        @Test
        @DisplayName("Should update list drive settings")
        void applyDriveSettingsUpdates_Lists() {
            // Given
            DriveSettings settings = DriveSettings.builder().build();
            Map<String, Object> updates = Map.of(
                    "pinnedFolders", List.of("folder-1", "folder-2"),
                    "favoriteDocuments", List.of("doc-1"),
                    "visibleColumns", List.of("name", "size", "modified")
            );

            // When
            settingsMapper.applyDriveSettingsUpdates(settings, updates);

            // Then
            assertThat(settings.getPinnedFolders()).containsExactly("folder-1", "folder-2");
            assertThat(settings.getFavoriteDocuments()).containsExactly("doc-1");
            assertThat(settings.getVisibleColumns()).containsExactly("name", "size", "modified");
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle unknown keys gracefully")
        void applyUpdates_UnknownKeys() {
            // Given
            UserSettings settings = UserSettings.builder().build();
            Map<String, Object> updates = Map.of(
                    "unknownKey", "value",
                    "theme", "dark"
            );

            // When - should not throw
            settingsMapper.applyUserSettingsUpdates(settings, updates);

            // Then - known key should still be applied
            assertThat(settings.getTheme()).isEqualTo("dark");
        }

        @Test
        @DisplayName("Should handle null values gracefully")
        void applyUpdates_NullValues() {
            // Given
            UserSettings settings = UserSettings.builder().theme("light").build();
            Map<String, Object> updates = new java.util.HashMap<>();
            updates.put("theme", null);

            // When - should not throw
            settingsMapper.applyUserSettingsUpdates(settings, updates);
        }

        @Test
        @DisplayName("Should handle empty update map")
        void applyUpdates_EmptyMap() {
            // Given
            UserSettings settings = UserSettings.builder().theme("light").build();
            Map<String, Object> updates = Map.of();

            // When
            settingsMapper.applyUserSettingsUpdates(settings, updates);

            // Then - settings unchanged
            assertThat(settings.getTheme()).isEqualTo("light");
        }
    }
}
