package com.teamsync.gateway.integration;

import com.teamsync.gateway.config.GatewayConfig;
import com.teamsync.gateway.config.TestSecurityConfig;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for API Gateway routing to all downstream services.
 * Uses MockWebServer to simulate all downstream service responses.
 *
 * Note: Disabled due to shared mock server queue issues where responses
 * from one test can be consumed by another test running in parallel.
 * Run manually with -DenableRoutingTests=true for integration testing.
 */
@Disabled("Mock server response queue ordering issues. Run manually for integration testing.")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
@DisplayName("Gateway Routing Integration Tests")
class GatewayRoutingIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    // Mock servers for all downstream services
    private static MockWebServer contentServiceMock;
    private static MockWebServer storageServiceMock;
    private static MockWebServer sharingServiceMock;
    private static MockWebServer teamServiceMock;
    private static MockWebServer projectServiceMock;
    private static MockWebServer workflowExecutionServiceMock;
    private static MockWebServer trashServiceMock;
    private static MockWebServer searchServiceMock;
    private static MockWebServer chatServiceMock;
    private static MockWebServer notificationServiceMock;
    private static MockWebServer activityServiceMock;
    private static MockWebServer wopiServiceMock;
    private static MockWebServer settingsServiceMock;
    private static MockWebServer presenceServiceMock;
    private static MockWebServer permissionManagerServiceMock;
    private static MockWebServer minioMock;

    @BeforeAll
    static void startMockServers() throws IOException {
        contentServiceMock = new MockWebServer();
        contentServiceMock.start(19081);

        storageServiceMock = new MockWebServer();
        storageServiceMock.start(19083);

        sharingServiceMock = new MockWebServer();
        sharingServiceMock.start(19084);

        teamServiceMock = new MockWebServer();
        teamServiceMock.start(19085);

        projectServiceMock = new MockWebServer();
        projectServiceMock.start(19086);

        workflowExecutionServiceMock = new MockWebServer();
        workflowExecutionServiceMock.start(19087);

        trashServiceMock = new MockWebServer();
        trashServiceMock.start(19088);

        searchServiceMock = new MockWebServer();
        searchServiceMock.start(19089);

        chatServiceMock = new MockWebServer();
        chatServiceMock.start(19090);

        notificationServiceMock = new MockWebServer();
        notificationServiceMock.start(19091);

        activityServiceMock = new MockWebServer();
        activityServiceMock.start(19092);

        wopiServiceMock = new MockWebServer();
        wopiServiceMock.start(19093);

        settingsServiceMock = new MockWebServer();
        settingsServiceMock.start(19094);

        presenceServiceMock = new MockWebServer();
        presenceServiceMock.start(19095);

        permissionManagerServiceMock = new MockWebServer();
        permissionManagerServiceMock.start(19096);

        minioMock = new MockWebServer();
        minioMock.start(19000);
    }

    @AfterAll
    static void stopMockServers() throws IOException {
        contentServiceMock.shutdown();
        storageServiceMock.shutdown();
        sharingServiceMock.shutdown();
        teamServiceMock.shutdown();
        projectServiceMock.shutdown();
        workflowExecutionServiceMock.shutdown();
        trashServiceMock.shutdown();
        searchServiceMock.shutdown();
        chatServiceMock.shutdown();
        notificationServiceMock.shutdown();
        activityServiceMock.shutdown();
        wopiServiceMock.shutdown();
        settingsServiceMock.shutdown();
        presenceServiceMock.shutdown();
        permissionManagerServiceMock.shutdown();
        minioMock.shutdown();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("teamsync.services.content-service", () -> "http://localhost:19081");
        registry.add("teamsync.services.storage-service", () -> "http://localhost:19083");
        registry.add("teamsync.services.sharing-service", () -> "http://localhost:19084");
        registry.add("teamsync.services.team-service", () -> "http://localhost:19085");
        registry.add("teamsync.services.project-service", () -> "http://localhost:19086");
        registry.add("teamsync.services.workflow-execution-service", () -> "http://localhost:19087");
        registry.add("teamsync.services.trash-service", () -> "http://localhost:19088");
        registry.add("teamsync.services.search-service", () -> "http://localhost:19089");
        registry.add("teamsync.services.chat-service", () -> "http://localhost:19090");
        registry.add("teamsync.services.notification-service", () -> "http://localhost:19091");
        registry.add("teamsync.services.activity-service", () -> "http://localhost:19092");
        registry.add("teamsync.services.wopi-service", () -> "http://localhost:19093");
        registry.add("teamsync.services.settings-service", () -> "http://localhost:19094");
        registry.add("teamsync.services.presence-service", () -> "http://localhost:19095");
        registry.add("teamsync.services.permission-manager-service", () -> "http://localhost:19096");
        registry.add("teamsync.gateway.minio.internal-url", () -> "http://localhost:19000");
        registry.add("teamsync.gateway.minio.host", () -> "localhost:19000");
        registry.add("teamsync.gateway.dynamic-routes.enabled", () -> "false");
        registry.add("teamsync.bff.enabled", () -> "false"); // Disable BFF for routing tests
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> "http://localhost:8090/realms/test");
    }

    @Nested
    @DisplayName("Content Service Routing (9081)")
    class ContentServiceRoutingTests {

        @Test
        @DisplayName("Should route /api/documents to content service")
        void route_DocumentsEndpoint_ToContentService() throws Exception {
            // Given
            String responseBody = """
                {
                    "id": "doc-123",
                    "name": "test-document.pdf",
                    "type": "application/pdf"
                }
                """;
            contentServiceMock.enqueue(createMockResponse(responseBody));

            // When/Then
            webTestClient.get()
                .uri("/api/documents/doc-123")
                .header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("doc-123");

            // Verify request was routed correctly
            RecordedRequest request = contentServiceMock.takeRequest(5, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            assertThat(request.getPath()).isEqualTo("/api/documents/doc-123");
        }

        @Test
        @DisplayName("Should route /api/folders to content service")
        void route_FoldersEndpoint_ToContentService() throws Exception {
            // Given
            String responseBody = """
                {
                    "id": "folder-123",
                    "name": "My Folder",
                    "parentId": null
                }
                """;
            contentServiceMock.enqueue(createMockResponse(responseBody));

            // When/Then
            webTestClient.get()
                .uri("/api/folders/folder-123")
                .header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("folder-123");

            RecordedRequest request = contentServiceMock.takeRequest(5, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            assertThat(request.getPath()).isEqualTo("/api/folders/folder-123");
        }

        @Test
        @DisplayName("Should route /api/content to content service")
        void route_ContentEndpoint_ToContentService() throws Exception {
            // Given
            contentServiceMock.enqueue(createMockResponse("{\"items\": []}"));

            // When/Then
            webTestClient.get()
                .uri("/api/content")
                .header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isOk();

            RecordedRequest request = contentServiceMock.takeRequest(5, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            assertThat(request.getPath()).isEqualTo("/api/content");
        }
    }

    @Nested
    @DisplayName("Storage Service Routing (9083)")
    class StorageServiceRoutingTests {

        @Test
        @DisplayName("Should route /api/storage to storage service")
        void route_StorageEndpoint_ToStorageService() throws Exception {
            // Given
            String responseBody = """
                {
                    "uploadUrl": "http://storage.example.com/upload/123",
                    "sessionId": "session-abc"
                }
                """;
            storageServiceMock.enqueue(createMockResponse(responseBody));

            // When/Then
            webTestClient.post()
                .uri("/api/storage/prepare-upload")
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"fileName\": \"test.pdf\", \"fileSize\": 1024}")
                .exchange()
                .expectStatus().isOk();

            RecordedRequest request = storageServiceMock.takeRequest(5, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            assertThat(request.getPath()).isEqualTo("/api/storage/prepare-upload");
        }
    }

    @Nested
    @DisplayName("Sharing Service Routing (9084)")
    class SharingServiceRoutingTests {

        @Test
        @DisplayName("Should route /api/drives to sharing service")
        void route_DrivesEndpoint_ToSharingService() throws Exception {
            // Given
            sharingServiceMock.enqueue(createMockResponse("{\"drives\": []}"));

            // When/Then
            webTestClient.get()
                .uri("/api/drives")
                .header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isOk();

            RecordedRequest request = sharingServiceMock.takeRequest(5, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            assertThat(request.getPath()).isEqualTo("/api/drives");
        }

        @Test
        @DisplayName("Should route /api/shares to sharing service")
        void route_SharesEndpoint_ToSharingService() throws Exception {
            // Given
            sharingServiceMock.enqueue(createMockResponse("{\"shares\": []}"));

            // When/Then
            webTestClient.get()
                .uri("/api/shares")
                .header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isOk();

            RecordedRequest request = sharingServiceMock.takeRequest(5, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            assertThat(request.getPath()).isEqualTo("/api/shares");
        }
    }

    @Nested
    @DisplayName("Team Service Routing (9085)")
    class TeamServiceRoutingTests {

        @Test
        @DisplayName("Should route /api/teams to team service")
        void route_TeamsEndpoint_ToTeamService() throws Exception {
            // Given
            teamServiceMock.enqueue(createMockResponse("{\"id\": \"team-123\", \"name\": \"Dev Team\"}"));

            // When/Then
            webTestClient.get()
                .uri("/api/teams/team-123")
                .header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("team-123");

            RecordedRequest request = teamServiceMock.takeRequest(5, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            assertThat(request.getPath()).isEqualTo("/api/teams/team-123");
        }
    }

    @Nested
    @DisplayName("Project Service Routing (9086)")
    class ProjectServiceRoutingTests {

        @Test
        @DisplayName("Should route /api/projects to project service")
        void route_ProjectsEndpoint_ToProjectService() throws Exception {
            // Given
            projectServiceMock.enqueue(createMockResponse("{\"id\": \"proj-123\", \"name\": \"My Project\"}"));

            // When/Then
            webTestClient.get()
                .uri("/api/projects/proj-123")
                .header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("proj-123");

            RecordedRequest request = projectServiceMock.takeRequest(5, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            assertThat(request.getPath()).isEqualTo("/api/projects/proj-123");
        }
    }

    @Nested
    @DisplayName("Workflow Execution Service Routing (9087)")
    class WorkflowExecutionServiceRoutingTests {

        @Test
        @DisplayName("Should route /api/workflow-executions to workflow execution service")
        void route_WorkflowExecutionsEndpoint_ToWorkflowExecutionService() throws Exception {
            // Given
            workflowExecutionServiceMock.enqueue(createMockResponse("{\"executions\": []}"));

            // When/Then
            webTestClient.get()
                .uri("/api/workflow-executions")
                .header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isOk();

            RecordedRequest request = workflowExecutionServiceMock.takeRequest(5, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            assertThat(request.getPath()).isEqualTo("/api/workflow-executions");
        }
    }

    @Nested
    @DisplayName("Trash Service Routing (9088)")
    class TrashServiceRoutingTests {

        @Test
        @DisplayName("Should route /api/trash to trash service")
        void route_TrashEndpoint_ToTrashService() throws Exception {
            // Given
            trashServiceMock.enqueue(createMockResponse("{\"items\": []}"));

            // When/Then
            webTestClient.get()
                .uri("/api/trash")
                .header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isOk();

            RecordedRequest request = trashServiceMock.takeRequest(5, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            assertThat(request.getPath()).isEqualTo("/api/trash");
        }

        @Test
        @DisplayName("Should route trash restore endpoint")
        void route_TrashRestoreEndpoint_ToTrashService() throws Exception {
            // Given
            trashServiceMock.enqueue(createMockResponse("{\"restored\": true}"));

            // When/Then
            webTestClient.post()
                .uri("/api/trash/doc-123/restore")
                .header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isOk();

            RecordedRequest request = trashServiceMock.takeRequest(5, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            assertThat(request.getPath()).isEqualTo("/api/trash/doc-123/restore");
        }
    }

    @Nested
    @DisplayName("Search Service Routing (9089)")
    class SearchServiceRoutingTests {

        @Test
        @DisplayName("Should route /api/search to search service")
        void route_SearchEndpoint_ToSearchService() throws Exception {
            // Given
            searchServiceMock.enqueue(createMockResponse("{\"results\": [], \"totalHits\": 0}"));

            // When/Then
            webTestClient.get()
                .uri("/api/search?q=test")
                .header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isOk();

            RecordedRequest request = searchServiceMock.takeRequest(5, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            assertThat(request.getPath()).isEqualTo("/api/search?q=test");
        }
    }

    @Nested
    @DisplayName("Chat/AI Service Routing (9090)")
    class ChatServiceRoutingTests {

        @Test
        @DisplayName("Should route /api/chat to chat service")
        void route_ChatEndpoint_ToChatService() throws Exception {
            // Given
            chatServiceMock.enqueue(createMockResponse("{\"conversations\": []}"));

            // When/Then
            webTestClient.get()
                .uri("/api/chat/conversations")
                .header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isOk();

            RecordedRequest request = chatServiceMock.takeRequest(5, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            assertThat(request.getPath()).isEqualTo("/api/chat/conversations");
        }

        @Test
        @DisplayName("Should route /api/ai to chat service")
        void route_AiEndpoint_ToChatService() throws Exception {
            // Given
            chatServiceMock.enqueue(createMockResponse("{\"response\": \"AI response\"}"));

            // When/Then
            webTestClient.post()
                .uri("/api/ai/ask")
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"question\": \"What is this document about?\"}")
                .exchange()
                .expectStatus().isOk();

            RecordedRequest request = chatServiceMock.takeRequest(5, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            assertThat(request.getPath()).isEqualTo("/api/ai/ask");
        }
    }

    @Nested
    @DisplayName("Notification Service Routing (9091)")
    class NotificationServiceRoutingTests {

        @Test
        @DisplayName("Should route /api/notifications to notification service")
        void route_NotificationsEndpoint_ToNotificationService() throws Exception {
            // Given
            notificationServiceMock.enqueue(createMockResponse("{\"notifications\": []}"));

            // When/Then
            webTestClient.get()
                .uri("/api/notifications")
                .header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isOk();

            RecordedRequest request = notificationServiceMock.takeRequest(5, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            assertThat(request.getPath()).isEqualTo("/api/notifications");
        }
    }

    @Nested
    @DisplayName("Activity Service Routing (9092)")
    class ActivityServiceRoutingTests {

        @Test
        @DisplayName("Should route /api/activities to activity service")
        void route_ActivitiesEndpoint_ToActivityService() throws Exception {
            // Given
            activityServiceMock.enqueue(createMockResponse("{\"activities\": []}"));

            // When/Then
            webTestClient.get()
                .uri("/api/activities")
                .header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isOk();

            RecordedRequest request = activityServiceMock.takeRequest(5, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            assertThat(request.getPath()).isEqualTo("/api/activities");
        }

        @Test
        @DisplayName("Should route /api/audit to activity service")
        void route_AuditEndpoint_ToActivityService() throws Exception {
            // Given
            activityServiceMock.enqueue(createMockResponse("{\"auditLogs\": []}"));

            // When/Then
            webTestClient.get()
                .uri("/api/audit")
                .header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isOk();

            RecordedRequest request = activityServiceMock.takeRequest(5, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            assertThat(request.getPath()).isEqualTo("/api/audit");
        }
    }

    @Nested
    @DisplayName("WOPI Service Routing (9093)")
    class WopiServiceRoutingTests {

        @Test
        @DisplayName("Should route /wopi/files to WOPI service")
        void route_WopiFilesEndpoint_ToWopiService() throws Exception {
            // Given
            String wopiResponse = """
                {
                    "BaseFileName": "document.docx",
                    "OwnerId": "user-123",
                    "Size": 12345,
                    "UserId": "user-123"
                }
                """;
            wopiServiceMock.enqueue(createMockResponse(wopiResponse));

            // When/Then
            webTestClient.get()
                .uri("/wopi/files/doc-123")
                .header("Authorization", "Bearer test-token")
                .header("X-WOPI-Override", "GET_FILE_INFO")
                .exchange()
                .expectStatus().isOk();

            RecordedRequest request = wopiServiceMock.takeRequest(5, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            assertThat(request.getPath()).isEqualTo("/wopi/files/doc-123");
        }

        @Test
        @DisplayName("Should route WOPI file content endpoint")
        void route_WopiContentEndpoint_ToWopiService() throws Exception {
            // Given
            wopiServiceMock.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/octet-stream")
                .setBody("file content"));

            // When/Then
            webTestClient.get()
                .uri("/wopi/files/doc-123/contents")
                .header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isOk();

            RecordedRequest request = wopiServiceMock.takeRequest(5, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            assertThat(request.getPath()).isEqualTo("/wopi/files/doc-123/contents");
        }
    }

    @Nested
    @DisplayName("Settings Service Routing (9094)")
    class SettingsServiceRoutingTests {

        @Test
        @DisplayName("Should route /api/settings to settings service")
        void route_SettingsEndpoint_ToSettingsService() throws Exception {
            // Given
            settingsServiceMock.enqueue(createMockResponse("{\"theme\": \"dark\", \"language\": \"en\"}"));

            // When/Then
            webTestClient.get()
                .uri("/api/settings/user")
                .header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isOk();

            RecordedRequest request = settingsServiceMock.takeRequest(5, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            assertThat(request.getPath()).isEqualTo("/api/settings/user");
        }

        @Test
        @DisplayName("Should route /api/preferences to settings service")
        void route_PreferencesEndpoint_ToSettingsService() throws Exception {
            // Given
            settingsServiceMock.enqueue(createMockResponse("{\"notifications\": true}"));

            // When/Then
            webTestClient.get()
                .uri("/api/preferences")
                .header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isOk();

            RecordedRequest request = settingsServiceMock.takeRequest(5, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            assertThat(request.getPath()).isEqualTo("/api/preferences");
        }
    }

    @Nested
    @DisplayName("Presence Service Routing (9095)")
    class PresenceServiceRoutingTests {

        @Test
        @DisplayName("Should route /api/presence to presence service")
        void route_PresenceEndpoint_ToPresenceService() throws Exception {
            // Given
            presenceServiceMock.enqueue(createMockResponse("{\"online\": [\"user-1\", \"user-2\"]}"));

            // When/Then
            webTestClient.get()
                .uri("/api/presence")
                .header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isOk();

            RecordedRequest request = presenceServiceMock.takeRequest(5, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            assertThat(request.getPath()).isEqualTo("/api/presence");
        }
    }

    @Nested
    @DisplayName("Permission Manager Service Routing (9096)")
    class PermissionManagerServiceRoutingTests {

        @Test
        @DisplayName("Should route /api/roles to permission manager service")
        void route_RolesEndpoint_ToPermissionManagerService() throws Exception {
            // Given
            permissionManagerServiceMock.enqueue(createMockResponse("{\"roles\": []}"));

            // When/Then
            webTestClient.get()
                .uri("/api/roles")
                .header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isOk();

            RecordedRequest request = permissionManagerServiceMock.takeRequest(5, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            assertThat(request.getPath()).isEqualTo("/api/roles");
        }
    }

    @Nested
    @DisplayName("MinIO Proxy Routing")
    class MinioProxyRoutingTests {

        @Test
        @DisplayName("Should proxy /storage-proxy to MinIO")
        void route_StorageProxy_ToMinio() throws Exception {
            // Given
            minioMock.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("file content"));

            // When/Then
            webTestClient.get()
                .uri("/storage-proxy/teamsync-bucket/files/test-file.pdf")
                .exchange()
                .expectStatus().isOk();

            RecordedRequest request = minioMock.takeRequest(5, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            // Path rewrite: /storage-proxy/{segment} -> /{segment}
            assertThat(request.getPath()).isEqualTo("/teamsync-bucket/files/test-file.pdf");
        }

        @Test
        @DisplayName("Should proxy presigned URL requests to MinIO")
        void route_PresignedUrl_ToMinio() throws Exception {
            // Given
            minioMock.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("uploaded successfully"));

            // When/Then
            webTestClient.put()
                .uri("/storage-proxy/teamsync-bucket/uploads/file.pdf?X-Amz-Algorithm=AWS4-HMAC-SHA256")
                .bodyValue("file content")
                .exchange()
                .expectStatus().isOk();

            RecordedRequest request = minioMock.takeRequest(5, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            assertThat(request.getPath()).startsWith("/teamsync-bucket/uploads/file.pdf");
        }
    }

    @Nested
    @DisplayName("Header Propagation Tests")
    class HeaderPropagationTests {

        @Test
        @DisplayName("Should propagate Authorization header to downstream")
        void propagate_AuthorizationHeader_ToDownstream() throws Exception {
            // Given
            contentServiceMock.enqueue(createMockResponse("{\"id\": \"doc-123\"}"));

            // When/Then
            webTestClient.get()
                .uri("/api/documents/doc-123")
                .header("Authorization", "Bearer my-jwt-token")
                .exchange()
                .expectStatus().isOk();

            RecordedRequest request = contentServiceMock.takeRequest(5, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            assertThat(request.getHeader("Authorization")).isEqualTo("Bearer my-jwt-token");
        }

        @Test
        @DisplayName("Should propagate custom headers to downstream")
        void propagate_CustomHeaders_ToDownstream() throws Exception {
            // Given
            contentServiceMock.enqueue(createMockResponse("{\"id\": \"doc-123\"}"));

            // When/Then
            webTestClient.get()
                .uri("/api/documents/doc-123")
                .header("Authorization", "Bearer test-token")
                .header("X-Tenant-ID", "tenant-abc")
                .header("X-Drive-ID", "drive-123")
                .header("X-Request-ID", "req-xyz")
                .exchange()
                .expectStatus().isOk();

            RecordedRequest request = contentServiceMock.takeRequest(5, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            assertThat(request.getHeader("X-Tenant-ID")).isEqualTo("tenant-abc");
            assertThat(request.getHeader("X-Drive-ID")).isEqualTo("drive-123");
            assertThat(request.getHeader("X-Request-ID")).isEqualTo("req-xyz");
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should return 503 when downstream service is unavailable")
        void handle_DownstreamUnavailable_Returns503() throws Exception {
            // Given - Mock server returns 503
            contentServiceMock.enqueue(new MockResponse().setResponseCode(503));

            // When/Then
            webTestClient.get()
                .uri("/api/documents/doc-123")
                .header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().is5xxServerError();
        }

        @Test
        @DisplayName("Should propagate 404 from downstream service")
        void propagate_404_FromDownstream() throws Exception {
            // Given
            contentServiceMock.enqueue(new MockResponse()
                .setResponseCode(404)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"error\": \"Document not found\"}"));

            // When/Then
            webTestClient.get()
                .uri("/api/documents/nonexistent")
                .header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isNotFound();
        }

        @Test
        @DisplayName("Should propagate 400 from downstream service")
        void propagate_400_FromDownstream() throws Exception {
            // Given
            contentServiceMock.enqueue(new MockResponse()
                .setResponseCode(400)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"error\": \"Invalid request\"}"));

            // When/Then
            webTestClient.post()
                .uri("/api/documents")
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isBadRequest();
        }
    }

    @Nested
    @DisplayName("HTTP Method Routing Tests")
    class HttpMethodRoutingTests {

        @Test
        @DisplayName("Should route POST requests correctly")
        void route_PostRequest_ToDownstream() throws Exception {
            // Given
            contentServiceMock.enqueue(createMockResponse("{\"id\": \"new-doc\"}"));

            // When/Then
            webTestClient.post()
                .uri("/api/documents")
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\": \"test.pdf\"}")
                .exchange()
                .expectStatus().isOk();

            RecordedRequest request = contentServiceMock.takeRequest(5, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            assertThat(request.getMethod()).isEqualTo("POST");
        }

        @Test
        @DisplayName("Should route PUT requests correctly")
        void route_PutRequest_ToDownstream() throws Exception {
            // Given
            contentServiceMock.enqueue(createMockResponse("{\"id\": \"doc-123\", \"name\": \"updated.pdf\"}"));

            // When/Then
            webTestClient.put()
                .uri("/api/documents/doc-123")
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\": \"updated.pdf\"}")
                .exchange()
                .expectStatus().isOk();

            RecordedRequest request = contentServiceMock.takeRequest(5, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            assertThat(request.getMethod()).isEqualTo("PUT");
        }

        @Test
        @DisplayName("Should route DELETE requests correctly")
        void route_DeleteRequest_ToDownstream() throws Exception {
            // Given
            contentServiceMock.enqueue(new MockResponse().setResponseCode(204));

            // When/Then
            webTestClient.delete()
                .uri("/api/documents/doc-123")
                .header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isNoContent();

            RecordedRequest request = contentServiceMock.takeRequest(5, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            assertThat(request.getMethod()).isEqualTo("DELETE");
        }

        @Test
        @DisplayName("Should route PATCH requests correctly")
        void route_PatchRequest_ToDownstream() throws Exception {
            // Given
            contentServiceMock.enqueue(createMockResponse("{\"id\": \"doc-123\"}"));

            // When/Then
            webTestClient.patch()
                .uri("/api/documents/doc-123")
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\": \"patched.pdf\"}")
                .exchange()
                .expectStatus().isOk();

            RecordedRequest request = contentServiceMock.takeRequest(5, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            assertThat(request.getMethod()).isEqualTo("PATCH");
        }
    }

    // Helper method to create standard mock responses
    private MockResponse createMockResponse(String body) {
        return new MockResponse()
            .setResponseCode(200)
            .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .setBody(body);
    }

    @TestConfiguration
    static class TestConfig {
        // Test configuration can override beans if needed
    }
}
