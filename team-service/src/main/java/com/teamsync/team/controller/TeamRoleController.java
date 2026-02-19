package com.teamsync.team.controller;

import com.teamsync.common.dto.ApiResponse;
import com.teamsync.team.dto.CreateTeamRoleRequest;
import com.teamsync.team.dto.TeamRoleDTO;
import com.teamsync.team.dto.UpdateTeamRoleRequest;
import com.teamsync.team.service.TeamRoleService;
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
 * REST controller for team role management.
 */
@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
@Validated
@Slf4j
@PreAuthorize("isAuthenticated()")
public class TeamRoleController {

    private static final String VALID_ID_PATTERN = "^[a-zA-Z0-9-]{1,64}$";

    private final TeamRoleService roleService;

    // ============== ROLE QUERIES ==============

    /**
     * Get all system roles (tenant-wide templates).
     */
    @GetMapping("/roles/system")
    public ResponseEntity<ApiResponse<List<TeamRoleDTO>>> getSystemRoles() {
        log.debug("GET /api/teams/roles/system");
        List<TeamRoleDTO> roles = roleService.getSystemRoles();

        return ResponseEntity.ok(ApiResponse.<List<TeamRoleDTO>>builder()
                .success(true)
                .data(roles)
                .build());
    }

    /**
     * Get all roles available for a team (system + custom).
     */
    @GetMapping("/{teamId}/roles")
    public ResponseEntity<ApiResponse<List<TeamRoleDTO>>> getRolesForTeam(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid team ID format")
            String teamId) {

        log.debug("GET /api/teams/{}/roles", teamId);
        List<TeamRoleDTO> roles = roleService.getRolesForTeam(teamId);

        return ResponseEntity.ok(ApiResponse.<List<TeamRoleDTO>>builder()
                .success(true)
                .data(roles)
                .build());
    }

    /**
     * Get a specific role by ID.
     */
    @GetMapping("/{teamId}/roles/{roleId}")
    public ResponseEntity<ApiResponse<TeamRoleDTO>> getRole(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid team ID format")
            String teamId,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid role ID format")
            String roleId) {

        log.debug("GET /api/teams/{}/roles/{}", teamId, roleId);
        TeamRoleDTO role = roleService.getRole(roleId);

        return ResponseEntity.ok(ApiResponse.<TeamRoleDTO>builder()
                .success(true)
                .data(role)
                .build());
    }

    // ============== CUSTOM ROLE CRUD ==============

    /**
     * Create a custom role for a team.
     */
    @PostMapping("/{teamId}/roles")
    public ResponseEntity<ApiResponse<TeamRoleDTO>> createRole(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid team ID format")
            String teamId,
            @Valid @RequestBody CreateTeamRoleRequest request) {

        log.info("POST /api/teams/{}/roles - name: {}", teamId, request.getName());
        TeamRoleDTO role = roleService.createRole(teamId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<TeamRoleDTO>builder()
                        .success(true)
                        .data(role)
                        .message("Role created successfully")
                        .build());
    }

    /**
     * Update a custom role.
     */
    @PatchMapping("/{teamId}/roles/{roleId}")
    public ResponseEntity<ApiResponse<TeamRoleDTO>> updateRole(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid team ID format")
            String teamId,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid role ID format")
            String roleId,
            @Valid @RequestBody UpdateTeamRoleRequest request) {

        log.info("PATCH /api/teams/{}/roles/{}", teamId, roleId);
        TeamRoleDTO role = roleService.updateRole(teamId, roleId, request);

        return ResponseEntity.ok(ApiResponse.<TeamRoleDTO>builder()
                .success(true)
                .data(role)
                .message("Role updated successfully")
                .build());
    }

    /**
     * Delete a custom role.
     */
    @DeleteMapping("/{teamId}/roles/{roleId}")
    public ResponseEntity<ApiResponse<Void>> deleteRole(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid team ID format")
            String teamId,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid role ID format")
            String roleId) {

        log.info("DELETE /api/teams/{}/roles/{}", teamId, roleId);
        roleService.deleteRole(teamId, roleId);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Role deleted successfully")
                .build());
    }
}
