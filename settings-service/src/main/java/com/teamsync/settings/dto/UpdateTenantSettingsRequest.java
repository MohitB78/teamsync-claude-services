package com.teamsync.settings.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * SECURITY: Typed request DTO for updating tenant settings.
 * Replaces unsafe Map<String, Object> to prevent NoSQL injection and
 * ensure all inputs are validated.
 *
 * Only admins can update tenant settings (enforced by @PreAuthorize in controller).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTenantSettingsRequest {

    @Valid
    private BrandingSettingsUpdate branding;

    @Valid
    private StorageSettingsUpdate storage;

    @Valid
    private SecuritySettingsUpdate security;

    @Valid
    private FeatureSettingsUpdate features;

    @Valid
    private EmailSettingsUpdate email;

    @Valid
    private IntegrationSettingsUpdate integrations;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BrandingSettingsUpdate {
        @Size(max = 500, message = "Logo URL must not exceed 500 characters")
        @Pattern(regexp = "^(https?://.*)?$", message = "Logo URL must be a valid HTTP/HTTPS URL")
        private String logoUrl;

        @Size(max = 500, message = "Favicon URL must not exceed 500 characters")
        @Pattern(regexp = "^(https?://.*)?$", message = "Favicon URL must be a valid HTTP/HTTPS URL")
        private String faviconUrl;

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Primary color must be a valid hex color (e.g., #ff0000)")
        private String primaryColor;

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Secondary color must be a valid hex color")
        private String secondaryColor;

        @Size(max = 100, message = "Company name must not exceed 100 characters")
        @Pattern(regexp = "^[\\w\\s\\-\\.]+$", message = "Company name contains invalid characters")
        private String companyName;

        @Email(message = "Support email must be a valid email address")
        private String supportEmail;

        private Boolean customLoginPage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StorageSettingsUpdate {
        @Min(value = 1073741824, message = "Default quota must be at least 1GB") // 1GB minimum
        @Max(value = 10995116277760L, message = "Default quota must not exceed 10TB") // 10TB max
        private Long defaultQuotaBytes;

        @Min(value = 1048576, message = "Max file size must be at least 1MB") // 1MB minimum
        @Max(value = 10737418240L, message = "Max file size must not exceed 10GB") // 10GB max
        private Long maxFileSizeBytes;

        @Min(value = 1, message = "Trash retention must be at least 1 day")
        @Max(value = 365, message = "Trash retention must not exceed 365 days")
        private Integer trashRetentionDays;

        @Min(value = 1, message = "Version retention must be at least 1")
        @Max(value = 100, message = "Version retention must not exceed 100")
        private Integer versionRetentionCount;

        private Boolean autoVersioning;

        @Size(max = 50, message = "Allowed file types list must not exceed 50 items")
        private List<@Pattern(regexp = "^\\.[a-zA-Z0-9]+$", message = "File type must start with a dot") String> allowedFileTypes;

        @Size(max = 50, message = "Blocked file types list must not exceed 50 items")
        private List<@Pattern(regexp = "^\\.[a-zA-Z0-9]+$", message = "File type must start with a dot") String> blockedFileTypes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SecuritySettingsUpdate {
        private Boolean mfaRequired;
        private Boolean mfaEnforced;

        @Min(value = 5, message = "Session timeout must be at least 5 minutes")
        @Max(value = 1440, message = "Session timeout must not exceed 1440 minutes (24 hours)")
        private Integer sessionTimeoutMinutes;

        @Min(value = 0, message = "Password expiry cannot be negative")
        @Max(value = 365, message = "Password expiry must not exceed 365 days")
        private Integer passwordExpiryDays;

        @Min(value = 8, message = "Password minimum length must be at least 8")
        @Max(value = 128, message = "Password minimum length must not exceed 128")
        private Integer passwordMinLength;

        private Boolean passwordRequireUppercase;
        private Boolean passwordRequireNumber;
        private Boolean passwordRequireSpecial;
        private Boolean allowPublicSharing;

        @Min(value = 1, message = "Public link expiry must be at least 1 day")
        @Max(value = 365, message = "Public link expiry must not exceed 365 days")
        private Integer publicLinkExpiryDays;

        private Boolean watermarkEnabled;
        private Boolean downloadRestrictions;

        @Size(max = 20, message = "Allowed IP ranges list must not exceed 20 items")
        private List<@Pattern(regexp = "^([0-9]{1,3}\\.){3}[0-9]{1,3}(/[0-9]{1,2})?$",
                              message = "IP range must be in CIDR notation") String> allowedIpRanges;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeatureSettingsUpdate {
        private Boolean aiMetadataExtraction;
        private Boolean docuTalkChat;
        private Boolean officeEditing;
        private Boolean advancedSearch;
        private Boolean workflowAutomation;
        private Boolean auditLogging;
        private Boolean realTimeCollaboration;
        private Boolean customDocumentTypes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmailSettingsUpdate {
        private Boolean notificationsEnabled;

        @Size(max = 100, message = "From name must not exceed 100 characters")
        @Pattern(regexp = "^[\\w\\s\\-\\.]+$", message = "From name contains invalid characters")
        private String fromName;

        @Email(message = "Reply-to email must be a valid email address")
        private String replyToEmail;

        private Boolean includeLogoInEmails;

        /**
         * SECURITY: Email footer text - no HTML/script tags allowed to prevent XSS
         * when rendered in emails or UI. Only allows plain text with basic punctuation.
         */
        @Size(max = 500, message = "Email footer must not exceed 500 characters")
        @Pattern(regexp = "^[^<>]*$", message = "Email footer cannot contain HTML tags")
        private String emailFooter;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IntegrationSettingsUpdate {
        private Boolean ldapEnabled;
        private Boolean ssoEnabled;
        private Boolean webhooksEnabled;
        private Boolean apiAccessEnabled;

        @Min(value = 10, message = "API rate limit must be at least 10 per minute")
        @Max(value = 10000, message = "API rate limit must not exceed 10000 per minute")
        private Integer apiRateLimitPerMinute;
    }
}
