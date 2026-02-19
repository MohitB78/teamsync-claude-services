package com.teamsync.signing.dto;

import com.teamsync.signing.model.SigningTemplate.SigningOrder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for creating a signature request.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSignatureRequestRequest {

    @NotBlank(message = "Template ID is required")
    private String templateId;

    @NotBlank(message = "Drive ID is required")
    private String driveId;

    /**
     * Optional document ID if using existing document.
     * If null, will create a copy from template's base document.
     */
    private String documentId;

    @NotBlank(message = "Subject is required")
    @Size(max = 200, message = "Subject cannot exceed 200 characters")
    private String subject;

    @Size(max = 2000, message = "Message cannot exceed 2000 characters")
    private String message;

    @NotEmpty(message = "At least one signer is required")
    @Valid
    private List<SignerRequest> signers;

    /**
     * Override template's signing order.
     */
    private SigningOrder signingOrder;

    /**
     * Override template's expiration days.
     */
    private Integer expirationDays;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SignerRequest {

        @NotBlank(message = "Signer email is required")
        @Email(message = "Invalid email format")
        private String email;

        @Size(max = 100, message = "Name cannot exceed 100 characters")
        private String name;

        /**
         * Signing order for sequential signing (1-based).
         * If not specified, will be assigned based on list order.
         */
        private Integer order;
    }
}
