package com.teamsync.team.controller;

import com.teamsync.common.dto.ApiResponse;
import com.teamsync.team.dto.*;
import com.teamsync.team.service.TaskService;
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
import java.util.Map;

/**
 * REST controller for task management within teams.
 */
@RestController
@RequestMapping("/api/teams/{teamId}/tasks")
@RequiredArgsConstructor
@Validated
@Slf4j
@PreAuthorize("isAuthenticated()")
public class TaskController {

    private static final String VALID_ID_PATTERN = "^[a-zA-Z0-9-]{1,64}$";

    private final TaskService taskService;

    // ============== TASK QUERIES ==============

    /**
     * Get tasks for a team with optional filtering.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<TaskDTO>>> getTasks(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid team ID format")
            String teamId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String assigneeId,
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit) {

        log.debug("GET /api/teams/{}/tasks", teamId);
        List<TaskDTO> tasks = taskService.getTasks(teamId, status, assigneeId, projectId, cursor, limit);

        return ResponseEntity.ok(ApiResponse.<List<TaskDTO>>builder()
                .success(true)
                .data(tasks)
                .build());
    }

    /**
     * Get tasks organized for Kanban board view.
     */
    @GetMapping("/board")
    public ResponseEntity<ApiResponse<Map<String, List<TaskDTO>>>> getKanbanBoard(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid team ID format")
            String teamId) {

        log.debug("GET /api/teams/{}/tasks/board", teamId);
        Map<String, List<TaskDTO>> board = taskService.getKanbanBoard(teamId);

        return ResponseEntity.ok(ApiResponse.<Map<String, List<TaskDTO>>>builder()
                .success(true)
                .data(board)
                .build());
    }

    /**
     * Get a specific task.
     */
    @GetMapping("/{taskId}")
    public ResponseEntity<ApiResponse<TaskDTO>> getTask(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid team ID format")
            String teamId,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid task ID format")
            String taskId) {

        log.debug("GET /api/teams/{}/tasks/{}", teamId, taskId);
        TaskDTO task = taskService.getTask(teamId, taskId);

        return ResponseEntity.ok(ApiResponse.<TaskDTO>builder()
                .success(true)
                .data(task)
                .build());
    }

    /**
     * Get tasks assigned to current user in this team.
     */
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<TaskDTO>>> getMyTasks(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid team ID format")
            String teamId) {

        log.debug("GET /api/teams/{}/tasks/my", teamId);
        List<TaskDTO> tasks = taskService.getMyTasks(teamId);

        return ResponseEntity.ok(ApiResponse.<List<TaskDTO>>builder()
                .success(true)
                .data(tasks)
                .build());
    }

    // ============== TASK CRUD ==============

    /**
     * Create a new task.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<TaskDTO>> createTask(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid team ID format")
            String teamId,
            @Valid @RequestBody CreateTaskRequest request) {

        log.info("POST /api/teams/{}/tasks - title: {}", teamId, request.getTitle());
        TaskDTO task = taskService.createTask(teamId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<TaskDTO>builder()
                        .success(true)
                        .data(task)
                        .message("Task created successfully")
                        .build());
    }

    /**
     * Update an existing task.
     */
    @PatchMapping("/{taskId}")
    public ResponseEntity<ApiResponse<TaskDTO>> updateTask(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid team ID format")
            String teamId,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid task ID format")
            String taskId,
            @Valid @RequestBody UpdateTaskRequest request) {

        log.info("PATCH /api/teams/{}/tasks/{}", teamId, taskId);
        TaskDTO task = taskService.updateTask(teamId, taskId, request);

        return ResponseEntity.ok(ApiResponse.<TaskDTO>builder()
                .success(true)
                .data(task)
                .message("Task updated successfully")
                .build());
    }

    /**
     * Delete a task.
     */
    @DeleteMapping("/{taskId}")
    public ResponseEntity<ApiResponse<Void>> deleteTask(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid team ID format")
            String teamId,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid task ID format")
            String taskId) {

        log.info("DELETE /api/teams/{}/tasks/{}", teamId, taskId);
        taskService.deleteTask(teamId, taskId);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Task deleted successfully")
                .build());
    }

    // ============== KANBAN OPERATIONS ==============

    /**
     * Move a task to a new column/position (drag-drop).
     */
    @PostMapping("/{taskId}/move")
    public ResponseEntity<ApiResponse<TaskDTO>> moveTask(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid team ID format")
            String teamId,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid task ID format")
            String taskId,
            @Valid @RequestBody MoveTaskRequest request) {

        log.info("POST /api/teams/{}/tasks/{}/move", teamId, taskId);
        TaskDTO task = taskService.moveTask(teamId, taskId, request);

        return ResponseEntity.ok(ApiResponse.<TaskDTO>builder()
                .success(true)
                .data(task)
                .message("Task moved successfully")
                .build());
    }

    // ============== CHECKLIST OPERATIONS ==============

    /**
     * Toggle a checklist item.
     */
    @PostMapping("/{taskId}/checklist/{itemId}/toggle")
    public ResponseEntity<ApiResponse<TaskDTO>> toggleChecklistItem(
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
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid item ID format")
            String itemId) {

        log.info("POST /api/teams/{}/tasks/{}/checklist/{}/toggle", teamId, taskId, itemId);
        TaskDTO task = taskService.toggleChecklistItem(teamId, taskId, itemId);

        return ResponseEntity.ok(ApiResponse.<TaskDTO>builder()
                .success(true)
                .data(task)
                .build());
    }
}
