package com.teamsync.team.controller;

import com.teamsync.common.dto.ApiResponse;
import com.teamsync.team.dto.CreateInvitationRequest;
import com.teamsync.team.dto.TeamInvitationDTO;
import com.teamsync.team.service.TeamInvitationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for team invitation management.
 */
@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
@Validated
@Slf4j
public class TeamInvitationController {

    private static final String VALID_ID_PATTERN = "^[a-zA-Z0-9-]{1,64}$";
    private static final String VALID_TOKEN_PATTERN = "^[a-zA-Z0-9_-]{20,64}$";

    private final TeamInvitationService invitationService;

    // ============== INVITATION MANAGEMENT (Authenticated) ==============

    /**
     * Get pending invitations for a team.
     */
    @GetMapping("/{teamId}/invitations")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<TeamInvitationDTO>>> getPendingInvitations(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid team ID format")
            String teamId) {

        log.debug("GET /api/teams/{}/invitations", teamId);
        List<TeamInvitationDTO> invitations = invitationService.getPendingInvitations(teamId);

        return ResponseEntity.ok(ApiResponse.<List<TeamInvitationDTO>>builder()
                .success(true)
                .data(invitations)
                .build());
    }

    /**
     * Create and send an invitation.
     */
    @PostMapping("/{teamId}/invitations")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TeamInvitationDTO>> createInvitation(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid team ID format")
            String teamId,
            @Valid @RequestBody CreateInvitationRequest request) {

        log.info("POST /api/teams/{}/invitations - email: {}", teamId, request.getEmail());
        TeamInvitationDTO invitation = invitationService.createInvitation(teamId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<TeamInvitationDTO>builder()
                        .success(true)
                        .data(invitation)
                        .message("Invitation sent successfully")
                        .build());
    }

    /**
     * Cancel a pending invitation.
     */
    @DeleteMapping("/{teamId}/invitations/{invitationId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> cancelInvitation(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid team ID format")
            String teamId,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid invitation ID format")
            String invitationId) {

        log.info("DELETE /api/teams/{}/invitations/{}", teamId, invitationId);
        invitationService.cancelInvitation(teamId, invitationId);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Invitation cancelled")
                .build());
    }

    /**
     * Resend an invitation email.
     */
    @PostMapping("/{teamId}/invitations/{invitationId}/resend")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> resendInvitation(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid team ID format")
            String teamId,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid invitation ID format")
            String invitationId) {

        log.info("POST /api/teams/{}/invitations/{}/resend", teamId, invitationId);
        invitationService.resendInvitation(teamId, invitationId);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Invitation resent")
                .build());
    }

    // ============== PUBLIC INVITATION ENDPOINTS (Token-based) ==============

    /**
     * Get invitation details by token (public endpoint).
     * Used to display invitation info before accepting/declining.
     */
    @GetMapping("/invitations/verify")
    public ResponseEntity<ApiResponse<TeamInvitationDTO>> getInvitationByToken(
            @RequestParam
            @NotBlank
            @Pattern(regexp = VALID_TOKEN_PATTERN, message = "Invalid token format")
            String token) {

        log.debug("GET /api/teams/invitations/verify - token provided");
        TeamInvitationDTO invitation = invitationService.getInvitationByToken(token);

        return ResponseEntity.ok(ApiResponse.<TeamInvitationDTO>builder()
                .success(true)
                .data(invitation)
                .build());
    }

    /**
     * Accept an invitation using the token.
     * For internal users, they must be authenticated.
     * For external users, this creates their portal session.
     */
    @PostMapping("/invitations/accept")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> acceptInvitation(
            @RequestParam
            @NotBlank
            @Pattern(regexp = VALID_TOKEN_PATTERN, message = "Invalid token format")
            String token,
            @RequestParam
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid user ID format")
            String userId) {

        log.info("POST /api/teams/invitations/accept - token provided");
        invitationService.acceptInvitation(token, userId);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Invitation accepted. You are now a team member.")
                .build());
    }

    /**
     * Decline an invitation.
     */
    @PostMapping("/invitations/decline")
    public ResponseEntity<ApiResponse<Void>> declineInvitation(
            @RequestParam
            @NotBlank
            @Pattern(regexp = VALID_TOKEN_PATTERN, message = "Invalid token format")
            String token) {

        log.info("POST /api/teams/invitations/decline - token provided");
        invitationService.declineInvitation(token);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Invitation declined")
                .build());
    }
}
