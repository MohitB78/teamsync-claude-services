package com.teamsync.settings.service;

import com.teamsync.settings.exception.InvalidSettingsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for SettingsValidationService.
 * Tests input validation for user, tenant, and drive settings.
 */
@DisplayName("Settings Validation Service Tests")
class SettingsValidationServiceTest {

    private SettingsValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new SettingsValidationService();
    }

    @Nested
    @DisplayName("User Settings Validation Tests")
    class UserSettingsValidationTests {

        @Test
        @DisplayName("Should accept valid theme values")
        void validateTheme_ValidValues() {
            assertThatCode(() -> validationService.validateUserSettings(Map.of("theme", "light"))).doesNotThrowAnyException();
            assertThatCode(() -> validationService.validateUserSettings(Map.of("theme", "dark"))).doesNotThrowAnyException();
            assertThatCode(() -> validationService.validateUserSettings(Map.of("theme", "system"))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should reject invalid theme values")
        void validateTheme_InvalidValues() {
            assertThatThrownBy(() -> validationService.validateUserSettings(Map.of("theme", "invalid")))
                    .isInstanceOf(InvalidSettingsException.class)
                    .hasMessageContaining("Invalid value");
        }

        @Test
        @DisplayName("Should accept valid language codes")
        void validateLanguage_ValidValues() {
            assertThatCode(() -> validationService.validateUserSettings(Map.of("language", "en"))).doesNotThrowAnyException();
            assertThatCode(() -> validationService.validateUserSettings(Map.of("language", "en-US"))).doesNotThrowAnyException();
            assertThatCode(() -> validationService.validateUserSettings(Map.of("language", "fr"))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should reject invalid language codes")
        void validateLanguage_InvalidValues() {
            assertThatThrownBy(() -> validationService.validateUserSettings(Map.of("language", "invalid-language-code-that-is-too-long")))
                    .isInstanceOf(InvalidSettingsException.class)
                    .hasMessageContaining("valid language code");
        }

        @Test
        @DisplayName("Should accept valid timezone values")
        void validateTimezone_ValidValues() {
            assertThatCode(() -> validationService.validateUserSettings(Map.of("timezone", "America/New_York"))).doesNotThrowAnyException();
            assertThatCode(() -> validationService.validateUserSettings(Map.of("timezone", "UTC"))).doesNotThrowAnyException();
            assertThatCode(() -> validationService.validateUserSettings(Map.of("timezone", "Europe/London"))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should reject invalid timezone values")
        void validateTimezone_InvalidValues() {
            assertThatThrownBy(() -> validationService.validateUserSettings(Map.of("timezone", "Invalid/Timezone")))
                    .isInstanceOf(InvalidSettingsException.class)
                    .hasMessageContaining("valid timezone");
        }

        @Test
        @DisplayName("Should accept valid boolean values")
        void validateBoolean_ValidValues() {
            assertThatCode(() -> validationService.validateUserSettings(Map.of("openDocumentsInNewTab", true))).doesNotThrowAnyException();
            assertThatCode(() -> validationService.validateUserSettings(Map.of("openDocumentsInNewTab", false))).doesNotThrowAnyException();
            assertThatCode(() -> validationService.validateUserSettings(Map.of("openDocumentsInNewTab", "true"))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should reject invalid boolean values")
        void validateBoolean_InvalidValues() {
            assertThatThrownBy(() -> validationService.validateUserSettings(Map.of("openDocumentsInNewTab", "maybe")))
                    .isInstanceOf(InvalidSettingsException.class)
                    .hasMessageContaining("boolean");
        }

        @Test
        @DisplayName("Should accept valid itemsPerPage values")
        void validateItemsPerPage_ValidValues() {
            assertThatCode(() -> validationService.validateUserSettings(Map.of("documentView.itemsPerPage", 50))).doesNotThrowAnyException();
            assertThatCode(() -> validationService.validateUserSettings(Map.of("documentView.itemsPerPage", 100))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should reject out-of-range itemsPerPage values")
        void validateItemsPerPage_InvalidValues() {
            assertThatThrownBy(() -> validationService.validateUserSettings(Map.of("documentView.itemsPerPage", 5)))
                    .isInstanceOf(InvalidSettingsException.class)
                    .hasMessageContaining("between");
            assertThatThrownBy(() -> validationService.validateUserSettings(Map.of("documentView.itemsPerPage", 500)))
                    .isInstanceOf(InvalidSettingsException.class)
                    .hasMessageContaining("between");
        }

        @Test
        @DisplayName("Should reject null settings map")
        void validateNullSettings() {
            assertThatThrownBy(() -> validationService.validateUserSettings(null))
                    .isInstanceOf(InvalidSettingsException.class)
                    .hasMessageContaining("cannot be empty");
        }

        @Test
        @DisplayName("Should reject empty settings map")
        void validateEmptySettings() {
            assertThatThrownBy(() -> validationService.validateUserSettings(Map.of()))
                    .isInstanceOf(InvalidSettingsException.class)
                    .hasMessageContaining("cannot be empty");
        }

        @Test
        @DisplayName("Should reject null values in settings")
        void validateNullValues() {
            Map<String, Object> settings = new java.util.HashMap<>();
            settings.put("theme", null);
            assertThatThrownBy(() -> validationService.validateUserSettings(settings))
                    .isInstanceOf(InvalidSettingsException.class)
                    .hasMessageContaining("cannot be null");
        }
    }

    @Nested
    @DisplayName("Tenant Settings Validation Tests")
    class TenantSettingsValidationTests {

        @Test
        @DisplayName("Should accept valid color values")
        void validateColor_ValidValues() {
            assertThatCode(() -> validationService.validateTenantSettings(Map.of("branding.primaryColor", "#FF5733"))).doesNotThrowAnyException();
            assertThatCode(() -> validationService.validateTenantSettings(Map.of("branding.primaryColor", "#000000"))).doesNotThrowAnyException();
            assertThatCode(() -> validationService.validateTenantSettings(Map.of("branding.secondaryColor", "#ffffff"))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should reject invalid color values")
        void validateColor_InvalidValues() {
            assertThatThrownBy(() -> validationService.validateTenantSettings(Map.of("branding.primaryColor", "red")))
                    .isInstanceOf(InvalidSettingsException.class)
                    .hasMessageContaining("hex color");
            assertThatThrownBy(() -> validationService.validateTenantSettings(Map.of("branding.primaryColor", "#GGG")))
                    .isInstanceOf(InvalidSettingsException.class)
                    .hasMessageContaining("hex color");
        }

        @Test
        @DisplayName("Should accept valid URL values")
        void validateUrl_ValidValues() {
            assertThatCode(() -> validationService.validateTenantSettings(Map.of("branding.logoUrl", "https://example.com/logo.png"))).doesNotThrowAnyException();
            assertThatCode(() -> validationService.validateTenantSettings(Map.of("branding.logoUrl", "http://example.com/logo.png"))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should reject invalid URL values")
        void validateUrl_InvalidValues() {
            assertThatThrownBy(() -> validationService.validateTenantSettings(Map.of("branding.logoUrl", "not-a-url")))
                    .isInstanceOf(InvalidSettingsException.class)
                    .hasMessageContaining("HTTP/HTTPS URL");
            assertThatThrownBy(() -> validationService.validateTenantSettings(Map.of("branding.logoUrl", "ftp://example.com/logo.png")))
                    .isInstanceOf(InvalidSettingsException.class)
                    .hasMessageContaining("HTTP/HTTPS URL");
        }

        @Test
        @DisplayName("Should accept valid email values")
        void validateEmail_ValidValues() {
            assertThatCode(() -> validationService.validateTenantSettings(Map.of("branding.supportEmail", "support@example.com"))).doesNotThrowAnyException();
            assertThatCode(() -> validationService.validateTenantSettings(Map.of("branding.supportEmail", "user+tag@domain.co.uk"))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should reject invalid email values")
        void validateEmail_InvalidValues() {
            assertThatThrownBy(() -> validationService.validateTenantSettings(Map.of("branding.supportEmail", "not-an-email")))
                    .isInstanceOf(InvalidSettingsException.class)
                    .hasMessageContaining("valid email");
        }

        @Test
        @DisplayName("Should accept valid storage quota values")
        void validateStorageQuota_ValidValues() {
            assertThatCode(() -> validationService.validateTenantSettings(Map.of("storage.defaultQuotaBytes", 1024 * 1024 * 1024L))).doesNotThrowAnyException(); // 1GB
            assertThatCode(() -> validationService.validateTenantSettings(Map.of("storage.defaultQuotaBytes", 10L * 1024 * 1024 * 1024))).doesNotThrowAnyException(); // 10GB
        }

        @Test
        @DisplayName("Should reject out-of-range storage quota values")
        void validateStorageQuota_InvalidValues() {
            assertThatThrownBy(() -> validationService.validateTenantSettings(Map.of("storage.defaultQuotaBytes", 1024L))) // Too small (1KB)
                    .isInstanceOf(InvalidSettingsException.class)
                    .hasMessageContaining("between");
            assertThatThrownBy(() -> validationService.validateTenantSettings(Map.of("storage.defaultQuotaBytes", -1L))) // Negative
                    .isInstanceOf(InvalidSettingsException.class)
                    .hasMessageContaining("between");
        }

        @Test
        @DisplayName("Should accept valid session timeout values")
        void validateSessionTimeout_ValidValues() {
            assertThatCode(() -> validationService.validateTenantSettings(Map.of("security.sessionTimeoutMinutes", 30))).doesNotThrowAnyException();
            assertThatCode(() -> validationService.validateTenantSettings(Map.of("security.sessionTimeoutMinutes", 480))).doesNotThrowAnyException(); // 8 hours
        }

        @Test
        @DisplayName("Should reject out-of-range session timeout values")
        void validateSessionTimeout_InvalidValues() {
            assertThatThrownBy(() -> validationService.validateTenantSettings(Map.of("security.sessionTimeoutMinutes", 1))) // Too short
                    .isInstanceOf(InvalidSettingsException.class)
                    .hasMessageContaining("between");
            assertThatThrownBy(() -> validationService.validateTenantSettings(Map.of("security.sessionTimeoutMinutes", 100000))) // Too long
                    .isInstanceOf(InvalidSettingsException.class)
                    .hasMessageContaining("between");
        }

        @Test
        @DisplayName("Should accept valid file type lists")
        void validateFileTypes_ValidValues() {
            assertThatCode(() -> validationService.validateTenantSettings(Map.of("storage.allowedFileTypes", List.of(".pdf", ".docx", ".xlsx")))).doesNotThrowAnyException();
            assertThatCode(() -> validationService.validateTenantSettings(Map.of("storage.allowedFileTypes", List.of("application/pdf")))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should reject invalid file type formats")
        void validateFileTypes_InvalidValues() {
            assertThatThrownBy(() -> validationService.validateTenantSettings(Map.of("storage.allowedFileTypes", List.of("pdf")))) // Missing dot
                    .isInstanceOf(InvalidSettingsException.class)
                    .hasMessageContaining("file type format");
        }

        @Test
        @DisplayName("Should accept valid IP CIDR ranges")
        void validateIpRanges_ValidValues() {
            assertThatCode(() -> validationService.validateTenantSettings(Map.of("security.allowedIpRanges", List.of("192.168.1.0/24")))).doesNotThrowAnyException();
            assertThatCode(() -> validationService.validateTenantSettings(Map.of("security.allowedIpRanges", List.of("10.0.0.1", "172.16.0.0/16")))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should reject invalid IP CIDR ranges")
        void validateIpRanges_InvalidValues() {
            assertThatThrownBy(() -> validationService.validateTenantSettings(Map.of("security.allowedIpRanges", List.of("not-an-ip"))))
                    .isInstanceOf(InvalidSettingsException.class)
                    .hasMessageContaining("IP/CIDR format");
            assertThatThrownBy(() -> validationService.validateTenantSettings(Map.of("security.allowedIpRanges", List.of("999.999.999.999"))))
                    .isInstanceOf(InvalidSettingsException.class)
                    .hasMessageContaining("IP/CIDR format");
        }

        @Test
        @DisplayName("Should reject XSS attempts in string fields")
        void validateXss_Rejected() {
            assertThatThrownBy(() -> validationService.validateTenantSettings(Map.of("branding.companyName", "<script>alert('xss')</script>")))
                    .isInstanceOf(InvalidSettingsException.class)
                    .hasMessageContaining("malicious");
            assertThatThrownBy(() -> validationService.validateTenantSettings(Map.of("branding.companyName", "test javascript: alert(1)")))
                    .isInstanceOf(InvalidSettingsException.class)
                    .hasMessageContaining("malicious");
        }
    }

    @Nested
    @DisplayName("Drive Settings Validation Tests")
    class DriveSettingsValidationTests {

        @Test
        @DisplayName("Should accept valid view values")
        void validateView_ValidValues() {
            assertThatCode(() -> validationService.validateDriveSettings(Map.of("defaultView", "list"))).doesNotThrowAnyException();
            assertThatCode(() -> validationService.validateDriveSettings(Map.of("defaultView", "grid"))).doesNotThrowAnyException();
            assertThatCode(() -> validationService.validateDriveSettings(Map.of("defaultView", "details"))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should reject invalid view values")
        void validateView_InvalidValues() {
            assertThatThrownBy(() -> validationService.validateDriveSettings(Map.of("defaultView", "invalid")))
                    .isInstanceOf(InvalidSettingsException.class)
                    .hasMessageContaining("Invalid value");
        }

        @Test
        @DisplayName("Should accept valid sort field values")
        void validateSortBy_ValidValues() {
            assertThatCode(() -> validationService.validateDriveSettings(Map.of("sortBy", "name"))).doesNotThrowAnyException();
            assertThatCode(() -> validationService.validateDriveSettings(Map.of("sortBy", "modifiedAt"))).doesNotThrowAnyException();
            assertThatCode(() -> validationService.validateDriveSettings(Map.of("sortBy", "size"))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should accept valid sort order values")
        void validateSortOrder_ValidValues() {
            assertThatCode(() -> validationService.validateDriveSettings(Map.of("sortOrder", "asc"))).doesNotThrowAnyException();
            assertThatCode(() -> validationService.validateDriveSettings(Map.of("sortOrder", "desc"))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should reject invalid sort order values")
        void validateSortOrder_InvalidValues() {
            assertThatThrownBy(() -> validationService.validateDriveSettings(Map.of("sortOrder", "random")))
                    .isInstanceOf(InvalidSettingsException.class)
                    .hasMessageContaining("Invalid value");
        }

        @Test
        @DisplayName("Should accept valid string lists")
        void validateStringList_ValidValues() {
            assertThatCode(() -> validationService.validateDriveSettings(Map.of("pinnedFolders", List.of("folder-1", "folder-2")))).doesNotThrowAnyException();
            assertThatCode(() -> validationService.validateDriveSettings(Map.of("favoriteDocuments", List.of()))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should reject oversized lists")
        void validateStringList_TooLarge() {
            List<String> hugeList = new java.util.ArrayList<>();
            for (int i = 0; i < 150; i++) {
                hugeList.add("item-" + i);
            }
            assertThatThrownBy(() -> validationService.validateDriveSettings(Map.of("pinnedFolders", hugeList)))
                    .isInstanceOf(InvalidSettingsException.class)
                    .hasMessageContaining("exceed");
        }
    }
}
