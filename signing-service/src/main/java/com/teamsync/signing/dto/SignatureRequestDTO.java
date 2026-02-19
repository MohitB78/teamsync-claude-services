package com.teamsync.signing.dto;

import com.teamsync.signing.model.SignatureRequest;
import com.teamsync.signing.model.SignatureRequest.SignatureRequestStatus;
import com.teamsync.signing.model.SignatureRequest.SignerStatus;
import com.teamsync.signing.model.SigningTemplate.SigningOrder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * DTO for SignatureRequest responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignatureRequestDTO {

    private String id;
    private String tenantId;
    private String driveId;
    private String templateId;
    private String templateName;

    // Document info
    private String documentId;
    private String documentName;
    private String documentStorageKey;
    private String documentBucket;
    private Long documentSize;
    private Integer pageCount;

    // Signed document (available after completion)
    private String signedDocumentStorageKey;

    // Sender info
    private String senderId;
    private String senderName;
    private String senderEmail;

    // Request configuration
    private String subject;
    private String message;
    private SigningOrder signingOrder;
    private Boolean requireAllSignatures;

    // Signers
    private List<SignerDTO> signers;
    private Integer totalSigners;
    private Integer completedSigners;

    // Status
    private SignatureRequestStatus status;
    private Instant expiresAt;
    private Instant sentAt;
    private Instant completedAt;
    private Instant voidedAt;
    private String voidReason;

    // Timestamps
    private Instant createdAt;
    private Instant updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SignerDTO {
        private String id;
        private String email;
        private String name;
        private Integer order;
        private SignerStatus status;
        private Instant viewedAt;
        private Instant signedAt;
        private Instant declinedAt;
        private String declineReason;
        // Note: accessToken is NOT included in DTO for security
    }

    /**
     * Map entity to DTO.
     */
    public static SignatureRequestDTO fromEntity(SignatureRequest entity) {
        return SignatureRequestDTO.builder()
                .id(entity.getId())
                .tenantId(entity.getTenantId())
                .driveId(entity.getDriveId())
                .templateId(entity.getTemplateId())
                .templateName(entity.getTemplateName())
                .documentId(entity.getDocumentId())
                .documentName(entity.getDocumentName())
                .documentStorageKey(entity.getDocumentStorageKey())
                .documentBucket(entity.getDocumentBucket())
                .documentSize(entity.getDocumentSize())
                .pageCount(entity.getPageCount())
                .signedDocumentStorageKey(entity.getSignedDocumentStorageKey())
                .senderId(entity.getSenderId())
                .senderName(entity.getSenderName())
                .senderEmail(entity.getSenderEmail())
                .subject(entity.getSubject())
                .message(entity.getMessage())
                .signingOrder(entity.getSigningOrder())
                .requireAllSignatures(entity.getRequireAllSignatures())
                .signers(mapSigners(entity.getSigners()))
                .totalSigners(entity.getSigners() != null ? entity.getSigners().size() : 0)
                .completedSigners(countCompletedSigners(entity.getSigners()))
                .status(entity.getStatus())
                .expiresAt(entity.getExpiresAt())
                .sentAt(entity.getSentAt())
                .completedAt(entity.getCompletedAt())
                .voidedAt(entity.getVoidedAt())
                .voidReason(entity.getVoidReason())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private static List<SignerDTO> mapSigners(List<SignatureRequest.Signer> signers) {
        if (signers == null) return List.of();

        return signers.stream()
                .map(s -> SignerDTO.builder()
                        .id(s.getId())
                        .email(s.getEmail())
                        .name(s.getName())
                        .order(s.getOrder())
                        .status(s.getStatus())
                        .viewedAt(s.getViewedAt())
                        .signedAt(s.getSignedAt())
                        .declinedAt(s.getDeclinedAt())
                        .declineReason(s.getDeclineReason())
                        .build())
                .toList();
    }

    private static int countCompletedSigners(List<SignatureRequest.Signer> signers) {
        if (signers == null) return 0;
        return (int) signers.stream()
                .filter(s -> s.getStatus() == SignerStatus.SIGNED)
                .count();
    }
}
