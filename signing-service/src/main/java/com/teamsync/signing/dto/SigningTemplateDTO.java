package com.teamsync.signing.dto;

import com.teamsync.signing.model.SigningTemplate;
import com.teamsync.signing.model.SigningTemplate.FieldType;
import com.teamsync.signing.model.SigningTemplate.SigningOrder;
import com.teamsync.signing.model.SigningTemplate.TemplateStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * DTO for SigningTemplate responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SigningTemplateDTO {

    private String id;
    private String name;
    private String description;
    private String baseDocumentName;
    private String baseDocumentContentType;
    private Long baseDocumentSize;
    private Integer baseDocumentPageCount;
    private List<FieldDefinitionDTO> fieldDefinitions;
    private WorkflowConfigDTO workflowConfig;
    private TemplateStatus status;
    private Integer expirationDays;
    private Boolean requireAllSignatures;
    private Boolean sendCompletionNotification;
    private Boolean allowSignerReordering;
    private String createdBy;
    private String createdByName;
    private Instant createdAt;
    private Instant updatedAt;
    private int fieldCount;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldDefinitionDTO {
        private String id;
        private String name;
        private FieldType type;
        private Integer pageNumber;
        private Double xPosition;
        private Double yPosition;
        private Double width;
        private Double height;
        private Boolean required;
        private Integer signerIndex;
        private String placeholder;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowConfigDTO {
        private SigningOrder signingOrder;
        private Boolean allowDelegation;
        private Boolean sendReminderEmails;
        private List<Integer> reminderDaysBeforeExpiry;
    }

    /**
     * Map entity to DTO.
     */
    public static SigningTemplateDTO fromEntity(SigningTemplate entity) {
        if (entity == null) return null;

        return SigningTemplateDTO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .baseDocumentName(entity.getBaseDocumentName())
                .baseDocumentContentType(entity.getBaseDocumentContentType())
                .baseDocumentSize(entity.getBaseDocumentSize())
                .baseDocumentPageCount(entity.getBaseDocumentPageCount())
                .fieldDefinitions(mapFieldDefinitions(entity.getFieldDefinitions()))
                .workflowConfig(mapWorkflowConfig(entity.getWorkflowConfig()))
                .status(entity.getStatus())
                .expirationDays(entity.getExpirationDays())
                .requireAllSignatures(entity.getRequireAllSignatures())
                .sendCompletionNotification(entity.getSendCompletionNotification())
                .allowSignerReordering(entity.getAllowSignerReordering())
                .createdBy(entity.getCreatedBy())
                .createdByName(entity.getCreatedByName())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .fieldCount(entity.getFieldDefinitions() != null ? entity.getFieldDefinitions().size() : 0)
                .build();
    }

    private static List<FieldDefinitionDTO> mapFieldDefinitions(
            List<SigningTemplate.SignatureFieldDefinition> fields) {
        if (fields == null) return null;
        return fields.stream()
                .map(f -> FieldDefinitionDTO.builder()
                        .id(f.getId())
                        .name(f.getName())
                        .type(f.getType())
                        .pageNumber(f.getPageNumber())
                        .xPosition(f.getXPosition())
                        .yPosition(f.getYPosition())
                        .width(f.getWidth())
                        .height(f.getHeight())
                        .required(f.getRequired())
                        .signerIndex(f.getSignerIndex())
                        .placeholder(f.getPlaceholder())
                        .build())
                .toList();
    }

    private static WorkflowConfigDTO mapWorkflowConfig(SigningTemplate.SigningWorkflowConfig config) {
        if (config == null) return null;
        return WorkflowConfigDTO.builder()
                .signingOrder(config.getSigningOrder())
                .allowDelegation(config.getAllowDelegation())
                .sendReminderEmails(config.getSendReminderEmails())
                .reminderDaysBeforeExpiry(config.getReminderDaysBeforeExpiry())
                .build();
    }
}
