package com.teamsync.signing.dto;

import com.teamsync.signing.model.SigningTemplate.FieldType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * DTO for portal signing session.
 * Contains all information needed for external user to sign the document.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortalSigningSessionDTO {

    private String requestId;
    private String signerId;

    // Document info
    private String documentName;
    private Integer pageCount;
    private Long documentSize;

    // Sender info
    private String senderName;
    private String senderEmail;

    // Request info
    private String subject;
    private String message;
    private Instant expiresAt;

    // Signer info
    private String signerEmail;
    private String signerName;
    private Boolean alreadySigned;

    // Fields this signer needs to complete
    private List<SignatureFieldDTO> fields;
    private Integer totalFields;
    private Integer completedFields;

    // Download info (only available after completion)
    private Boolean canDownload;
    private String downloadToken;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SignatureFieldDTO {
        private String id;
        private String name;
        private FieldType type;
        private Integer pageNumber;
        private Double xPosition;
        private Double yPosition;
        private Double width;
        private Double height;
        private Boolean required;
        private String placeholder;
        private Boolean completed;
        private String value; // Only for non-signature fields
    }
}
