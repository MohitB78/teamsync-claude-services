package com.teamsync.team.dto;

import com.teamsync.team.model.Task;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Request DTO for creating a new task.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTaskRequest {

    @NotBlank(message = "Task title is required")
    @Size(min = 1, max = 200, message = "Title must be between 1 and 200 characters")
    private String title;

    @Size(max = 10000, message = "Description cannot exceed 10000 characters")
    private String description;

    /**
     * Task priority.
     * Default: MEDIUM
     */
    @Builder.Default
    private Task.TaskPriority priority = Task.TaskPriority.MEDIUM;

    /**
     * Initial task status.
     * Default: TODO
     */
    @Builder.Default
    private Task.TaskStatus status = Task.TaskStatus.TODO;

    /**
     * Optional project association.
     */
    private String projectId;

    /**
     * User to assign the task to.
     * If null, task is unassigned.
     */
    private String assigneeId;

    /**
     * Due date for the task.
     */
    private Instant dueDate;

    /**
     * Start date for the task.
     */
    private Instant startDate;

    /**
     * Estimated effort in hours.
     */
    private Integer estimatedHours;

    /**
     * Parent task ID for creating subtasks.
     */
    private String parentTaskId;

    /**
     * Tasks that block this task.
     */
    private List<String> blockedByTaskIds;

    /**
     * Document IDs from team drive to attach.
     */
    private List<String> attachmentIds;

    /**
     * Labels/tags for the task.
     */
    private List<String> labels;

    /**
     * Color for visual identification (hex format).
     */
    private String color;

    /**
     * Kanban column ID for custom boards.
     * If null, uses default column based on status.
     */
    private String boardColumnId;

    /**
     * Position within the column.
     * If null, task is added at the end.
     */
    private Integer sortOrder;

    /**
     * Initial checklist items.
     */
    private List<ChecklistItemRequest> checklist;

    /**
     * Request for a checklist item.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChecklistItemRequest {
        @NotBlank(message = "Checklist item text is required")
        @Size(max = 500, message = "Checklist item text cannot exceed 500 characters")
        private String text;

        @Builder.Default
        private Boolean completed = false;

        private Integer sortOrder;
    }
}
