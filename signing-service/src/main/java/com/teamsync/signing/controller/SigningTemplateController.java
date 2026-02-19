package com.teamsync.signing.controller;

import com.teamsync.common.dto.ApiResponse;
import com.teamsync.signing.dto.CreateTemplateRequest;
import com.teamsync.signing.dto.SigningTemplateDTO;
import com.teamsync.signing.model.SigningTemplate.TemplateStatus;
import com.teamsync.signing.service.SigningTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for managing signing templates.
 * Internal API - requires JWT authentication via API Gateway.
 */
@RestController
@RequestMapping("/api/signing/templates")
@RequiredArgsConstructor
@Slf4j
public class SigningTemplateController {

    private final SigningTemplateService templateService;

    /**
     * Create a new signing template.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<SigningTemplateDTO>> createTemplate(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-User-ID") String userId,
            @RequestHeader("X-User-Name") String userName,
            @Valid @RequestBody CreateTemplateRequest request) {

        log.info("Creating signing template: {} for tenant: {}", request.getName(), tenantId);

        SigningTemplateDTO template = templateService.createTemplate(tenantId, userId, userName, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<SigningTemplateDTO>builder()
                        .success(true)
                        .data(template)
                        .message("Template created successfully")
                        .build());
    }

    /**
     * Get a template by ID.
     */
    @GetMapping("/{templateId}")
    public ResponseEntity<ApiResponse<SigningTemplateDTO>> getTemplate(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable String templateId) {

        SigningTemplateDTO template = templateService.getTemplate(tenantId, templateId);

        return ResponseEntity.ok(ApiResponse.<SigningTemplateDTO>builder()
                .success(true)
                .data(template)
                .build());
    }

    /**
     * List templates for a tenant (paginated).
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<SigningTemplateDTO>>> listTemplates(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam(required = false) TemplateStatus status,
            Pageable pageable) {

        Page<SigningTemplateDTO> templates;
        if (status != null) {
            templates = templateService.listTemplatesByStatus(tenantId, status, pageable);
        } else {
            templates = templateService.listTemplates(tenantId, pageable);
        }

        return ResponseEntity.ok(ApiResponse.<Page<SigningTemplateDTO>>builder()
                .success(true)
                .data(templates)
                .build());
    }

    /**
     * Get active templates (for dropdown selection).
     */
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<SigningTemplateDTO>>> getActiveTemplates(
            @RequestHeader("X-Tenant-ID") String tenantId) {

        List<SigningTemplateDTO> templates = templateService.getActiveTemplates(tenantId);

        return ResponseEntity.ok(ApiResponse.<List<SigningTemplateDTO>>builder()
                .success(true)
                .data(templates)
                .build());
    }

    /**
     * Update a template.
     */
    @PutMapping("/{templateId}")
    public ResponseEntity<ApiResponse<SigningTemplateDTO>> updateTemplate(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-User-ID") String userId,
            @PathVariable String templateId,
            @Valid @RequestBody CreateTemplateRequest request) {

        log.info("Updating signing template: {} for tenant: {}", templateId, tenantId);

        SigningTemplateDTO template = templateService.updateTemplate(tenantId, templateId, userId, request);

        return ResponseEntity.ok(ApiResponse.<SigningTemplateDTO>builder()
                .success(true)
                .data(template)
                .message("Template updated successfully")
                .build());
    }

    /**
     * Activate a template (make it available for use).
     */
    @PostMapping("/{templateId}/activate")
    public ResponseEntity<ApiResponse<SigningTemplateDTO>> activateTemplate(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable String templateId) {

        log.info("Activating signing template: {} for tenant: {}", templateId, tenantId);

        SigningTemplateDTO template = templateService.activateTemplate(tenantId, templateId);

        return ResponseEntity.ok(ApiResponse.<SigningTemplateDTO>builder()
                .success(true)
                .data(template)
                .message("Template activated successfully")
                .build());
    }

    /**
     * Archive a template (soft delete).
     */
    @DeleteMapping("/{templateId}")
    public ResponseEntity<ApiResponse<Void>> archiveTemplate(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable String templateId) {

        log.info("Archiving signing template: {} for tenant: {}", templateId, tenantId);

        templateService.archiveTemplate(tenantId, templateId);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Template archived successfully")
                .build());
    }

    /**
     * Duplicate a template.
     */
    @PostMapping("/{templateId}/duplicate")
    public ResponseEntity<ApiResponse<SigningTemplateDTO>> duplicateTemplate(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-User-ID") String userId,
            @RequestHeader("X-User-Name") String userName,
            @PathVariable String templateId,
            @RequestParam(required = false) String newName) {

        log.info("Duplicating signing template: {} for tenant: {}", templateId, tenantId);

        SigningTemplateDTO template = templateService.duplicateTemplate(
                tenantId, templateId, userId, userName, newName);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<SigningTemplateDTO>builder()
                        .success(true)
                        .data(template)
                        .message("Template duplicated successfully")
                        .build());
    }
}
