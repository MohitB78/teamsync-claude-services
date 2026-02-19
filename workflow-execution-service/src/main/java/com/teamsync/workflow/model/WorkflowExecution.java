package com.teamsync.workflow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "workflow_executions")
@CompoundIndexes({
        @CompoundIndex(name = "tenant_idx", def = "{'tenantId': 1}"),
        @CompoundIndex(name = "tenant_document_idx", def = "{'tenantId': 1, 'documentId': 1}"),
        @CompoundIndex(name = "tenant_status_idx", def = "{'tenantId': 1, 'status': 1}")
})
public class WorkflowExecution {

    @Id
    private String id;

    private String tenantId;
    private String workflowId;
    private String workflowName;
    private String documentId;
    private String documentName;

    // Current state
    private String currentStepId;
    private String currentStepName;
    private ExecutionStatus status;

    // Execution history
    private List<ExecutionStep> steps;

    // Data passed through workflow
    private Map<String, Object> workflowData;

    // Assignees for current step
    private List<String> currentAssignees;

    // Dates
    private Instant startedAt;
    private Instant completedAt;
    private Instant dueDate;

    // Initiator
    private String initiatedBy;

    // Audit
    private Instant createdAt;
    private Instant updatedAt;

    public enum ExecutionStatus {
        PENDING,
        IN_PROGRESS,
        WAITING_APPROVAL,
        APPROVED,
        REJECTED,
        COMPLETED,
        CANCELLED,
        FAILED
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecutionStep {
        private String stepId;
        private String stepName;
        private StepType stepType;
        private StepStatus status;
        private String assignedTo;
        private String completedBy;
        private String action;  // approve, reject, etc.
        private String comment;
        private Instant startedAt;
        private Instant completedAt;
    }

    public enum StepType {
        START,
        APPROVAL,
        REVIEW,
        TASK,
        NOTIFICATION,
        CONDITION,
        END
    }

    public enum StepStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        SKIPPED,
        FAILED
    }
}
