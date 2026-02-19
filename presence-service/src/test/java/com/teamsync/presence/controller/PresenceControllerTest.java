package com.teamsync.presence.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamsync.common.dto.ApiResponse;
import com.teamsync.presence.dto.*;
import com.teamsync.presence.exception.DocumentFullException;
import com.teamsync.presence.exception.GlobalExceptionHandler;
import com.teamsync.presence.exception.ParticipantNotFoundException;
import com.teamsync.presence.service.DocumentPresenceService;
import com.teamsync.presence.service.UserPresenceService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Presence Controller Tests")
class PresenceControllerTest {

    @Mock
    private UserPresenceService userPresenceService;

    @Mock
    private DocumentPresenceService documentPresenceService;

    @InjectMocks
    private PresenceController presenceController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private static final String TENANT_ID = "tenant-123";
    private static final String USER_ID = "user-456";
    private static final String DOCUMENT_ID = "doc-789";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(presenceController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
    }

    @Nested
    @DisplayName("Heartbeat Tests")
    class HeartbeatTests {

        @Test
        @DisplayName("Should return 200 when sending heartbeat")
        void heartbeat_ValidRequest_ReturnsOk() throws Exception {
            HeartbeatRequest request = HeartbeatRequest.builder()
                    .userId(USER_ID)
                    .status(UserPresenceDTO.PresenceStatus.ONLINE)
                    .build();

            HeartbeatResponse response = HeartbeatResponse.builder()
                    .acknowledged(true)
                    .serverTime(Instant.now())
                    .heartbeatIntervalSeconds(30)
                    .timeoutSeconds(120)
                    .currentStatus(UserPresenceDTO.PresenceStatus.ONLINE)
                    .sessionId("session-123")
                    .build();

            when(userPresenceService.processHeartbeat(any(), eq(TENANT_ID), eq(USER_ID), any(), any(), any()))
                    .thenReturn(response);

            mockMvc.perform(post("/api/presence/heartbeat")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-User-ID", USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.acknowledged").value(true));

            verify(userPresenceService).processHeartbeat(any(), eq(TENANT_ID), eq(USER_ID), any(), any(), any());
        }

        @Test
        @DisplayName("Should return 400 when tenant header missing")
        void heartbeat_MissingTenantHeader_ReturnsBadRequest() throws Exception {
            HeartbeatRequest request = HeartbeatRequest.builder()
                    .userId(USER_ID)
                    .build();

            mockMvc.perform(post("/api/presence/heartbeat")
                            .header("X-User-ID", USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Online Users Tests")
    class OnlineUsersTests {

        @Test
        @DisplayName("Should return online users")
        void getOnlineUsers_ReturnsUserList() throws Exception {
            List<UserPresenceDTO> users = List.of(
                    UserPresenceDTO.builder()
                            .userId("user-1")
                            .userName("User One")
                            .status(UserPresenceDTO.PresenceStatus.ONLINE)
                            .build(),
                    UserPresenceDTO.builder()
                            .userId("user-2")
                            .userName("User Two")
                            .status(UserPresenceDTO.PresenceStatus.AWAY)
                            .build()
            );

            when(userPresenceService.getOnlineUsers(TENANT_ID)).thenReturn(users);

            mockMvc.perform(get("/api/presence/online")
                            .header("X-Tenant-ID", TENANT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(2));

            verify(userPresenceService).getOnlineUsers(TENANT_ID);
        }

        @Test
        @DisplayName("Should return empty list when no users online")
        void getOnlineUsers_NoUsersOnline_ReturnsEmptyList() throws Exception {
            when(userPresenceService.getOnlineUsers(TENANT_ID)).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/presence/online")
                            .header("X-Tenant-ID", TENANT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }
    }

    @Nested
    @DisplayName("User Presence Tests")
    class UserPresenceTests {

        @Test
        @DisplayName("Should return user presence when user is online")
        void getUserPresence_UserOnline_ReturnsPresence() throws Exception {
            UserPresenceDTO presence = UserPresenceDTO.builder()
                    .userId(USER_ID)
                    .tenantId(TENANT_ID)
                    .userName("Test User")
                    .status(UserPresenceDTO.PresenceStatus.ONLINE)
                    .build();

            when(userPresenceService.getUserPresence(TENANT_ID, USER_ID)).thenReturn(Optional.of(presence));

            mockMvc.perform(get("/api/presence/user/{userId}", USER_ID)
                            .header("X-Tenant-ID", TENANT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.userId").value(USER_ID))
                    .andExpect(jsonPath("$.data.status").value("ONLINE"));

            verify(userPresenceService).getUserPresence(TENANT_ID, USER_ID);
        }

        @Test
        @DisplayName("Should return offline status when user not found")
        void getUserPresence_UserNotFound_ReturnsOffline() throws Exception {
            when(userPresenceService.getUserPresence(TENANT_ID, USER_ID)).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/presence/user/{userId}", USER_ID)
                            .header("X-Tenant-ID", TENANT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("OFFLINE"));
        }
    }

    @Nested
    @DisplayName("Document Presence Tests")
    class DocumentPresenceTests {

        @Test
        @DisplayName("Should return document presence")
        void getDocumentPresence_ValidDocId_ReturnsPresence() throws Exception {
            DocumentPresenceDTO presence = DocumentPresenceDTO.builder()
                    .documentId(DOCUMENT_ID)
                    .tenantId(TENANT_ID)
                    .viewers(List.of(
                            DocumentPresenceDTO.DocumentViewer.builder()
                                    .userId("viewer-1")
                                    .userName("Viewer One")
                                    .build()
                    ))
                    .editors(Collections.emptyList())
                    .totalViewers(1)
                    .totalEditors(0)
                    .build();

            when(documentPresenceService.getDocumentPresence(TENANT_ID, DOCUMENT_ID)).thenReturn(presence);

            mockMvc.perform(get("/api/presence/document/{documentId}", DOCUMENT_ID)
                            .header("X-Tenant-ID", TENANT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.documentId").value(DOCUMENT_ID))
                    .andExpect(jsonPath("$.data.totalViewers").value(1));

            verify(documentPresenceService).getDocumentPresence(TENANT_ID, DOCUMENT_ID);
        }
    }

    @Nested
    @DisplayName("Join Document Tests")
    class JoinDocumentTests {

        @Test
        @DisplayName("Should return 200 when joining document")
        void joinDocument_ValidRequest_ReturnsOk() throws Exception {
            JoinDocumentRequest request = JoinDocumentRequest.builder()
                    .documentId(DOCUMENT_ID)
                    .mode(JoinDocumentRequest.JoinMode.VIEW)
                    .build();

            JoinDocumentResponse response = JoinDocumentResponse.builder()
                    .joined(true)
                    .documentId(DOCUMENT_ID)
                    .sessionId("session-abc")
                    .assignedColor("#FF6B6B")
                    .joinedAt(Instant.now())
                    .totalParticipants(1)
                    .build();

            when(documentPresenceService.joinDocument(any(), eq(TENANT_ID), eq(USER_ID), any(), any(), any()))
                    .thenReturn(response);

            mockMvc.perform(post("/api/presence/document/{documentId}/join", DOCUMENT_ID)
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-User-ID", USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.joined").value(true))
                    .andExpect(jsonPath("$.data.assignedColor").value("#FF6B6B"));

            verify(documentPresenceService).joinDocument(any(), eq(TENANT_ID), eq(USER_ID), any(), any(), any());
        }

        @Test
        @DisplayName("Should return 409 when document is full")
        void joinDocument_DocumentFull_ReturnsConflict() throws Exception {
            JoinDocumentRequest request = JoinDocumentRequest.builder()
                    .documentId(DOCUMENT_ID)
                    .build();

            when(documentPresenceService.joinDocument(any(), eq(TENANT_ID), eq(USER_ID), any(), any(), any()))
                    .thenThrow(new DocumentFullException("Document has reached maximum viewer limit"));

            mockMvc.perform(post("/api/presence/document/{documentId}/join", DOCUMENT_ID)
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-User-ID", USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("DOCUMENT_FULL"));
        }
    }

    @Nested
    @DisplayName("Leave Document Tests")
    class LeaveDocumentTests {

        @Test
        @DisplayName("Should return 200 when leaving document")
        void leaveDocument_ValidRequest_ReturnsOk() throws Exception {
            doNothing().when(documentPresenceService).leaveDocument(TENANT_ID, DOCUMENT_ID, USER_ID);

            mockMvc.perform(post("/api/presence/document/{documentId}/leave", DOCUMENT_ID)
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-User-ID", USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Left document: " + DOCUMENT_ID));

            verify(documentPresenceService).leaveDocument(TENANT_ID, DOCUMENT_ID, USER_ID);
        }
    }

    @Nested
    @DisplayName("Cursor Update Tests")
    class CursorUpdateTests {

        @Test
        @DisplayName("Should return 200 when updating cursor")
        void updateCursor_ValidRequest_ReturnsOk() throws Exception {
            UpdateCursorRequest request = UpdateCursorRequest.builder()
                    .documentId(DOCUMENT_ID)
                    .state(DocumentPresenceDTO.EditorState.EDITING)
                    .cursorPosition(DocumentPresenceDTO.CursorPosition.builder()
                            .line(10)
                            .column(5)
                            .offset(150)
                            .build())
                    .build();

            doNothing().when(documentPresenceService).updateCursor(any(), eq(TENANT_ID), eq(USER_ID));

            mockMvc.perform(put("/api/presence/document/{documentId}/cursor", DOCUMENT_ID)
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-User-ID", USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(documentPresenceService).updateCursor(any(), eq(TENANT_ID), eq(USER_ID));
        }

        @Test
        @DisplayName("Should return 404 when user not in document")
        void updateCursor_UserNotInDocument_ReturnsNotFound() throws Exception {
            UpdateCursorRequest request = UpdateCursorRequest.builder()
                    .documentId(DOCUMENT_ID)
                    .build();

            doThrow(new ParticipantNotFoundException("User is not a participant in this document"))
                    .when(documentPresenceService).updateCursor(any(), eq(TENANT_ID), eq(USER_ID));

            mockMvc.perform(put("/api/presence/document/{documentId}/cursor", DOCUMENT_ID)
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-User-ID", USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("PARTICIPANT_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("Presence Stats Tests")
    class PresenceStatsTests {

        @Test
        @DisplayName("Should return presence stats")
        void getPresenceStats_ReturnsStats() throws Exception {
            PresenceStatsDTO stats = PresenceStatsDTO.builder()
                    .tenantId(TENANT_ID)
                    .totalOnlineUsers(10)
                    .totalAwayUsers(5)
                    .totalBusyUsers(2)
                    .totalActiveDocuments(3)
                    .totalActiveEditors(4)
                    .generatedAt(Instant.now())
                    .build();

            when(userPresenceService.getPresenceStats(TENANT_ID)).thenReturn(stats);

            mockMvc.perform(get("/api/presence/stats")
                            .header("X-Tenant-ID", TENANT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.totalOnlineUsers").value(10))
                    .andExpect(jsonPath("$.data.totalAwayUsers").value(5))
                    .andExpect(jsonPath("$.data.totalActiveDocuments").value(3));

            verify(userPresenceService).getPresenceStats(TENANT_ID);
        }
    }

    @Nested
    @DisplayName("Active Documents Tests")
    class ActiveDocumentsTests {

        @Test
        @DisplayName("Should return active documents")
        void getActiveDocuments_ReturnsDocumentList() throws Exception {
            List<DocumentPresenceDTO> documents = List.of(
                    DocumentPresenceDTO.builder()
                            .documentId("doc-1")
                            .documentName("Document 1")
                            .totalViewers(5)
                            .build(),
                    DocumentPresenceDTO.builder()
                            .documentId("doc-2")
                            .documentName("Document 2")
                            .totalViewers(3)
                            .build()
            );

            when(documentPresenceService.getActiveDocuments(TENANT_ID)).thenReturn(documents);

            mockMvc.perform(get("/api/presence/documents/active")
                            .header("X-Tenant-ID", TENANT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(2));

            verify(documentPresenceService).getActiveDocuments(TENANT_ID);
        }
    }

    @Nested
    @DisplayName("Health Check Tests")
    class HealthCheckTests {

        @Test
        @DisplayName("Should return healthy status")
        void healthCheck_ReturnsHealthy() throws Exception {
            mockMvc.perform(get("/api/presence/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").value("healthy"));
        }
    }
}
