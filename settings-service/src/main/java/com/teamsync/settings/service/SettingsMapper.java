package com.teamsync.settings.service;

import com.teamsync.settings.dto.SettingsValidationResult;
import com.teamsync.settings.exception.InvalidSettingsException;
import com.teamsync.settings.model.DriveSettings;
import com.teamsync.settings.model.TenantSettings;
import com.teamsync.settings.model.UserSettings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * SECURITY: Mapper for applying partial updates to settings entities.
 * Handles dot notation for nested properties.
 *
 * Security improvements:
 * - Throws exceptions on invalid numeric values instead of defaulting to 0
 * - Collects all validation errors and returns them to the caller
 * - Validates allowed setting keys (prevents injection of arbitrary keys)
 */
@Component
@Slf4j
public class SettingsMapper {

    /**
     * Apply updates to user settings.
     * SECURITY: Now collects errors and throws exception if any updates fail.
     *
     * @throws InvalidSettingsException if any settings fail validation
     */
    public SettingsValidationResult applyUserSettingsUpdates(UserSettings settings, Map<String, Object> updates) {
        SettingsValidationResult result = new SettingsValidationResult();

        updates.forEach((key, value) -> {
            try {
                applyUserSettingUpdate(settings, key, value);
                result.incrementApplied();
            } catch (InvalidSettingsException e) {
                log.warn("SECURITY: Validation failed for user setting '{}': {}", key, e.getMessage());
                result.addError(key, e.getMessage());
            } catch (Exception e) {
                log.warn("SECURITY: Failed to apply update for key '{}': {}", key, e.getMessage());
                result.addError(key, "Invalid value type");
            }
        });

        if (result.hasErrors()) {
            throw new InvalidSettingsException(result);
        }

        return result;
    }

    /**
     * Apply updates to tenant settings.
     * SECURITY: Now collects errors and throws exception if any updates fail.
     *
     * @throws InvalidSettingsException if any settings fail validation
     */
    public SettingsValidationResult applyTenantSettingsUpdates(TenantSettings settings, Map<String, Object> updates) {
        SettingsValidationResult result = new SettingsValidationResult();

        updates.forEach((key, value) -> {
            try {
                applyTenantSettingUpdate(settings, key, value);
                result.incrementApplied();
            } catch (InvalidSettingsException e) {
                log.warn("SECURITY: Validation failed for tenant setting '{}': {}", key, e.getMessage());
                result.addError(key, e.getMessage());
            } catch (Exception e) {
                log.warn("SECURITY: Failed to apply update for key '{}': {}", key, e.getMessage());
                result.addError(key, "Invalid value type");
            }
        });

        if (result.hasErrors()) {
            throw new InvalidSettingsException(result);
        }

        return result;
    }

    /**
     * Apply updates to drive settings.
     * SECURITY: Now collects errors and throws exception if any updates fail.
     *
     * @throws InvalidSettingsException if any settings fail validation
     */
    public SettingsValidationResult applyDriveSettingsUpdates(DriveSettings settings, Map<String, Object> updates) {
        SettingsValidationResult result = new SettingsValidationResult();

        updates.forEach((key, value) -> {
            try {
                applyDriveSettingUpdate(settings, key, value);
                result.incrementApplied();
            } catch (InvalidSettingsException e) {
                log.warn("SECURITY: Validation failed for drive setting '{}': {}", key, e.getMessage());
                result.addError(key, e.getMessage());
            } catch (Exception e) {
                log.warn("SECURITY: Failed to apply update for key '{}': {}", key, e.getMessage());
                result.addError(key, "Invalid value type");
            }
        });

        if (result.hasErrors()) {
            throw new InvalidSettingsException(result);
        }

        return result;
    }

    private void applyUserSettingUpdate(UserSettings settings, String key, Object value) {
        if (settings.getNotifications() == null) {
            settings.setNotifications(new UserSettings.NotificationSettings());
        }
        if (settings.getDocumentView() == null) {
            settings.setDocumentView(new UserSettings.DocumentViewSettings());
        }
        if (settings.getAccessibility() == null) {
            settings.setAccessibility(new UserSettings.AccessibilitySettings());
        }
        if (settings.getViewerPreferences() == null) {
            settings.setViewerPreferences(new UserSettings.ViewerPreferences());
        }

        switch (key) {
            case "theme" -> settings.setTheme(toString(key, value));
            case "language" -> settings.setLanguage(toString(key, value));
            case "timezone" -> settings.setTimezone(toString(key, value));
            case "dateFormat" -> settings.setDateFormat(toString(key, value));
            case "timeFormat" -> settings.setTimeFormat(toString(key, value));
            case "defaultLandingPage" -> settings.setDefaultLandingPage(toString(key, value));
            case "openDocumentsInNewTab" -> settings.setOpenDocumentsInNewTab(toBoolean(key, value));

            // Notification settings
            case "notifications.emailEnabled" -> settings.getNotifications().setEmailEnabled(toBoolean(key, value));
            case "notifications.pushEnabled" -> settings.getNotifications().setPushEnabled(toBoolean(key, value));
            case "notifications.inAppEnabled" -> settings.getNotifications().setInAppEnabled(toBoolean(key, value));
            case "notifications.mentionsOnly" -> settings.getNotifications().setMentionsOnly(toBoolean(key, value));
            case "notifications.dailyDigest" -> settings.getNotifications().setDailyDigest(toBoolean(key, value));
            case "notifications.digestTime" -> settings.getNotifications().setDigestTime(toString(key, value));

            // Document view settings
            case "documentView.defaultView" -> settings.getDocumentView().setDefaultView(toString(key, value));
            case "documentView.sortBy" -> settings.getDocumentView().setSortBy(toString(key, value));
            case "documentView.sortOrder" -> settings.getDocumentView().setSortOrder(toString(key, value));
            case "documentView.showHiddenFiles" -> settings.getDocumentView().setShowHiddenFiles(toBoolean(key, value));
            case "documentView.itemsPerPage" -> settings.getDocumentView().setItemsPerPage(toInt(key, value));

            // Accessibility settings
            case "accessibility.highContrast" -> settings.getAccessibility().setHighContrast(toBoolean(key, value));
            case "accessibility.reducedMotion" -> settings.getAccessibility().setReducedMotion(toBoolean(key, value));
            case "accessibility.fontSize" -> settings.getAccessibility().setFontSize(toString(key, value));
            case "accessibility.screenReaderOptimized" -> settings.getAccessibility().setScreenReaderOptimized(toBoolean(key, value));

            // Viewer preferences
            case "viewerPreferences.pdf" -> settings.getViewerPreferences().setPdf(toString(key, value));
            case "viewerPreferences.office" -> settings.getViewerPreferences().setOffice(toString(key, value));
            case "viewerPreferences.image" -> settings.getViewerPreferences().setImage(toString(key, value));

            default -> throw new InvalidSettingsException(key, "Unknown setting key");
        }
    }

    private void applyTenantSettingUpdate(TenantSettings settings, String key, Object value) {
        if (settings.getBranding() == null) {
            settings.setBranding(new TenantSettings.BrandingSettings());
        }
        if (settings.getStorage() == null) {
            settings.setStorage(new TenantSettings.StorageSettings());
        }
        if (settings.getSecurity() == null) {
            settings.setSecurity(new TenantSettings.SecuritySettings());
        }
        if (settings.getFeatures() == null) {
            settings.setFeatures(new TenantSettings.FeatureSettings());
        }
        if (settings.getEmail() == null) {
            settings.setEmail(new TenantSettings.EmailSettings());
        }
        if (settings.getIntegrations() == null) {
            settings.setIntegrations(new TenantSettings.IntegrationSettings());
        }

        switch (key) {
            // Branding settings
            case "branding.logoUrl" -> settings.getBranding().setLogoUrl(toString(key, value));
            case "branding.faviconUrl" -> settings.getBranding().setFaviconUrl(toString(key, value));
            case "branding.primaryColor" -> settings.getBranding().setPrimaryColor(toString(key, value));
            case "branding.secondaryColor" -> settings.getBranding().setSecondaryColor(toString(key, value));
            case "branding.companyName" -> settings.getBranding().setCompanyName(toString(key, value));
            case "branding.supportEmail" -> settings.getBranding().setSupportEmail(toString(key, value));
            case "branding.customLoginPage" -> settings.getBranding().setCustomLoginPage(toBoolean(key, value));

            // Storage settings
            case "storage.defaultQuotaBytes" -> settings.getStorage().setDefaultQuotaBytes(toLong(key, value));
            case "storage.maxFileSizeBytes" -> settings.getStorage().setMaxFileSizeBytes(toLong(key, value));
            case "storage.trashRetentionDays" -> settings.getStorage().setTrashRetentionDays(toPositiveInt(key, value));
            case "storage.versionRetentionCount" -> settings.getStorage().setVersionRetentionCount(toPositiveInt(key, value));
            case "storage.autoVersioning" -> settings.getStorage().setAutoVersioning(toBoolean(key, value));
            case "storage.allowedFileTypes" -> settings.getStorage().setAllowedFileTypes(toStringList(key, value));
            case "storage.blockedFileTypes" -> settings.getStorage().setBlockedFileTypes(toStringList(key, value));

            // Security settings - CRITICAL: These must validate properly
            case "security.mfaRequired" -> settings.getSecurity().setMfaRequired(toBoolean(key, value));
            case "security.mfaEnforced" -> settings.getSecurity().setMfaEnforced(toBoolean(key, value));
            case "security.sessionTimeoutMinutes" -> settings.getSecurity().setSessionTimeoutMinutes(toPositiveInt(key, value));
            case "security.passwordExpiryDays" -> settings.getSecurity().setPasswordExpiryDays(toPositiveInt(key, value));
            case "security.passwordMinLength" -> settings.getSecurity().setPasswordMinLength(toPositiveInt(key, value, 8, 128));
            case "security.passwordRequireUppercase" -> settings.getSecurity().setPasswordRequireUppercase(toBoolean(key, value));
            case "security.passwordRequireNumber" -> settings.getSecurity().setPasswordRequireNumber(toBoolean(key, value));
            case "security.passwordRequireSpecial" -> settings.getSecurity().setPasswordRequireSpecial(toBoolean(key, value));
            case "security.allowPublicSharing" -> settings.getSecurity().setAllowPublicSharing(toBoolean(key, value));
            case "security.publicLinkExpiryDays" -> settings.getSecurity().setPublicLinkExpiryDays(toPositiveInt(key, value));
            case "security.watermarkEnabled" -> settings.getSecurity().setWatermarkEnabled(toBoolean(key, value));
            case "security.downloadRestrictions" -> settings.getSecurity().setDownloadRestrictions(toBoolean(key, value));
            case "security.allowedIpRanges" -> settings.getSecurity().setAllowedIpRanges(toStringList(key, value));

            // Feature settings
            case "features.aiMetadataExtraction" -> settings.getFeatures().setAiMetadataExtraction(toBoolean(key, value));
            case "features.docuTalkChat" -> settings.getFeatures().setDocuTalkChat(toBoolean(key, value));
            case "features.officeEditing" -> settings.getFeatures().setOfficeEditing(toBoolean(key, value));
            case "features.advancedSearch" -> settings.getFeatures().setAdvancedSearch(toBoolean(key, value));
            case "features.workflowAutomation" -> settings.getFeatures().setWorkflowAutomation(toBoolean(key, value));
            case "features.auditLogging" -> settings.getFeatures().setAuditLogging(toBoolean(key, value));
            case "features.realTimeCollaboration" -> settings.getFeatures().setRealTimeCollaboration(toBoolean(key, value));
            case "features.customDocumentTypes" -> settings.getFeatures().setCustomDocumentTypes(toBoolean(key, value));

            // Email settings
            case "email.notificationsEnabled" -> settings.getEmail().setNotificationsEnabled(toBoolean(key, value));
            case "email.fromName" -> settings.getEmail().setFromName(toString(key, value));
            case "email.replyToEmail" -> settings.getEmail().setReplyToEmail(toString(key, value));
            case "email.includeLogoInEmails" -> settings.getEmail().setIncludeLogoInEmails(toBoolean(key, value));
            case "email.emailFooter" -> settings.getEmail().setEmailFooter(toString(key, value));

            // Integration settings
            case "integrations.ldapEnabled" -> settings.getIntegrations().setLdapEnabled(toBoolean(key, value));
            case "integrations.ssoEnabled" -> settings.getIntegrations().setSsoEnabled(toBoolean(key, value));
            case "integrations.webhooksEnabled" -> settings.getIntegrations().setWebhooksEnabled(toBoolean(key, value));
            case "integrations.apiAccessEnabled" -> settings.getIntegrations().setApiAccessEnabled(toBoolean(key, value));
            case "integrations.apiRateLimitPerMinute" -> settings.getIntegrations().setApiRateLimitPerMinute(toPositiveInt(key, value));

            default -> throw new InvalidSettingsException(key, "Unknown setting key");
        }
    }

    private void applyDriveSettingUpdate(DriveSettings settings, String key, Object value) {
        switch (key) {
            case "defaultView" -> settings.setDefaultView(toString(key, value));
            case "sortBy" -> settings.setSortBy(toString(key, value));
            case "sortOrder" -> settings.setSortOrder(toString(key, value));
            case "showHiddenFiles" -> settings.setShowHiddenFiles(toBoolean(key, value));
            case "showThumbnails" -> settings.setShowThumbnails(toBoolean(key, value));
            case "itemsPerPage" -> settings.setItemsPerPage(toPositiveInt(key, value, 10, 200));
            case "visibleColumns" -> settings.setVisibleColumns(toStringList(key, value));
            case "columnOrder" -> settings.setColumnOrder(toStringList(key, value));
            case "pinnedFolders" -> settings.setPinnedFolders(toStringList(key, value));
            case "favoriteDocuments" -> settings.setFavoriteDocuments(toStringList(key, value));
            case "defaultFilters" -> settings.setDefaultFilters(toStringList(key, value));
            case "rememberLastFolder" -> settings.setRememberLastFolder(toBoolean(key, value));
            case "lastFolderId" -> settings.setLastFolderId(toString(key, value));

            default -> throw new InvalidSettingsException(key, "Unknown setting key");
        }
    }

    /**
     * SECURITY: Convert to string with null check.
     */
    private String toString(String key, Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s;
        }
        return value.toString();
    }

    /**
     * SECURITY: Convert to boolean, throw on invalid type.
     */
    private boolean toBoolean(String key, Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            if ("true".equalsIgnoreCase(s) || "false".equalsIgnoreCase(s)) {
                return Boolean.parseBoolean(s);
            }
            throw new InvalidSettingsException(key, "Must be true or false, got: " + s);
        }
        throw new InvalidSettingsException(key, "Must be a boolean value");
    }

    /**
     * SECURITY: Convert to int, throw on invalid value instead of defaulting to 0.
     */
    private int toInt(String key, Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException e) {
                throw new InvalidSettingsException(key, "Invalid integer value: " + s);
            }
        }
        throw new InvalidSettingsException(key, "Must be an integer value");
    }

    /**
     * SECURITY: Convert to positive int with optional min/max bounds.
     */
    private int toPositiveInt(String key, Object value) {
        int intValue = toInt(key, value);
        if (intValue < 0) {
            throw new InvalidSettingsException(key, "Must be a positive integer, got: " + intValue);
        }
        return intValue;
    }

    /**
     * SECURITY: Convert to positive int with min/max bounds validation.
     */
    private int toPositiveInt(String key, Object value, int min, int max) {
        int intValue = toInt(key, value);
        if (intValue < min || intValue > max) {
            throw new InvalidSettingsException(key,
                    String.format("Must be between %d and %d, got: %d", min, max, intValue));
        }
        return intValue;
    }

    /**
     * SECURITY: Convert to long, throw on invalid value instead of defaulting to 0.
     */
    private long toLong(String key, Object value) {
        if (value instanceof Number n) {
            long longValue = n.longValue();
            if (longValue < 0) {
                throw new InvalidSettingsException(key, "Must be a positive value, got: " + longValue);
            }
            return longValue;
        }
        if (value instanceof String s) {
            try {
                long longValue = Long.parseLong(s.trim());
                if (longValue < 0) {
                    throw new InvalidSettingsException(key, "Must be a positive value, got: " + longValue);
                }
                return longValue;
            } catch (NumberFormatException e) {
                throw new InvalidSettingsException(key, "Invalid numeric value: " + s);
            }
        }
        throw new InvalidSettingsException(key, "Must be a numeric value");
    }

    /**
     * SECURITY: Convert to string list with validation.
     */
    @SuppressWarnings("unchecked")
    private List<String> toStringList(String key, Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(item -> item != null)
                    .map(Object::toString)
                    .toList();
        }
        if (value instanceof String s && !s.isBlank()) {
            // Support comma-separated strings
            return List.of(s.split(",")).stream()
                    .map(String::trim)
                    .filter(item -> !item.isEmpty())
                    .toList();
        }
        throw new InvalidSettingsException(key, "Must be an array or comma-separated string");
    }
}
