package com.teamsync.audit.controller;

import com.teamsync.audit.dto.*;
import com.teamsync.audit.service.VerificationService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * REST controller for audit verification endpoints.
 *
 * Provides:
 * - Single event verification
 * - Resource audit trail verification
 * - Signature request verification (legal compliance)
 * - Integrity reports
 * - Cryptographic proofs
 */
@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@Validated
@Slf4j
@ConditionalOnProperty(name = "teamsync.audit.immudb.enabled", havingValue = "true")
public class AuditVerificationController {

    private final VerificationService verificationService;

    // Validation patterns
    private static final String ID_PATTERN = "^[a-zA-Z0-9-]{1,64}$";
    private static final String RESOURCE_TYPE_PATTERN = "^[a-zA-Z_]{1,32}$";

    /**
     * Verify a specific audit event's integrity.
     *
     * @param eventId The audit event ID to verify
     * @return Verification result with cryptographic proof
     */
    @GetMapping("/verify/{eventId}")
    public ResponseEntity<ApiResponse<VerificationResult>> verifyAuditEvent(
            @RequestHeader("X-TenantId") String tenantId,
            @PathVariable @NotBlank @Pattern(regexp = ID_PATTERN) String eventId) {
        log.info("Verifying audit event: {} for tenant: {}", eventId, tenantId);

        VerificationResult result = verificationService.verifyEvent(tenantId, eventId);

        return ResponseEntity.ok(ApiResponse.<VerificationResult>builder()
                .success(true)
                .data(result)
                .build());
    }

    /**
     * Verify audit trail integrity for a resource.
     * Returns whether the entire audit chain for a document/folder is intact.
     */
    @GetMapping("/verify/resource/{resourceType}/{resourceId}")
    public ResponseEntity<ApiResponse<ResourceAuditVerification>> verifyResourceAuditTrail(
            @RequestHeader("X-TenantId") String tenantId,
            @PathVariable @NotBlank @Pattern(regexp = RESOURCE_TYPE_PATTERN) String resourceType,
            @PathVariable @NotBlank @Pattern(regexp = ID_PATTERN) String resourceId) {

        log.info("Verifying audit trail for {}/{} in tenant {}", resourceType, resourceId, tenantId);

        ResourceAuditVerification result = verificationService.verifyResourceAuditTrail(
                tenantId, resourceType, resourceId);

        return ResponseEntity.ok(ApiResponse.<ResourceAuditVerification>builder()
                .success(true)
                .data(result)
                .build());
    }

    /**
     * Verify signature audit trail for a signing request.
     * Critical for legal compliance - proves chain of custody for e-signatures.
     */
    @GetMapping("/verify/signature-request/{requestId}")
    public ResponseEntity<ApiResponse<ResourceAuditVerification>> verifySignatureAuditTrail(
            @RequestHeader("X-TenantId") String tenantId,
            @PathVariable @NotBlank @Pattern(regexp = ID_PATTERN) String requestId) {

        log.info("Verifying signature audit trail for request {} in tenant {}", requestId, tenantId);

        // Signature events use "SIGNATURE_REQUEST" as the resource type
        ResourceAuditVerification result = verificationService.verifyResourceAuditTrail(
                tenantId, "SIGNATURE_REQUEST", requestId);

        return ResponseEntity.ok(ApiResponse.<ResourceAuditVerification>builder()
                .success(true)
                .data(result)
                .build());
    }

    /**
     * Generate an integrity report for a tenant.
     * Verifies all audit events in a time range and returns a compliance report.
     */
    @GetMapping("/integrity-report")
    public ResponseEntity<ApiResponse<IntegrityReport>> generateIntegrityReport(
            @RequestHeader("X-TenantId") String tenantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime) {

        log.info("Generating integrity report for tenant {} from {} to {}", tenantId, startTime, endTime);

        IntegrityReport report = verificationService.generateIntegrityReport(
                tenantId, startTime, endTime);

        return ResponseEntity.ok(ApiResponse.<IntegrityReport>builder()
                .success(true)
                .data(report)
                .build());
    }

    /**
     * Get cryptographic proof for an audit event.
     * Returns Merkle proof that can be independently verified.
     */
    @GetMapping("/proof/{eventId}")
    public ResponseEntity<ApiResponse<CryptographicProof>> getCryptographicProof(
            @RequestHeader("X-TenantId") String tenantId,
            @PathVariable @NotBlank @Pattern(regexp = ID_PATTERN) String eventId) {

        log.info("Getting cryptographic proof for event {} in tenant {}", eventId, tenantId);

        CryptographicProof proof = verificationService.getCryptographicProof(tenantId, eventId);

        if (proof == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(ApiResponse.<CryptographicProof>builder()
                .success(true)
                .data(proof)
                .build());
    }


    @GetMapping("/health")
    public ResponseEntity<ApiResponse<String>> healthCheck() {
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .success(true)
                .data("Audit verification service is healthy")
                .build());
    }
}
