package com.teamsync.trash.controller;

import com.teamsync.common.dto.ApiResponse;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for TrashController.
 * Tests all trash management endpoints with various scenarios.
 */
@DisplayName("Trash Controller Tests")
class TrashControllerTest {

    private TrashController trashController;

    private static final String ITEM_ID = "item-123";

    @BeforeEach
    void setUp() {
        trashController = new TrashController();
    }

    @Nested
    @DisplayName("List Trash Tests")
    class ListTrashTests {

        @Test
        @DisplayName("Should return 200 and list trashed items")
        void listTrash_ReturnsOk() {
            // When
            ResponseEntity<ApiResponse<String>> response = trashController.listTrash();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getMessage()).contains("List Trashed Items");
        }

        @Test
        @DisplayName("Response should have success=true")
        void listTrash_HasSuccessTrue() {
            // When
            ResponseEntity<ApiResponse<String>> response = trashController.listTrash();

            // Then
            assertThat(response.getBody().isSuccess()).isTrue();
        }
    }

    @Nested
    @DisplayName("Restore Item Tests")
    class RestoreItemTests {

        @Test
        @DisplayName("Should return 200 when restoring item")
        void restoreItem_ValidId_ReturnsOk() {
            // When
            ResponseEntity<ApiResponse<String>> response = trashController.restoreItem(ITEM_ID);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getMessage()).isEqualTo("Trash Service - Restore Item: " + ITEM_ID);
        }

        @Test
        @DisplayName("Should include item ID in response message")
        void restoreItem_IncludesItemIdInMessage() {
            // Given
            String customId = "custom-item-456";

            // When
            ResponseEntity<ApiResponse<String>> response = trashController.restoreItem(customId);

            // Then
            assertThat(response.getBody().getMessage()).contains(customId);
        }

        @Test
        @DisplayName("Should handle UUID-formatted item ID")
        void restoreItem_UuidId_ReturnsOk() {
            // Given
            String uuidId = "550e8400-e29b-41d4-a716-446655440000";

            // When
            ResponseEntity<ApiResponse<String>> response = trashController.restoreItem(uuidId);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getMessage()).contains(uuidId);
        }

        @Test
        @DisplayName("Should handle numeric item ID")
        void restoreItem_NumericId_ReturnsOk() {
            // Given
            String numericId = "12345";

            // When
            ResponseEntity<ApiResponse<String>> response = trashController.restoreItem(numericId);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getMessage()).contains("12345");
        }

        @Test
        @DisplayName("Should handle alphanumeric item ID with special chars")
        void restoreItem_AlphanumericId_ReturnsOk() {
            // Given
            String specialId = "item-with-dash_and_underscore";

            // When
            ResponseEntity<ApiResponse<String>> response = trashController.restoreItem(specialId);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();
        }
    }

    @Nested
    @DisplayName("Permanent Delete Tests")
    class PermanentDeleteTests {

        @Test
        @DisplayName("Should return 200 when permanently deleting item")
        void permanentDelete_ValidId_ReturnsOk() {
            // When
            ResponseEntity<ApiResponse<String>> response = trashController.permanentDelete(ITEM_ID);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getMessage()).isEqualTo("Trash Service - Permanent Delete: " + ITEM_ID);
        }

        @Test
        @DisplayName("Should include item ID in delete response")
        void permanentDelete_IncludesItemId() {
            // Given
            String itemId = "delete-me-789";

            // When
            ResponseEntity<ApiResponse<String>> response = trashController.permanentDelete(itemId);

            // Then
            assertThat(response.getBody().getMessage()).contains(itemId);
        }

        @Test
        @DisplayName("Should handle UUID item ID for delete")
        void permanentDelete_UuidId_ReturnsOk() {
            // Given
            String uuidId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";

            // When
            ResponseEntity<ApiResponse<String>> response = trashController.permanentDelete(uuidId);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should handle long item ID")
        void permanentDelete_LongId_ReturnsOk() {
            // Given
            String longId = "very-long-item-identifier-that-spans-many-characters-12345678901234567890";

            // When
            ResponseEntity<ApiResponse<String>> response = trashController.permanentDelete(longId);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getMessage()).contains(longId);
        }
    }

    @Nested
    @DisplayName("Empty Trash Tests")
    class EmptyTrashTests {

        @Test
        @DisplayName("Should return 200 when emptying trash")
        void emptyTrash_ReturnsOk() {
            // When
            ResponseEntity<ApiResponse<String>> response = trashController.emptyTrash();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getMessage()).isEqualTo("Trash Service - Empty Trash endpoint");
        }

        @Test
        @DisplayName("Empty trash response should indicate success")
        void emptyTrash_HasSuccessResponse() {
            // When
            ResponseEntity<ApiResponse<String>> response = trashController.emptyTrash();

            // Then
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getMessage()).contains("Empty Trash");
        }
    }

    @Nested
    @DisplayName("Response Format Tests")
    class ResponseFormatTests {

        @Test
        @DisplayName("All endpoints should return ApiResponse wrapper")
        void allEndpoints_ReturnApiResponse() {
            // Test all endpoints return proper ApiResponse
            assertThat(trashController.listTrash().getBody()).isInstanceOf(ApiResponse.class);
            assertThat(trashController.restoreItem("test").getBody()).isInstanceOf(ApiResponse.class);
            assertThat(trashController.permanentDelete("test").getBody()).isInstanceOf(ApiResponse.class);
            assertThat(trashController.emptyTrash().getBody()).isInstanceOf(ApiResponse.class);
        }

        @Test
        @DisplayName("All successful responses should have success=true")
        void allEndpoints_HaveSuccessTrue() {
            assertThat(trashController.listTrash().getBody().isSuccess()).isTrue();
            assertThat(trashController.restoreItem("test").getBody().isSuccess()).isTrue();
            assertThat(trashController.permanentDelete("test").getBody().isSuccess()).isTrue();
            assertThat(trashController.emptyTrash().getBody().isSuccess()).isTrue();
        }

        @Test
        @DisplayName("All responses should have HTTP 200 OK status")
        void allEndpoints_Return200() {
            assertThat(trashController.listTrash().getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(trashController.restoreItem("test").getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(trashController.permanentDelete("test").getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(trashController.emptyTrash().getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("All responses should have non-null body")
        void allEndpoints_HaveNonNullBody() {
            assertThat(trashController.listTrash().getBody()).isNotNull();
            assertThat(trashController.restoreItem("test").getBody()).isNotNull();
            assertThat(trashController.permanentDelete("test").getBody()).isNotNull();
            assertThat(trashController.emptyTrash().getBody()).isNotNull();
        }

        @Test
        @DisplayName("All responses should have message field")
        void allEndpoints_HaveMessage() {
            assertThat(trashController.listTrash().getBody().getMessage()).isNotBlank();
            assertThat(trashController.restoreItem("test").getBody().getMessage()).isNotBlank();
            assertThat(trashController.permanentDelete("test").getBody().getMessage()).isNotBlank();
            assertThat(trashController.emptyTrash().getBody().getMessage()).isNotBlank();
        }
    }

    @Nested
    @DisplayName("Service Identification Tests")
    class ServiceIdentificationTests {

        @Test
        @DisplayName("All responses should identify as Trash Service")
        void allEndpoints_IdentifyAsTrashService() {
            assertThat(trashController.listTrash().getBody().getMessage()).contains("Trash Service");
            assertThat(trashController.restoreItem("test").getBody().getMessage()).contains("Trash Service");
            assertThat(trashController.permanentDelete("test").getBody().getMessage()).contains("Trash Service");
            assertThat(trashController.emptyTrash().getBody().getMessage()).contains("Trash Service");
        }
    }
}
