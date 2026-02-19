package com.teamsync.team.dto;

import com.teamsync.team.model.Task;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Request DTO for updating an existing task.
 * All fields are optional - only non-null fields are updated.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTaskRequest {

    @Size(min = 1, max = 200, message = "Title must be between 1 and 200 characters")
    private String title;

    @Size(max = 10000, message = "Description cannot exceed 10000 characters")
    private String description;

    private Task.TaskPriority priority;

    private Task.TaskStatus status;

    private String projectId;

    private String assigneeId;

    private Instant dueDate;

    private Instant startDate;

    private Integer estimatedHours;

    private Integer actualHours;

    private List<String> blockedByTaskIds;

    private List<String> attachmentIds;

    private List<String> labels;

    private String color;

    private String boardColumnId;

    private Integer sortOrder;

    private Boolean isPinned;

    private List<CreateTaskRequest.ChecklistItemRequest> checklist;
}
