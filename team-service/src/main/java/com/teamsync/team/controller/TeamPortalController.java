package com.teamsync.team.controller;

import com.teamsync.common.dto.ApiResponse;
import com.teamsync.team.dto.portal.*;
import com.teamsync.team.service.PortalTeamService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Portal controller for external user team access.
 * External users have limited access - they can only view/upload files and comment on assigned tasks.
 */
@RestController
@RequestMapping("/portal/teams")
@RequiredArgsConstructor
@Validated
@Slf4j
public class TeamPortalController {

    private static final String VALID_ID_PATTERN = "^[a-zA-Z0-9-]{1,64}$";

    private final PortalTeamService portalTeamService;

    // ============== TEAM ENDPOINTS ==============

    /**
     * Get teams accessible to current portal user.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<PortalTeamDTO>>> getMyTeams(
            @RequestHeader("Authorization") String authHeader) {

        log.debug("GET /portal/teams");

        String token = extractToken(authHeader);
        List<PortalTeamDTO> teams = portalTeamService.getMyTeams(token);

        return ResponseEntity.ok(ApiResponse.<List<PortalTeamDTO>>builder()
                .success(true)
                .data(teams)
                .build());
    }

    /**
     * Get a specific team.
     */
    @GetMapping("/{teamId}")
    public ResponseEntity<ApiResponse<PortalTeamDTO>> getTeam(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid team ID format")
            String teamId) {

        log.debug("GET /portal/teams/{}", teamId);

        String token = extractToken(authHeader);
        PortalTeamDTO team = portalTeamService.getTeam(token, teamId);

        return ResponseEntity.ok(ApiResponse.<PortalTeamDTO>builder()
                .success(true)
                .data(team)
                .build());
    }

    /**
     * Get team members.
     */
    @GetMapping("/{teamId}/members")
    public ResponseEntity<ApiResponse<List<PortalMemberDTO>>> getTeamMembers(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid team ID format")
            String teamId) {

        log.debug("GET /portal/teams/{}/members", teamId);

        String token = extractToken(authHeader);
        List<PortalMemberDTO> members = portalTeamService.getTeamMembers(token, teamId);

        return ResponseEntity.ok(ApiResponse.<List<PortalMemberDTO>>builder()
                .success(true)
                .data(members)
                .build());
    }

    // ============== TASK ENDPOINTS ==============

    /**
     * Get tasks assigned to current user.
     */
    @GetMapping("/{teamId}/tasks/assigned")
    public ResponseEntity<ApiResponse<List<PortalTaskDTO>>> getMyTasks(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid team ID format")
            String teamId) {

        log.debug("GET /portal/teams/{}/tasks/assigned", teamId);

        String token = extractToken(authHeader);
        List<PortalTaskDTO> tasks = portalTeamService.getMyTasks(token, teamId);

        return ResponseEntity.ok(ApiResponse.<List<PortalTaskDTO>>builder()
                .success(true)
                .data(tasks)
                .build());
    }

    /**
     * Get a specific task (must be assigned to user).
     */
    @GetMapping("/{teamId}/tasks/{taskId}")
    public ResponseEntity<ApiResponse<PortalTaskDTO>> getTask(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid team ID format")
            String teamId,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid task ID format")
            String taskId) {

        log.debug("GET /portal/teams/{}/tasks/{}", teamId, taskId);

        String token = extractToken(authHeader);
        PortalTaskDTO task = portalTeamService.getTask(token, teamId, taskId);

        return ResponseEntity.ok(ApiResponse.<PortalTaskDTO>builder()
                .success(true)
                .data(task)
                .build());
    }

    /**
     * Get comments for a task.
     */
    @GetMapping("/{teamId}/tasks/{taskId}/comments")
    public ResponseEntity<ApiResponse<List<PortalCommentDTO>>> getTaskComments(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid team ID format")
            String teamId,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid task ID format")
            String taskId) {

        log.debug("GET /portal/teams/{}/tasks/{}/comments", teamId, taskId);

        String token = extractToken(authHeader);
        List<PortalCommentDTO> comments = portalTeamService.getTaskComments(token, teamId, taskId);

        return ResponseEntity.ok(ApiResponse.<List<PortalCommentDTO>>builder()
                .success(true)
                .data(comments)
                .build());
    }

    /**
     * Add a comment to a task.
     */
    @PostMapping("/{teamId}/tasks/{taskId}/comments")
    public ResponseEntity<ApiResponse<PortalCommentDTO>> addComment(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid team ID format")
            String teamId,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid task ID format")
            String taskId,
            @Valid @RequestBody CreateCommentRequest request) {

        log.info("POST /portal/teams/{}/tasks/{}/comments", teamId, taskId);

        String token = extractToken(authHeader);
        PortalCommentDTO comment = portalTeamService.addComment(token, teamId, taskId, request.getContent());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<PortalCommentDTO>builder()
                        .success(true)
                        .data(comment)
                        .message("Comment added successfully")
                        .build());
    }

    /**
     * Update own comment.
     */
    @PatchMapping("/{teamId}/tasks/{taskId}/comments/{commentId}")
    public ResponseEntity<ApiResponse<PortalCommentDTO>> updateComment(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid team ID format")
            String teamId,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid task ID format")
            String taskId,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid comment ID format")
            String commentId,
            @Valid @RequestBody UpdateCommentRequest request) {

        log.info("PATCH /portal/teams/{}/tasks/{}/comments/{}", teamId, taskId, commentId);

        String token = extractToken(authHeader);
        PortalCommentDTO comment = portalTeamService.updateComment(
                token, teamId, taskId, commentId, request.getContent());

        return ResponseEntity.ok(ApiResponse.<PortalCommentDTO>builder()
                .success(true)
                .data(comment)
                .message("Comment updated successfully")
                .build());
    }

    /**
     * Delete own comment.
     */
    @DeleteMapping("/{teamId}/tasks/{taskId}/comments/{commentId}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid team ID format")
            String teamId,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid task ID format")
            String taskId,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid comment ID format")
            String commentId) {

        log.info("DELETE /portal/teams/{}/tasks/{}/comments/{}", teamId, taskId, commentId);

        String token = extractToken(authHeader);
        portalTeamService.deleteComment(token, teamId, taskId, commentId);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Comment deleted successfully")
                .build());
    }

    // ============== HELPER METHODS ==============

    private String extractToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        throw new IllegalArgumentException("Invalid Authorization header");
    }

    /**
     * Request body for creating a comment.
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CreateCommentRequest {
        @NotBlank(message = "Content is required")
        @Size(max = 10000, message = "Content cannot exceed 10000 characters")
        private String content;
    }

    /**
     * Request body for updating a comment.
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class UpdateCommentRequest {
        @NotBlank(message = "Content is required")
        @Size(max = 10000, message = "Content cannot exceed 10000 characters")
        private String content;
    }
}
