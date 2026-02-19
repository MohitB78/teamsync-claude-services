package com.teamsync.signing.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;

/**
 * SigningTemplate defines a reusable signing configuration with field positions.
 *
 * Templates specify:
 * - A base PDF document that signers will receive
 * - Signature field definitions (positions, types, assigned signers)
 * - Workflow configuration (sequential/parallel, expiration)
 *
 * Only documents created from signing templates can be sent for signing.
 */
@Document(collection = "signing_templates")
@CompoundIndexes({
        @CompoundIndex(name = "tenant_idx", def = "{'tenantId': 1}"),
        @CompoundIndex(name = "tenant_name_idx", def = "{'tenantId': 1, 'name': 1}", unique = true),
        @CompoundIndex(name = "tenant_status_idx", def = "{'tenantId': 1, 'status': 1}")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SigningTemplate {

    @Id
    private String id;

    @Version
    private Long entityVersion;

    @Field("tenantId")
    private String tenantId;

    @Field("name")
    private String name;

    @Field("description")
    private String description;

    // Base document that signers will receive (stored in storage service)
    @Field("baseDocumentStorageKey")
    private String baseDocumentStorageKey;

    @Field("baseDocumentBucket")
    private String baseDocumentBucket;

    @Field("baseDocumentName")
    private String baseDocumentName;

    @Field("baseDocumentContentType")
    private String baseDocumentContentType;

    @Field("baseDocumentSize")
    private Long baseDocumentSize;

    @Field("baseDocumentPageCount")
    private Integer baseDocumentPageCount;

    // Signature field definitions
    @Field("fieldDefinitions")
    private List<SignatureFieldDefinition> fieldDefinitions;

    // Workflow configuration
    @Field("workflowConfig")
    private SigningWorkflowConfig workflowConfig;

    // Template status
    @Field("status")
    @Builder.Default
    private TemplateStatus status = TemplateStatus.DRAFT;

    // Default expiration for signing requests (in days)
    @Field("expirationDays")
    @Builder.Default
    private Integer expirationDays = 7;

    // Settings
    @Field("requireAllSignatures")
    @Builder.Default
    private Boolean requireAllSignatures = true;

    @Field("sendCompletionNotification")
    @Builder.Default
    private Boolean sendCompletionNotification = true;

    @Field("allowSignerReordering")
    @Builder.Default
    private Boolean allowSignerReordering = false;

    // Audit fields
    @Field("createdBy")
    private String createdBy;

    @Field("createdByName")
    private String createdByName;

    @Field("lastModifiedBy")
    private String lastModifiedBy;

    @CreatedDate
    @Field("createdAt")
    private Instant createdAt;

    @LastModifiedDate
    @Field("updatedAt")
    private Instant updatedAt;

    /**
     * Signature field definition - position and type of a signature field.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SignatureFieldDefinition {
        private String id;  // UUID for field identification
        private String name;  // Display name: "Contractor Signature", "Date"
        private FieldType type;
        private Integer pageNumber;  // 1-based page number
        private Double xPosition;  // Percentage 0-100 from left
        private Double yPosition;  // Percentage 0-100 from top
        private Double width;  // Percentage of page width
        private Double height;  // Percentage of page height
        private Boolean required;
        private Integer signerIndex;  // Which signer fills this (0-based)
        private String placeholder;  // Default text for TEXT fields
    }

    /**
     * Workflow configuration for signing process.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SigningWorkflowConfig {
        @Builder.Default
        private SigningOrder signingOrder = SigningOrder.SEQUENTIAL;
        @Builder.Default
        private Boolean allowDelegation = false;
        @Builder.Default
        private Boolean sendReminderEmails = true;
        private List<Integer> reminderDaysBeforeExpiry;  // e.g., [2, 1] = remind 2 days and 1 day before
    }

    /**
     * Field types for signature fields.
     */
    public enum FieldType {
        SIGNATURE,   // Full signature (draw/type/upload)
        INITIALS,    // Initials only
        DATE,        // Auto-filled date
        TEXT,        // Free-form text input
        CHECKBOX     // Checkbox (for agreements)
    }

    /**
     * Signing order options.
     */
    public enum SigningOrder {
        SEQUENTIAL,  // One signer after another
        PARALLEL     // All signers can sign simultaneously
    }

    /**
     * Template status.
     */
    public enum TemplateStatus {
        DRAFT,     // Template is being edited
        ACTIVE,    // Template is ready for use
        ARCHIVED   // Template is no longer in use
    }
}
