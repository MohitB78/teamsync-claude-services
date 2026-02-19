package com.teamsync.team.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for moving a task in Kanban view.
 * Used for drag-and-drop operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MoveTaskRequest {

    /**
     * Target column ID.
     * If null, uses default column based on status.
     */
    private String boardColumnId;

    /**
     * New position within the column.
     * Lower numbers appear first.
     */
    @NotNull(message = "Sort order is required")
    private Integer sortOrder;

    /**
     * Optional: Update status when moving between columns.
     * Useful when columns map to statuses.
     */
    private String newStatus;
}
