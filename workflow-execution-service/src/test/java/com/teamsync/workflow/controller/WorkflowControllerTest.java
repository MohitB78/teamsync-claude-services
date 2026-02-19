package com.teamsync.workflow.controller;

import com.teamsync.common.dto.ApiResponse;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for WorkflowController.
 * Tests workflow execution management endpoints.
 */
@DisplayName("Workflow Controller Tests")
class WorkflowControllerTest {

    private WorkflowController workflowController;

    private static final String EXECUTION_ID = "exec-123";

    @BeforeEach
    void setUp() {
        workflowController = new WorkflowController();
    }

    @Nested
    @DisplayName("List Executions Tests")
    class ListExecutionsTests {

        @Test
        @DisplayName("Should return 200 when listing executions")
        void listExecutions_ReturnsOk() {
            // When
            ResponseEntity<ApiResponse<String>> response = workflowController.listExecutions();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getMessage()).isEqualTo("Workflow Service - List Executions endpoint");
        }

        @Test
        @DisplayName("Should have success=true")
        void listExecutions_HasSuccessTrue() {
            // When
            ResponseEntity<ApiResponse<String>> response = workflowController.listExecutions();

            // Then
            assertThat(response.getBody().isSuccess()).isTrue();
        }
    }

    @Nested
    @DisplayName("Start Workflow Tests")
    class StartWorkflowTests {

        @Test
        @DisplayName("Should return 200 when starting workflow")
        void startWorkflow_ReturnsOk() {
            // When
            ResponseEntity<ApiResponse<String>> response = workflowController.startWorkflow();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getMessage()).isEqualTo("Workflow Service - Start Workflow endpoint");
        }
    }

    @Nested
    @DisplayName("Approve Execution Tests")
    class ApproveExecutionTests {

        @Test
        @DisplayName("Should return 200 when approving execution")
        void approve_ValidId_ReturnsOk() {
            // When
            ResponseEntity<ApiResponse<String>> response = workflowController.approve(EXECUTION_ID);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getMessage()).isEqualTo("Workflow Service - Approve: " + EXECUTION_ID);
        }

        @Test
        @DisplayName("Should include execution ID in approve response")
        void approve_IncludesExecutionId() {
            // Given
            String customId = "custom-exec-456";

            // When
            ResponseEntity<ApiResponse<String>> response = workflowController.approve(customId);

            // Then
            assertThat(response.getBody().getMessage()).contains(customId);
        }

        @Test
        @DisplayName("Should handle UUID execution ID")
        void approve_UuidId_ReturnsOk() {
            // Given
            String uuidId = "550e8400-e29b-41d4-a716-446655440000";

            // When
            ResponseEntity<ApiResponse<String>> response = workflowController.approve(uuidId);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getMessage()).contains(uuidId);
        }

        @Test
        @DisplayName("Should handle numeric execution ID")
        void approve_NumericId_ReturnsOk() {
            // When
            ResponseEntity<ApiResponse<String>> response = workflowController.approve("12345");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getMessage()).contains("12345");
        }
    }

    @Nested
    @DisplayName("Reject Execution Tests")
    class RejectExecutionTests {

        @Test
        @DisplayName("Should return 200 when rejecting execution")
        void reject_ValidId_ReturnsOk() {
            // When
            ResponseEntity<ApiResponse<String>> response = workflowController.reject(EXECUTION_ID);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getMessage()).isEqualTo("Workflow Service - Reject: " + EXECUTION_ID);
        }

        @Test
        @DisplayName("Should include execution ID in reject response")
        void reject_IncludesExecutionId() {
            // Given
            String customId = "reject-exec-789";

            // When
            ResponseEntity<ApiResponse<String>> response = workflowController.reject(customId);

            // Then
            assertThat(response.getBody().getMessage()).contains(customId);
        }

        @Test
        @DisplayName("Should handle UUID execution ID")
        void reject_UuidId_ReturnsOk() {
            // Given
            String uuidId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";

            // When
            ResponseEntity<ApiResponse<String>> response = workflowController.reject(uuidId);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getMessage()).contains(uuidId);
        }
    }

    @Nested
    @DisplayName("Response Format Tests")
    class ResponseFormatTests {

        @Test
        @DisplayName("All endpoints should return ApiResponse wrapper")
        void allEndpoints_ReturnApiResponse() {
            assertThat(workflowController.listExecutions().getBody()).isInstanceOf(ApiResponse.class);
            assertThat(workflowController.startWorkflow().getBody()).isInstanceOf(ApiResponse.class);
            assertThat(workflowController.approve("id").getBody()).isInstanceOf(ApiResponse.class);
            assertThat(workflowController.reject("id").getBody()).isInstanceOf(ApiResponse.class);
        }

        @Test
        @DisplayName("All successful responses should have success=true")
        void allEndpoints_HaveSuccessTrue() {
            assertThat(workflowController.listExecutions().getBody().isSuccess()).isTrue();
            assertThat(workflowController.startWorkflow().getBody().isSuccess()).isTrue();
            assertThat(workflowController.approve("id").getBody().isSuccess()).isTrue();
            assertThat(workflowController.reject("id").getBody().isSuccess()).isTrue();
        }

        @Test
        @DisplayName("All responses should have HTTP 200 OK status")
        void allEndpoints_Return200() {
            assertThat(workflowController.listExecutions().getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(workflowController.startWorkflow().getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(workflowController.approve("id").getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(workflowController.reject("id").getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("All responses should have non-null body")
        void allEndpoints_HaveNonNullBody() {
            assertThat(workflowController.listExecutions().getBody()).isNotNull();
            assertThat(workflowController.startWorkflow().getBody()).isNotNull();
            assertThat(workflowController.approve("id").getBody()).isNotNull();
            assertThat(workflowController.reject("id").getBody()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Service Identification Tests")
    class ServiceIdentificationTests {

        @Test
        @DisplayName("All responses should identify as Workflow Service")
        void allEndpoints_IdentifyAsWorkflowService() {
            assertThat(workflowController.listExecutions().getBody().getMessage()).contains("Workflow Service");
            assertThat(workflowController.startWorkflow().getBody().getMessage()).contains("Workflow Service");
            assertThat(workflowController.approve("id").getBody().getMessage()).contains("Workflow Service");
            assertThat(workflowController.reject("id").getBody().getMessage()).contains("Workflow Service");
        }
    }

    @Nested
    @DisplayName("Path Variable Tests")
    class PathVariableTests {

        @Test
        @DisplayName("Approve should work with various execution IDs")
        void approve_VariousIds_AllWork() {
            String[] ids = {"simple", "with-dash", "with_underscore", "UPPERCASE", "MixedCase123"};

            for (String id : ids) {
                ResponseEntity<ApiResponse<String>> response = workflowController.approve(id);
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody().getMessage()).contains(id);
            }
        }

        @Test
        @DisplayName("Reject should work with various execution IDs")
        void reject_VariousIds_AllWork() {
            String[] ids = {"exec1", "exec-2", "exec_3", "EXEC4"};

            for (String id : ids) {
                ResponseEntity<ApiResponse<String>> response = workflowController.reject(id);
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody().getMessage()).contains(id);
            }
        }
    }

    @Nested
    @DisplayName("Approve vs Reject Tests")
    class ApproveRejectTests {

        @Test
        @DisplayName("Approve and reject should produce different messages")
        void approveAndReject_DifferentMessages() {
            // Given
            String execId = "test-exec";

            // When
            String approveMessage = workflowController.approve(execId).getBody().getMessage();
            String rejectMessage = workflowController.reject(execId).getBody().getMessage();

            // Then
            assertThat(approveMessage).contains("Approve");
            assertThat(rejectMessage).contains("Reject");
            assertThat(approveMessage).isNotEqualTo(rejectMessage);
        }
    }
}
