package com.teamsync.wopi.controller;

import com.teamsync.common.dto.ApiResponse;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for WopiController.
 * Tests WOPI (Web Application Open Platform Interface) endpoints for Office editing.
 */
@DisplayName("WOPI Controller Tests")
class WopiControllerTest {

    private WopiController wopiController;

    private static final String FILE_ID = "file-123";
    private static final String DOCUMENT_ID = "doc-456";

    @BeforeEach
    void setUp() {
        wopiController = new WopiController();
    }

    @Nested
    @DisplayName("CheckFileInfo Tests")
    class CheckFileInfoTests {

        @Test
        @DisplayName("Should return 200 when checking file info")
        void checkFileInfo_ValidFileId_ReturnsOk() {
            // When
            ResponseEntity<Map<String, Object>> response = wopiController.checkFileInfo(FILE_ID);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
        }

        @Test
        @DisplayName("Should return WOPI CheckFileInfo fields")
        void checkFileInfo_ReturnsRequiredFields() {
            // When
            ResponseEntity<Map<String, Object>> response = wopiController.checkFileInfo(FILE_ID);

            // Then
            Map<String, Object> body = response.getBody();
            assertThat(body).containsKey("BaseFileName");
            assertThat(body).containsKey("OwnerId");
            assertThat(body).containsKey("Size");
            assertThat(body).containsKey("UserId");
            assertThat(body).containsKey("Version");
            assertThat(body).containsKey("UserCanWrite");
            assertThat(body).containsKey("SupportsUpdate");
            assertThat(body).containsKey("SupportsLocks");
        }

        @Test
        @DisplayName("Should return UserCanWrite as true")
        void checkFileInfo_UserCanWriteTrue() {
            // When
            ResponseEntity<Map<String, Object>> response = wopiController.checkFileInfo(FILE_ID);

            // Then
            assertThat(response.getBody().get("UserCanWrite")).isEqualTo(true);
        }

        @Test
        @DisplayName("Should return SupportsLocks as true")
        void checkFileInfo_SupportsLocksTrue() {
            // When
            ResponseEntity<Map<String, Object>> response = wopiController.checkFileInfo(FILE_ID);

            // Then
            assertThat(response.getBody().get("SupportsLocks")).isEqualTo(true);
        }

        @Test
        @DisplayName("Should handle UUID file ID")
        void checkFileInfo_UuidId_ReturnsOk() {
            // Given
            String uuidId = "550e8400-e29b-41d4-a716-446655440000";

            // When
            ResponseEntity<Map<String, Object>> response = wopiController.checkFileInfo(uuidId);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
        }
    }

    @Nested
    @DisplayName("GetFile Tests")
    class GetFileTests {

        @Test
        @DisplayName("Should return 200 when getting file contents")
        void getFile_ValidFileId_ReturnsOk() {
            // When
            ResponseEntity<byte[]> response = wopiController.getFile(FILE_ID);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
        }

        @Test
        @DisplayName("Should return empty byte array (placeholder)")
        void getFile_ReturnsEmptyBytes() {
            // When
            ResponseEntity<byte[]> response = wopiController.getFile(FILE_ID);

            // Then
            assertThat(response.getBody()).isEmpty();
        }

        @Test
        @DisplayName("Should handle UUID file ID")
        void getFile_UuidId_ReturnsOk() {
            // Given
            String uuidId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";

            // When
            ResponseEntity<byte[]> response = wopiController.getFile(uuidId);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("Should handle various file ID formats")
        void getFile_VariousIds_AllWork() {
            String[] ids = {"simple", "with-dash", "with_underscore", "12345"};

            for (String id : ids) {
                ResponseEntity<byte[]> response = wopiController.getFile(id);
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            }
        }
    }

    @Nested
    @DisplayName("PutFile Tests")
    class PutFileTests {

        @Test
        @DisplayName("Should return 200 when putting file contents")
        void putFile_ValidContent_ReturnsOk() {
            // Given
            byte[] contents = "test content".getBytes();

            // When
            ResponseEntity<Map<String, Object>> response = wopiController.putFile(FILE_ID, contents);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
        }

        @Test
        @DisplayName("Should return ItemVersion in response")
        void putFile_ReturnsItemVersion() {
            // Given
            byte[] contents = "test content".getBytes();

            // When
            ResponseEntity<Map<String, Object>> response = wopiController.putFile(FILE_ID, contents);

            // Then
            assertThat(response.getBody()).containsKey("ItemVersion");
            assertThat(response.getBody().get("ItemVersion")).isEqualTo("v2");
        }

        @Test
        @DisplayName("Should handle empty content")
        void putFile_EmptyContent_ReturnsOk() {
            // Given
            byte[] contents = new byte[0];

            // When
            ResponseEntity<Map<String, Object>> response = wopiController.putFile(FILE_ID, contents);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("Should handle large content")
        void putFile_LargeContent_ReturnsOk() {
            // Given
            byte[] contents = new byte[1024 * 1024]; // 1MB

            // When
            ResponseEntity<Map<String, Object>> response = wopiController.putFile(FILE_ID, contents);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Nested
    @DisplayName("Lock Operation Tests")
    class LockOperationTests {

        @Test
        @DisplayName("Should return 200 for LOCK operation")
        void lock_LockOperation_ReturnsOk() {
            // When
            ResponseEntity<Void> response = wopiController.lock(FILE_ID, "LOCK", "lock-token-123");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("Should return 200 for UNLOCK operation")
        void lock_UnlockOperation_ReturnsOk() {
            // When
            ResponseEntity<Void> response = wopiController.lock(FILE_ID, "UNLOCK", "lock-token-123");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("Should return 200 for REFRESH_LOCK operation")
        void lock_RefreshLockOperation_ReturnsOk() {
            // When
            ResponseEntity<Void> response = wopiController.lock(FILE_ID, "REFRESH_LOCK", "lock-token-123");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("Should handle null lock header")
        void lock_NullLockHeader_ReturnsOk() {
            // When
            ResponseEntity<Void> response = wopiController.lock(FILE_ID, "LOCK", null);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("Should handle GET_LOCK operation")
        void lock_GetLockOperation_ReturnsOk() {
            // When
            ResponseEntity<Void> response = wopiController.lock(FILE_ID, "GET_LOCK", null);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("Should handle various file IDs for lock")
        void lock_VariousFileIds_AllWork() {
            String[] ids = {"file1", "file-2", "file_3"};

            for (String id : ids) {
                ResponseEntity<Void> response = wopiController.lock(id, "LOCK", "token");
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            }
        }
    }

    @Nested
    @DisplayName("Get Editor URL Tests")
    class GetEditorUrlTests {

        @Test
        @DisplayName("Should return 200 when getting editor URL")
        void getEditorUrl_ValidDocId_ReturnsOk() {
            // When
            ResponseEntity<ApiResponse<Map<String, String>>> response = wopiController.getEditorUrl(DOCUMENT_ID);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should return editor URL data")
        void getEditorUrl_ReturnsEditorData() {
            // When
            ResponseEntity<ApiResponse<Map<String, String>>> response = wopiController.getEditorUrl(DOCUMENT_ID);

            // Then
            Map<String, String> data = response.getBody().getData();
            assertThat(data).containsKey("editorUrl");
            assertThat(data).containsKey("accessToken");
            assertThat(data).containsKey("tokenTtl");
        }

        @Test
        @DisplayName("Should return valid editor URL")
        void getEditorUrl_HasValidUrl() {
            // When
            ResponseEntity<ApiResponse<Map<String, String>>> response = wopiController.getEditorUrl(DOCUMENT_ID);

            // Then
            String editorUrl = response.getBody().getData().get("editorUrl");
            assertThat(editorUrl).startsWith("https://");
        }

        @Test
        @DisplayName("Should return access token")
        void getEditorUrl_HasAccessToken() {
            // When
            ResponseEntity<ApiResponse<Map<String, String>>> response = wopiController.getEditorUrl(DOCUMENT_ID);

            // Then
            String accessToken = response.getBody().getData().get("accessToken");
            assertThat(accessToken).isNotBlank();
        }

        @Test
        @DisplayName("Should handle UUID document ID")
        void getEditorUrl_UuidId_ReturnsOk() {
            // Given
            String uuidId = "11111111-2222-3333-4444-555555555555";

            // When
            ResponseEntity<ApiResponse<Map<String, String>>> response = wopiController.getEditorUrl(uuidId);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();
        }
    }

    @Nested
    @DisplayName("Response Format Tests")
    class ResponseFormatTests {

        @Test
        @DisplayName("CheckFileInfo should return Map")
        void checkFileInfo_ReturnsMap() {
            ResponseEntity<Map<String, Object>> response = wopiController.checkFileInfo(FILE_ID);
            assertThat(response.getBody()).isInstanceOf(Map.class);
        }

        @Test
        @DisplayName("GetFile should return byte array")
        void getFile_ReturnsByteArray() {
            ResponseEntity<byte[]> response = wopiController.getFile(FILE_ID);
            assertThat(response.getBody()).isInstanceOf(byte[].class);
        }

        @Test
        @DisplayName("PutFile should return Map")
        void putFile_ReturnsMap() {
            ResponseEntity<Map<String, Object>> response = wopiController.putFile(FILE_ID, new byte[0]);
            assertThat(response.getBody()).isInstanceOf(Map.class);
        }

        @Test
        @DisplayName("GetEditorUrl should return ApiResponse")
        void getEditorUrl_ReturnsApiResponse() {
            ResponseEntity<ApiResponse<Map<String, String>>> response = wopiController.getEditorUrl(DOCUMENT_ID);
            assertThat(response.getBody()).isInstanceOf(ApiResponse.class);
        }

        @Test
        @DisplayName("All endpoints should return HTTP 200")
        void allEndpoints_Return200() {
            assertThat(wopiController.checkFileInfo(FILE_ID).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(wopiController.getFile(FILE_ID).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(wopiController.putFile(FILE_ID, new byte[0]).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(wopiController.lock(FILE_ID, "LOCK", null).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(wopiController.getEditorUrl(DOCUMENT_ID).getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("All endpoints should have non-null body")
        void allEndpoints_HaveNonNullBody() {
            assertThat(wopiController.checkFileInfo(FILE_ID).getBody()).isNotNull();
            assertThat(wopiController.getFile(FILE_ID).getBody()).isNotNull();
            assertThat(wopiController.putFile(FILE_ID, new byte[0]).getBody()).isNotNull();
            assertThat(wopiController.getEditorUrl(DOCUMENT_ID).getBody()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Path Variable Tests")
    class PathVariableTests {

        @Test
        @DisplayName("All endpoints should handle various file IDs")
        void allEndpoints_VariousFileIds_AllWork() {
            String[] ids = {"simple", "with-dash", "with_underscore", "UPPERCASE", "MixedCase123"};

            for (String id : ids) {
                assertThat(wopiController.checkFileInfo(id).getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(wopiController.getFile(id).getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(wopiController.putFile(id, new byte[0]).getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(wopiController.lock(id, "LOCK", null).getStatusCode()).isEqualTo(HttpStatus.OK);
            }
        }

        @Test
        @DisplayName("GetEditorUrl should handle various document IDs")
        void getEditorUrl_VariousIds_AllWork() {
            String[] ids = {"doc1", "doc-2", "doc_3", "DOC4"};

            for (String id : ids) {
                ResponseEntity<ApiResponse<Map<String, String>>> response = wopiController.getEditorUrl(id);
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            }
        }
    }
}
