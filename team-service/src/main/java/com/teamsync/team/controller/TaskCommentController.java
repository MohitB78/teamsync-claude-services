package com.teamsync.team.controller;

import com.teamsync.common.dto.ApiResponse;
import com.teamsync.team.dto.CreateTaskCommentRequest;
import com.teamsync.team.dto.TaskCommentDTO;
import com.teamsync.team.service.TaskCommentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for task comment operations.
 */
@RestController
@RequestMapping("/api/teams/{teamId}/tasks/{taskId}/comments")
@RequiredArgsConstructor
@Validated
@Slf4j
@PreAuthorize("isAuthenticated()")
public class TaskCommentController {

    private static final String VALID_ID_PATTERN = "^[a-zA-Z0-9-]{1,64}$";

    private final TaskCommentService commentService;

    // ============== COMMENT QUERIES ==============

    /**
     * Get comments for a task (paginated).
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<TaskCommentDTO>>> getComments(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid team ID format")
            String teamId,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid task ID format")
            String taskId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int limit) {

        log.debug("GET /api/teams/{}/tasks/{}/comments", teamId, taskId);
        List<TaskCommentDTO> comments = commentService.getComments(teamId, taskId, page, limit);

        return ResponseEntity.ok(ApiResponse.<List<TaskCommentDTO>>builder()
                .success(true)
                .data(comments)
                .build());
    }

    /**
     * Get comment count for a task.
     */
    @GetMapping("/count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getCommentCount(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid team ID format")
            String teamId,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid task ID format")
            String taskId) {

        log.debug("GET /api/teams/{}/tasks/{}/comments/count", teamId, taskId);
        long count = commentService.getCommentCount(teamId, taskId);

        return ResponseEntity.ok(ApiResponse.<Map<String, Long>>builder()
                .success(true)
                .data(Map.of("count", count))
                .build());
    }

    // ============== COMMENT CRUD ==============

    /**
     * Create a new comment on a task.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<TaskCommentDTO>> createComment(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid team ID format")
            String teamId,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid task ID format")
            String taskId,
            @Valid @RequestBody CreateTaskCommentRequest request) {

        log.info("POST /api/teams/{}/tasks/{}/comments", teamId, taskId);
        TaskCommentDTO comment = commentService.createComment(teamId, taskId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<TaskCommentDTO>builder()
                        .success(true)
                        .data(comment)
                        .message("Comment added successfully")
                        .build());
    }

    /**
     * Update a comment (only author can update).
     */
    @PatchMapping("/{commentId}")
    public ResponseEntity<ApiResponse<TaskCommentDTO>> updateComment(
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
            @RequestBody
            @NotBlank
            @Size(max = 10000, message = "Content cannot exceed 10000 characters")
            String content) {

        log.info("PATCH /api/teams/{}/tasks/{}/comments/{}", teamId, taskId, commentId);
        TaskCommentDTO comment = commentService.updateComment(teamId, taskId, commentId, content);

        return ResponseEntity.ok(ApiResponse.<TaskCommentDTO>builder()
                .success(true)
                .data(comment)
                .message("Comment updated successfully")
                .build());
    }

    /**
     * Delete a comment.
     */
    @DeleteMapping("/{commentId}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
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

        log.info("DELETE /api/teams/{}/tasks/{}/comments/{}", teamId, taskId, commentId);
        commentService.deleteComment(teamId, taskId, commentId);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Comment deleted successfully")
                .build());
    }

    // ============== REACTIONS ==============

    /**
     * Add a reaction to a comment.
     */
    @PostMapping("/{commentId}/reactions")
    public ResponseEntity<ApiResponse<TaskCommentDTO>> addReaction(
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
            @RequestParam
            @NotBlank
            @Size(min = 1, max = 10, message = "Invalid emoji")
            String emoji) {

        log.info("POST /api/teams/{}/tasks/{}/comments/{}/reactions - {}", teamId, taskId, commentId, emoji);
        TaskCommentDTO comment = commentService.addReaction(teamId, taskId, commentId, emoji);

        return ResponseEntity.ok(ApiResponse.<TaskCommentDTO>builder()
                .success(true)
                .data(comment)
                .build());
    }
}
