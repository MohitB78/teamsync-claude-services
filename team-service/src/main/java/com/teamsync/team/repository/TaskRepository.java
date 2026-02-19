package com.teamsync.team.repository;

import com.teamsync.team.model.Task;
import com.teamsync.team.model.Task.TaskStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Task entities.
 * All queries MUST filter by tenantId for multi-tenant isolation.
 */
@Repository
public interface TaskRepository extends MongoRepository<Task, String> {

    // ============== SINGLE TASK QUERIES ==============

    /**
     * Find task by ID with tenant and team isolation.
     */
    Optional<Task> findByIdAndTenantIdAndTeamId(String id, String tenantId, String teamId);

    /**
     * Find task by ID with tenant isolation only.
     */
    Optional<Task> findByIdAndTenantId(String id, String tenantId);

    /**
     * Check if task exists.
     */
    boolean existsByIdAndTenantIdAndTeamId(String id, String tenantId, String teamId);

    // ============== TEAM TASK QUERIES ==============

    /**
     * Find all tasks in a team.
     */
    List<Task> findByTenantIdAndTeamId(String tenantId, String teamId, Pageable pageable);

    /**
     * Find tasks in a team by status.
     */
    List<Task> findByTenantIdAndTeamIdAndStatus(String tenantId, String teamId, TaskStatus status, Pageable pageable);

    /**
     * Find tasks assigned to a user in a team (all tasks, no pagination).
     */
    List<Task> findByTenantIdAndTeamIdAndAssigneeId(String tenantId, String teamId, String assigneeId);

    /**
     * Find tasks assigned to a user in a team with pagination.
     * Note: Pageable parameter handles pagination automatically.
     */
    @Query("{ 'tenantId': ?0, 'teamId': ?1, 'assigneeId': ?2 }")
    List<Task> findByTenantIdAndTeamIdAndAssigneeIdPaged(String tenantId, String teamId, String assigneeId, Pageable pageable);

    /**
     * Find tasks assigned to a user across all teams.
     */
    List<Task> findByTenantIdAndAssigneeId(String tenantId, String assigneeId, Pageable pageable);

    /**
     * Find tasks in a team by project ID.
     */
    List<Task> findByTenantIdAndTeamIdAndProjectId(String tenantId, String teamId, String projectId, Pageable pageable);

    /**
     * Find tasks in a team ordered by creation date (newest first).
     */
    List<Task> findByTenantIdAndTeamIdOrderByCreatedAtDesc(String tenantId, String teamId, Pageable pageable);

    /**
     * Find tasks not in specified statuses.
     */
    @Query("{ 'tenantId': ?0, 'teamId': ?1, 'status': { $nin: ?2 } }")
    List<Task> findByTenantIdAndTeamIdAndStatusNotIn(String tenantId, String teamId, List<TaskStatus> excludedStatuses, Pageable pageable);

    /**
     * Count tasks in a column.
     */
    @Query(value = "{ 'tenantId': ?0, 'teamId': ?1, $or: [ { 'boardColumnId': ?2 }, { $and: [ { 'boardColumnId': null }, { 'status': ?2 } ] } ] }", count = true)
    Long countByTenantIdAndTeamIdAndBoardColumnId(String tenantId, String teamId, String columnId);

    // ============== KANBAN BOARD QUERIES ==============

    /**
     * Find tasks in a specific Kanban column.
     */
    @Query("{ 'tenantId': ?0, 'teamId': ?1, 'boardColumnId': ?2 }")
    List<Task> findByTenantIdAndTeamIdAndBoardColumnIdOrderBySortOrderAsc(
            String tenantId, String teamId, String boardColumnId);

    /**
     * Find tasks by status for Kanban (default columns use status).
     */
    List<Task> findByTenantIdAndTeamIdAndStatusOrderBySortOrderAsc(
            String tenantId, String teamId, TaskStatus status);

    /**
     * Get max sort order in a column (for inserting at end).
     */
    @Query(value = "{ 'tenantId': ?0, 'teamId': ?1, 'boardColumnId': ?2 }",
           sort = "{ 'sortOrder': -1 }")
    Optional<Task> findTopByTenantIdAndTeamIdAndBoardColumnIdOrderBySortOrderDesc(
            String tenantId, String teamId, String boardColumnId);

    // ============== DUE DATE QUERIES ==============

    /**
     * Find overdue tasks in a team.
     */
    @Query("{ 'tenantId': ?0, 'teamId': ?1, 'dueDate': { $lt: ?2 }, 'status': { $nin: ['DONE', 'CANCELLED'] } }")
    List<Task> findOverdueTasks(String tenantId, String teamId, Instant now);

    /**
     * Find tasks due within a date range.
     */
    @Query("{ 'tenantId': ?0, 'teamId': ?1, 'dueDate': { $gte: ?2, $lte: ?3 }, 'status': { $nin: ['DONE', 'CANCELLED'] } }")
    List<Task> findTasksDueInRange(String tenantId, String teamId, Instant start, Instant end);

    /**
     * Find tasks due soon for a user (for notifications).
     */
    @Query("{ 'tenantId': ?0, 'assigneeId': ?1, 'dueDate': { $gte: ?2, $lte: ?3 }, 'status': { $nin: ['DONE', 'CANCELLED'] } }")
    List<Task> findUserTasksDueSoon(String tenantId, String assigneeId, Instant start, Instant end);

    // ============== SUBTASK QUERIES ==============

    /**
     * Find subtasks of a parent task.
     */
    List<Task> findByTenantIdAndTeamIdAndParentTaskId(String tenantId, String teamId, String parentTaskId);

    /**
     * Count subtasks of a parent task.
     */
    long countByTenantIdAndTeamIdAndParentTaskId(String tenantId, String teamId, String parentTaskId);

    // ============== CURSOR-BASED PAGINATION ==============

    /**
     * Find tasks with cursor-based pagination.
     */
    @Query("{ 'tenantId': ?0, 'teamId': ?1, '_id': { $gt: ?2 } }")
    List<Task> findByTenantIdAndTeamIdAfterCursor(
            String tenantId, String teamId, String cursor, Pageable pageable);

    // ============== SEARCH ==============

    /**
     * Search tasks by title in a team.
     */
    @Query("{ 'tenantId': ?0, 'teamId': ?1, 'title': { $regex: ?2, $options: 'i' } }")
    List<Task> searchByTitle(String tenantId, String teamId, String titlePattern, Pageable pageable);

    // ============== COUNTS ==============

    /**
     * Count tasks in a team.
     */
    long countByTenantIdAndTeamId(String tenantId, String teamId);

    /**
     * Count tasks by status in a team.
     */
    long countByTenantIdAndTeamIdAndStatus(String tenantId, String teamId, TaskStatus status);

    /**
     * Count tasks assigned to a user in a team.
     */
    long countByTenantIdAndTeamIdAndAssigneeId(String tenantId, String teamId, String assigneeId);

    // ============== BULK UPDATES ==============

    /**
     * Update comment count (called when comment is added/removed).
     */
    @Query("{ '_id': ?0, 'tenantId': ?1 }")
    @Update("{ '$inc': { 'commentCount': ?2 } }")
    void incrementCommentCount(String taskId, String tenantId, int delta);
}
