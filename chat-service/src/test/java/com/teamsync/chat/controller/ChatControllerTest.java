package com.teamsync.chat.controller;

import com.teamsync.common.dto.ApiResponse;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ChatController.
 * Tests channel messages and document comments endpoints.
 */
@DisplayName("Chat Controller Tests")
class ChatControllerTest {

    private ChatController chatController;

    private static final String CHANNEL_ID = "channel-123";
    private static final String DOCUMENT_ID = "doc-456";

    @BeforeEach
    void setUp() {
        chatController = new ChatController();
    }

    @Nested
    @DisplayName("Channel Messages Tests")
    class ChannelMessagesTests {

        @Test
        @DisplayName("Should return 200 when getting messages")
        void getMessages_ValidChannel_ReturnsOk() {
            // When
            ResponseEntity<ApiResponse<String>> response = chatController.getMessages(CHANNEL_ID);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getMessage()).isEqualTo("Chat Service - Messages for channel: " + CHANNEL_ID);
        }

        @Test
        @DisplayName("Should include channel ID in get messages response")
        void getMessages_IncludesChannelId() {
            // Given
            String customChannelId = "custom-channel-789";

            // When
            ResponseEntity<ApiResponse<String>> response = chatController.getMessages(customChannelId);

            // Then
            assertThat(response.getBody().getMessage()).contains(customChannelId);
        }

        @Test
        @DisplayName("Should handle UUID channel ID")
        void getMessages_UuidChannelId_ReturnsOk() {
            // Given
            String uuidId = "550e8400-e29b-41d4-a716-446655440000";

            // When
            ResponseEntity<ApiResponse<String>> response = chatController.getMessages(uuidId);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getMessage()).contains(uuidId);
        }

        @Test
        @DisplayName("Should handle numeric channel ID")
        void getMessages_NumericChannelId_ReturnsOk() {
            // Given
            String numericId = "12345";

            // When
            ResponseEntity<ApiResponse<String>> response = chatController.getMessages(numericId);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getMessage()).contains("12345");
        }

        @Test
        @DisplayName("Should return 200 when sending message")
        void sendMessage_ValidChannel_ReturnsOk() {
            // When
            ResponseEntity<ApiResponse<String>> response = chatController.sendMessage(CHANNEL_ID);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getMessage()).isEqualTo("Chat Service - Send to channel: " + CHANNEL_ID);
        }

        @Test
        @DisplayName("Should include channel ID in send message response")
        void sendMessage_IncludesChannelId() {
            // Given
            String customChannelId = "send-channel-456";

            // When
            ResponseEntity<ApiResponse<String>> response = chatController.sendMessage(customChannelId);

            // Then
            assertThat(response.getBody().getMessage()).contains(customChannelId);
        }

        @Test
        @DisplayName("Should handle long channel ID for send")
        void sendMessage_LongChannelId_ReturnsOk() {
            // Given
            String longId = "very-long-channel-identifier-12345678901234567890";

            // When
            ResponseEntity<ApiResponse<String>> response = chatController.sendMessage(longId);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getMessage()).contains(longId);
        }
    }

    @Nested
    @DisplayName("Document Comments Tests")
    class DocumentCommentsTests {

        @Test
        @DisplayName("Should return 200 when getting comments")
        void getComments_ValidDocument_ReturnsOk() {
            // When
            ResponseEntity<ApiResponse<String>> response = chatController.getComments(DOCUMENT_ID);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getMessage()).isEqualTo("Chat Service - Comments for document: " + DOCUMENT_ID);
        }

        @Test
        @DisplayName("Should include document ID in get comments response")
        void getComments_IncludesDocumentId() {
            // Given
            String customDocId = "custom-doc-789";

            // When
            ResponseEntity<ApiResponse<String>> response = chatController.getComments(customDocId);

            // Then
            assertThat(response.getBody().getMessage()).contains(customDocId);
        }

        @Test
        @DisplayName("Should return 200 when adding comment")
        void addComment_ValidDocument_ReturnsOk() {
            // When
            ResponseEntity<ApiResponse<String>> response = chatController.addComment(DOCUMENT_ID);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getMessage()).isEqualTo("Chat Service - Add comment to: " + DOCUMENT_ID);
        }

        @Test
        @DisplayName("Should include document ID in add comment response")
        void addComment_IncludesDocumentId() {
            // Given
            String customDocId = "add-comment-doc-123";

            // When
            ResponseEntity<ApiResponse<String>> response = chatController.addComment(customDocId);

            // Then
            assertThat(response.getBody().getMessage()).contains(customDocId);
        }

        @Test
        @DisplayName("Should handle long document ID")
        void getComments_LongDocumentId_ReturnsOk() {
            // Given
            String longId = "document-with-very-long-identifier-12345678901234567890";

            // When
            ResponseEntity<ApiResponse<String>> response = chatController.getComments(longId);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getMessage()).contains(longId);
        }

        @Test
        @DisplayName("Should handle UUID document ID")
        void addComment_UuidDocumentId_ReturnsOk() {
            // Given
            String uuidId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";

            // When
            ResponseEntity<ApiResponse<String>> response = chatController.addComment(uuidId);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getMessage()).contains(uuidId);
        }

        @Test
        @DisplayName("Should handle alphanumeric document ID")
        void getComments_AlphanumericDocId_ReturnsOk() {
            // Given
            String alphanumericId = "doc123abc";

            // When
            ResponseEntity<ApiResponse<String>> response = chatController.getComments(alphanumericId);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getMessage()).contains(alphanumericId);
        }
    }

    @Nested
    @DisplayName("Response Format Tests")
    class ResponseFormatTests {

        @Test
        @DisplayName("All endpoints should return ApiResponse wrapper")
        void allEndpoints_ReturnApiResponse() {
            assertThat(chatController.getMessages("test").getBody()).isInstanceOf(ApiResponse.class);
            assertThat(chatController.sendMessage("test").getBody()).isInstanceOf(ApiResponse.class);
            assertThat(chatController.getComments("test").getBody()).isInstanceOf(ApiResponse.class);
            assertThat(chatController.addComment("test").getBody()).isInstanceOf(ApiResponse.class);
        }

        @Test
        @DisplayName("All successful responses should have success=true")
        void allEndpoints_HaveSuccessTrue() {
            assertThat(chatController.getMessages("test").getBody().isSuccess()).isTrue();
            assertThat(chatController.sendMessage("test").getBody().isSuccess()).isTrue();
            assertThat(chatController.getComments("test").getBody().isSuccess()).isTrue();
            assertThat(chatController.addComment("test").getBody().isSuccess()).isTrue();
        }

        @Test
        @DisplayName("All responses should have HTTP 200 OK status")
        void allEndpoints_Return200() {
            assertThat(chatController.getMessages("test").getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(chatController.sendMessage("test").getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(chatController.getComments("test").getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(chatController.addComment("test").getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("All responses should have non-null body")
        void allEndpoints_HaveNonNullBody() {
            assertThat(chatController.getMessages("test").getBody()).isNotNull();
            assertThat(chatController.sendMessage("test").getBody()).isNotNull();
            assertThat(chatController.getComments("test").getBody()).isNotNull();
            assertThat(chatController.addComment("test").getBody()).isNotNull();
        }

        @Test
        @DisplayName("All responses should have message field")
        void allEndpoints_HaveMessage() {
            assertThat(chatController.getMessages("test").getBody().getMessage()).isNotBlank();
            assertThat(chatController.sendMessage("test").getBody().getMessage()).isNotBlank();
            assertThat(chatController.getComments("test").getBody().getMessage()).isNotBlank();
            assertThat(chatController.addComment("test").getBody().getMessage()).isNotBlank();
        }
    }

    @Nested
    @DisplayName("Service Identification Tests")
    class ServiceIdentificationTests {

        @Test
        @DisplayName("All responses should identify as Chat Service")
        void allEndpoints_IdentifyAsChatService() {
            assertThat(chatController.getMessages("test").getBody().getMessage()).contains("Chat Service");
            assertThat(chatController.sendMessage("test").getBody().getMessage()).contains("Chat Service");
            assertThat(chatController.getComments("test").getBody().getMessage()).contains("Chat Service");
            assertThat(chatController.addComment("test").getBody().getMessage()).contains("Chat Service");
        }
    }

    @Nested
    @DisplayName("Path Variable Tests")
    class PathVariableTests {

        @Test
        @DisplayName("Messages endpoint should work with various channel IDs")
        void getMessages_VariousIds_AllWork() {
            // Test various ID formats
            String[] ids = {"simple", "with-dash", "with_underscore", "UPPERCASE", "MixedCase123"};

            for (String id : ids) {
                ResponseEntity<ApiResponse<String>> response = chatController.getMessages(id);
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody().getMessage()).contains(id);
            }
        }

        @Test
        @DisplayName("Comments endpoint should work with various document IDs")
        void getComments_VariousIds_AllWork() {
            // Test various ID formats
            String[] ids = {"doc1", "doc-2", "doc_3", "DOC4", "Doc5Mixed"};

            for (String id : ids) {
                ResponseEntity<ApiResponse<String>> response = chatController.getComments(id);
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody().getMessage()).contains(id);
            }
        }
    }
}
