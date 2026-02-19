package com.teamsync.team.controller;

import com.teamsync.common.dto.ApiResponse;
import com.teamsync.team.dto.TeamMemberDTO;
import com.teamsync.team.dto.UpdateMemberRoleRequest;
import com.teamsync.team.service.TeamMemberService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for team member management operations.
 */
@RestController
@RequestMapping("/api/teams/{teamId}/members")
@RequiredArgsConstructor
@Validated
@Slf4j
@PreAuthorize("isAuthenticated()")
public class TeamMemberController {

    private static final String VALID_ID_PATTERN = "^[a-zA-Z0-9-]{1,64}$";

    private final TeamMemberService memberService;

    // ============== MEMBER QUERIES ==============

    /**
     * Get all members of a team.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<TeamMemberDTO>>> getMembers(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid team ID format")
            String teamId) {

        log.debug("GET /api/teams/{}/members", teamId);
        List<TeamMemberDTO> members = memberService.getMembers(teamId);

        return ResponseEntity.ok(ApiResponse.<List<TeamMemberDTO>>builder()
                .success(true)
                .data(members)
                .build());
    }

    /**
     * Get a specific member's details.
     */
    @GetMapping("/{memberId}")
    public ResponseEntity<ApiResponse<TeamMemberDTO>> getMember(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid team ID format")
            String teamId,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid member ID format")
            String memberId) {

        log.debug("GET /api/teams/{}/members/{}", teamId, memberId);
        TeamMemberDTO member = memberService.getMember(teamId, memberId);

        return ResponseEntity.ok(ApiResponse.<TeamMemberDTO>builder()
                .success(true)
                .data(member)
                .build());
    }

    // ============== ROLE ASSIGNMENT ==============

    /**
     * Update a member's role.
     */
    @PatchMapping("/{memberId}/role")
    public ResponseEntity<ApiResponse<TeamMemberDTO>> updateMemberRole(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid team ID format")
            String teamId,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid member ID format")
            String memberId,
            @Valid @RequestBody UpdateMemberRoleRequest request) {

        log.info("PATCH /api/teams/{}/members/{}/role", teamId, memberId);
        TeamMemberDTO member = memberService.updateMemberRole(teamId, memberId, request);

        return ResponseEntity.ok(ApiResponse.<TeamMemberDTO>builder()
                .success(true)
                .data(member)
                .message("Member role updated successfully")
                .build());
    }

    // ============== MEMBER REMOVAL ==============

    /**
     * Remove a member from the team.
     */
    @DeleteMapping("/{memberId}")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid team ID format")
            String teamId,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid member ID format")
            String memberId) {

        log.info("DELETE /api/teams/{}/members/{}", teamId, memberId);
        memberService.removeMember(teamId, memberId);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Member removed successfully")
                .build());
    }

    /**
     * Leave a team (self-removal).
     */
    @PostMapping("/leave")
    public ResponseEntity<ApiResponse<Void>> leaveTeam(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid team ID format")
            String teamId) {

        log.info("POST /api/teams/{}/members/leave", teamId);
        memberService.leaveTeam(teamId);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("You have left the team")
                .build());
    }
}
