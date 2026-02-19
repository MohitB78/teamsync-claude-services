package com.teamsync.team.controller;

import com.teamsync.common.dto.ApiResponse;
import com.teamsync.team.dto.portal.*;
import com.teamsync.team.model.TeamInvitation;
import com.teamsync.team.service.PortalAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Portal authentication controller for external users.
 * Handles magic link authentication flow.
 */
@RestController
@RequestMapping("/portal/auth")
@RequiredArgsConstructor
@Slf4j
public class PortalAuthController {

    private final PortalAuthService portalAuthService;

    /**
     * Request a magic link to be sent to email.
     * Public endpoint - no authentication required.
     */
    @PostMapping("/magic-link")
    public ResponseEntity<ApiResponse<Void>> requestMagicLink(
            @RequestHeader(value = "X-Tenant-ID", defaultValue = "default") String tenantId,
            @Valid @RequestBody MagicLinkRequest request) {

        log.info("POST /portal/auth/magic-link - email: {}", request.getEmail());

        portalAuthService.requestMagicLink(tenantId, request);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Magic link sent to your email")
                .build());
    }

    /**
     * Verify magic link token and get auth tokens.
     * Public endpoint - no authentication required.
     */
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<PortalAuthResponse>> verifyMagicLink(
            @RequestHeader(value = "X-Tenant-ID", defaultValue = "default") String tenantId,
            @Valid @RequestBody VerifyMagicLinkRequest request) {

        log.info("POST /portal/auth/verify");

        PortalAuthResponse response = portalAuthService.verifyMagicLink(tenantId, request);

        return ResponseEntity.ok(ApiResponse.<PortalAuthResponse>builder()
                .success(true)
                .data(response)
                .message("Authentication successful")
                .build());
    }

    /**
     * Refresh access token using refresh token.
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<PortalAuthResponse>> refreshToken(
            @RequestHeader(value = "X-Tenant-ID", defaultValue = "default") String tenantId,
            @Valid @RequestBody RefreshTokenRequest request) {

        log.debug("POST /portal/auth/refresh");

        PortalAuthResponse response = portalAuthService.refreshToken(tenantId, request);

        return ResponseEntity.ok(ApiResponse.<PortalAuthResponse>builder()
                .success(true)
                .data(response)
                .build());
    }

    /**
     * Get current user info.
     * Requires portal authentication.
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PortalUserDTO>> getCurrentUser(
            @RequestHeader("Authorization") String authHeader) {

        String token = extractToken(authHeader);
        PortalUserDTO user = portalAuthService.getCurrentUser(token);

        return ResponseEntity.ok(ApiResponse.<PortalUserDTO>builder()
                .success(true)
                .data(user)
                .build());
    }

    /**
     * Get invitation info by token.
     * Public endpoint for displaying invitation details.
     */
    @GetMapping("/invitations/verify")
    public ResponseEntity<ApiResponse<InvitationInfoDTO>> getInvitationInfo(
            @RequestParam String token) {

        log.info("GET /portal/invitations/verify");

        TeamInvitation invitation = portalAuthService.getInvitationByToken(token);

        InvitationInfoDTO info = InvitationInfoDTO.builder()
                .id(invitation.getId())
                .teamId(invitation.getTeamId())
                .teamName(invitation.getTeamName())
                .email(invitation.getEmail())
                .roleName(invitation.getRoleName())
                .invitedByName(invitation.getInvitedByName())
                .expiresAt(invitation.getExpiresAt())
                .status(invitation.getStatus().name())
                .build();

        return ResponseEntity.ok(ApiResponse.<InvitationInfoDTO>builder()
                .success(true)
                .data(info)
                .build());
    }

    private String extractToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        throw new IllegalArgumentException("Invalid Authorization header");
    }

    /**
     * Invitation info DTO for the verify endpoint.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class InvitationInfoDTO {
        private String id;
        private String teamId;
        private String teamName;
        private String email;
        private String roleName;
        private String invitedByName;
        private java.time.Instant expiresAt;
        private String status;
    }
}
