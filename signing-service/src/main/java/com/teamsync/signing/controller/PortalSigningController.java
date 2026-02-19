package com.teamsync.signing.controller;

import com.teamsync.common.dto.ApiResponse;
import com.teamsync.signing.dto.DeclineSigningRequest;
import com.teamsync.signing.dto.PortalSigningSessionDTO;
import com.teamsync.signing.dto.SubmitSignaturesRequest;
import com.teamsync.signing.service.PortalSigningService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for portal signing operations.
 * PUBLIC API - uses token-based authentication (no JWT required).
 *
 * External users access these endpoints via signing links sent by email.
 */
@RestController
@RequestMapping("/portal/signing")
@RequiredArgsConstructor
@Slf4j
public class PortalSigningController {

    private final PortalSigningService portalSigningService;

    /**
     * Validate signing token and get session info.
     *
     * Called when external user opens signing link.
     * Returns document info and signature field list.
     */
    @GetMapping("/{token}")
    public ResponseEntity<ApiResponse<PortalSigningSessionDTO>> validateToken(
            @PathVariable String token) {

        log.debug("Validating signing token");

        PortalSigningSessionDTO session = portalSigningService.validateToken(token);

        return ResponseEntity.ok(ApiResponse.<PortalSigningSessionDTO>builder()
                .success(true)
                .data(session)
                .build());
    }

    /**
     * Get document URL for viewing.
     *
     * Returns a download token for the document.
     */
    @GetMapping("/{token}/document")
    public ResponseEntity<ApiResponse<Map<String, String>>> getDocument(
            @PathVariable String token) {

        String downloadToken = portalSigningService.getDocumentUrl(token);

        return ResponseEntity.ok(ApiResponse.<Map<String, String>>builder()
                .success(true)
                .data(Map.of("downloadToken", downloadToken))
                .build());
    }

    /**
     * Record document view event.
     *
     * Called when external user first views the document.
     * Logs IP address and user agent for audit trail.
     */
    @PostMapping("/{token}/view")
    public ResponseEntity<ApiResponse<Void>> recordView(
            @PathVariable String token,
            HttpServletRequest httpRequest) {

        String ipAddress = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        portalSigningService.recordDocumentView(token, ipAddress, userAgent);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("View recorded")
                .build());
    }

    /**
     * Submit signatures for the document.
     *
     * Called when external user completes all signature fields.
     * Returns updated session with download availability.
     */
    @PostMapping("/{token}/sign")
    public ResponseEntity<ApiResponse<PortalSigningSessionDTO>> submitSignatures(
            @PathVariable String token,
            @Valid @RequestBody SubmitSignaturesRequest request,
            HttpServletRequest httpRequest) {

        log.info("Submitting signatures for token");

        String ipAddress = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        PortalSigningSessionDTO session = portalSigningService.submitSignatures(
                token, request, ipAddress, userAgent);

        return ResponseEntity.ok(ApiResponse.<PortalSigningSessionDTO>builder()
                .success(true)
                .data(session)
                .message("Signatures submitted successfully")
                .build());
    }

    /**
     * Decline to sign the document.
     *
     * Called when external user refuses to sign.
     * Requires a reason which is logged for audit.
     */
    @PostMapping("/{token}/decline")
    public ResponseEntity<ApiResponse<Void>> declineSigning(
            @PathVariable String token,
            @Valid @RequestBody DeclineSigningRequest request,
            HttpServletRequest httpRequest) {

        log.info("Declining to sign for token");

        String ipAddress = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        portalSigningService.declineSigning(token, request, ipAddress, userAgent);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Signing declined")
                .build());
    }

    /**
     * Download signed document.
     *
     * Only available after document is fully signed.
     * Returns a download token for the signed PDF.
     */
    @GetMapping("/{token}/download")
    public ResponseEntity<ApiResponse<Map<String, String>>> downloadSignedDocument(
            @PathVariable String token) {

        String downloadToken = portalSigningService.getSignedDocumentUrl(token);

        return ResponseEntity.ok(ApiResponse.<Map<String, String>>builder()
                .success(true)
                .data(Map.of("downloadToken", downloadToken))
                .build());
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }
}
