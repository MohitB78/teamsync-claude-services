package com.teamsync.audit.controller;

import com.teamsync.audit.dto.*;
import com.teamsync.audit.model.PurgeRecord;
import com.teamsync.audit.service.AuditPurgeService;
import com.teamsync.audit.service.AuditSearchService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for audit administration endpoints.
 *
 * Provides:
 * - Search and filter audit logs
 * - Export audit logs
 * - Get audit statistics
 * - Preview and execute purge operations
 * - View purge history
 */
@RestController
@RequestMapping("/api/audit/admin")
@RequiredArgsConstructor
@Validated
@Slf4j
public class AuditAdminController {

    private final AuditSearchService auditSearchService;
    private final AuditPurgeService auditPurgeService;

    // Validation patterns
    private static final String ID_PATTERN = "^[a-zA-Z0-9-]{1,64}$";

    @PostMapping("/search")
    public ResponseEntity<ApiResponse<AuditSearchResponse>> searchAuditLogs(
            @RequestBody @Valid AuditSearchRequest request,
            @RequestHeader("X-TenantId") String tenantId
            ) {

        log.info("Searching audit logs for tenant {}", tenantId);

        AuditSearchResponse response = auditSearchService.search(tenantId, request);

        return ResponseEntity.ok(ApiResponse.<AuditSearchResponse>builder()
                .success(true)
                .data(response)
                .build());
    }

    /**
     * Get audit statistics for dashboard.
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<AuditStats>> getAuditStats(@RequestHeader("X-TenantId") String tenantId) {

        log.info("Getting audit stats for tenant {}", tenantId);

        AuditStats stats = auditSearchService.getStats(tenantId);

        return ResponseEntity.ok(ApiResponse.<AuditStats>builder()
                .success(true)
                .data(stats)
                .build());
    }

    /**
     * Get audit activity for a specific user.
     */
    @GetMapping("/users/{userId}/activity")
    public ResponseEntity<ApiResponse<AuditSearchResponse>> getUserActivity(
            @PathVariable @NotBlank @Pattern(regexp = ID_PATTERN) String userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestHeader("X-TenantId") String tenantId,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        log.info("Getting activity for user {} in tenant {}", userId, tenantId);

        AuditSearchResponse response = auditSearchService.getUserActivity(tenantId, userId, page, size);

        return ResponseEntity.ok(ApiResponse.<AuditSearchResponse>builder()
                .success(true)
                .data(response)
                .build());
    }

    // ============================================
    // Purge Endpoints
    // ============================================

    /**
     * Preview what will be purged (dry run).
     */
    @PostMapping("/purge/preview")
    public ResponseEntity<ApiResponse<PurgeResponse>> previewPurge(
            @RequestBody @Valid PurgeRequest request,
            @RequestHeader("X-TenantId") String tenantId) {

        log.info("Previewing purge for tenant {} with retention {}", tenantId, request.getRetentionPeriod());

        PurgeResponse response = auditPurgeService.previewPurge(tenantId, request);

        return ResponseEntity.ok(ApiResponse.<PurgeResponse>builder()
                .success(true)
                .data(response)
                .build());
    }

    /**
     * Execute purge operation.
     * Requires AUDIT_ADMIN role and valid confirmation code.
     */
/*
TODO :
    @PostMapping("/purge/execute")
    public ResponseEntity<ApiResponse<PurgeResponse>> executePurge(
            @RequestBody @Valid PurgeRequest request,
            @RequestHeader("X-TenantId") String tenantId)
    //        @AuthenticationPrincipal Jwt jwt
    ) {

        String tenantId = TenantContext.getTenantId();
        String userId = jwt.getSubject();
        String userEmail = jwt.getClaimAsString("email");

        log.info("Executing purge for tenant {} by user {}", tenantId, userId);

        PurgeResponse response = auditPurgeService.executePurge(
                tenantId, userId, userEmail, request);

        return ResponseEntity.ok(ApiResponse.<PurgeResponse>builder()
                .success(response.getSuccess() != null && response.getSuccess())
                .data(response)
                .error(response.getErrorMessage())
                .build());
    }
*/

    /**
     * Get purge history.
     */
    @GetMapping("/purge/history")
    public ResponseEntity<ApiResponse<PurgeHistoryResponse>> getPurgeHistory(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestHeader("X-TenantId") String tenantId
            ) {

        log.info("Getting purge history for tenant {}", tenantId);

        Page<PurgeRecord> purgeRecords = auditPurgeService.getPurgeHistory(tenantId, page, size);

        List<PurgeHistoryResponse.PurgeRecordDto> items = purgeRecords.getContent().stream()
                .map(this::toPurgeRecordDto)
                .collect(Collectors.toList());

        PurgeHistoryResponse response = PurgeHistoryResponse.builder()
                .items(items)
                .totalItems(purgeRecords.getTotalElements())
                .totalPages(purgeRecords.getTotalPages())
                .currentPage(purgeRecords.getNumber())
                .pageSize(purgeRecords.getSize())
                .hasMore(purgeRecords.hasNext())
                .build();

        return ResponseEntity.ok(ApiResponse.<PurgeHistoryResponse>builder()
                .success(true)
                .data(response)
                .build());
    }

    /**
     * Convert PurgeRecord to DTO.
     */
    private PurgeHistoryResponse.PurgeRecordDto toPurgeRecordDto(PurgeRecord record) {
        return PurgeHistoryResponse.PurgeRecordDto.builder()
                .id(record.getId())
                .purgedBy(record.getPurgedBy())
                .purgedByEmail(record.getPurgedByEmail())
                .purgeReason(record.getPurgeReason())
                .retentionPeriod(record.getRetentionPeriod())
                .eventsPurged(record.getEventsPurged())
                .purgeTime(record.getPurgeTime() != null ? record.getPurgeTime().toString() : null)
                .hashBefore(record.getHashBefore())
                .hashAfter(record.getHashAfter())
                .immudbPurged(record.isImmudbPurged())
                .mongodbPurged(record.isMongodbPurged())
                .errorMessage(record.getErrorMessage())
                .build();
    }
}
