package com.teamsync.settings.service;

import com.teamsync.settings.exception.InvalidSettingsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Service for validating settings values before persistence.
 * Provides security validation to prevent injection attacks and invalid data.
 */
@Service
@Slf4j
public class SettingsValidationService {

    // Valid themes
    private static final Set<String> VALID_THEMES = Set.of("light", "dark", "system", "high-contrast");

    // Valid view types
    private static final Set<String> VALID_VIEWS = Set.of("list", "grid", "details", "compact");

    // Valid sort fields
    private static final Set<String> VALID_SORT_FIELDS = Set.of(
            "name", "modifiedAt", "createdAt", "size", "type", "owner"
    );

    // Valid sort orders
    private static final Set<String> VALID_SORT_ORDERS = Set.of("asc", "desc");

    // Valid font sizes
    private static final Set<String> VALID_FONT_SIZES = Set.of("small", "medium", "large", "extra-large");

    // Valid time formats
    private static final Set<String> VALID_TIME_FORMATS = Set.of("12h", "24h");

    // Valid date formats
    private static final Set<String> VALID_DATE_FORMATS = Set.of(
            "MM/dd/yyyy", "dd/MM/yyyy", "yyyy-MM-dd", "dd-MM-yyyy",
            "MMM dd, yyyy", "dd MMM yyyy"
    );

    // Valid viewer preferences
    private static final Set<String> VALID_VIEWERS = Set.of("native", "browser", "embedded", "download");

    // Valid landing pages
    private static final Set<String> VALID_LANDING_PAGES = Set.of(
            "home", "recent", "favorites", "shared", "trash", "drive"
    );

    // Patterns for validation
    private static final Pattern COLOR_PATTERN = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final Pattern URL_PATTERN = Pattern.compile("^https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    private static final Pattern SAFE_STRING_PATTERN = Pattern.compile("^[\\p{L}\\p{N}\\s\\-_.,:;!?@#$%^&*()+=\\[\\]{}|/\\\\~`'\"<>]+$");
    private static final Pattern IP_CIDR_PATTERN = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)(/([0-9]|[1-2]\\d|3[0-2]))?$"
    );
    private static final Pattern TIME_PATTERN = Pattern.compile("^([01]?\\d|2[0-3]):[0-5]\\d$");

    // Maximum lengths
    private static final int MAX_STRING_LENGTH = 500;
    private static final int MAX_URL_LENGTH = 2048;
    private static final int MAX_LIST_SIZE = 100;
    private static final int MAX_FILE_TYPE_LENGTH = 50;

    // Numeric bounds
    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final int MAX_PASSWORD_LENGTH = 128;
    private static final int MIN_SESSION_TIMEOUT = 5;
    private static final int MAX_SESSION_TIMEOUT = 43200; // 30 days in minutes
    private static final int MIN_ITEMS_PER_PAGE = 10;
    private static final int MAX_ITEMS_PER_PAGE = 200;
    private static final long MIN_QUOTA_BYTES = 1024 * 1024; // 1MB
    private static final long MAX_QUOTA_BYTES = 100L * 1024 * 1024 * 1024 * 1024; // 100TB
    private static final long MIN_FILE_SIZE_BYTES = 1024; // 1KB
    private static final long MAX_FILE_SIZE_BYTES = 50L * 1024 * 1024 * 1024; // 50GB
    private static final int MIN_TRASH_RETENTION = 1;
    private static final int MAX_TRASH_RETENTION = 365;
    private static final int MIN_VERSION_RETENTION = 1;
    private static final int MAX_VERSION_RETENTION = 1000;
    private static final int MIN_API_RATE_LIMIT = 10;
    private static final int MAX_API_RATE_LIMIT = 10000;
    private static final int MIN_PASSWORD_EXPIRY = 0; // 0 = no expiry
    private static final int MAX_PASSWORD_EXPIRY = 365;
    private static final int MIN_PUBLIC_LINK_EXPIRY = 1;
    private static final int MAX_PUBLIC_LINK_EXPIRY = 365;

    /**
     * Validate user settings.
     */
    public void validateUserSettings(Map<String, Object> settings) {
        if (settings == null || settings.isEmpty()) {
            throw new InvalidSettingsException("Settings cannot be empty");
        }

        for (Map.Entry<String, Object> entry : settings.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value == null) {
                throw new InvalidSettingsException(key, "Value cannot be null");
            }

            validateUserSettingValue(key, value);
        }
    }

    /**
     * Validate tenant settings.
     */
    public void validateTenantSettings(Map<String, Object> settings) {
        if (settings == null || settings.isEmpty()) {
            throw new InvalidSettingsException("Settings cannot be empty");
        }

        for (Map.Entry<String, Object> entry : settings.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value == null) {
                throw new InvalidSettingsException(key, "Value cannot be null");
            }

            validateTenantSettingValue(key, value);
        }
    }

    /**
     * Validate drive settings.
     */
    public void validateDriveSettings(Map<String, Object> settings) {
        if (settings == null || settings.isEmpty()) {
            throw new InvalidSettingsException("Settings cannot be empty");
        }

        for (Map.Entry<String, Object> entry : settings.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value == null) {
                throw new InvalidSettingsException(key, "Value cannot be null");
            }

            validateDriveSettingValue(key, value);
        }
    }

    private void validateUserSettingValue(String key, Object value) {
        switch (key) {
            case "theme" -> validateEnum(key, value, VALID_THEMES);
            case "language" -> validateLanguage(key, value);
            case "timezone" -> validateTimezone(key, value);
            case "dateFormat" -> validateEnum(key, value, VALID_DATE_FORMATS);
            case "timeFormat" -> validateEnum(key, value, VALID_TIME_FORMATS);
            case "defaultLandingPage" -> validateEnum(key, value, VALID_LANDING_PAGES);
            case "openDocumentsInNewTab" -> validateBoolean(key, value);

            // Notification settings
            case "notifications.emailEnabled",
                 "notifications.pushEnabled",
                 "notifications.inAppEnabled",
                 "notifications.mentionsOnly",
                 "notifications.dailyDigest" -> validateBoolean(key, value);
            case "notifications.digestTime" -> validateTime(key, value);

            // Document view settings
            case "documentView.defaultView" -> validateEnum(key, value, VALID_VIEWS);
            case "documentView.sortBy" -> validateEnum(key, value, VALID_SORT_FIELDS);
            case "documentView.sortOrder" -> validateEnum(key, value, VALID_SORT_ORDERS);
            case "documentView.showHiddenFiles" -> validateBoolean(key, value);
            case "documentView.itemsPerPage" -> validateIntRange(key, value, MIN_ITEMS_PER_PAGE, MAX_ITEMS_PER_PAGE);

            // Accessibility settings
            case "accessibility.highContrast",
                 "accessibility.reducedMotion",
                 "accessibility.screenReaderOptimized" -> validateBoolean(key, value);
            case "accessibility.fontSize" -> validateEnum(key, value, VALID_FONT_SIZES);

            // Viewer preferences
            case "viewerPreferences.pdf",
                 "viewerPreferences.office",
                 "viewerPreferences.image" -> validateEnum(key, value, VALID_VIEWERS);

            default -> log.warn("Unknown user setting key: {}", key);
        }
    }

    private void validateTenantSettingValue(String key, Object value) {
        switch (key) {
            // Branding settings
            case "branding.logoUrl",
                 "branding.faviconUrl" -> validateUrl(key, value);
            case "branding.primaryColor",
                 "branding.secondaryColor" -> validateColor(key, value);
            case "branding.companyName" -> validateSafeString(key, value, MAX_STRING_LENGTH);
            case "branding.supportEmail" -> validateEmail(key, value);
            case "branding.customLoginPage" -> validateBoolean(key, value);

            // Storage settings
            case "storage.defaultQuotaBytes" -> validateLongRange(key, value, MIN_QUOTA_BYTES, MAX_QUOTA_BYTES);
            case "storage.maxFileSizeBytes" -> validateLongRange(key, value, MIN_FILE_SIZE_BYTES, MAX_FILE_SIZE_BYTES);
            case "storage.trashRetentionDays" -> validateIntRange(key, value, MIN_TRASH_RETENTION, MAX_TRASH_RETENTION);
            case "storage.versionRetentionCount" -> validateIntRange(key, value, MIN_VERSION_RETENTION, MAX_VERSION_RETENTION);
            case "storage.autoVersioning" -> validateBoolean(key, value);
            case "storage.allowedFileTypes",
                 "storage.blockedFileTypes" -> validateFileTypeList(key, value);

            // Security settings
            case "security.mfaRequired",
                 "security.mfaEnforced",
                 "security.passwordRequireUppercase",
                 "security.passwordRequireNumber",
                 "security.passwordRequireSpecial",
                 "security.allowPublicSharing",
                 "security.watermarkEnabled",
                 "security.downloadRestrictions" -> validateBoolean(key, value);
            case "security.sessionTimeoutMinutes" -> validateIntRange(key, value, MIN_SESSION_TIMEOUT, MAX_SESSION_TIMEOUT);
            case "security.passwordExpiryDays" -> validateIntRange(key, value, MIN_PASSWORD_EXPIRY, MAX_PASSWORD_EXPIRY);
            case "security.passwordMinLength" -> validateIntRange(key, value, MIN_PASSWORD_LENGTH, MAX_PASSWORD_LENGTH);
            case "security.publicLinkExpiryDays" -> validateIntRange(key, value, MIN_PUBLIC_LINK_EXPIRY, MAX_PUBLIC_LINK_EXPIRY);
            case "security.allowedIpRanges" -> validateIpRangeList(key, value);

            // Feature settings
            case "features.aiMetadataExtraction",
                 "features.docuTalkChat",
                 "features.officeEditing",
                 "features.advancedSearch",
                 "features.workflowAutomation",
                 "features.auditLogging",
                 "features.realTimeCollaboration",
                 "features.customDocumentTypes" -> validateBoolean(key, value);

            // Email settings
            case "email.notificationsEnabled",
                 "email.includeLogoInEmails" -> validateBoolean(key, value);
            case "email.fromName" -> validateSafeString(key, value, MAX_STRING_LENGTH);
            case "email.replyToEmail" -> validateEmail(key, value);
            case "email.emailFooter" -> validateSafeString(key, value, MAX_STRING_LENGTH);

            // Integration settings
            case "integrations.ldapEnabled",
                 "integrations.ssoEnabled",
                 "integrations.webhooksEnabled",
                 "integrations.apiAccessEnabled" -> validateBoolean(key, value);
            case "integrations.apiRateLimitPerMinute" -> validateIntRange(key, value, MIN_API_RATE_LIMIT, MAX_API_RATE_LIMIT);

            default -> log.warn("Unknown tenant setting key: {}", key);
        }
    }

    private void validateDriveSettingValue(String key, Object value) {
        switch (key) {
            case "defaultView" -> validateEnum(key, value, VALID_VIEWS);
            case "sortBy" -> validateEnum(key, value, VALID_SORT_FIELDS);
            case "sortOrder" -> validateEnum(key, value, VALID_SORT_ORDERS);
            case "showHiddenFiles",
                 "showThumbnails",
                 "rememberLastFolder" -> validateBoolean(key, value);
            case "itemsPerPage" -> validateIntRange(key, value, MIN_ITEMS_PER_PAGE, MAX_ITEMS_PER_PAGE);
            case "visibleColumns",
                 "columnOrder",
                 "pinnedFolders",
                 "favoriteDocuments",
                 "defaultFilters" -> validateStringList(key, value);
            case "lastFolderId" -> validateSafeString(key, value, MAX_STRING_LENGTH);

            default -> log.warn("Unknown drive setting key: {}", key);
        }
    }

    // ==================== Validation Helper Methods ====================

    private void validateBoolean(String key, Object value) {
        if (!(value instanceof Boolean) && !(value instanceof String s && ("true".equalsIgnoreCase(s) || "false".equalsIgnoreCase(s)))) {
            throw new InvalidSettingsException(key, "Must be a boolean value (true/false)");
        }
    }

    private void validateEnum(String key, Object value, Set<String> validValues) {
        String strValue = toString(value);
        if (!validValues.contains(strValue.toLowerCase())) {
            throw new InvalidSettingsException(key,
                    "Invalid value '" + strValue + "'. Valid values are: " + validValues);
        }
    }

    private void validateIntRange(String key, Object value, int min, int max) {
        int intValue;
        try {
            if (value instanceof Number n) {
                intValue = n.intValue();
            } else if (value instanceof String s) {
                intValue = Integer.parseInt(s);
            } else {
                throw new InvalidSettingsException(key, "Must be an integer");
            }
        } catch (NumberFormatException e) {
            throw new InvalidSettingsException(key, "Must be a valid integer");
        }

        if (intValue < min || intValue > max) {
            throw new InvalidSettingsException(key,
                    "Must be between " + min + " and " + max + " (got " + intValue + ")");
        }
    }

    private void validateLongRange(String key, Object value, long min, long max) {
        long longValue;
        try {
            if (value instanceof Number n) {
                longValue = n.longValue();
            } else if (value instanceof String s) {
                longValue = Long.parseLong(s);
            } else {
                throw new InvalidSettingsException(key, "Must be a number");
            }
        } catch (NumberFormatException e) {
            throw new InvalidSettingsException(key, "Must be a valid number");
        }

        if (longValue < min || longValue > max) {
            throw new InvalidSettingsException(key,
                    "Must be between " + min + " and " + max + " (got " + longValue + ")");
        }
    }

    private void validateSafeString(String key, Object value, int maxLength) {
        String strValue = toString(value);
        if (strValue.length() > maxLength) {
            throw new InvalidSettingsException(key,
                    "Must not exceed " + maxLength + " characters (got " + strValue.length() + ")");
        }
        // Check for potentially malicious content
        if (strValue.contains("<script") || strValue.contains("javascript:") || strValue.contains("onerror=")) {
            throw new InvalidSettingsException(key, "Contains potentially malicious content");
        }
    }

    private void validateColor(String key, Object value) {
        String strValue = toString(value);
        if (!COLOR_PATTERN.matcher(strValue).matches()) {
            throw new InvalidSettingsException(key,
                    "Must be a valid hex color (e.g., #FF5733)");
        }
    }

    /**
     * SECURITY FIX: Validates URL and prevents SSRF attacks by blocking:
     * - Private IP ranges (127.0.0.0/8, 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, 169.254.0.0/16)
     * - Localhost references
     * - Cloud metadata endpoints (169.254.169.254)
     * - Non-HTTP(S) protocols
     */
    private void validateUrl(String key, Object value) {
        String strValue = toString(value);
        if (strValue.length() > MAX_URL_LENGTH) {
            throw new InvalidSettingsException(key,
                    "URL must not exceed " + MAX_URL_LENGTH + " characters");
        }
        if (!URL_PATTERN.matcher(strValue).matches()) {
            throw new InvalidSettingsException(key, "Must be a valid HTTP/HTTPS URL");
        }

        // SSRF Prevention: Validate the URL host is not internal/private
        try {
            URI uri = new URI(strValue);
            String host = uri.getHost();

            if (host == null || host.isBlank()) {
                throw new InvalidSettingsException(key, "URL must have a valid hostname");
            }

            // Block localhost references
            if (isLocalhostReference(host)) {
                log.warn("SSRF attempt detected: localhost reference in URL for key={}", key);
                throw new InvalidSettingsException(key, "URLs pointing to localhost are not allowed");
            }

            // Resolve hostname and check if it's a private IP
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) {
                if (isPrivateOrReservedAddress(address)) {
                    log.warn("SSRF attempt detected: private/reserved IP in URL for key={}, host={}, resolved={}",
                            key, host, address.getHostAddress());
                    throw new InvalidSettingsException(key, "URLs pointing to private or reserved IP addresses are not allowed");
                }
            }

        } catch (URISyntaxException e) {
            throw new InvalidSettingsException(key, "Invalid URL format");
        } catch (UnknownHostException e) {
            // Don't block unknown hosts - might be valid external domains not resolvable from this server
            log.debug("Could not resolve hostname for URL validation: {}", strValue);
        }
    }

    /**
     * Check if hostname is a localhost reference.
     */
    private boolean isLocalhostReference(String host) {
        String lowerHost = host.toLowerCase();
        return lowerHost.equals("localhost") ||
               lowerHost.equals("127.0.0.1") ||
               lowerHost.equals("::1") ||
               lowerHost.equals("[::1]") ||
               lowerHost.endsWith(".localhost") ||
               lowerHost.equals("0.0.0.0");
    }

    /**
     * SECURITY: Check if IP address is private, reserved, or a cloud metadata endpoint.
     * Blocks:
     * - 127.0.0.0/8 (loopback)
     * - 10.0.0.0/8 (private class A)
     * - 172.16.0.0/12 (private class B)
     * - 192.168.0.0/16 (private class C)
     * - 169.254.0.0/16 (link-local, includes AWS/GCP/Azure metadata at 169.254.169.254)
     * - 0.0.0.0/8 (current network)
     * - IPv6 equivalents
     */
    private boolean isPrivateOrReservedAddress(InetAddress address) {
        // Use Java's built-in checks for common cases
        if (address.isLoopbackAddress() ||
            address.isLinkLocalAddress() ||
            address.isSiteLocalAddress() ||
            address.isAnyLocalAddress()) {
            return true;
        }

        byte[] bytes = address.getAddress();

        // IPv4 specific checks
        if (bytes.length == 4) {
            int firstOctet = bytes[0] & 0xFF;
            int secondOctet = bytes[1] & 0xFF;

            // 169.254.0.0/16 - Link local (cloud metadata endpoints)
            if (firstOctet == 169 && secondOctet == 254) {
                return true;
            }

            // 0.0.0.0/8 - Current network
            if (firstOctet == 0) {
                return true;
            }
        }

        return false;
    }

    private void validateEmail(String key, Object value) {
        String strValue = toString(value);
        if (!EMAIL_PATTERN.matcher(strValue).matches()) {
            throw new InvalidSettingsException(key, "Must be a valid email address");
        }
    }

    private void validateLanguage(String key, Object value) {
        String strValue = toString(value);
        try {
            Locale locale = Locale.forLanguageTag(strValue);
            if (locale.getLanguage().isEmpty()) {
                throw new InvalidSettingsException(key, "Must be a valid language code (e.g., en, en-US, fr)");
            }
        } catch (Exception e) {
            throw new InvalidSettingsException(key, "Must be a valid language code");
        }
    }

    private void validateTimezone(String key, Object value) {
        String strValue = toString(value);
        try {
            ZoneId.of(strValue);
        } catch (Exception e) {
            throw new InvalidSettingsException(key,
                    "Must be a valid timezone (e.g., America/New_York, UTC, Europe/London)");
        }
    }

    private void validateTime(String key, Object value) {
        String strValue = toString(value);
        if (!TIME_PATTERN.matcher(strValue).matches()) {
            throw new InvalidSettingsException(key, "Must be a valid time in HH:mm format (e.g., 09:00)");
        }
    }

    @SuppressWarnings("unchecked")
    private void validateStringList(String key, Object value) {
        if (!(value instanceof List<?>)) {
            throw new InvalidSettingsException(key, "Must be a list");
        }
        List<?> list = (List<?>) value;
        if (list.size() > MAX_LIST_SIZE) {
            throw new InvalidSettingsException(key,
                    "List must not exceed " + MAX_LIST_SIZE + " items (got " + list.size() + ")");
        }
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (item == null) {
                throw new InvalidSettingsException(key, "List item at index " + i + " cannot be null");
            }
            String strItem = item.toString();
            if (strItem.length() > MAX_STRING_LENGTH) {
                throw new InvalidSettingsException(key,
                        "List item at index " + i + " exceeds maximum length of " + MAX_STRING_LENGTH);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void validateFileTypeList(String key, Object value) {
        if (!(value instanceof List<?>)) {
            throw new InvalidSettingsException(key, "Must be a list of file extensions");
        }
        List<?> list = (List<?>) value;
        if (list.size() > MAX_LIST_SIZE) {
            throw new InvalidSettingsException(key,
                    "List must not exceed " + MAX_LIST_SIZE + " items");
        }
        for (Object item : list) {
            if (item == null) {
                throw new InvalidSettingsException(key, "File type cannot be null");
            }
            String ext = item.toString();
            if (ext.length() > MAX_FILE_TYPE_LENGTH) {
                throw new InvalidSettingsException(key,
                        "File extension '" + ext + "' exceeds maximum length");
            }
            // Ensure it looks like a file extension (starts with . or is a MIME type)
            if (!ext.matches("^\\.[a-zA-Z0-9]+$") && !ext.matches("^[a-zA-Z]+/[a-zA-Z0-9.+-]+$")) {
                throw new InvalidSettingsException(key,
                        "Invalid file type format: '" + ext + "'. Use .ext or MIME type format");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void validateIpRangeList(String key, Object value) {
        if (!(value instanceof List<?>)) {
            throw new InvalidSettingsException(key, "Must be a list of IP addresses or CIDR ranges");
        }
        List<?> list = (List<?>) value;
        if (list.size() > MAX_LIST_SIZE) {
            throw new InvalidSettingsException(key,
                    "List must not exceed " + MAX_LIST_SIZE + " items");
        }
        for (Object item : list) {
            if (item == null) {
                throw new InvalidSettingsException(key, "IP range cannot be null");
            }
            String ip = item.toString();
            if (!IP_CIDR_PATTERN.matcher(ip).matches()) {
                throw new InvalidSettingsException(key,
                        "Invalid IP/CIDR format: '" + ip + "'. Use format like 192.168.1.0/24");
            }
        }
    }

    private String toString(Object value) {
        if (value == null) {
            return "";
        }
        return value.toString().trim();
    }
}
