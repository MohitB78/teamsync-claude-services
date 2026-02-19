package com.teamsync.signing.service;

import com.teamsync.common.exception.BadRequestException;
import com.teamsync.common.exception.ResourceNotFoundException;
import com.teamsync.signing.dto.CreateTemplateRequest;
import com.teamsync.signing.dto.SigningTemplateDTO;
import com.teamsync.signing.model.SigningTemplate;
import com.teamsync.signing.model.SigningTemplate.SignatureFieldDefinition;
import com.teamsync.signing.model.SigningTemplate.SigningWorkflowConfig;
import com.teamsync.signing.model.SigningTemplate.TemplateStatus;
import com.teamsync.signing.repository.SigningTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing signing templates.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SigningTemplateService {

    private final SigningTemplateRepository templateRepository;

    /**
     * Create a new signing template.
     */
    @Transactional
    public SigningTemplateDTO createTemplate(String tenantId, String userId, String userName,
                                             CreateTemplateRequest request) {
        // Validate unique name
        if (templateRepository.existsByTenantIdAndName(tenantId, request.getName())) {
            throw new BadRequestException("A template with this name already exists");
        }

        SigningTemplate template = SigningTemplate.builder()
                .tenantId(tenantId)
                .name(request.getName())
                .description(request.getDescription())
                .baseDocumentStorageKey(request.getBaseDocumentStorageKey())
                .baseDocumentBucket(request.getBaseDocumentBucket())
                .baseDocumentName(request.getBaseDocumentName())
                .baseDocumentContentType(request.getBaseDocumentContentType())
                .baseDocumentSize(request.getBaseDocumentSize())
                .baseDocumentPageCount(request.getBaseDocumentPageCount())
                .fieldDefinitions(mapFieldDefinitions(request.getFieldDefinitions()))
                .workflowConfig(mapWorkflowConfig(request.getWorkflowConfig()))
                .status(TemplateStatus.DRAFT)
                .expirationDays(request.getExpirationDays())
                .requireAllSignatures(request.getRequireAllSignatures())
                .sendCompletionNotification(request.getSendCompletionNotification())
                .allowSignerReordering(request.getAllowSignerReordering())
                .createdBy(userId)
                .createdByName(userName)
                .createdAt(Instant.now())
                .build();

        SigningTemplate saved = templateRepository.save(template);
        log.info("Created signing template: {} for tenant: {}", saved.getId(), tenantId);

        return SigningTemplateDTO.fromEntity(saved);
    }

    /**
     * Get a template by ID.
     */
    public SigningTemplateDTO getTemplate(String tenantId, String templateId) {
        SigningTemplate template = templateRepository.findByIdAndTenantId(templateId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Template not found"));
        return SigningTemplateDTO.fromEntity(template);
    }

    /**
     * Get template entity (for internal use).
     */
    public SigningTemplate getTemplateEntity(String tenantId, String templateId) {
        return templateRepository.findByIdAndTenantId(templateId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Template not found"));
    }

    /**
     * List templates for a tenant.
     */
    public Page<SigningTemplateDTO> listTemplates(String tenantId, Pageable pageable) {
        return templateRepository.findByTenantId(tenantId, pageable)
                .map(SigningTemplateDTO::fromEntity);
    }

    /**
     * List templates by status.
     */
    public Page<SigningTemplateDTO> listTemplatesByStatus(String tenantId, TemplateStatus status,
                                                          Pageable pageable) {
        return templateRepository.findByTenantIdAndStatus(tenantId, status, pageable)
                .map(SigningTemplateDTO::fromEntity);
    }

    /**
     * Get active templates (for template selection dropdowns).
     */
    public List<SigningTemplateDTO> getActiveTemplates(String tenantId) {
        return templateRepository.findActiveByTenantId(tenantId).stream()
                .map(SigningTemplateDTO::fromEntity)
                .toList();
    }

    /**
     * Update a template.
     */
    @Transactional
    public SigningTemplateDTO updateTemplate(String tenantId, String templateId, String userId,
                                             CreateTemplateRequest request) {
        SigningTemplate template = templateRepository.findByIdAndTenantId(templateId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Template not found"));

        // Check for name conflicts
        if (!template.getName().equals(request.getName()) &&
                templateRepository.existsByTenantIdAndName(tenantId, request.getName())) {
            throw new BadRequestException("A template with this name already exists");
        }

        template.setName(request.getName());
        template.setDescription(request.getDescription());
        template.setBaseDocumentStorageKey(request.getBaseDocumentStorageKey());
        template.setBaseDocumentBucket(request.getBaseDocumentBucket());
        template.setBaseDocumentName(request.getBaseDocumentName());
        template.setBaseDocumentContentType(request.getBaseDocumentContentType());
        template.setBaseDocumentSize(request.getBaseDocumentSize());
        template.setBaseDocumentPageCount(request.getBaseDocumentPageCount());
        template.setFieldDefinitions(mapFieldDefinitions(request.getFieldDefinitions()));
        template.setWorkflowConfig(mapWorkflowConfig(request.getWorkflowConfig()));
        template.setExpirationDays(request.getExpirationDays());
        template.setRequireAllSignatures(request.getRequireAllSignatures());
        template.setSendCompletionNotification(request.getSendCompletionNotification());
        template.setAllowSignerReordering(request.getAllowSignerReordering());
        template.setLastModifiedBy(userId);
        template.setUpdatedAt(Instant.now());

        SigningTemplate saved = templateRepository.save(template);
        log.info("Updated signing template: {}", templateId);

        return SigningTemplateDTO.fromEntity(saved);
    }

    /**
     * Activate a template (make it available for use).
     */
    @Transactional
    public SigningTemplateDTO activateTemplate(String tenantId, String templateId) {
        SigningTemplate template = templateRepository.findByIdAndTenantId(templateId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Template not found"));

        // Validate template is ready for activation
        validateTemplateForActivation(template);

        template.setStatus(TemplateStatus.ACTIVE);
        template.setUpdatedAt(Instant.now());

        SigningTemplate saved = templateRepository.save(template);
        log.info("Activated signing template: {}", templateId);

        return SigningTemplateDTO.fromEntity(saved);
    }

    /**
     * Archive a template (soft delete).
     */
    @Transactional
    public void archiveTemplate(String tenantId, String templateId) {
        SigningTemplate template = templateRepository.findByIdAndTenantId(templateId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Template not found"));

        template.setStatus(TemplateStatus.ARCHIVED);
        template.setUpdatedAt(Instant.now());

        templateRepository.save(template);
        log.info("Archived signing template: {}", templateId);
    }

    /**
     * Duplicate a template.
     */
    @Transactional
    public SigningTemplateDTO duplicateTemplate(String tenantId, String templateId,
                                                String userId, String userName,
                                                String newName) {
        SigningTemplate original = templateRepository.findByIdAndTenantId(templateId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Template not found"));

        // Generate unique name if not provided
        String duplicateName = newName != null ? newName : original.getName() + " (Copy)";

        // Ensure unique name
        int counter = 1;
        String baseName = duplicateName;
        while (templateRepository.existsByTenantIdAndName(tenantId, duplicateName)) {
            duplicateName = baseName + " (" + (++counter) + ")";
        }

        SigningTemplate duplicate = SigningTemplate.builder()
                .tenantId(tenantId)
                .name(duplicateName)
                .description(original.getDescription())
                .baseDocumentStorageKey(original.getBaseDocumentStorageKey())
                .baseDocumentBucket(original.getBaseDocumentBucket())
                .baseDocumentName(original.getBaseDocumentName())
                .baseDocumentContentType(original.getBaseDocumentContentType())
                .baseDocumentSize(original.getBaseDocumentSize())
                .baseDocumentPageCount(original.getBaseDocumentPageCount())
                .fieldDefinitions(copyFieldDefinitions(original.getFieldDefinitions()))
                .workflowConfig(copyWorkflowConfig(original.getWorkflowConfig()))
                .status(TemplateStatus.DRAFT)
                .expirationDays(original.getExpirationDays())
                .requireAllSignatures(original.getRequireAllSignatures())
                .sendCompletionNotification(original.getSendCompletionNotification())
                .allowSignerReordering(original.getAllowSignerReordering())
                .createdBy(userId)
                .createdByName(userName)
                .createdAt(Instant.now())
                .build();

        SigningTemplate saved = templateRepository.save(duplicate);
        log.info("Duplicated signing template: {} -> {}", templateId, saved.getId());

        return SigningTemplateDTO.fromEntity(saved);
    }

    // ========================================
    // Private Helper Methods
    // ========================================

    private void validateTemplateForActivation(SigningTemplate template) {
        if (template.getBaseDocumentStorageKey() == null) {
            throw new BadRequestException("Template must have a base document");
        }
        if (template.getFieldDefinitions() == null || template.getFieldDefinitions().isEmpty()) {
            throw new BadRequestException("Template must have at least one signature field");
        }
    }

    private List<SignatureFieldDefinition> mapFieldDefinitions(
            List<CreateTemplateRequest.FieldDefinitionRequest> requests) {
        if (requests == null) return null;

        return requests.stream()
                .map(r -> SignatureFieldDefinition.builder()
                        .id(UUID.randomUUID().toString())
                        .name(r.getName())
                        .type(r.getType())
                        .pageNumber(r.getPageNumber())
                        .xPosition(r.getXPosition())
                        .yPosition(r.getYPosition())
                        .width(r.getWidth())
                        .height(r.getHeight())
                        .required(r.getRequired())
                        .signerIndex(r.getSignerIndex())
                        .placeholder(r.getPlaceholder())
                        .build())
                .toList();
    }

    private SigningWorkflowConfig mapWorkflowConfig(
            CreateTemplateRequest.WorkflowConfigRequest request) {
        if (request == null) {
            return SigningWorkflowConfig.builder().build();
        }

        return SigningWorkflowConfig.builder()
                .signingOrder(request.getSigningOrder())
                .allowDelegation(request.getAllowDelegation())
                .sendReminderEmails(request.getSendReminderEmails())
                .reminderDaysBeforeExpiry(request.getReminderDaysBeforeExpiry())
                .build();
    }

    private List<SignatureFieldDefinition> copyFieldDefinitions(
            List<SignatureFieldDefinition> original) {
        if (original == null) return null;

        return original.stream()
                .map(f -> SignatureFieldDefinition.builder()
                        .id(UUID.randomUUID().toString())  // New IDs for copy
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

    private SigningWorkflowConfig copyWorkflowConfig(SigningWorkflowConfig original) {
        if (original == null) return null;

        return SigningWorkflowConfig.builder()
                .signingOrder(original.getSigningOrder())
                .allowDelegation(original.getAllowDelegation())
                .sendReminderEmails(original.getSendReminderEmails())
                .reminderDaysBeforeExpiry(original.getReminderDaysBeforeExpiry() != null
                        ? List.copyOf(original.getReminderDaysBeforeExpiry()) : null)
                .build();
    }
}
