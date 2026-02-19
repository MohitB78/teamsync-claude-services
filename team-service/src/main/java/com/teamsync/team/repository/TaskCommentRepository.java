package com.teamsync.team.repository;

import com.teamsync.team.model.TaskComment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for TaskComment entities.
 */
@Repository
public interface TaskCommentRepository extends MongoRepository<TaskComment, String> {

    // ============== SINGLE COMMENT QUERIES ==============

    /**
     * Find comment by ID with tenant isolation.
     */
    Optional<TaskComment> findByIdAndTenantId(String id, String tenantId);

    /**
     * Find comment by ID with task isolation.
     */
    Optional<TaskComment> findByIdAndTenantIdAndTaskId(String id, String tenantId, String taskId);

    // ============== LIST QUERIES ==============

    /**
     * Find comments for a task (newest first).
     */
    List<TaskComment> findByTenantIdAndTaskIdOrderByCreatedAtDesc(
            String tenantId, String taskId, Pageable pageable);

    /**
     * Find comments for a task (oldest first) with pagination.
     */
    List<TaskComment> findByTenantIdAndTaskIdOrderByCreatedAtAsc(
            String tenantId, String taskId, Pageable pageable);

    /**
     * Find all comments for a task (oldest first).
     */
    List<TaskComment> findByTenantIdAndTaskIdOrderByCreatedAtAsc(
            String tenantId, String taskId);

    /**
     * Find comments by a specific author.
     */
    List<TaskComment> findByTenantIdAndTaskIdAndAuthorId(
            String tenantId, String taskId, String authorId);

    /**
     * Find comments mentioning a user.
     */
    List<TaskComment> findByTenantIdAndMentionedUserIdsContaining(
            String tenantId, String userId, Pageable pageable);

    // ============== COUNTS ==============

    /**
     * Count comments for a task.
     */
    long countByTenantIdAndTaskId(String tenantId, String taskId);

    // ============== DELETION ==============

    /**
     * Delete all comments for a task (when task is deleted).
     */
    void deleteByTenantIdAndTaskId(String tenantId, String taskId);

    /**
     * Delete all comments for a team (when team is deleted).
     */
    void deleteByTenantIdAndTeamId(String tenantId, String teamId);
}
