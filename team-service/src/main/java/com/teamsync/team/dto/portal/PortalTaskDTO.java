package com.teamsync.team.dto.portal;

import com.teamsync.team.model.Task.TaskPriority;
import com.teamsync.team.model.Task.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Limited task view for external portal users.
 * External users can only see tasks assigned to them.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortalTaskDTO {

    private String id;
    private String teamId;
    private String title;
    private String description;
    private TaskPriority priority;
    private TaskStatus status;
    private Instant dueDate;
    private String assigneeId;
    private String assigneeName;
    private List<String> labels;
    private int checklistProgress;
    private int commentCount;
    private boolean isOverdue;
    private Instant createdAt;
    private Instant updatedAt;
}
