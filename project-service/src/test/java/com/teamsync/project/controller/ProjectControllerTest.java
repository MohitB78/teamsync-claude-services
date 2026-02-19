package com.teamsync.project.controller;

import com.teamsync.common.dto.ApiResponse;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ProjectController.
 * Tests project CRUD endpoints.
 */
@DisplayName("Project Controller Tests")
class ProjectControllerTest {

    private ProjectController projectController;

    private static final String PROJECT_ID = "project-123";

    @BeforeEach
    void setUp() {
        projectController = new ProjectController();
    }

    @Nested
    @DisplayName("List Projects Tests")
    class ListProjectsTests {

        @Test
        @DisplayName("Should return 200 when listing projects")
        void listProjects_ReturnsOk() {
            // When
            ResponseEntity<ApiResponse<String>> response = projectController.listProjects();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getMessage()).isEqualTo("Project Service - List Projects endpoint");
        }

        @Test
        @DisplayName("Should have success=true")
        void listProjects_HasSuccessTrue() {
            // When
            ResponseEntity<ApiResponse<String>> response = projectController.listProjects();

            // Then
            assertThat(response.getBody().isSuccess()).isTrue();
        }
    }

    @Nested
    @DisplayName("Create Project Tests")
    class CreateProjectTests {

        @Test
        @DisplayName("Should return 200 when creating project")
        void createProject_ReturnsOk() {
            // When
            ResponseEntity<ApiResponse<String>> response = projectController.createProject();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getMessage()).isEqualTo("Project Service - Create Project endpoint");
        }
    }

    @Nested
    @DisplayName("Get Project Tests")
    class GetProjectTests {

        @Test
        @DisplayName("Should return 200 when getting project")
        void getProject_ValidId_ReturnsOk() {
            // When
            ResponseEntity<ApiResponse<String>> response = projectController.getProject(PROJECT_ID);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getMessage()).isEqualTo("Project Service - Get Project: " + PROJECT_ID);
        }

        @Test
        @DisplayName("Should include project ID in response")
        void getProject_IncludesProjectId() {
            // Given
            String customId = "custom-project-456";

            // When
            ResponseEntity<ApiResponse<String>> response = projectController.getProject(customId);

            // Then
            assertThat(response.getBody().getMessage()).contains(customId);
        }

        @Test
        @DisplayName("Should handle UUID project ID")
        void getProject_UuidId_ReturnsOk() {
            // Given
            String uuidId = "550e8400-e29b-41d4-a716-446655440000";

            // When
            ResponseEntity<ApiResponse<String>> response = projectController.getProject(uuidId);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getMessage()).contains(uuidId);
        }

        @Test
        @DisplayName("Should handle numeric project ID")
        void getProject_NumericId_ReturnsOk() {
            // When
            ResponseEntity<ApiResponse<String>> response = projectController.getProject("12345");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getMessage()).contains("12345");
        }
    }

    @Nested
    @DisplayName("Response Format Tests")
    class ResponseFormatTests {

        @Test
        @DisplayName("All endpoints should return ApiResponse wrapper")
        void allEndpoints_ReturnApiResponse() {
            assertThat(projectController.listProjects().getBody()).isInstanceOf(ApiResponse.class);
            assertThat(projectController.createProject().getBody()).isInstanceOf(ApiResponse.class);
            assertThat(projectController.getProject("id").getBody()).isInstanceOf(ApiResponse.class);
        }

        @Test
        @DisplayName("All successful responses should have success=true")
        void allEndpoints_HaveSuccessTrue() {
            assertThat(projectController.listProjects().getBody().isSuccess()).isTrue();
            assertThat(projectController.createProject().getBody().isSuccess()).isTrue();
            assertThat(projectController.getProject("id").getBody().isSuccess()).isTrue();
        }

        @Test
        @DisplayName("All responses should have HTTP 200 OK status")
        void allEndpoints_Return200() {
            assertThat(projectController.listProjects().getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(projectController.createProject().getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(projectController.getProject("id").getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("All responses should have non-null body")
        void allEndpoints_HaveNonNullBody() {
            assertThat(projectController.listProjects().getBody()).isNotNull();
            assertThat(projectController.createProject().getBody()).isNotNull();
            assertThat(projectController.getProject("id").getBody()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Service Identification Tests")
    class ServiceIdentificationTests {

        @Test
        @DisplayName("All responses should identify as Project Service")
        void allEndpoints_IdentifyAsProjectService() {
            assertThat(projectController.listProjects().getBody().getMessage()).contains("Project Service");
            assertThat(projectController.createProject().getBody().getMessage()).contains("Project Service");
            assertThat(projectController.getProject("id").getBody().getMessage()).contains("Project Service");
        }
    }

    @Nested
    @DisplayName("Path Variable Tests")
    class PathVariableTests {

        @Test
        @DisplayName("Get project should work with various project IDs")
        void getProject_VariousIds_AllWork() {
            String[] ids = {"simple", "with-dash", "with_underscore", "UPPERCASE", "MixedCase123"};

            for (String id : ids) {
                ResponseEntity<ApiResponse<String>> response = projectController.getProject(id);
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody().getMessage()).contains(id);
            }
        }
    }
}
