package com.teamsync.notification.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamsync.notification.dto.*;
import com.teamsync.notification.model.Notification;
import com.teamsync.notification.service.NotificationService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for NotificationController.
 * Tests all notification endpoints with mocked service layer.
 */
@WebMvcTest(NotificationController.class)
@ExtendWith(MockitoExtension.class)
@DisplayName("Notification Controller Tests")
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private NotificationService notificationService;

    private static final String TENANT_ID = "tenant-123";
    private static final String USER_ID = "user-456";
    private static final String NOTIFICATION_ID = "notif-789";

    // ==================== List Notifications Tests ====================

    @Nested
    @DisplayName("List Notifications Tests")
    class ListNotificationsTests {

        @Test
        @DisplayName("Should return paginated notifications")
        void listNotifications_ReturnsPage() throws Exception {
            // Given
            NotificationDTO dto = createNotificationDTO();
            Page<NotificationDTO> page = new PageImpl<>(List.of(dto));

            when(notificationService.getNotifications(
                    eq(TENANT_ID), eq(USER_ID), anyBoolean(), anyBoolean(),
                    any(), anyInt(), anyInt()
            )).thenReturn(page);

            // When/Then
            mockMvc.perform(get("/api/notifications")
                            .with(jwt().jwt(jwt -> jwt
                                    .subject(USER_ID)
                                    .claim("tenant_id", TENANT_ID)
                                    .claim("name", "Test User")))
                            .header("X-Tenant-ID", TENANT_ID)
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content").isArray());
        }

        @Test
        @DisplayName("Should filter unread only notifications")
        void listNotifications_UnreadOnly_ReturnsFiltered() throws Exception {
            // Given
            Page<NotificationDTO> page = new PageImpl<>(Collections.emptyList());
            when(notificationService.getNotifications(
                    eq(TENANT_ID), eq(USER_ID), eq(true), anyBoolean(),
                    any(), anyInt(), anyInt()
            )).thenReturn(page);

            // When/Then
            mockMvc.perform(get("/api/notifications")
                            .with(jwt().jwt(jwt -> jwt
                                    .subject(USER_ID)
                                    .claim("tenant_id", TENANT_ID)
                                    .claim("name", "Test User")))
                            .header("X-Tenant-ID", TENANT_ID)
                            .param("unreadOnly", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(notificationService).getNotifications(
                    TENANT_ID, USER_ID, true, false, null, 0, 20);
        }

        @Test
        @DisplayName("Should filter by notification type")
        void listNotifications_ByType_ReturnsFiltered() throws Exception {
            // Given
            Page<NotificationDTO> page = new PageImpl<>(Collections.emptyList());
            when(notificationService.getNotifications(
                    eq(TENANT_ID), eq(USER_ID), anyBoolean(), anyBoolean(),
                    eq(Notification.NotificationType.COMMENT), anyInt(), anyInt()
            )).thenReturn(page);

            // When/Then
            mockMvc.perform(get("/api/notifications")
                            .with(jwt().jwt(jwt -> jwt
                                    .subject(USER_ID)
                                    .claim("tenant_id", TENANT_ID)
                                    .claim("name", "Test User")))
                            .header("X-Tenant-ID", TENANT_ID)
                            .param("type", "COMMENT"))
                    .andExpect(status().isOk());

            verify(notificationService).getNotifications(
                    TENANT_ID, USER_ID, false, false, Notification.NotificationType.COMMENT, 0, 20);
        }

        @Test
        @DisplayName("Should return 401 without authentication")
        void listNotifications_NoAuth_Returns401() throws Exception {
            mockMvc.perform(get("/api/notifications"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ==================== Get Notification Tests ====================

    @Nested
    @DisplayName("Get Notification Tests")
    class GetNotificationTests {

        @Test
        @DisplayName("Should return notification by ID")
        void getNotification_ValidId_ReturnsNotification() throws Exception {
            // Given
            NotificationDTO dto = createNotificationDTO();
            when(notificationService.getNotification(TENANT_ID, USER_ID, NOTIFICATION_ID))
                    .thenReturn(dto);

            // When/Then
            mockMvc.perform(get("/api/notifications/{id}", NOTIFICATION_ID)
                            .with(jwt().jwt(jwt -> jwt
                                    .subject(USER_ID)
                                    .claim("tenant_id", TENANT_ID)
                                    .claim("name", "Test User")))
                            .header("X-Tenant-ID", TENANT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(NOTIFICATION_ID));
        }
    }

    // ==================== Unread Count Tests ====================

    @Nested
    @DisplayName("Unread Count Tests")
    class UnreadCountTests {

        @Test
        @DisplayName("Should return unread count with breakdown")
        void getUnreadCount_ReturnsCount() throws Exception {
            // Given
            NotificationCountDTO countDTO = NotificationCountDTO.builder()
                    .unreadCount(5)
                    .countByType(Map.of("COMMENT", 3L, "SHARE", 2L))
                    .countByPriority(Map.of("NORMAL", 4L, "HIGH", 1L))
                    .hasUrgent(false)
                    .build();

            when(notificationService.getUnreadCount(TENANT_ID, USER_ID))
                    .thenReturn(countDTO);

            // When/Then
            mockMvc.perform(get("/api/notifications/unread/count")
                            .with(jwt().jwt(jwt -> jwt
                                    .subject(USER_ID)
                                    .claim("tenant_id", TENANT_ID)
                                    .claim("name", "Test User")))
                            .header("X-Tenant-ID", TENANT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.unreadCount").value(5))
                    .andExpect(jsonPath("$.data.hasUrgent").value(false));
        }
    }

    // ==================== Mark As Read Tests ====================

    @Nested
    @DisplayName("Mark As Read Tests")
    class MarkAsReadTests {

        @Test
        @DisplayName("Should mark notification as read")
        void markAsRead_ValidId_MarksRead() throws Exception {
            // Given
            NotificationDTO dto = createNotificationDTO();
            dto.setIsRead(true);
            dto.setReadAt(Instant.now());

            when(notificationService.markAsRead(TENANT_ID, USER_ID, NOTIFICATION_ID))
                    .thenReturn(dto);

            // When/Then
            mockMvc.perform(put("/api/notifications/{id}/read", NOTIFICATION_ID)
                            .with(jwt().jwt(jwt -> jwt
                                    .subject(USER_ID)
                                    .claim("tenant_id", TENANT_ID)
                                    .claim("name", "Test User")))
                            .header("X-Tenant-ID", TENANT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.isRead").value(true));
        }

        @Test
        @DisplayName("Should mark notification as unread")
        void markAsUnread_ValidId_MarksUnread() throws Exception {
            // Given
            NotificationDTO dto = createNotificationDTO();
            dto.setIsRead(false);

            when(notificationService.markAsUnread(TENANT_ID, USER_ID, NOTIFICATION_ID))
                    .thenReturn(dto);

            // When/Then
            mockMvc.perform(delete("/api/notifications/{id}/read", NOTIFICATION_ID)
                            .with(jwt().jwt(jwt -> jwt
                                    .subject(USER_ID)
                                    .claim("tenant_id", TENANT_ID)
                                    .claim("name", "Test User")))
                            .header("X-Tenant-ID", TENANT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.isRead").value(false));
        }

        @Test
        @DisplayName("Should mark all notifications as read")
        void markAllAsRead_MarksAll() throws Exception {
            // Given
            when(notificationService.markAllAsRead(TENANT_ID, USER_ID))
                    .thenReturn(10L);

            // When/Then
            mockMvc.perform(put("/api/notifications/read-all")
                            .with(jwt().jwt(jwt -> jwt
                                    .subject(USER_ID)
                                    .claim("tenant_id", TENANT_ID)
                                    .claim("name", "Test User")))
                            .header("X-Tenant-ID", TENANT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").value(10));
        }

        @Test
        @DisplayName("Should mark type as read")
        void markTypeAsRead_ValidType_MarksAll() throws Exception {
            // Given
            when(notificationService.markTypeAsRead(TENANT_ID, USER_ID, Notification.NotificationType.COMMENT))
                    .thenReturn(5L);

            // When/Then
            mockMvc.perform(put("/api/notifications/read-all")
                            .with(jwt().jwt(jwt -> jwt
                                    .subject(USER_ID)
                                    .claim("tenant_id", TENANT_ID)
                                    .claim("name", "Test User")))
                            .header("X-Tenant-ID", TENANT_ID)
                            .param("type", "COMMENT"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value(5));
        }
    }

    // ==================== Archive Tests ====================

    @Nested
    @DisplayName("Archive Tests")
    class ArchiveTests {

        @Test
        @DisplayName("Should archive notification")
        void archiveNotification_ValidId_Archives() throws Exception {
            // Given
            NotificationDTO dto = createNotificationDTO();
            dto.setIsArchived(true);

            when(notificationService.archiveNotification(TENANT_ID, USER_ID, NOTIFICATION_ID))
                    .thenReturn(dto);

            // When/Then
            mockMvc.perform(put("/api/notifications/{id}/archive", NOTIFICATION_ID)
                            .with(jwt().jwt(jwt -> jwt
                                    .subject(USER_ID)
                                    .claim("tenant_id", TENANT_ID)
                                    .claim("name", "Test User")))
                            .header("X-Tenant-ID", TENANT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.isArchived").value(true));
        }

        @Test
        @DisplayName("Should unarchive notification")
        void unarchiveNotification_ValidId_Unarchives() throws Exception {
            // Given
            NotificationDTO dto = createNotificationDTO();
            dto.setIsArchived(false);

            when(notificationService.unarchiveNotification(TENANT_ID, USER_ID, NOTIFICATION_ID))
                    .thenReturn(dto);

            // When/Then
            mockMvc.perform(delete("/api/notifications/{id}/archive", NOTIFICATION_ID)
                            .with(jwt().jwt(jwt -> jwt
                                    .subject(USER_ID)
                                    .claim("tenant_id", TENANT_ID)
                                    .claim("name", "Test User")))
                            .header("X-Tenant-ID", TENANT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.isArchived").value(false));
        }

        @Test
        @DisplayName("Should get archived notifications")
        void getArchivedNotifications_ReturnsList() throws Exception {
            // Given
            Page<NotificationDTO> page = new PageImpl<>(Collections.emptyList());
            when(notificationService.getArchivedNotifications(TENANT_ID, USER_ID, 0, 20))
                    .thenReturn(page);

            // When/Then
            mockMvc.perform(get("/api/notifications/archived")
                            .with(jwt().jwt(jwt -> jwt
                                    .subject(USER_ID)
                                    .claim("tenant_id", TENANT_ID)
                                    .claim("name", "Test User")))
                            .header("X-Tenant-ID", TENANT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    // ==================== Delete Tests ====================

    @Nested
    @DisplayName("Delete Tests")
    class DeleteTests {

        @Test
        @DisplayName("Should delete notification")
        void deleteNotification_ValidId_Deletes() throws Exception {
            // Given
            doNothing().when(notificationService)
                    .deleteNotification(TENANT_ID, USER_ID, NOTIFICATION_ID);

            // When/Then
            mockMvc.perform(delete("/api/notifications/{id}", NOTIFICATION_ID)
                            .with(jwt().jwt(jwt -> jwt
                                    .subject(USER_ID)
                                    .claim("tenant_id", TENANT_ID)
                                    .claim("name", "Test User")))
                            .header("X-Tenant-ID", TENANT_ID))
                    .andExpect(status().isNoContent());

            verify(notificationService).deleteNotification(TENANT_ID, USER_ID, NOTIFICATION_ID);
        }
    }

    // ==================== Bulk Operations Tests ====================

    @Nested
    @DisplayName("Bulk Operations Tests")
    class BulkOperationsTests {

        @Test
        @DisplayName("Should perform bulk mark read")
        void bulkOperation_MarkRead_Success() throws Exception {
            // Given
            BulkNotificationRequest request = new BulkNotificationRequest();
            request.setNotificationIds(List.of("id1", "id2", "id3"));
            request.setOperation(BulkNotificationRequest.BulkOperation.MARK_READ);

            when(notificationService.bulkOperation(eq(TENANT_ID), eq(USER_ID), any()))
                    .thenReturn(3);

            // When/Then
            mockMvc.perform(post("/api/notifications/bulk")
                            .with(jwt().jwt(jwt -> jwt
                                    .subject(USER_ID)
                                    .claim("tenant_id", TENANT_ID)
                                    .claim("name", "Test User")))
                            .header("X-Tenant-ID", TENANT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value(3));
        }

        @Test
        @DisplayName("Should perform bulk delete")
        void bulkOperation_Delete_Success() throws Exception {
            // Given
            BulkNotificationRequest request = new BulkNotificationRequest();
            request.setNotificationIds(List.of("id1", "id2"));
            request.setOperation(BulkNotificationRequest.BulkOperation.DELETE);

            when(notificationService.bulkOperation(eq(TENANT_ID), eq(USER_ID), any()))
                    .thenReturn(2);

            // When/Then
            mockMvc.perform(post("/api/notifications/bulk")
                            .with(jwt().jwt(jwt -> jwt
                                    .subject(USER_ID)
                                    .claim("tenant_id", TENANT_ID)
                                    .claim("name", "Test User")))
                            .header("X-Tenant-ID", TENANT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value(2));
        }
    }

    // ==================== Preferences Tests ====================

    @Nested
    @DisplayName("Preferences Tests")
    class PreferencesTests {

        @Test
        @DisplayName("Should get user preferences")
        void getPreferences_ReturnsPreferences() throws Exception {
            // Given
            NotificationPreferenceDTO dto = createPreferenceDTO();
            when(notificationService.getPreferences(TENANT_ID, USER_ID))
                    .thenReturn(dto);

            // When/Then
            mockMvc.perform(get("/api/notifications/preferences")
                            .with(jwt().jwt(jwt -> jwt
                                    .subject(USER_ID)
                                    .claim("tenant_id", TENANT_ID)
                                    .claim("name", "Test User")))
                            .header("X-Tenant-ID", TENANT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.emailEnabled").value(true));
        }

        @Test
        @DisplayName("Should update preferences")
        void updatePreferences_ValidUpdates_ReturnsUpdated() throws Exception {
            // Given
            Map<String, Object> updates = Map.of(
                    "emailEnabled", false,
                    "quietHours.enabled", true
            );

            NotificationPreferenceDTO dto = createPreferenceDTO();
            dto.setEmailEnabled(false);

            when(notificationService.updatePreferences(eq(TENANT_ID), eq(USER_ID), any()))
                    .thenReturn(dto);

            // When/Then
            mockMvc.perform(patch("/api/notifications/preferences")
                            .with(jwt().jwt(jwt -> jwt
                                    .subject(USER_ID)
                                    .claim("tenant_id", TENANT_ID)
                                    .claim("name", "Test User")))
                            .header("X-Tenant-ID", TENANT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updates)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.emailEnabled").value(false));
        }
    }

    // ==================== Mute Tests ====================

    @Nested
    @DisplayName("Mute Resource Tests")
    class MuteResourceTests {

        @Test
        @DisplayName("Should mute resource")
        void muteResource_ValidResource_Mutes() throws Exception {
            // Given
            doNothing().when(notificationService)
                    .muteResource(eq(TENANT_ID), eq(USER_ID), anyString(), anyString(), any());

            // When/Then
            mockMvc.perform(post("/api/notifications/mute/DOCUMENT/doc-123")
                            .with(jwt().jwt(jwt -> jwt
                                    .subject(USER_ID)
                                    .claim("tenant_id", TENANT_ID)
                                    .claim("name", "Test User")))
                            .header("X-Tenant-ID", TENANT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(notificationService).muteResource(
                    eq(TENANT_ID), eq(USER_ID), eq("DOCUMENT"), eq("doc-123"), any());
        }

        @Test
        @DisplayName("Should unmute resource")
        void unmuteResource_ValidResource_Unmutes() throws Exception {
            // Given
            doNothing().when(notificationService)
                    .unmuteResource(TENANT_ID, USER_ID, "DOCUMENT", "doc-123");

            // When/Then
            mockMvc.perform(delete("/api/notifications/mute/DOCUMENT/doc-123")
                            .with(jwt().jwt(jwt -> jwt
                                    .subject(USER_ID)
                                    .claim("tenant_id", TENANT_ID)
                                    .claim("name", "Test User")))
                            .header("X-Tenant-ID", TENANT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(notificationService).unmuteResource(TENANT_ID, USER_ID, "DOCUMENT", "doc-123");
        }
    }

    // ==================== Create Notification Tests ====================

    @Nested
    @DisplayName("Create Notification Tests")
    class CreateNotificationTests {

        @Test
        @DisplayName("Should create notification")
        void createNotification_ValidRequest_CreatesNotification() throws Exception {
            // Given
            CreateNotificationRequest request = CreateNotificationRequest.builder()
                    .userIds(Set.of("user-1", "user-2"))
                    .title("Test Notification")
                    .message("This is a test message")
                    .type(Notification.NotificationType.SHARE)
                    .priority(Notification.NotificationPriority.NORMAL)
                    .build();

            NotificationDTO dto = createNotificationDTO();
            when(notificationService.createNotifications(
                    eq(TENANT_ID), any(CreateNotificationRequest.class), eq(USER_ID), anyString()
            )).thenReturn(List.of(dto));

            // When/Then
            mockMvc.perform(post("/api/notifications")
                            .with(jwt().jwt(jwt -> jwt
                                    .subject(USER_ID)
                                    .claim("tenant_id", TENANT_ID)
                                    .claim("name", "Test User")))
                            .header("X-Tenant-ID", TENANT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray());
        }
    }

    // ==================== Helper Methods ====================

    private NotificationDTO createNotificationDTO() {
        NotificationDTO dto = new NotificationDTO();
        dto.setId(NOTIFICATION_ID);
        dto.setTenantId(TENANT_ID);
        dto.setUserId(USER_ID);
        dto.setTitle("Test Notification");
        dto.setMessage("This is a test message");
        dto.setType(Notification.NotificationType.SHARE);
        dto.setPriority(Notification.NotificationPriority.NORMAL);
        dto.setIsRead(false);
        dto.setIsArchived(false);
        dto.setCreatedAt(Instant.now());
        return dto;
    }

    private NotificationPreferenceDTO createPreferenceDTO() {
        NotificationPreferenceDTO dto = new NotificationPreferenceDTO();
        dto.setId("pref-123");
        dto.setTenantId(TENANT_ID);
        dto.setUserId(USER_ID);
        dto.setEmailEnabled(true);
        dto.setPushEnabled(true);
        dto.setInAppEnabled(true);
        dto.setMentionsOnly(false);
        dto.setDigestSettings(new NotificationPreferenceDTO.DigestSettingsDTO());
        dto.setQuietHours(new NotificationPreferenceDTO.QuietHoursDTO());
        return dto;
    }
}
