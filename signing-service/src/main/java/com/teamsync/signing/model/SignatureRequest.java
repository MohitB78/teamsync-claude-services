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
 * SignatureRequest tracks a specific signing workflow instance.
 *
 * A request is created from a SigningTemplate and sent to one or more signers.
 * Each signer receives a unique token to access and sign the document.
 */
@Document(collection = "signature_requests")
@CompoundIndexes({
        @CompoundIndex(name = "tenant_idx", def = "{'tenantId': 1}"),
        @CompoundIndex(name = "tenant_status_idx", def = "{'tenantId': 1, 'status': 1}"),
        @CompoundIndex(name = "tenant_sender_idx", def = "{'tenantId': 1, 'senderId': 1}"),
        @CompoundIndex(name = "tenant_doc_idx", def = "{'tenantId': 1, 'documentId': 1}"),
        @CompoundIndex(name = "signer_token_idx", def = "{'signers.accessTokenHash': 1}"),
        @CompoundIndex(name = "signer_email_idx", def = "{'signers.email': 1}")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignatureRequest {

    @Id
    private String id;

    @Version
    private Long entityVersion;

    @Field("tenantId")
    private String tenantId;

    @Field("driveId")
    private String driveId;

    // Template reference
    @Field("templateId")
    private String templateId;

    @Field("templateName")
    private String templateName;

    // Document being signed (copy created from template)
    @Field("documentId")
    private String documentId;

    @Field("documentName")
    private String documentName;

    @Field("documentStorageKey")
    private String documentStorageKey;

    @Field("documentBucket")
    private String documentBucket;

    @Field("documentSize")
    private Long documentSize;

    @Field("pageCount")
    private Integer pageCount;

    // Signed document output (after all signatures)
    @Field("signedDocumentStorageKey")
    private String signedDocumentStorageKey;

    @Field("signedDocumentBucket")
    private String signedDocumentBucket;

    // Download token for completed document (hashed)
    @Field("downloadTokenHash")
    private String downloadTokenHash;

    @Field("downloadTokenExpiresAt")
    private Instant downloadTokenExpiresAt;

    // Sender (internal user)
    @Field("senderId")
    private String senderId;

    @Field("senderName")
    private String senderName;

    @Field("senderEmail")
    private String senderEmail;

    // Signers (external users)
    @Field("signers")
    private List<Signer> signers;

    // Status tracking
    @Field("status")
    @Builder.Default
    private SignatureRequestStatus status = SignatureRequestStatus.DRAFT;

    @Field("currentSignerIndex")
    @Builder.Default
    private Integer currentSignerIndex = 0;  // For sequential signing

    // Signature fields with values (copied from template, filled during signing)
    @Field("fieldValues")
    private List<SignatureFieldValue> fieldValues;

    // Configuration (copied from template, can be overridden)
    @Field("signingOrder")
    @Builder.Default
    private SigningTemplate.SigningOrder signingOrder = SigningTemplate.SigningOrder.SEQUENTIAL;

    @Field("requireAllSignatures")
    @Builder.Default
    private Boolean requireAllSignatures = true;

    @Field("subject")
    private String subject;  // Email subject

    @Field("message")
    private String message;  // Custom message to signers

    // Field definitions copied from template
    @Field("fieldDefinitions")
    private List<SigningTemplate.SignatureFieldDefinition> fieldDefinitions;

    // Timing
    @Field("expiresAt")
    private Instant expiresAt;

    @Field("sentAt")
    private Instant sentAt;

    @Field("completedAt")
    private Instant completedAt;

    @Field("voidedAt")
    private Instant voidedAt;

    @Field("voidedBy")
    private String voidedBy;

    @Field("voidReason")
    private String voidReason;

    // Audit
    @CreatedDate
    @Field("createdAt")
    private Instant createdAt;

    @LastModifiedDate
    @Field("updatedAt")
    private Instant updatedAt;

    /**
     * Signer represents an external user who needs to sign the document.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Signer {
        private String id;  // UUID
        private Integer order;  // Order for sequential signing (1-based)
        private String email;
        private String name;

        // Authentication - token stored as hash
        private String accessTokenHash;  // SHA-256 hash of the token
        private Instant accessTokenExpiresAt;

        // Status
        @Builder.Default
        private SignerStatus status = SignerStatus.PENDING;
        private Instant notifiedAt;
        private Instant viewedAt;
        private Instant signedAt;
        private Instant declinedAt;
        private String declineReason;

        // Security info for audit
        private String signedFromIp;
        private String signedFromUserAgent;
    }

    /**
     * Captured signature value for a field.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SignatureFieldValue {
        private String fieldId;  // Links to SignatureFieldDefinition.id
        private String signerId;  // Which signer filled this
        private String signerEmail;  // Signer's email for audit
        private SigningTemplate.FieldType type;
        private String value;  // Base64 image for signatures, text for others
        private String signatureMethod;  // DRAW, TYPE, or UPLOAD
        private Instant appliedAt;  // When this field was signed
    }

    /**
     * Status of a signature request.
     */
    public enum SignatureRequestStatus {
        DRAFT,        // Created but not sent
        PENDING,      // Sent, waiting for signatures
        IN_PROGRESS,  // At least one signature received
        COMPLETED,    // All required signatures received
        DECLINED,     // A signer declined
        VOIDED,       // Sender cancelled
        EXPIRED       // Past expiration date
    }

    /**
     * Status of an individual signer.
     */
    public enum SignerStatus {
        PENDING,    // Not yet notified
        NOTIFIED,   // Email sent
        VIEWED,     // Opened the document
        SIGNED,     // Signed successfully
        DECLINED    // Declined to sign
    }

    /**
     * Check if this request is still active (can be signed).
     */
    public boolean isActive() {
        return status == SignatureRequestStatus.PENDING
                || status == SignatureRequestStatus.IN_PROGRESS;
    }

    /**
     * Get the count of signers who have completed signing.
     */
    public long getSignedCount() {
        if (signers == null) return 0;
        return signers.stream()
                .filter(s -> s.getStatus() == SignerStatus.SIGNED)
                .count();
    }

    /**
     * Check if all signers have signed.
     */
    public boolean isFullySigned() {
        if (signers == null || signers.isEmpty()) return false;
        return signers.stream()
                .allMatch(s -> s.getStatus() == SignerStatus.SIGNED);
    }
}
