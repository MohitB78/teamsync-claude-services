package com.teamsync.signing.dto;

import com.teamsync.signing.model.SigningTemplate.FieldType;
import com.teamsync.signing.model.SigningTemplate.SigningOrder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request to create a new signing template.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTemplateRequest {

    @NotBlank(message = "Template name is required")
    @Size(max = 200, message = "Template name must be less than 200 characters")
    private String name;

    @Size(max = 1000, message = "Description must be less than 1000 characters")
    private String description;

    // Base document - can be uploaded separately or referenced
    private String baseDocumentStorageKey;
    private String baseDocumentBucket;
    private String baseDocumentName;
    private String baseDocumentContentType;
    private Long baseDocumentSize;
    private Integer baseDocumentPageCount;

    @Valid
    private List<FieldDefinitionRequest> fieldDefinitions;

    @Valid
    private WorkflowConfigRequest workflowConfig;

    @Positive(message = "Expiration days must be positive")
    @Builder.Default
    private Integer expirationDays = 7;

    @Builder.Default
    private Boolean requireAllSignatures = true;

    @Builder.Default
    private Boolean sendCompletionNotification = true;

    @Builder.Default
    private Boolean allowSignerReordering = false;

    /**
     * Field definition in the template.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldDefinitionRequest {

        @NotBlank(message = "Field name is required")
        private String name;

        @NotNull(message = "Field type is required")
        private FieldType type;

        @NotNull(message = "Page number is required")
        @Positive(message = "Page number must be positive")
        private Integer pageNumber;

        @NotNull(message = "X position is required")
        private Double xPosition;

        @NotNull(message = "Y position is required")
        private Double yPosition;

        @NotNull(message = "Width is required")
        private Double width;

        @NotNull(message = "Height is required")
        private Double height;

        @Builder.Default
        private Boolean required = true;

        @NotNull(message = "Signer index is required")
        private Integer signerIndex;

        private String placeholder;
    }

    /**
     * Workflow configuration.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowConfigRequest {
        @Builder.Default
        private SigningOrder signingOrder = SigningOrder.SEQUENTIAL;
        @Builder.Default
        private Boolean allowDelegation = false;
        @Builder.Default
        private Boolean sendReminderEmails = true;
        private List<Integer> reminderDaysBeforeExpiry;
    }
}
