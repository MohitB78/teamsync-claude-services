package com.teamsync.settings.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Organization-wide settings for a tenant.
 * These settings apply to all users within the tenant.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "tenant_settings")
public class TenantSettings {

    @Id
    private String id;

    @Indexed(unique = true)
    private String tenantId;

    // Branding Settings
    @Builder.Default
    private BrandingSettings branding = new BrandingSettings();

    // Storage Settings
    @Builder.Default
    private StorageSettings storage = new StorageSettings();

    // Security Settings
    @Builder.Default
    private SecuritySettings security = new SecuritySettings();

    // Feature Flags
    @Builder.Default
    private FeatureSettings features = new FeatureSettings();

    // Email Settings
    @Builder.Default
    private EmailSettings email = new EmailSettings();

    // Integration Settings
    @Builder.Default
    private IntegrationSettings integrations = new IntegrationSettings();

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BrandingSettings {
        @Builder.Default
        private String logoUrl = "";

        @Builder.Default
        private String faviconUrl = "";

        @Builder.Default
        private String primaryColor = "#1976d2";

        @Builder.Default
        private String secondaryColor = "#dc004e";

        @Builder.Default
        private String companyName = "";

        @Builder.Default
        private String supportEmail = "";

        @Builder.Default
        private boolean customLoginPage = false;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StorageSettings {
        @Builder.Default
        private long defaultQuotaBytes = 10L * 1024 * 1024 * 1024; // 10GB

        @Builder.Default
        private long maxFileSizeBytes = 1L * 1024 * 1024 * 1024; // 1GB

        @Builder.Default
        private int trashRetentionDays = 30;

        @Builder.Default
        private int versionRetentionCount = 10;

        @Builder.Default
        private boolean autoVersioning = true;

        @Builder.Default
        private List<String> allowedFileTypes = new ArrayList<>();

        @Builder.Default
        private List<String> blockedFileTypes = new ArrayList<>(List.of(".exe", ".bat", ".cmd", ".scr"));
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SecuritySettings {
        @Builder.Default
        private boolean mfaRequired = false;

        @Builder.Default
        private boolean mfaEnforced = false;

        @Builder.Default
        private int sessionTimeoutMinutes = 480; // 8 hours

        @Builder.Default
        private int passwordExpiryDays = 90;

        @Builder.Default
        private int passwordMinLength = 8;

        @Builder.Default
        private boolean passwordRequireUppercase = true;

        @Builder.Default
        private boolean passwordRequireNumber = true;

        @Builder.Default
        private boolean passwordRequireSpecial = true;

        @Builder.Default
        private boolean allowPublicSharing = true;

        @Builder.Default
        private int publicLinkExpiryDays = 7;

        @Builder.Default
        private boolean watermarkEnabled = false;

        @Builder.Default
        private boolean downloadRestrictions = false;

        @Builder.Default
        private List<String> allowedIpRanges = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeatureSettings {
        @Builder.Default
        private boolean aiMetadataExtraction = true;

        @Builder.Default
        private boolean docuTalkChat = true;

        @Builder.Default
        private boolean officeEditing = true;

        @Builder.Default
        private boolean advancedSearch = true;

        @Builder.Default
        private boolean workflowAutomation = true;

        @Builder.Default
        private boolean auditLogging = true;

        @Builder.Default
        private boolean realTimeCollaboration = true;

        @Builder.Default
        private boolean customDocumentTypes = true;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmailSettings {
        @Builder.Default
        private boolean notificationsEnabled = true;

        @Builder.Default
        private String fromName = "TeamSync";

        @Builder.Default
        private String replyToEmail = "";

        @Builder.Default
        private boolean includeLogoInEmails = true;

        @Builder.Default
        private String emailFooter = "";
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IntegrationSettings {
        @Builder.Default
        private boolean ldapEnabled = false;

        @Builder.Default
        private boolean ssoEnabled = false;

        @Builder.Default
        private boolean webhooksEnabled = false;

        @Builder.Default
        private boolean apiAccessEnabled = true;

        @Builder.Default
        private int apiRateLimitPerMinute = 1000;
    }
}
