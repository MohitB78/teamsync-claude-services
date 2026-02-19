package com.teamsync.team.dto;

import com.teamsync.team.model.Task;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Data Transfer Object for Task entity.
 * Used for API responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskDTO {

    private String id;
    private String tenantId;
    private String teamId;
    private String projectId;

    private String title;
    private String description;

    private Task.TaskPriority priority;
    private Task.TaskStatus status;

    // Kanban board position
    private String boardColumnId;
    private Integer sortOrder;

    // Assignment
    private String assigneeId;
    private String assigneeName;
    private String assigneeAvatar;
    private List<String> watcherIds;

    // Dates
    private Instant dueDate;
    private Instant startDate;
    private Instant completedAt;

    // Effort tracking
    private Integer estimatedHours;
    private Integer actualHours;

    // Relationships
    private String parentTaskId;
    private List<String> subtaskIds;
    private Integer subtaskCount;
    private Integer subtaskCompletedCount;
    private List<String> blockedByTaskIds;

    // Attachments
    private List<String> attachmentIds;
    private Integer attachmentCount;

    // Labels and organization
    private List<String> labels;
    private String color;

    // Checklist
    private List<ChecklistItemDTO> checklist;
    private Integer checklistProgress;

    // Comments
    private Integer commentCount;

    // Flags
    private Boolean isPinned;
    private Boolean isOverdue;
    private Boolean isBlocked;

    // Audit
    private String createdBy;
    private String createdByName;
    private Instant createdAt;
    private String lastModifiedBy;
    private Instant updatedAt;

    /**
     * DTO for checklist items within a task.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChecklistItemDTO {
        private String id;
        private String text;
        private Boolean completed;
        private Instant completedAt;
        private String completedBy;
        private String completedByName;
        private Integer sortOrder;
    }
}
