package com.teamsync.team.service;

import com.teamsync.common.context.TenantContext;
import com.teamsync.common.exception.AccessDeniedException;
import com.teamsync.common.exception.ResourceNotFoundException;
import com.teamsync.team.dto.*;
import com.teamsync.team.model.Task;
import com.teamsync.team.model.Team;
import com.teamsync.team.model.TeamPermission;
import com.teamsync.team.repository.TaskRepository;
import com.teamsync.team.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing tasks (to-dos) within teams.
 *
 * Responsibilities:
 * - Task CRUD operations
 * - Kanban board operations (move, reorder)
 * - Assignment management
 * - Status transitions
 * - Checklist management
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final TeamRepository teamRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final int DEFAULT_PAGE_SIZE = 50;

    // ============== TASK QUERIES ==============

    /**
     * Get tasks for a team with optional filtering.
     */
    public List<TaskDTO> getTasks(String teamId, String status, String assigneeId,
                                   String projectId, String cursor, int limit) {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();

        Team team = teamRepository.findByIdAndTenantId(teamId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));

        verifyTaskViewPermission(team, userId);

        Pageable pageable = PageRequest.of(0, Math.min(limit, DEFAULT_PAGE_SIZE));
        List<Task> tasks;

        if (status != null) {
            Task.TaskStatus taskStatus = Task.TaskStatus.valueOf(status);
            tasks = taskRepository.findByTenantIdAndTeamIdAndStatus(tenantId, teamId, taskStatus, pageable);
        } else if (assigneeId != null) {
            tasks = taskRepository.findByTenantIdAndTeamIdAndAssigneeIdPaged(tenantId, teamId, assigneeId, pageable);
        } else if (projectId != null) {
            tasks = taskRepository.findByTenantIdAndTeamIdAndProjectId(tenantId, teamId, projectId, pageable);
        } else {
            tasks = taskRepository.findByTenantIdAndTeamIdOrderByCreatedAtDesc(tenantId, teamId, pageable);
        }

        return tasks.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get tasks organized for Kanban board view.
     */
    public Map<String, List<TaskDTO>> getKanbanBoard(String teamId) {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();

        Team team = teamRepository.findByIdAndTenantId(teamId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));

        verifyTaskViewPermission(team, userId);

        Map<String, List<TaskDTO>> board = new LinkedHashMap<>();

        // Default columns based on status
        for (Task.TaskStatus status : Task.TaskStatus.values()) {
            if (status != Task.TaskStatus.CANCELLED) {
                board.put(status.name(), new ArrayList<>());
            }
        }

        // Fetch all non-cancelled tasks
        List<Task> tasks = taskRepository.findByTenantIdAndTeamIdAndStatusNotIn(
                tenantId, teamId, List.of(Task.TaskStatus.CANCELLED), PageRequest.of(0, 500));

        // Group by column (or status if no custom column)
        for (Task task : tasks) {
            String column = task.getBoardColumnId() != null ? task.getBoardColumnId() : task.getStatus().name();
            board.computeIfAbsent(column, k -> new ArrayList<>()).add(mapToDTO(task));
        }

        // Sort each column by sortOrder
        board.values().forEach(list ->
                list.sort(Comparator.comparing(TaskDTO::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()))));

        return board;
    }

    /**
     * Get a specific task.
     */
    public TaskDTO getTask(String teamId, String taskId) {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();

        Team team = teamRepository.findByIdAndTenantId(teamId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));

        verifyTaskViewPermission(team, userId);

        Task task = taskRepository.findByIdAndTenantIdAndTeamId(taskId, tenantId, teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));

        return mapToDTO(task);
    }

    /**
     * Get tasks assigned to current user.
     */
    public List<TaskDTO> getMyTasks(String teamId) {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();

        Team team = teamRepository.findByIdAndTenantId(teamId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));

        verifyMembership(team, userId);

        List<Task> tasks = taskRepository.findByTenantIdAndTeamIdAndAssigneeIdPaged(
                tenantId, teamId, userId, PageRequest.of(0, 100));

        return tasks.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    // ============== TASK CRUD ==============

    /**
     * Create a new task.
     */
    @Transactional
    public TaskDTO createTask(String teamId, CreateTaskRequest request) {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();

        Team team = teamRepository.findByIdAndTenantId(teamId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));

        verifyTaskCreatePermission(team, userId);

        // Validate assignee is a team member
        String assigneeName = null;
        if (request.getAssigneeId() != null) {
            Team.TeamMember assignee = team.getMembers().stream()
                    .filter(m -> m.getUserId().equals(request.getAssigneeId()) &&
                                 m.getStatus() == Team.MemberStatus.ACTIVE)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Assignee is not a team member"));
            assigneeName = assignee.getEmail();
        }

        // Calculate sort order (add at end of column)
        Integer sortOrder = request.getSortOrder();
        if (sortOrder == null) {
            String column = request.getBoardColumnId() != null ?
                    request.getBoardColumnId() : request.getStatus().name();
            sortOrder = taskRepository.countByTenantIdAndTeamIdAndBoardColumnId(tenantId, teamId, column).intValue();
        }

        // Build checklist items
        List<Task.ChecklistItem> checklist = null;
        if (request.getChecklist() != null && !request.getChecklist().isEmpty()) {
            checklist = new ArrayList<>();
            int order = 0;
            for (CreateTaskRequest.ChecklistItemRequest item : request.getChecklist()) {
                checklist.add(Task.ChecklistItem.builder()
                        .id(UUID.randomUUID().toString())
                        .text(item.getText())
                        .completed(item.getCompleted())
                        .sortOrder(item.getSortOrder() != null ? item.getSortOrder() : order++)
                        .build());
            }
        }

        Task task = Task.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .projectId(request.getProjectId())
                .title(request.getTitle())
                .description(request.getDescription())
                .priority(request.getPriority())
                .status(request.getStatus())
                .boardColumnId(request.getBoardColumnId())
                .sortOrder(sortOrder)
                .assigneeId(request.getAssigneeId())
                .assigneeName(assigneeName)
                .watcherIds(new ArrayList<>())
                .dueDate(request.getDueDate())
                .startDate(request.getStartDate())
                .estimatedHours(request.getEstimatedHours())
                .parentTaskId(request.getParentTaskId())
                .blockedByTaskIds(request.getBlockedByTaskIds())
                .attachmentIds(request.getAttachmentIds())
                .labels(request.getLabels())
                .color(request.getColor())
                .checklist(checklist)
                .commentCount(0)
                .isPinned(false)
                .createdBy(userId)
                .createdAt(Instant.now())
                .build();

        Task savedTask = taskRepository.save(task);

        // Update parent task's subtask list if this is a subtask
        if (request.getParentTaskId() != null) {
            final String savedTaskId = savedTask.getId();
            taskRepository.findByIdAndTenantIdAndTeamId(request.getParentTaskId(), tenantId, teamId)
                    .ifPresent(parent -> {
                        if (parent.getSubtaskIds() == null) {
                            parent.setSubtaskIds(new ArrayList<>());
                        }
                        parent.getSubtaskIds().add(savedTaskId);
                        taskRepository.save(parent);
                    });
        }
        task = savedTask;

        // Publish event
        publishTaskCreated(task, team);

        log.info("Created task: {} in team: {}", task.getTitle(), teamId);

        return mapToDTO(task);
    }

    /**
     * Update an existing task.
     */
    @Transactional
    public TaskDTO updateTask(String teamId, String taskId, UpdateTaskRequest request) {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();

        Team team = teamRepository.findByIdAndTenantId(teamId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));

        Task task = taskRepository.findByIdAndTenantIdAndTeamId(taskId, tenantId, teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));

        // Check permissions - assignee can update some fields, others need TASK_EDIT
        boolean isAssignee = userId.equals(task.getAssigneeId());
        if (!isAssignee) {
            verifyTaskEditPermission(team, userId);
        }

        // Update fields
        if (request.getTitle() != null) {
            task.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            task.setDescription(request.getDescription());
        }
        if (request.getPriority() != null) {
            task.setPriority(request.getPriority());
        }
        if (request.getStatus() != null) {
            updateTaskStatus(task, request.getStatus(), userId);
        }
        if (request.getProjectId() != null) {
            task.setProjectId(request.getProjectId());
        }
        if (request.getAssigneeId() != null) {
            verifyTaskAssignPermission(team, userId);
            // Validate new assignee
            Team.TeamMember newAssignee = team.getMembers().stream()
                    .filter(m -> m.getUserId().equals(request.getAssigneeId()) &&
                                 m.getStatus() == Team.MemberStatus.ACTIVE)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Assignee is not a team member"));
            task.setAssigneeId(newAssignee.getUserId());
            task.setAssigneeName(newAssignee.getEmail());
        }
        if (request.getDueDate() != null) {
            task.setDueDate(request.getDueDate());
        }
        if (request.getStartDate() != null) {
            task.setStartDate(request.getStartDate());
        }
        if (request.getEstimatedHours() != null) {
            task.setEstimatedHours(request.getEstimatedHours());
        }
        if (request.getActualHours() != null) {
            task.setActualHours(request.getActualHours());
        }
        if (request.getBlockedByTaskIds() != null) {
            task.setBlockedByTaskIds(request.getBlockedByTaskIds());
        }
        if (request.getAttachmentIds() != null) {
            task.setAttachmentIds(request.getAttachmentIds());
        }
        if (request.getLabels() != null) {
            task.setLabels(request.getLabels());
        }
        if (request.getColor() != null) {
            task.setColor(request.getColor());
        }
        if (request.getIsPinned() != null) {
            task.setIsPinned(request.getIsPinned());
        }

        task.setLastModifiedBy(userId);
        task.setUpdatedAt(Instant.now());

        task = taskRepository.save(task);

        // Publish event
        publishTaskUpdated(task);

        log.info("Updated task: {} in team: {}", taskId, teamId);

        return mapToDTO(task);
    }

    /**
     * Delete a task.
     */
    @Transactional
    public void deleteTask(String teamId, String taskId) {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();

        Team team = teamRepository.findByIdAndTenantId(teamId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));

        verifyTaskDeletePermission(team, userId);

        Task task = taskRepository.findByIdAndTenantIdAndTeamId(taskId, tenantId, teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));

        // Remove from parent's subtask list if applicable
        if (task.getParentTaskId() != null) {
            taskRepository.findByIdAndTenantIdAndTeamId(task.getParentTaskId(), tenantId, teamId)
                    .ifPresent(parent -> {
                        if (parent.getSubtaskIds() != null) {
                            parent.getSubtaskIds().remove(taskId);
                            taskRepository.save(parent);
                        }
                    });
        }

        // Delete subtasks recursively
        if (task.getSubtaskIds() != null) {
            for (String subtaskId : task.getSubtaskIds()) {
                taskRepository.deleteById(subtaskId);
            }
        }

        taskRepository.delete(task);

        log.info("Deleted task: {} from team: {}", taskId, teamId);
    }

    // ============== KANBAN OPERATIONS ==============

    /**
     * Move a task to a new column/position (drag-drop).
     */
    @Transactional
    public TaskDTO moveTask(String teamId, String taskId, MoveTaskRequest request) {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();

        Team team = teamRepository.findByIdAndTenantId(teamId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));

        verifyTaskEditPermission(team, userId);

        Task task = taskRepository.findByIdAndTenantIdAndTeamId(taskId, tenantId, teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));

        String oldColumn = task.getBoardColumnId() != null ? task.getBoardColumnId() : task.getStatus().name();
        String newColumn = request.getBoardColumnId() != null ? request.getBoardColumnId() : oldColumn;

        // Update column position
        task.setBoardColumnId(newColumn);
        task.setSortOrder(request.getSortOrder());

        // Optionally update status
        if (request.getNewStatus() != null) {
            Task.TaskStatus newStatus = Task.TaskStatus.valueOf(request.getNewStatus());
            updateTaskStatus(task, newStatus, userId);
        }

        task.setLastModifiedBy(userId);
        task.setUpdatedAt(Instant.now());

        task = taskRepository.save(task);

        // Reorder other tasks in columns if needed
        // (simplified: just save this task's position)

        log.info("Moved task {} from {} to {} at position {}",
                taskId, oldColumn, newColumn, request.getSortOrder());

        return mapToDTO(task);
    }

    // ============== STATUS TRANSITIONS ==============

    /**
     * Update task status with proper lifecycle handling.
     */
    private void updateTaskStatus(Task task, Task.TaskStatus newStatus, String userId) {
        Task.TaskStatus oldStatus = task.getStatus();

        if (oldStatus == newStatus) {
            return;
        }

        // Handle completion
        if (newStatus == Task.TaskStatus.DONE && oldStatus != Task.TaskStatus.DONE) {
            task.setCompletedAt(Instant.now());
        } else if (newStatus != Task.TaskStatus.DONE && oldStatus == Task.TaskStatus.DONE) {
            task.setCompletedAt(null);
        }

        task.setStatus(newStatus);

        // Publish status change event
        kafkaTemplate.send("teamsync.tasks.status_changed", task.getId(), Map.of(
                "tenantId", task.getTenantId(),
                "teamId", task.getTeamId(),
                "taskId", task.getId(),
                "oldStatus", oldStatus.name(),
                "newStatus", newStatus.name(),
                "changedBy", userId
        ));
    }

    // ============== CHECKLIST OPERATIONS ==============

    /**
     * Toggle a checklist item.
     */
    @Transactional
    public TaskDTO toggleChecklistItem(String teamId, String taskId, String itemId) {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();

        Team team = teamRepository.findByIdAndTenantId(teamId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));

        Task task = taskRepository.findByIdAndTenantIdAndTeamId(taskId, tenantId, teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));

        // Assignee or task editor can toggle
        boolean isAssignee = userId.equals(task.getAssigneeId());
        if (!isAssignee) {
            verifyTaskEditPermission(team, userId);
        }

        if (task.getChecklist() == null) {
            throw new ResourceNotFoundException("Task has no checklist");
        }

        Task.ChecklistItem item = task.getChecklist().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Checklist item not found: " + itemId));

        // Toggle
        boolean newCompleted = !Boolean.TRUE.equals(item.getCompleted());
        item.setCompleted(newCompleted);
        if (newCompleted) {
            item.setCompletedAt(Instant.now());
            item.setCompletedBy(userId);
        } else {
            item.setCompletedAt(null);
            item.setCompletedBy(null);
        }

        task.setUpdatedAt(Instant.now());
        task = taskRepository.save(task);

        return mapToDTO(task);
    }

    // ============== HELPER METHODS ==============

    private void verifyMembership(Team team, String userId) {
        team.getMembers().stream()
                .filter(m -> m.getUserId().equals(userId) && m.getStatus() == Team.MemberStatus.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("Not a team member"));
    }

    private void verifyTaskViewPermission(Team team, String userId) {
        Team.TeamMember member = team.getMembers().stream()
                .filter(m -> m.getUserId().equals(userId) && m.getStatus() == Team.MemberStatus.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("Not a team member"));

        if (!member.getPermissions().contains(TeamPermission.TASK_VIEW.name())) {
            throw new AccessDeniedException("Permission denied: TASK_VIEW");
        }
    }

    private void verifyTaskCreatePermission(Team team, String userId) {
        Team.TeamMember member = team.getMembers().stream()
                .filter(m -> m.getUserId().equals(userId) && m.getStatus() == Team.MemberStatus.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("Not a team member"));

        if (!member.getPermissions().contains(TeamPermission.TASK_CREATE.name())) {
            throw new AccessDeniedException("Permission denied: TASK_CREATE");
        }
    }

    private void verifyTaskEditPermission(Team team, String userId) {
        Team.TeamMember member = team.getMembers().stream()
                .filter(m -> m.getUserId().equals(userId) && m.getStatus() == Team.MemberStatus.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("Not a team member"));

        if (!member.getPermissions().contains(TeamPermission.TASK_EDIT.name())) {
            throw new AccessDeniedException("Permission denied: TASK_EDIT");
        }
    }

    private void verifyTaskDeletePermission(Team team, String userId) {
        Team.TeamMember member = team.getMembers().stream()
                .filter(m -> m.getUserId().equals(userId) && m.getStatus() == Team.MemberStatus.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("Not a team member"));

        if (!member.getPermissions().contains(TeamPermission.TASK_DELETE.name())) {
            throw new AccessDeniedException("Permission denied: TASK_DELETE");
        }
    }

    private void verifyTaskAssignPermission(Team team, String userId) {
        Team.TeamMember member = team.getMembers().stream()
                .filter(m -> m.getUserId().equals(userId) && m.getStatus() == Team.MemberStatus.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("Not a team member"));

        if (!member.getPermissions().contains(TeamPermission.TASK_ASSIGN.name())) {
            throw new AccessDeniedException("Permission denied: TASK_ASSIGN");
        }
    }

    private TaskDTO mapToDTO(Task task) {
        List<TaskDTO.ChecklistItemDTO> checklistDTOs = null;
        if (task.getChecklist() != null) {
            checklistDTOs = task.getChecklist().stream()
                    .map(item -> TaskDTO.ChecklistItemDTO.builder()
                            .id(item.getId())
                            .text(item.getText())
                            .completed(item.getCompleted())
                            .completedAt(item.getCompletedAt())
                            .completedBy(item.getCompletedBy())
                            .sortOrder(item.getSortOrder())
                            .build())
                    .collect(Collectors.toList());
        }

        return TaskDTO.builder()
                .id(task.getId())
                .tenantId(task.getTenantId())
                .teamId(task.getTeamId())
                .projectId(task.getProjectId())
                .title(task.getTitle())
                .description(task.getDescription())
                .priority(task.getPriority())
                .status(task.getStatus())
                .boardColumnId(task.getBoardColumnId())
                .sortOrder(task.getSortOrder())
                .assigneeId(task.getAssigneeId())
                .assigneeName(task.getAssigneeName())
                .watcherIds(task.getWatcherIds())
                .dueDate(task.getDueDate())
                .startDate(task.getStartDate())
                .completedAt(task.getCompletedAt())
                .estimatedHours(task.getEstimatedHours())
                .actualHours(task.getActualHours())
                .parentTaskId(task.getParentTaskId())
                .subtaskIds(task.getSubtaskIds())
                .subtaskCount(task.getSubtaskIds() != null ? task.getSubtaskIds().size() : 0)
                .blockedByTaskIds(task.getBlockedByTaskIds())
                .attachmentIds(task.getAttachmentIds())
                .attachmentCount(task.getAttachmentIds() != null ? task.getAttachmentIds().size() : 0)
                .labels(task.getLabels())
                .color(task.getColor())
                .checklist(checklistDTOs)
                .checklistProgress(task.getChecklistProgress())
                .commentCount(task.getCommentCount())
                .isPinned(task.getIsPinned())
                .isOverdue(task.isOverdue())
                .isBlocked(task.isBlocked())
                .createdBy(task.getCreatedBy())
                .createdAt(task.getCreatedAt())
                .lastModifiedBy(task.getLastModifiedBy())
                .updatedAt(task.getUpdatedAt())
                .build();
    }

    private void publishTaskCreated(Task task, Team team) {
        kafkaTemplate.send("teamsync.tasks.created", task.getId(), Map.of(
                "tenantId", task.getTenantId(),
                "teamId", task.getTeamId(),
                "taskId", task.getId(),
                "title", task.getTitle(),
                "assigneeId", task.getAssigneeId() != null ? task.getAssigneeId() : "",
                "createdBy", task.getCreatedBy()
        ));
    }

    private void publishTaskUpdated(Task task) {
        kafkaTemplate.send("teamsync.tasks.updated", task.getId(), Map.of(
                "tenantId", task.getTenantId(),
                "teamId", task.getTeamId(),
                "taskId", task.getId(),
                "title", task.getTitle(),
                "modifiedBy", task.getLastModifiedBy()
        ));
    }
}
