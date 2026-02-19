package com.teamsync.settings.dto;

import com.teamsync.settings.model.TenantSettings;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * DTO for tenant settings response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantSettingsDTO {

    private String id;
    private String tenantId;
    private BrandingSettingsDTO branding;
    private StorageSettingsDTO storage;
    private SecuritySettingsDTO security;
    private FeatureSettingsDTO features;
    private EmailSettingsDTO email;
    private IntegrationSettingsDTO integrations;
    private Instant updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BrandingSettingsDTO {
        private String logoUrl;
        private String faviconUrl;
        private String primaryColor;
        private String secondaryColor;
        private String companyName;
        private String supportEmail;
        private boolean customLoginPage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StorageSettingsDTO {
        private long defaultQuotaBytes;
        private long maxFileSizeBytes;
        private int trashRetentionDays;
        private int versionRetentionCount;
        private boolean autoVersioning;
        private List<String> allowedFileTypes;
        private List<String> blockedFileTypes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SecuritySettingsDTO {
        private boolean mfaRequired;
        private boolean mfaEnforced;
        private int sessionTimeoutMinutes;
        private int passwordExpiryDays;
        private int passwordMinLength;
        private boolean passwordRequireUppercase;
        private boolean passwordRequireNumber;
        private boolean passwordRequireSpecial;
        private boolean allowPublicSharing;
        private int publicLinkExpiryDays;
        private boolean watermarkEnabled;
        private boolean downloadRestrictions;
        private List<String> allowedIpRanges;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeatureSettingsDTO {
        private boolean aiMetadataExtraction;
        private boolean docuTalkChat;
        private boolean officeEditing;
        private boolean advancedSearch;
        private boolean workflowAutomation;
        private boolean auditLogging;
        private boolean realTimeCollaboration;
        private boolean customDocumentTypes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmailSettingsDTO {
        private boolean notificationsEnabled;
        private String fromName;
        private String replyToEmail;
        private boolean includeLogoInEmails;
        private String emailFooter;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IntegrationSettingsDTO {
        private boolean ldapEnabled;
        private boolean ssoEnabled;
        private boolean webhooksEnabled;
        private boolean apiAccessEnabled;
        private int apiRateLimitPerMinute;
    }

    public static TenantSettingsDTO fromEntity(TenantSettings entity) {
        if (entity == null) return null;

        BrandingSettingsDTO branding = null;
        if (entity.getBranding() != null) {
            var b = entity.getBranding();
            branding = BrandingSettingsDTO.builder()
                    .logoUrl(b.getLogoUrl())
                    .faviconUrl(b.getFaviconUrl())
                    .primaryColor(b.getPrimaryColor())
                    .secondaryColor(b.getSecondaryColor())
                    .companyName(b.getCompanyName())
                    .supportEmail(b.getSupportEmail())
                    .customLoginPage(b.isCustomLoginPage())
                    .build();
        }

        StorageSettingsDTO storage = null;
        if (entity.getStorage() != null) {
            var s = entity.getStorage();
            storage = StorageSettingsDTO.builder()
                    .defaultQuotaBytes(s.getDefaultQuotaBytes())
                    .maxFileSizeBytes(s.getMaxFileSizeBytes())
                    .trashRetentionDays(s.getTrashRetentionDays())
                    .versionRetentionCount(s.getVersionRetentionCount())
                    .autoVersioning(s.isAutoVersioning())
                    .allowedFileTypes(s.getAllowedFileTypes())
                    .blockedFileTypes(s.getBlockedFileTypes())
                    .build();
        }

        SecuritySettingsDTO security = null;
        if (entity.getSecurity() != null) {
            var sec = entity.getSecurity();
            security = SecuritySettingsDTO.builder()
                    .mfaRequired(sec.isMfaRequired())
                    .mfaEnforced(sec.isMfaEnforced())
                    .sessionTimeoutMinutes(sec.getSessionTimeoutMinutes())
                    .passwordExpiryDays(sec.getPasswordExpiryDays())
                    .passwordMinLength(sec.getPasswordMinLength())
                    .passwordRequireUppercase(sec.isPasswordRequireUppercase())
                    .passwordRequireNumber(sec.isPasswordRequireNumber())
                    .passwordRequireSpecial(sec.isPasswordRequireSpecial())
                    .allowPublicSharing(sec.isAllowPublicSharing())
                    .publicLinkExpiryDays(sec.getPublicLinkExpiryDays())
                    .watermarkEnabled(sec.isWatermarkEnabled())
                    .downloadRestrictions(sec.isDownloadRestrictions())
                    .allowedIpRanges(sec.getAllowedIpRanges())
                    .build();
        }

        FeatureSettingsDTO features = null;
        if (entity.getFeatures() != null) {
            var f = entity.getFeatures();
            features = FeatureSettingsDTO.builder()
                    .aiMetadataExtraction(f.isAiMetadataExtraction())
                    .docuTalkChat(f.isDocuTalkChat())
                    .officeEditing(f.isOfficeEditing())
                    .advancedSearch(f.isAdvancedSearch())
                    .workflowAutomation(f.isWorkflowAutomation())
                    .auditLogging(f.isAuditLogging())
                    .realTimeCollaboration(f.isRealTimeCollaboration())
                    .customDocumentTypes(f.isCustomDocumentTypes())
                    .build();
        }

        EmailSettingsDTO email = null;
        if (entity.getEmail() != null) {
            var e = entity.getEmail();
            email = EmailSettingsDTO.builder()
                    .notificationsEnabled(e.isNotificationsEnabled())
                    .fromName(e.getFromName())
                    .replyToEmail(e.getReplyToEmail())
                    .includeLogoInEmails(e.isIncludeLogoInEmails())
                    .emailFooter(e.getEmailFooter())
                    .build();
        }

        IntegrationSettingsDTO integrations = null;
        if (entity.getIntegrations() != null) {
            var i = entity.getIntegrations();
            integrations = IntegrationSettingsDTO.builder()
                    .ldapEnabled(i.isLdapEnabled())
                    .ssoEnabled(i.isSsoEnabled())
                    .webhooksEnabled(i.isWebhooksEnabled())
                    .apiAccessEnabled(i.isApiAccessEnabled())
                    .apiRateLimitPerMinute(i.getApiRateLimitPerMinute())
                    .build();
        }

        return TenantSettingsDTO.builder()
                .id(entity.getId())
                .tenantId(entity.getTenantId())
                .branding(branding)
                .storage(storage)
                .security(security)
                .features(features)
                .email(email)
                .integrations(integrations)
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
