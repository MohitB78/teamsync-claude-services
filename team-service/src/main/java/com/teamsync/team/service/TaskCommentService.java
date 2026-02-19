package com.teamsync.team.service;

import com.teamsync.common.context.TenantContext;
import com.teamsync.common.exception.AccessDeniedException;
import com.teamsync.common.exception.ResourceNotFoundException;
import com.teamsync.team.dto.CreateTaskCommentRequest;
import com.teamsync.team.dto.TaskCommentDTO;
import com.teamsync.team.model.Task;
import com.teamsync.team.model.TaskComment;
import com.teamsync.team.model.Team;
import com.teamsync.team.model.TeamPermission;
import com.teamsync.team.repository.TaskCommentRepository;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for managing task comments.
 *
 * Responsibilities:
 * - Comment CRUD operations
 * - @mention handling
 * - Comment notifications
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TaskCommentService {

    private final TaskCommentRepository commentRepository;
    private final TaskRepository taskRepository;
    private final TeamRepository teamRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final int DEFAULT_PAGE_SIZE = 50;

    // ============== COMMENT QUERIES ==============

    /**
     * Get comments for a task (paginated, newest first).
     */
    public List<TaskCommentDTO> getComments(String teamId, String taskId, int page, int limit) {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();

        Team team = teamRepository.findByIdAndTenantId(teamId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));

        verifyTaskViewPermission(team, userId);

        // Verify task exists
        taskRepository.findByIdAndTenantIdAndTeamId(taskId, tenantId, teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));

        Pageable pageable = PageRequest.of(page, Math.min(limit, DEFAULT_PAGE_SIZE));
        List<TaskComment> comments = commentRepository.findByTenantIdAndTaskIdOrderByCreatedAtDesc(
                tenantId, taskId, pageable);

        return comments.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get comment count for a task.
     */
    public long getCommentCount(String teamId, String taskId) {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();

        Team team = teamRepository.findByIdAndTenantId(teamId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));

        verifyTaskViewPermission(team, userId);

        return commentRepository.countByTenantIdAndTaskId(tenantId, taskId);
    }

    // ============== COMMENT CRUD ==============

    /**
     * Create a new comment on a task.
     * External users can only comment on tasks assigned to them.
     */
    @Transactional
    public TaskCommentDTO createComment(String teamId, String taskId, CreateTaskCommentRequest request) {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();

        Team team = teamRepository.findByIdAndTenantId(teamId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));

        Task task = taskRepository.findByIdAndTenantIdAndTeamId(taskId, tenantId, teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));

        // Get member to check permissions
        Team.TeamMember member = team.getMembers().stream()
                .filter(m -> m.getUserId().equals(userId) && m.getStatus() == Team.MemberStatus.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("Not a team member"));

        // External users can only comment on tasks assigned to them
        if (member.getMemberType() == Team.MemberType.EXTERNAL) {
            if (!userId.equals(task.getAssigneeId())) {
                throw new AccessDeniedException("External users can only comment on tasks assigned to them");
            }
            if (!member.getPermissions().contains(TeamPermission.TASK_COMMENT.name())) {
                throw new AccessDeniedException("Permission denied: TASK_COMMENT");
            }
        } else {
            // Internal users need TASK_VIEW or TASK_COMMENT
            if (!member.getPermissions().contains(TeamPermission.TASK_VIEW.name()) &&
                !member.getPermissions().contains(TeamPermission.TASK_COMMENT.name())) {
                throw new AccessDeniedException("Permission denied to comment");
            }
        }

        TaskComment comment = TaskComment.builder()
                .tenantId(tenantId)
                .taskId(taskId)
                .teamId(teamId)
                .content(request.getContent())
                .authorId(userId)
                .authorName(member.getEmail())
                .mentionedUserIds(request.getMentionedUserIds())
                .attachmentIds(request.getAttachmentIds())
                .parentCommentId(request.getParentCommentId())
                .replyCount(0)
                .isEdited(false)
                .isPinned(false)
                .createdAt(Instant.now())
                .build();

        comment = commentRepository.save(comment);

        // Update task's comment count
        task.setCommentCount((task.getCommentCount() != null ? task.getCommentCount() : 0) + 1);
        task.setUpdatedAt(Instant.now());
        taskRepository.save(task);

        // Update parent comment's reply count if this is a reply
        if (request.getParentCommentId() != null) {
            commentRepository.findByIdAndTenantId(request.getParentCommentId(), tenantId)
                    .ifPresent(parent -> {
                        parent.setReplyCount((parent.getReplyCount() != null ? parent.getReplyCount() : 0) + 1);
                        commentRepository.save(parent);
                    });
        }

        // Publish event for notifications
        publishCommentCreated(comment, task, team);

        log.info("Created comment on task {} in team {}", taskId, teamId);

        return mapToDTO(comment);
    }

    /**
     * Update a comment.
     * Only the author can update their comment.
     */
    @Transactional
    public TaskCommentDTO updateComment(String teamId, String taskId, String commentId, String newContent) {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();

        // Verify task exists in team
        taskRepository.findByIdAndTenantIdAndTeamId(taskId, tenantId, teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));

        TaskComment comment = commentRepository.findByIdAndTenantIdAndTaskId(commentId, tenantId, taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + commentId));

        // Only author can edit
        if (!comment.getAuthorId().equals(userId)) {
            throw new AccessDeniedException("Only the author can edit this comment");
        }

        comment.setContent(newContent);
        comment.setIsEdited(true);
        comment.setUpdatedAt(Instant.now());

        comment = commentRepository.save(comment);

        log.info("Updated comment {} on task {}", commentId, taskId);

        return mapToDTO(comment);
    }

    /**
     * Delete a comment.
     * Author or users with TASK_EDIT permission can delete.
     */
    @Transactional
    public void deleteComment(String teamId, String taskId, String commentId) {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();

        Team team = teamRepository.findByIdAndTenantId(teamId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));

        Task task = taskRepository.findByIdAndTenantIdAndTeamId(taskId, tenantId, teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));

        TaskComment comment = commentRepository.findByIdAndTenantIdAndTaskId(commentId, tenantId, taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + commentId));

        // Author can delete their own comment, otherwise need TASK_EDIT
        if (!comment.getAuthorId().equals(userId)) {
            verifyTaskEditPermission(team, userId);
        }

        // Update parent comment's reply count
        if (comment.getParentCommentId() != null) {
            commentRepository.findByIdAndTenantId(comment.getParentCommentId(), tenantId)
                    .ifPresent(parent -> {
                        parent.setReplyCount(Math.max(0, (parent.getReplyCount() != null ? parent.getReplyCount() : 0) - 1));
                        commentRepository.save(parent);
                    });
        }

        commentRepository.delete(comment);

        // Update task's comment count
        task.setCommentCount(Math.max(0, (task.getCommentCount() != null ? task.getCommentCount() : 0) - 1));
        task.setUpdatedAt(Instant.now());
        taskRepository.save(task);

        log.info("Deleted comment {} from task {}", commentId, taskId);
    }

    /**
     * Add a reaction to a comment.
     */
    @Transactional
    public TaskCommentDTO addReaction(String teamId, String taskId, String commentId, String emoji) {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();

        Team team = teamRepository.findByIdAndTenantId(teamId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));

        verifyMembership(team, userId);

        TaskComment comment = commentRepository.findByIdAndTenantIdAndTaskId(commentId, tenantId, taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + commentId));

        if (comment.getReactions() == null) {
            comment.setReactions(new java.util.HashMap<>());
        }

        // Add user to the list of users who reacted with this emoji
        comment.getReactions().computeIfAbsent(emoji, k -> new java.util.ArrayList<>());
        if (!comment.getReactions().get(emoji).contains(userId)) {
            comment.getReactions().get(emoji).add(userId);
        }
        comment = commentRepository.save(comment);

        return mapToDTO(comment);
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

    private void verifyTaskEditPermission(Team team, String userId) {
        Team.TeamMember member = team.getMembers().stream()
                .filter(m -> m.getUserId().equals(userId) && m.getStatus() == Team.MemberStatus.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("Not a team member"));

        if (!member.getPermissions().contains(TeamPermission.TASK_EDIT.name())) {
            throw new AccessDeniedException("Permission denied: TASK_EDIT");
        }
    }

    private TaskCommentDTO mapToDTO(TaskComment comment) {
        // Convert reactions from Map<String, List<String>> to Map<String, Integer> (counts)
        Map<String, Integer> reactionCounts = null;
        if (comment.getReactions() != null) {
            reactionCounts = comment.getReactions().entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(
                            Map.Entry::getKey,
                            e -> e.getValue() != null ? e.getValue().size() : 0
                    ));
        }

        return TaskCommentDTO.builder()
                .id(comment.getId())
                .tenantId(comment.getTenantId())
                .taskId(comment.getTaskId())
                .teamId(comment.getTeamId())
                .content(comment.getContent())
                .authorId(comment.getAuthorId())
                .authorName(comment.getAuthorName())
                .authorAvatar(comment.getAuthorAvatarUrl())
                .mentionedUserIds(comment.getMentionedUserIds())
                .attachmentIds(comment.getAttachmentIds())
                .reactions(reactionCounts)
                .parentCommentId(comment.getParentCommentId())
                .replyCount(comment.getReplyCount())
                .isEdited(comment.getIsEdited())
                .isPinned(comment.getIsPinned())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }

    private void publishCommentCreated(TaskComment comment, Task task, Team team) {
        kafkaTemplate.send("teamsync.tasks.comment_created", comment.getId(), Map.of(
                "tenantId", comment.getTenantId(),
                "teamId", comment.getTeamId(),
                "taskId", comment.getTaskId(),
                "taskTitle", task.getTitle(),
                "commentId", comment.getId(),
                "authorId", comment.getAuthorId(),
                "authorName", comment.getAuthorName(),
                "mentionedUserIds", comment.getMentionedUserIds() != null ? comment.getMentionedUserIds() : List.of(),
                "assigneeId", task.getAssigneeId() != null ? task.getAssigneeId() : ""
        ));
    }
}
