package com.teamsync.team.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * Task (To-Do) entity representing a work item within a team.
 * Tasks support Kanban board visualization, assignments, priorities,
 * attachments from the team drive, and comments.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "tasks")
@CompoundIndexes({
        @CompoundIndex(name = "tenant_team_idx", def = "{'tenantId': 1, 'teamId': 1}"),
        @CompoundIndex(name = "tenant_team_status_idx", def = "{'tenantId': 1, 'teamId': 1, 'status': 1}"),
        @CompoundIndex(name = "tenant_team_assignee_idx", def = "{'tenantId': 1, 'teamId': 1, 'assigneeId': 1}"),
        @CompoundIndex(name = "tenant_team_project_idx", def = "{'tenantId': 1, 'teamId': 1, 'projectId': 1}"),
        @CompoundIndex(name = "tenant_due_date_idx", def = "{'tenantId': 1, 'dueDate': 1}"),
        @CompoundIndex(name = "tenant_team_column_order_idx",
                       def = "{'tenantId': 1, 'teamId': 1, 'boardColumnId': 1, 'sortOrder': 1}")
})
public class Task {

    @Id
    private String id;

    /**
     * Optimistic locking for concurrent updates (Kanban drag-drop).
     */
    @Version
    private Long entityVersion;

    // Index handled by compound indexes above
    private String tenantId;

    /**
     * The team this task belongs to.
     */
    private String teamId;

    /**
     * Optional project association for project-based task grouping.
     */
    private String projectId;

    // Task details
    private String title;

    /**
     * Task description (supports Markdown).
     */
    private String description;

    private TaskPriority priority;
    private TaskStatus status;

    // Kanban board support
    /**
     * Column ID for custom Kanban boards.
     * If null, uses status-based default columns.
     */
    private String boardColumnId;

    /**
     * Position within the column (lower numbers appear first).
     */
    private Integer sortOrder;

    // Assignment
    private String assigneeId;

    /**
     * Denormalized assignee name for display.
     */
    private String assigneeName;

    /**
     * Users watching this task (receive notifications on updates).
     */
    private List<String> watcherIds;

    // Dates
    private Instant dueDate;
    private Instant startDate;
    private Instant completedAt;

    // Effort tracking (optional)
    private Integer estimatedHours;
    private Integer actualHours;

    // Task relationships
    /**
     * Parent task ID for subtasks.
     */
    private String parentTaskId;

    /**
     * Denormalized list of subtask IDs for efficient UI display.
     */
    private List<String> subtaskIds;

    /**
     * Tasks that block this task (dependencies).
     */
    private List<String> blockedByTaskIds;

    /**
     * Document IDs from the team drive attached to this task.
     */
    private List<String> attachmentIds;

    // Labels and organization
    private List<String> labels;

    /**
     * Color for visual identification (hex format).
     */
    private String color;

    // Checklist items (embedded for performance)
    private List<ChecklistItem> checklist;

    /**
     * Denormalized comment count for display.
     */
    private Integer commentCount;

    /**
     * Whether this task is pinned (shown at top).
     */
    private Boolean isPinned;

    // Audit fields
    private String createdBy;
    private Instant createdAt;
    private String lastModifiedBy;
    private Instant updatedAt;

    /**
     * Task priority levels.
     */
    public enum TaskPriority {
        URGENT,
        HIGH,
        MEDIUM,
        LOW
    }

    /**
     * Task status values.
     * These map to default Kanban columns.
     */
    public enum TaskStatus {
        /** Task is not yet started */
        TODO,
        /** Task is actively being worked on */
        IN_PROGRESS,
        /** Task is blocked by dependencies or issues */
        BLOCKED,
        /** Task is completed */
        DONE,
        /** Task was cancelled */
        CANCELLED
    }

    /**
     * Checklist item within a task.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChecklistItem {
        private String id;
        private String text;
        private Boolean completed;
        private Instant completedAt;
        private String completedBy;
        private Integer sortOrder;
    }

    // Helper methods

    /**
     * Checks if this task is completed.
     */
    public boolean isCompleted() {
        return status == TaskStatus.DONE;
    }

    /**
     * Checks if this task is blocked.
     */
    public boolean isBlocked() {
        return status == TaskStatus.BLOCKED;
    }

    /**
     * Checks if this task is overdue.
     */
    public boolean isOverdue() {
        return dueDate != null &&
               Instant.now().isAfter(dueDate) &&
               status != TaskStatus.DONE &&
               status != TaskStatus.CANCELLED;
    }

    /**
     * Gets the checklist completion percentage.
     *
     * @return percentage (0-100), or 0 if no checklist
     */
    public int getChecklistProgress() {
        if (checklist == null || checklist.isEmpty()) {
            return 0;
        }
        long completed = checklist.stream()
                .filter(item -> Boolean.TRUE.equals(item.getCompleted()))
                .count();
        return (int) ((completed * 100) / checklist.size());
    }

    /**
     * Checks if this task has subtasks.
     */
    public boolean hasSubtasks() {
        return subtaskIds != null && !subtaskIds.isEmpty();
    }

    /**
     * Checks if this is a subtask of another task.
     */
    public boolean isSubtask() {
        return parentTaskId != null && !parentTaskId.isEmpty();
    }
}
