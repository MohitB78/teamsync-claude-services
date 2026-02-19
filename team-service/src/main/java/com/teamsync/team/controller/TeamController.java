package com.teamsync.team.controller;

import com.teamsync.common.dto.ApiResponse;
import com.teamsync.common.dto.CursorPage;
import com.teamsync.team.dto.CreateTeamRequest;
import com.teamsync.team.dto.TeamDTO;
import com.teamsync.team.dto.UpdateTeamRequest;
import com.teamsync.team.service.TeamService;
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
 * Team Controller - manages team operations.
 *
 * SECURITY FIX (Round 12): Added @PreAuthorize annotations to all endpoints.
 * SECURITY FIX (Round 13 #54): Added @Validated and path variable validation.
 * SECURITY FIX (Round 15 #H11): Added class-level @PreAuthorize for defense-in-depth.
 */
@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
@Validated
@Slf4j
@PreAuthorize("isAuthenticated()")
public class TeamController {

    private static final String VALID_ID_PATTERN = "^[a-zA-Z0-9-]{1,64}$";

    private final TeamService teamService;

    // ============== TEAM CRUD ==============

    /**
     * Get teams the current user is a member of.
     * This is the primary endpoint - returns teams where the user has membership.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<CursorPage<TeamDTO>>> getTeams(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit) {

        log.debug("GET /api/teams - limit: {}", limit);
        CursorPage<TeamDTO> teams = teamService.getMyTeams(cursor, limit);

        return ResponseEntity.ok(ApiResponse.<CursorPage<TeamDTO>>builder()
                .success(true)
                .data(teams)
                .build());
    }

    /**
     * Get teams the current user is a member of (alias for /api/teams).
     */
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<CursorPage<TeamDTO>>> getMyTeams(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit) {

        log.debug("GET /api/teams/my - limit: {}", limit);
        CursorPage<TeamDTO> teams = teamService.getMyTeams(cursor, limit);

        return ResponseEntity.ok(ApiResponse.<CursorPage<TeamDTO>>builder()
                .success(true)
                .data(teams)
                .build());
    }

    /**
     * Get a specific team by ID.
     */
    @GetMapping("/{teamId}")
    public ResponseEntity<ApiResponse<TeamDTO>> getTeam(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid team ID format")
            String teamId) {

        log.debug("GET /api/teams/{}", teamId);
        TeamDTO team = teamService.getTeam(teamId);

        return ResponseEntity.ok(ApiResponse.<TeamDTO>builder()
                .success(true)
                .data(team)
                .build());
    }

    /**
     * Create a new team.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<TeamDTO>> createTeam(@Valid @RequestBody CreateTeamRequest request) {
        log.info("POST /api/teams - name: {}", request.getName());
        TeamDTO team = teamService.createTeam(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<TeamDTO>builder()
                        .success(true)
                        .data(team)
                        .message("Team created successfully")
                        .build());
    }

    /**
     * Update an existing team.
     */
    @PatchMapping("/{teamId}")
    public ResponseEntity<ApiResponse<TeamDTO>> updateTeam(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid team ID format")
            String teamId,
            @Valid @RequestBody UpdateTeamRequest request) {

        log.info("PATCH /api/teams/{}", teamId);
        TeamDTO team = teamService.updateTeam(teamId, request);

        return ResponseEntity.ok(ApiResponse.<TeamDTO>builder()
                .success(true)
                .data(team)
                .message("Team updated successfully")
                .build());
    }

    /**
     * Delete a team (soft delete to archived).
     */
    @DeleteMapping("/{teamId}")
    public ResponseEntity<ApiResponse<Void>> deleteTeam(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid team ID format")
            String teamId) {

        log.info("DELETE /api/teams/{}", teamId);
        teamService.deleteTeam(teamId);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Team deleted successfully")
                .build());
    }

    // ============== OWNERSHIP ==============

    /**
     * Transfer team ownership to another member.
     */
    @PostMapping("/{teamId}/transfer-ownership")
    public ResponseEntity<ApiResponse<Void>> transferOwnership(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid team ID format")
            String teamId,
            @RequestParam
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid user ID format")
            String newOwnerId) {

        log.info("POST /api/teams/{}/transfer-ownership to {}", teamId, newOwnerId);
        teamService.transferOwnership(teamId, newOwnerId);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Ownership transferred successfully")
                .build());
    }

    // ============== SEARCH ==============

    /**
     * Search for public teams.
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<TeamDTO>>> searchTeams(
            @RequestParam String query,
            @RequestParam(defaultValue = "20") int limit) {

        log.debug("GET /api/teams/search - query: {}, limit: {}", query, limit);
        List<TeamDTO> teams = teamService.searchPublicTeams(query, limit);

        return ResponseEntity.ok(ApiResponse.<List<TeamDTO>>builder()
                .success(true)
                .data(teams)
                .build());
    }

}
