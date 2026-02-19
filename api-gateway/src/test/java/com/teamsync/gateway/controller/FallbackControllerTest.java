package com.teamsync.gateway.controller;

import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive tests for FallbackController.
 * Tests all circuit breaker fallback endpoints.
 */
@DisplayName("Fallback Controller Tests")
class FallbackControllerTest {

    private FallbackController controller;

    @BeforeEach
    void setUp() {
        controller = new FallbackController();
    }

    @Nested
    @DisplayName("Document Service Fallback Tests")
    class DocumentServiceFallbackTests {

        @Test
        @DisplayName("Should return SERVICE_UNAVAILABLE for documents fallback")
        void documentsFallback_ReturnsCorrectStatus() {
            // When
            Mono<ResponseEntity<Map<String, Object>>> result = controller.documentsFallback();

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                        assertThat(response.getBody()).isNotNull();
                        assertThat(response.getBody().get("success")).isEqualTo(false);
                        assertThat(response.getBody().get("code")).isEqualTo("SERVICE_UNAVAILABLE");
                        assertThat(response.getBody().get("error").toString())
                                .contains("Document Service");
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Folder Service Fallback Tests")
    class FolderServiceFallbackTests {

        @Test
        @DisplayName("Should return SERVICE_UNAVAILABLE for folders fallback")
        void foldersFallback_ReturnsCorrectStatus() {
            // When
            Mono<ResponseEntity<Map<String, Object>>> result = controller.foldersFallback();

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                        assertThat(response.getBody()).isNotNull();
                        assertThat(response.getBody().get("success")).isEqualTo(false);
                        assertThat(response.getBody().get("error").toString())
                                .contains("Folder Service");
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Storage Service Fallback Tests")
    class StorageServiceFallbackTests {

        @Test
        @DisplayName("Should return SERVICE_UNAVAILABLE for storage fallback")
        void storageFallback_ReturnsCorrectStatus() {
            // When
            Mono<ResponseEntity<Map<String, Object>>> result = controller.storageFallback();

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                        assertThat(response.getBody().get("error").toString())
                                .contains("Storage Service");
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Sharing Service Fallback Tests")
    class SharingServiceFallbackTests {

        @Test
        @DisplayName("Should return SERVICE_UNAVAILABLE for sharing fallback")
        void sharingFallback_ReturnsCorrectStatus() {
            // When
            Mono<ResponseEntity<Map<String, Object>>> result = controller.sharingFallback();

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                        assertThat(response.getBody().get("error").toString())
                                .contains("Sharing Service");
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Team Service Fallback Tests")
    class TeamServiceFallbackTests {

        @Test
        @DisplayName("Should return SERVICE_UNAVAILABLE for teams fallback")
        void teamsFallback_ReturnsCorrectStatus() {
            // When
            Mono<ResponseEntity<Map<String, Object>>> result = controller.teamsFallback();

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                        assertThat(response.getBody().get("error").toString())
                                .contains("Team Service");
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Project Service Fallback Tests")
    class ProjectServiceFallbackTests {

        @Test
        @DisplayName("Should return SERVICE_UNAVAILABLE for projects fallback")
        void projectsFallback_ReturnsCorrectStatus() {
            // When
            Mono<ResponseEntity<Map<String, Object>>> result = controller.projectsFallback();

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                        assertThat(response.getBody().get("error").toString())
                                .contains("Project Service");
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Workflow Service Fallback Tests")
    class WorkflowServiceFallbackTests {

        @Test
        @DisplayName("Should return SERVICE_UNAVAILABLE for workflows fallback")
        void workflowsFallback_ReturnsCorrectStatus() {
            // When
            Mono<ResponseEntity<Map<String, Object>>> result = controller.workflowsFallback();

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                        assertThat(response.getBody().get("error").toString())
                                .contains("Workflow Execution Service");
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Trash Service Fallback Tests")
    class TrashServiceFallbackTests {

        @Test
        @DisplayName("Should return SERVICE_UNAVAILABLE for trash fallback")
        void trashFallback_ReturnsCorrectStatus() {
            // When
            Mono<ResponseEntity<Map<String, Object>>> result = controller.trashFallback();

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                        assertThat(response.getBody().get("error").toString())
                                .contains("Trash Service");
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Search Service Fallback Tests")
    class SearchServiceFallbackTests {

        @Test
        @DisplayName("Should return SERVICE_UNAVAILABLE for search fallback")
        void searchFallback_ReturnsCorrectStatus() {
            // When
            Mono<ResponseEntity<Map<String, Object>>> result = controller.searchFallback();

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                        assertThat(response.getBody().get("error").toString())
                                .contains("Search Service");
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Chat Service Fallback Tests")
    class ChatServiceFallbackTests {

        @Test
        @DisplayName("Should return SERVICE_UNAVAILABLE for chat fallback")
        void chatFallback_ReturnsCorrectStatus() {
            // When
            Mono<ResponseEntity<Map<String, Object>>> result = controller.chatFallback();

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                        assertThat(response.getBody().get("error").toString())
                                .contains("Chat Service");
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Notification Service Fallback Tests")
    class NotificationServiceFallbackTests {

        @Test
        @DisplayName("Should return SERVICE_UNAVAILABLE for notifications fallback")
        void notificationsFallback_ReturnsCorrectStatus() {
            // When
            Mono<ResponseEntity<Map<String, Object>>> result = controller.notificationsFallback();

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                        assertThat(response.getBody().get("error").toString())
                                .contains("Notification Service");
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Activity Service Fallback Tests")
    class ActivityServiceFallbackTests {

        @Test
        @DisplayName("Should return SERVICE_UNAVAILABLE for activities fallback")
        void activitiesFallback_ReturnsCorrectStatus() {
            // When
            Mono<ResponseEntity<Map<String, Object>>> result = controller.activitiesFallback();

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                        assertThat(response.getBody().get("error").toString())
                                .contains("Activity Service");
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("WOPI Service Fallback Tests")
    class WopiServiceFallbackTests {

        @Test
        @DisplayName("Should return SERVICE_UNAVAILABLE for WOPI fallback")
        void wopiFallback_ReturnsCorrectStatus() {
            // When
            Mono<ResponseEntity<Map<String, Object>>> result = controller.wopiFallback();

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                        assertThat(response.getBody().get("error").toString())
                                .contains("WOPI Service");
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Settings Service Fallback Tests")
    class SettingsServiceFallbackTests {

        @Test
        @DisplayName("Should return SERVICE_UNAVAILABLE for settings fallback")
        void settingsFallback_ReturnsCorrectStatus() {
            // When
            Mono<ResponseEntity<Map<String, Object>>> result = controller.settingsFallback();

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                        assertThat(response.getBody().get("error").toString())
                                .contains("Settings Service");
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Presence Service Fallback Tests")
    class PresenceServiceFallbackTests {

        @Test
        @DisplayName("Should return SERVICE_UNAVAILABLE for presence fallback")
        void presenceFallback_ReturnsCorrectStatus() {
            // When
            Mono<ResponseEntity<Map<String, Object>>> result = controller.presenceFallback();

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                        assertThat(response.getBody().get("error").toString())
                                .contains("Presence Service");
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Response Format Tests")
    class ResponseFormatTests {

        @Test
        @DisplayName("All fallbacks should have consistent response format")
        void allFallbacks_HaveConsistentFormat() {
            // Test all fallback endpoints have same structure
            assertConsistentResponse(controller.documentsFallback(), "Document Service");
            assertConsistentResponse(controller.foldersFallback(), "Folder Service");
            assertConsistentResponse(controller.storageFallback(), "Storage Service");
            assertConsistentResponse(controller.sharingFallback(), "Sharing Service");
            assertConsistentResponse(controller.teamsFallback(), "Team Service");
            assertConsistentResponse(controller.projectsFallback(), "Project Service");
            assertConsistentResponse(controller.workflowsFallback(), "Workflow Execution Service");
            assertConsistentResponse(controller.trashFallback(), "Trash Service");
            assertConsistentResponse(controller.searchFallback(), "Search Service");
            assertConsistentResponse(controller.chatFallback(), "Chat Service");
            assertConsistentResponse(controller.notificationsFallback(), "Notification Service");
            assertConsistentResponse(controller.activitiesFallback(), "Activity Service");
            assertConsistentResponse(controller.wopiFallback(), "WOPI Service");
            assertConsistentResponse(controller.settingsFallback(), "Settings Service");
            assertConsistentResponse(controller.presenceFallback(), "Presence Service");
        }

        private void assertConsistentResponse(Mono<ResponseEntity<Map<String, Object>>> mono, String serviceName) {
            StepVerifier.create(mono)
                    .assertNext(response -> {
                        // Verify HTTP status
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

                        // Verify body structure
                        Map<String, Object> body = response.getBody();
                        assertThat(body).isNotNull();
                        assertThat(body).containsKeys("success", "error", "code");

                        // Verify values
                        assertThat(body.get("success")).isEqualTo(false);
                        assertThat(body.get("code")).isEqualTo("SERVICE_UNAVAILABLE");
                        assertThat(body.get("error").toString()).contains(serviceName);
                        assertThat(body.get("error").toString()).contains("currently unavailable");
                        assertThat(body.get("error").toString()).contains("Please try again later");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Response body should be immutable map")
        void fallbackResponse_HasImmutableBody() {
            // When
            ResponseEntity<Map<String, Object>> response = controller.documentsFallback().block();

            // Then - Map.of creates immutable map
            assertThat(response).isNotNull();
            assertThat(response.getBody()).isNotNull();

            // Attempting to modify should throw exception
            Map<String, Object> body = response.getBody();
            Assertions.assertThrows(UnsupportedOperationException.class, () -> {
                body.put("newKey", "newValue");
            });
        }
    }

    @Nested
    @DisplayName("Error Message Tests")
    class ErrorMessageTests {

        @Test
        @DisplayName("Error messages should be user-friendly")
        void errorMessages_AreUserFriendly() {
            // When
            ResponseEntity<Map<String, Object>> response = controller.documentsFallback().block();

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getBody()).isNotNull();
            String errorMessage = response.getBody().get("error").toString();

            // Should be friendly to end users
            assertThat(errorMessage).doesNotContain("Exception");
            assertThat(errorMessage).doesNotContain("Error:");
            assertThat(errorMessage).doesNotContain("null");
            assertThat(errorMessage).contains("Please try again later");
        }

        @Test
        @DisplayName("Each service has distinct error message")
        void errorMessages_AreDistinct() {
            // Collect all error messages
            String docsError = controller.documentsFallback().block().getBody().get("error").toString();
            String storageError = controller.storageFallback().block().getBody().get("error").toString();
            String searchError = controller.searchFallback().block().getBody().get("error").toString();

            // All should be different
            assertThat(docsError).isNotEqualTo(storageError);
            assertThat(docsError).isNotEqualTo(searchError);
            assertThat(storageError).isNotEqualTo(searchError);
        }
    }

    @Nested
    @DisplayName("HTTP Status Code Tests")
    class HttpStatusCodeTests {

        @Test
        @DisplayName("All fallbacks should return 503 Service Unavailable")
        void allFallbacks_Return503() {
            // All fallbacks should return exactly 503, not 500 or other error codes
            assertThat(controller.documentsFallback().block().getStatusCode())
                    .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(controller.storageFallback().block().getStatusCode())
                    .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(controller.sharingFallback().block().getStatusCode())
                    .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        }

        @Test
        @DisplayName("503 status code value should be 503")
        void statusCode_Is503() {
            ResponseEntity<Map<String, Object>> response = controller.documentsFallback().block();
            assertThat(response.getStatusCode().value()).isEqualTo(503);
        }
    }

    @Nested
    @DisplayName("Reactive Stream Tests")
    class ReactiveStreamTests {

        @Test
        @DisplayName("Fallback should complete immediately")
        void fallback_CompletesImmediately() {
            // When
            Mono<ResponseEntity<Map<String, Object>>> result = controller.documentsFallback();

            // Then - Should complete without delay
            StepVerifier.create(result)
                    .expectNextCount(1)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Fallback should not throw exceptions")
        void fallback_DoesNotThrow() {
            // When/Then
            StepVerifier.create(controller.documentsFallback())
                    .expectNextMatches(response -> response.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Multiple subscriptions should work independently")
        void fallback_SupportsMultipleSubscriptions() {
            // Given
            Mono<ResponseEntity<Map<String, Object>>> mono = controller.documentsFallback();

            // When - Subscribe multiple times
            ResponseEntity<Map<String, Object>> response1 = mono.block();
            ResponseEntity<Map<String, Object>> response2 = mono.block();

            // Then - Both should succeed
            assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        }
    }
}
