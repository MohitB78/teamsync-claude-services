package com.teamsync.team.service;

import com.teamsync.common.exception.AccessDeniedException;
import com.teamsync.common.exception.ResourceNotFoundException;
import com.teamsync.team.dto.portal.*;
import com.teamsync.team.model.*;
import com.teamsync.team.model.Team.TeamMember;
import com.teamsync.team.model.Team.MemberStatus;
import com.teamsync.team.model.Team.MemberType;
import com.teamsync.team.model.Task.TaskStatus;
import com.teamsync.team.repository.*;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for portal team operations.
 * External users have limited access to teams and their resources.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PortalTeamService {

    private final TeamRepository teamRepository;
    private final TaskRepository taskRepository;
    private final TaskCommentRepository taskCommentRepository;
    private final PortalAuthService portalAuthService;

    /**
     * Get teams accessible to the current portal user.
     */
    public List<PortalTeamDTO> getMyTeams(String accessToken) {
        Claims claims = portalAuthService.parseAccessToken(accessToken);
        String tenantId = claims.get("tenantId", String.class);
        String userId = claims.getSubject();

        List<Team> teams = teamRepository.findByTenantIdAndMembersUserId(tenantId, userId);

        return teams.stream()
                .map(team -> mapToPortalTeamDTO(team, userId))
                .collect(Collectors.toList());
    }

    /**
     * Get a specific team.
     */
    public PortalTeamDTO getTeam(String accessToken, String teamId) {
        TeamContext ctx = getTeamContext(accessToken, teamId);
        verifyAccess(ctx, TeamPermission.TEAM_VIEW);

        return mapToPortalTeamDTO(ctx.team, ctx.userId);
    }

    /**
     * Get team members.
     */
    public List<PortalMemberDTO> getTeamMembers(String accessToken, String teamId) {
        TeamContext ctx = getTeamContext(accessToken, teamId);
        verifyAccess(ctx, TeamPermission.MEMBER_VIEW);

        return ctx.team.getMembers().stream()
                .filter(m -> m.getStatus() == MemberStatus.ACTIVE)
                .map(this::mapToPortalMemberDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get tasks assigned to the current user.
     */
    public List<PortalTaskDTO> getMyTasks(String accessToken, String teamId) {
        TeamContext ctx = getTeamContext(accessToken, teamId);
        verifyAccess(ctx, TeamPermission.TASK_VIEW);

        // External users can only see tasks assigned to them
        List<Task> tasks = taskRepository.findByTenantIdAndTeamIdAndAssigneeId(
                ctx.tenantId, teamId, ctx.userId);

        return tasks.stream()
                .map(this::mapToPortalTaskDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get a specific task (must be assigned to user).
     */
    public PortalTaskDTO getTask(String accessToken, String teamId, String taskId) {
        TeamContext ctx = getTeamContext(accessToken, teamId);
        verifyAccess(ctx, TeamPermission.TASK_VIEW);

        Task task = taskRepository.findByIdAndTenantIdAndTeamId(taskId, ctx.tenantId, teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        // External users can only view tasks assigned to them
        if (!ctx.userId.equals(task.getAssigneeId())) {
            throw new AccessDeniedException("You can only view tasks assigned to you");
        }

        return mapToPortalTaskDTO(task);
    }

    /**
     * Get comments for a task.
     */
    public List<PortalCommentDTO> getTaskComments(String accessToken, String teamId, String taskId) {
        TeamContext ctx = getTeamContext(accessToken, teamId);
        verifyAccess(ctx, TeamPermission.TASK_VIEW);

        Task task = taskRepository.findByIdAndTenantIdAndTeamId(taskId, ctx.tenantId, teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        // External users can only view comments on tasks assigned to them
        if (!ctx.userId.equals(task.getAssigneeId())) {
            throw new AccessDeniedException("You can only view comments on tasks assigned to you");
        }

        List<TaskComment> comments = taskCommentRepository.findByTenantIdAndTaskIdOrderByCreatedAtAsc(
                ctx.tenantId, taskId);

        return comments.stream()
                .map(this::mapToPortalCommentDTO)
                .collect(Collectors.toList());
    }

    /**
     * Add a comment to a task.
     */
    public PortalCommentDTO addComment(String accessToken, String teamId, String taskId, String content) {
        TeamContext ctx = getTeamContext(accessToken, teamId);
        verifyAccess(ctx, TeamPermission.TASK_VIEW);

        Task task = taskRepository.findByIdAndTenantIdAndTeamId(taskId, ctx.tenantId, teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        // External users can only comment on tasks assigned to them
        if (!ctx.userId.equals(task.getAssigneeId())) {
            throw new AccessDeniedException("You can only comment on tasks assigned to you");
        }

        TaskComment comment = TaskComment.builder()
                .tenantId(ctx.tenantId)
                .teamId(teamId)
                .taskId(taskId)
                .content(content)
                .authorId(ctx.userId)
                .authorName(ctx.displayName)
                .isEdited(false)
                .createdAt(Instant.now())
                .build();

        TaskComment saved = taskCommentRepository.save(comment);

        // Update task comment count
        task.setCommentCount(task.getCommentCount() + 1);
        taskRepository.save(task);

        log.info("External user {} added comment to task {}", ctx.userId, taskId);

        return mapToPortalCommentDTO(saved);
    }

    /**
     * Update own comment.
     */
    public PortalCommentDTO updateComment(String accessToken, String teamId, String taskId,
                                          String commentId, String content) {
        TeamContext ctx = getTeamContext(accessToken, teamId);

        TaskComment comment = taskCommentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));

        // Can only edit own comments
        if (!comment.getAuthorId().equals(ctx.userId)) {
            throw new AccessDeniedException("You can only edit your own comments");
        }

        comment.setContent(content);
        comment.setIsEdited(true);
        comment.setUpdatedAt(Instant.now());

        TaskComment saved = taskCommentRepository.save(comment);

        return mapToPortalCommentDTO(saved);
    }

    /**
     * Delete own comment.
     */
    public void deleteComment(String accessToken, String teamId, String taskId, String commentId) {
        TeamContext ctx = getTeamContext(accessToken, teamId);

        TaskComment comment = taskCommentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));

        // Can only delete own comments
        if (!comment.getAuthorId().equals(ctx.userId)) {
            throw new AccessDeniedException("You can only delete your own comments");
        }

        taskCommentRepository.delete(comment);

        // Update task comment count
        Task task = taskRepository.findById(taskId).orElse(null);
        if (task != null) {
            task.setCommentCount(Math.max(0, task.getCommentCount() - 1));
            taskRepository.save(task);
        }

        log.info("External user {} deleted comment {}", ctx.userId, commentId);
    }

    // ========================================
    // Helper Methods
    // ========================================

    private TeamContext getTeamContext(String accessToken, String teamId) {
        Claims claims = portalAuthService.parseAccessToken(accessToken);
        String tenantId = claims.get("tenantId", String.class);
        String userId = claims.getSubject();
        String displayName = claims.get("displayName", String.class);

        Team team = teamRepository.findByIdAndTenantId(teamId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found"));

        TeamMember member = team.getMembers().stream()
                .filter(m -> m.getUserId().equals(userId) && m.getStatus() == MemberStatus.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("You are not a member of this team"));

        return new TeamContext(tenantId, userId, displayName, team, member);
    }

    private void verifyAccess(TeamContext ctx, TeamPermission permission) {
        if (!ctx.member.getPermissions().contains(permission.name())) {
            throw new AccessDeniedException("You don't have permission to perform this action");
        }
    }

    private PortalTeamDTO mapToPortalTeamDTO(Team team, String userId) {
        TeamMember member = team.getMembers().stream()
                .filter(m -> m.getUserId().equals(userId))
                .findFirst()
                .orElse(null);

        return PortalTeamDTO.builder()
                .id(team.getId())
                .name(team.getName())
                .description(team.getDescription())
                .avatar(team.getAvatar())
                .memberCount(team.getMemberCount())
                .myRole(member != null ? member.getRoleName() : null)
                .myPermissions(member != null
                        ? member.getPermissions()
                        : Set.of())
                .build();
    }

    private PortalMemberDTO mapToPortalMemberDTO(TeamMember member) {
        return PortalMemberDTO.builder()
                .userId(member.getUserId())
                .displayName(member.getEmail()) // Use email as display name for external users
                .roleName(member.getRoleName())
                .isExternal(member.getMemberType() == MemberType.EXTERNAL)
                .build();
    }

    private PortalTaskDTO mapToPortalTaskDTO(Task task) {
        boolean isOverdue = task.getDueDate() != null
                && task.getDueDate().isBefore(Instant.now())
                && task.getStatus() != TaskStatus.DONE
                && task.getStatus() != TaskStatus.CANCELLED;

        int checklistProgress = 0;
        if (task.getChecklist() != null && !task.getChecklist().isEmpty()) {
            long completed = task.getChecklist().stream()
                    .filter(item -> Boolean.TRUE.equals(item.getCompleted()))
                    .count();
            checklistProgress = (int) ((completed * 100) / task.getChecklist().size());
        }

        return PortalTaskDTO.builder()
                .id(task.getId())
                .teamId(task.getTeamId())
                .title(task.getTitle())
                .description(task.getDescription())
                .priority(task.getPriority())
                .status(task.getStatus())
                .dueDate(task.getDueDate())
                .assigneeId(task.getAssigneeId())
                .assigneeName(task.getAssigneeName())
                .labels(task.getLabels())
                .checklistProgress(checklistProgress)
                .commentCount(task.getCommentCount())
                .isOverdue(isOverdue)
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }

    private PortalCommentDTO mapToPortalCommentDTO(TaskComment comment) {
        return PortalCommentDTO.builder()
                .id(comment.getId())
                .taskId(comment.getTaskId())
                .content(comment.getContent())
                .authorId(comment.getAuthorId())
                .authorName(comment.getAuthorName())
                .authorAvatar(comment.getAuthorAvatarUrl())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .isEdited(comment.getIsEdited())
                .build();
    }

    /**
     * Context for team access validation.
     */
    private record TeamContext(
            String tenantId,
            String userId,
            String displayName,
            Team team,
            TeamMember member
    ) {}
}
