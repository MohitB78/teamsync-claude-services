package com.teamsync.activity.controller;

import com.teamsync.common.dto.ApiResponse;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ActivityController.
 * Tests activity listing, user activities, document activities, and audit log endpoints.
 */
@DisplayName("Activity Controller Tests")
class ActivityControllerTest {

    private ActivityController activityController;

    private static final String USER_ID = "user-123";
    private static final String DOCUMENT_ID = "doc-456";

    @BeforeEach
    void setUp() {
        activityController = new ActivityController();
    }

    @Nested
    @DisplayName("List Activities Tests")
    class ListActivitiesTests {

        @Test
        @DisplayName("Should return 200 when listing all activities")
        void listActivities_All_ReturnsOk() {
            // When
            ResponseEntity<ApiResponse<String>> response = activityController.listActivities(null, null);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getMessage()).isEqualTo("Activity Service - List Activities");
        }

        @Test
        @DisplayName("Should return 200 when filtering by resource type")
        void listActivities_WithResourceType_ReturnsOk() {
            // When
            ResponseEntity<ApiResponse<String>> response = activityController.listActivities("DOCUMENT", null);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should return 200 when filtering by resource ID")
        void listActivities_WithResourceId_ReturnsOk() {
            // When
            ResponseEntity<ApiResponse<String>> response = activityController.listActivities(null, "resource-123");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should return 200 when filtering by both resource type and ID")
        void listActivities_WithBothFilters_ReturnsOk() {
            // When
            ResponseEntity<ApiResponse<String>> response = activityController.listActivities("FOLDER", "folder-789");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();
        }
    }

    @Nested
    @DisplayName("User Activities Tests")
    class UserActivitiesTests {

        @Test
        @DisplayName("Should return 200 when getting user activities")
        void getUserActivities_ValidUserId_ReturnsOk() {
            // When
            ResponseEntity<ApiResponse<String>> response = activityController.getUserActivities(USER_ID);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getMessage()).isEqualTo("Activity Service - User Activities: " + USER_ID);
        }

        @Test
        @DisplayName("Should include user ID in response message")
        void getUserActivities_IncludesUserId() {
            // Given
            String customUserId = "custom-user-456";

            // When
            ResponseEntity<ApiResponse<String>> response = activityController.getUserActivities(customUserId);

            // Then
            assertThat(response.getBody().getMessage()).contains(customUserId);
        }

        @Test
        @DisplayName("Should handle UUID user ID")
        void getUserActivities_UuidId_ReturnsOk() {
            // Given
            String uuidId = "550e8400-e29b-41d4-a716-446655440000";

            // When
            ResponseEntity<ApiResponse<String>> response = activityController.getUserActivities(uuidId);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getMessage()).contains(uuidId);
        }
    }

    @Nested
    @DisplayName("Document Activities Tests")
    class DocumentActivitiesTests {

        @Test
        @DisplayName("Should return 200 when getting document activities")
        void getDocumentActivities_ValidDocId_ReturnsOk() {
            // When
            ResponseEntity<ApiResponse<String>> response = activityController.getDocumentActivities(DOCUMENT_ID);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getMessage()).isEqualTo("Activity Service - Document Activities: " + DOCUMENT_ID);
        }

        @Test
        @DisplayName("Should include document ID in response message")
        void getDocumentActivities_IncludesDocumentId() {
            // Given
            String customDocId = "custom-doc-789";

            // When
            ResponseEntity<ApiResponse<String>> response = activityController.getDocumentActivities(customDocId);

            // Then
            assertThat(response.getBody().getMessage()).contains(customDocId);
        }

        @Test
        @DisplayName("Should handle UUID document ID")
        void getDocumentActivities_UuidId_ReturnsOk() {
            // Given
            String uuidId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";

            // When
            ResponseEntity<ApiResponse<String>> response = activityController.getDocumentActivities(uuidId);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getMessage()).contains(uuidId);
        }
    }

    @Nested
    @DisplayName("Audit Log Tests")
    class AuditLogTests {

        @Test
        @DisplayName("Should return 200 when getting audit log without dates")
        void getAuditLog_NoDates_ReturnsOk() {
            // When
            ResponseEntity<ApiResponse<String>> response = activityController.getAuditLog(null, null);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getMessage()).isEqualTo("Activity Service - Audit Log");
        }

        @Test
        @DisplayName("Should return 200 when getting audit log with start date")
        void getAuditLog_WithStartDate_ReturnsOk() {
            // When
            ResponseEntity<ApiResponse<String>> response = activityController.getAuditLog("2024-01-01", null);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should return 200 when getting audit log with end date")
        void getAuditLog_WithEndDate_ReturnsOk() {
            // When
            ResponseEntity<ApiResponse<String>> response = activityController.getAuditLog(null, "2024-12-31");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should return 200 when getting audit log with date range")
        void getAuditLog_WithDateRange_ReturnsOk() {
            // When
            ResponseEntity<ApiResponse<String>> response = activityController.getAuditLog("2024-01-01", "2024-12-31");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();
        }
    }

    @Nested
    @DisplayName("Response Format Tests")
    class ResponseFormatTests {

        @Test
        @DisplayName("All endpoints should return ApiResponse wrapper")
        void allEndpoints_ReturnApiResponse() {
            assertThat(activityController.listActivities(null, null).getBody()).isInstanceOf(ApiResponse.class);
            assertThat(activityController.getUserActivities("user").getBody()).isInstanceOf(ApiResponse.class);
            assertThat(activityController.getDocumentActivities("doc").getBody()).isInstanceOf(ApiResponse.class);
            assertThat(activityController.getAuditLog(null, null).getBody()).isInstanceOf(ApiResponse.class);
        }

        @Test
        @DisplayName("All successful responses should have success=true")
        void allEndpoints_HaveSuccessTrue() {
            assertThat(activityController.listActivities(null, null).getBody().isSuccess()).isTrue();
            assertThat(activityController.getUserActivities("user").getBody().isSuccess()).isTrue();
            assertThat(activityController.getDocumentActivities("doc").getBody().isSuccess()).isTrue();
            assertThat(activityController.getAuditLog(null, null).getBody().isSuccess()).isTrue();
        }

        @Test
        @DisplayName("All responses should have HTTP 200 OK status")
        void allEndpoints_Return200() {
            assertThat(activityController.listActivities(null, null).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(activityController.getUserActivities("user").getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(activityController.getDocumentActivities("doc").getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(activityController.getAuditLog(null, null).getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("All responses should have non-null body")
        void allEndpoints_HaveNonNullBody() {
            assertThat(activityController.listActivities(null, null).getBody()).isNotNull();
            assertThat(activityController.getUserActivities("user").getBody()).isNotNull();
            assertThat(activityController.getDocumentActivities("doc").getBody()).isNotNull();
            assertThat(activityController.getAuditLog(null, null).getBody()).isNotNull();
        }

        @Test
        @DisplayName("All responses should have message field")
        void allEndpoints_HaveMessage() {
            assertThat(activityController.listActivities(null, null).getBody().getMessage()).isNotBlank();
            assertThat(activityController.getUserActivities("user").getBody().getMessage()).isNotBlank();
            assertThat(activityController.getDocumentActivities("doc").getBody().getMessage()).isNotBlank();
            assertThat(activityController.getAuditLog(null, null).getBody().getMessage()).isNotBlank();
        }
    }

    @Nested
    @DisplayName("Service Identification Tests")
    class ServiceIdentificationTests {

        @Test
        @DisplayName("All responses should identify as Activity Service")
        void allEndpoints_IdentifyAsActivityService() {
            assertThat(activityController.listActivities(null, null).getBody().getMessage()).contains("Activity Service");
            assertThat(activityController.getUserActivities("user").getBody().getMessage()).contains("Activity Service");
            assertThat(activityController.getDocumentActivities("doc").getBody().getMessage()).contains("Activity Service");
            assertThat(activityController.getAuditLog(null, null).getBody().getMessage()).contains("Activity Service");
        }
    }

    @Nested
    @DisplayName("Path Variable Tests")
    class PathVariableTests {

        @Test
        @DisplayName("User activities should work with various user IDs")
        void getUserActivities_VariousIds_AllWork() {
            String[] ids = {"simple", "with-dash", "with_underscore", "UPPERCASE", "MixedCase123"};

            for (String id : ids) {
                ResponseEntity<ApiResponse<String>> response = activityController.getUserActivities(id);
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody().getMessage()).contains(id);
            }
        }

        @Test
        @DisplayName("Document activities should work with various document IDs")
        void getDocumentActivities_VariousIds_AllWork() {
            String[] ids = {"doc1", "doc-2", "doc_3", "DOC4", "Doc5Mixed"};

            for (String id : ids) {
                ResponseEntity<ApiResponse<String>> response = activityController.getDocumentActivities(id);
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody().getMessage()).contains(id);
            }
        }
    }
}
