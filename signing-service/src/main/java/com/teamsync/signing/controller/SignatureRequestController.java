package com.teamsync.signing.controller;

import com.teamsync.common.dto.ApiResponse;
import com.teamsync.signing.dto.CreateSignatureRequestRequest;
import com.teamsync.signing.dto.SendRequestRequest;
import com.teamsync.signing.dto.SignatureRequestDTO;
import com.teamsync.signing.model.SignatureRequest.SignatureRequestStatus;
import com.teamsync.signing.service.SignatureRequestService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for managing signature requests.
 * Internal API - requires JWT authentication via API Gateway.
 */
@RestController
@RequestMapping("/api/signing/requests")
@RequiredArgsConstructor
@Slf4j
public class SignatureRequestController {

    private final SignatureRequestService requestService;

    /**
     * Create a new signature request.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<SignatureRequestDTO>> createRequest(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-User-ID") String userId,
            @RequestHeader("X-User-Name") String userName,
            @RequestHeader("X-User-Email") String userEmail,
            @Valid @RequestBody CreateSignatureRequestRequest request) {

        log.info("Creating signature request for template: {} in tenant: {}",
                request.getTemplateId(), tenantId);

        SignatureRequestDTO sigRequest = requestService.createRequest(
                tenantId, userId, userName, userEmail, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<SignatureRequestDTO>builder()
                        .success(true)
                        .data(sigRequest)
                        .message("Signature request created successfully")
                        .build());
    }

    /**
     * Get a signature request by ID.
     */
    @GetMapping("/{requestId}")
    public ResponseEntity<ApiResponse<SignatureRequestDTO>> getRequest(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable String requestId) {

        SignatureRequestDTO request = requestService.getRequest(tenantId, requestId);

        return ResponseEntity.ok(ApiResponse.<SignatureRequestDTO>builder()
                .success(true)
                .data(request)
                .build());
    }

    /**
     * List signature requests for current user.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<SignatureRequestDTO>>> listRequests(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-User-ID") String userId,
            @RequestParam(required = false) SignatureRequestStatus status,
            Pageable pageable) {

        Page<SignatureRequestDTO> requests;
        if (status != null) {
            requests = requestService.listRequestsByStatus(tenantId, status, pageable);
        } else {
            requests = requestService.listRequestsBySender(tenantId, userId, pageable);
        }

        return ResponseEntity.ok(ApiResponse.<Page<SignatureRequestDTO>>builder()
                .success(true)
                .data(requests)
                .build());
    }

    /**
     * Send a signature request to signers.
     */
    @PostMapping("/{requestId}/send")
    public ResponseEntity<ApiResponse<SignatureRequestDTO>> sendRequest(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-User-ID") String userId,
            @RequestHeader("X-User-Email") String userEmail,
            @RequestHeader("X-User-Name") String userName,
            @PathVariable String requestId,
            @Valid @RequestBody(required = false) SendRequestRequest sendRequest,
            HttpServletRequest httpRequest) {

        log.info("Sending signature request: {} in tenant: {}", requestId, tenantId);

        String ipAddress = getClientIpAddress(httpRequest);

        SignatureRequestDTO request = requestService.sendRequest(
                tenantId, requestId, userId, userEmail, userName, sendRequest, ipAddress);

        return ResponseEntity.ok(ApiResponse.<SignatureRequestDTO>builder()
                .success(true)
                .data(request)
                .message("Signature request sent successfully")
                .build());
    }

    /**
     * Void (cancel) a signature request.
     */
    @PostMapping("/{requestId}/void")
    public ResponseEntity<ApiResponse<SignatureRequestDTO>> voidRequest(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-User-ID") String userId,
            @RequestHeader("X-User-Email") String userEmail,
            @RequestHeader("X-User-Name") String userName,
            @PathVariable String requestId,
            @RequestParam(required = false) String reason,
            HttpServletRequest httpRequest) {

        log.info("Voiding signature request: {} in tenant: {}", requestId, tenantId);

        String ipAddress = getClientIpAddress(httpRequest);

        SignatureRequestDTO request = requestService.voidRequest(
                tenantId, requestId, userId, userEmail, userName, reason, ipAddress);

        return ResponseEntity.ok(ApiResponse.<SignatureRequestDTO>builder()
                .success(true)
                .data(request)
                .message("Signature request voided successfully")
                .build());
    }

    /**
     * Send reminder to pending signers.
     */
    @PostMapping("/{requestId}/remind")
    public ResponseEntity<ApiResponse<Void>> sendReminder(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-User-ID") String userId,
            @RequestHeader("X-User-Email") String userEmail,
            @RequestHeader("X-User-Name") String userName,
            @PathVariable String requestId,
            HttpServletRequest httpRequest) {

        log.info("Sending reminder for signature request: {} in tenant: {}", requestId, tenantId);

        String ipAddress = getClientIpAddress(httpRequest);

        requestService.sendReminder(tenantId, requestId, userId, userEmail, userName, ipAddress);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Reminder sent successfully")
                .build());
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
