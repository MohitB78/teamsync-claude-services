package com.teamsync.notification.service;

import com.teamsync.common.exception.ResourceNotFoundException;
import com.teamsync.notification.dto.*;
import com.teamsync.notification.model.Notification;
import com.teamsync.notification.model.NotificationPreference;
import com.teamsync.notification.repository.NotificationPreferenceRepository;
import com.teamsync.notification.repository.NotificationRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for NotificationService.
 * Tests business logic for notification management.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Notification Service Tests")
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationPreferenceRepository preferenceRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private NotificationService notificationService;

    private static final String TENANT_ID = "tenant-123";
    private static final String USER_ID = "user-456";
    private static final String NOTIFICATION_ID = "notif-789";
    private static final String SENDER_ID = "sender-111";

    // ==================== Create Notifications Tests ====================

    @Nested
    @DisplayName("Create Notifications Tests")
    class CreateNotificationsTests {

        @Test
        @DisplayName("Should create notifications for multiple users")
        void createNotifications_MultipleUsers_CreatesAll() {
            // Given
            CreateNotificationRequest request = CreateNotificationRequest.builder()
                    .userIds(Set.of("user-1", "user-2"))
                    .title("Test Notification")
                    .message("Test message")
                    .type(Notification.NotificationType.SHARE)
                    .priority(Notification.NotificationPriority.NORMAL)
                    .build();

            NotificationPreference prefs = createDefaultPreference("user-1");
            when(preferenceRepository.findByTenantIdAndUserId(eq(TENANT_ID), anyString()))
                    .thenReturn(Optional.of(prefs));

            when(notificationRepository.saveAll(anyList()))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When
            List<NotificationDTO> result = notificationService.createNotifications(
                    TENANT_ID, request, SENDER_ID, "Sender Name");

            // Then
            assertThat(result).hasSize(2);
            verify(notificationRepository).saveAll(argThat(list -> list.size() == 2));
        }

        @Test
        @DisplayName("Should skip notifying the sender")
        void createNotifications_IncludesSender_SkipsSender() {
            // Given
            CreateNotificationRequest request = CreateNotificationRequest.builder()
                    .userIds(Set.of(SENDER_ID, "user-2"))
                    .title("Test Notification")
                    .message("Test message")
                    .type(Notification.NotificationType.SHARE)
                    .build();

            NotificationPreference prefs = createDefaultPreference("user-2");
            when(preferenceRepository.findByTenantIdAndUserId(TENANT_ID, "user-2"))
                    .thenReturn(Optional.of(prefs));

            when(notificationRepository.saveAll(anyList()))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When
            List<NotificationDTO> result = notificationService.createNotifications(
                    TENANT_ID, request, SENDER_ID, "Sender Name");

            // Then
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("Should skip disabled notification types")
        void createNotifications_TypeDisabled_Skips() {
            // Given
            CreateNotificationRequest request = CreateNotificationRequest.builder()
                    .userIds(Set.of("user-1"))
                    .title("Test Notification")
                    .message("Test message")
                    .type(Notification.NotificationType.COMMENT)
                    .build();

            NotificationPreference prefs = createDefaultPreference("user-1");
            NotificationPreference.TypePreference typePref = new NotificationPreference.TypePreference();
            typePref.setEnabled(false);
            prefs.getTypePreferences().put("COMMENT", typePref);

            when(preferenceRepository.findByTenantIdAndUserId(TENANT_ID, "user-1"))
                    .thenReturn(Optional.of(prefs));

            // When
            List<NotificationDTO> result = notificationService.createNotifications(
                    TENANT_ID, request, SENDER_ID, "Sender Name");

            // Then
            assertThat(result).isEmpty();
            verify(notificationRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("Should skip muted resources")
        void createNotifications_ResourceMuted_Skips() {
            // Given
            CreateNotificationRequest request = CreateNotificationRequest.builder()
                    .userIds(Set.of("user-1"))
                    .title("Test Notification")
                    .message("Test message")
                    .type(Notification.NotificationType.COMMENT)
                    .resourceType("DOCUMENT")
                    .resourceId("doc-123")
                    .build();

            NotificationPreference prefs = createDefaultPreference("user-1");
            NotificationPreference.MutedResource muted = NotificationPreference.MutedResource.builder()
                    .resourceType("DOCUMENT")
                    .resourceId("doc-123")
                    .mutedAt(Instant.now())
                    .build();
            prefs.getMutedResources().put("DOCUMENT:doc-123", muted);

            when(preferenceRepository.findByTenantIdAndUserId(TENANT_ID, "user-1"))
                    .thenReturn(Optional.of(prefs));

            // When
            List<NotificationDTO> result = notificationService.createNotifications(
                    TENANT_ID, request, SENDER_ID, "Sender Name");

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should create default preferences if none exist")
        void createNotifications_NoPreferences_CreatesDefault() {
            // Given
            CreateNotificationRequest request = CreateNotificationRequest.builder()
                    .userIds(Set.of("user-1"))
                    .title("Test Notification")
                    .message("Test message")
                    .type(Notification.NotificationType.SHARE)
                    .build();

            when(preferenceRepository.findByTenantIdAndUserId(TENANT_ID, "user-1"))
                    .thenReturn(Optional.empty());

            NotificationPreference newPrefs = createDefaultPreference("user-1");
            when(preferenceRepository.save(any(NotificationPreference.class)))
                    .thenReturn(newPrefs);

            when(notificationRepository.saveAll(anyList()))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When
            List<NotificationDTO> result = notificationService.createNotifications(
                    TENANT_ID, request, SENDER_ID, "Sender Name");

            // Then
            assertThat(result).hasSize(1);
            verify(preferenceRepository).save(any(NotificationPreference.class));
        }
    }

    // ==================== Get Notifications Tests ====================

    @Nested
    @DisplayName("Get Notifications Tests")
    class GetNotificationsTests {

        @Test
        @DisplayName("Should return all notifications for user")
        void getNotifications_All_ReturnsList() {
            // Given
            Notification notification = createNotification();
            Page<Notification> page = new PageImpl<>(List.of(notification));

            when(notificationRepository.findByTenantIdAndUserIdOrderByCreatedAtDesc(
                    eq(TENANT_ID), eq(USER_ID), any(PageRequest.class)
            )).thenReturn(page);

            // When
            Page<NotificationDTO> result = notificationService.getNotifications(
                    TENANT_ID, USER_ID, false, true, null, 0, 20);

            // Then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getId()).isEqualTo(NOTIFICATION_ID);
        }

        @Test
        @DisplayName("Should filter unread notifications")
        void getNotifications_UnreadOnly_ReturnsFiltered() {
            // Given
            Page<Notification> page = new PageImpl<>(Collections.emptyList());
            when(notificationRepository.findByTenantIdAndUserIdAndIsReadFalseAndIsArchivedFalseOrderByCreatedAtDesc(
                    eq(TENANT_ID), eq(USER_ID), any(PageRequest.class)
            )).thenReturn(page);

            // When
            Page<NotificationDTO> result = notificationService.getNotifications(
                    TENANT_ID, USER_ID, true, false, null, 0, 20);

            // Then
            verify(notificationRepository).findByTenantIdAndUserIdAndIsReadFalseAndIsArchivedFalseOrderByCreatedAtDesc(
                    eq(TENANT_ID), eq(USER_ID), any(PageRequest.class));
        }

        @Test
        @DisplayName("Should filter by notification type")
        void getNotifications_ByType_ReturnsFiltered() {
            // Given
            Page<Notification> page = new PageImpl<>(Collections.emptyList());
            when(notificationRepository.findByTenantIdAndUserIdAndTypeOrderByCreatedAtDesc(
                    eq(TENANT_ID), eq(USER_ID), eq(Notification.NotificationType.COMMENT), any(PageRequest.class)
            )).thenReturn(page);

            // When
            notificationService.getNotifications(
                    TENANT_ID, USER_ID, false, false, Notification.NotificationType.COMMENT, 0, 20);

            // Then
            verify(notificationRepository).findByTenantIdAndUserIdAndTypeOrderByCreatedAtDesc(
                    eq(TENANT_ID), eq(USER_ID), eq(Notification.NotificationType.COMMENT), any(PageRequest.class));
        }

        @Test
        @DisplayName("Should cap page size at maximum")
        void getNotifications_LargeSize_CapsToMax() {
            // Given
            Page<Notification> page = new PageImpl<>(Collections.emptyList());
            when(notificationRepository.findByTenantIdAndUserIdOrderByCreatedAtDesc(
                    eq(TENANT_ID), eq(USER_ID), any(PageRequest.class)
            )).thenReturn(page);

            // When
            notificationService.getNotifications(TENANT_ID, USER_ID, false, true, null, 0, 500);

            // Then
            verify(notificationRepository).findByTenantIdAndUserIdOrderByCreatedAtDesc(
                    eq(TENANT_ID), eq(USER_ID), eq(PageRequest.of(0, 100))); // 100 is MAX_PAGE_SIZE
        }
    }

    // ==================== Get Notification Tests ====================

    @Nested
    @DisplayName("Get Single Notification Tests")
    class GetNotificationTests {

        @Test
        @DisplayName("Should return notification by ID")
        void getNotification_ValidId_ReturnsNotification() {
            // Given
            Notification notification = createNotification();
            when(notificationRepository.findByIdAndTenantIdAndUserId(NOTIFICATION_ID, TENANT_ID, USER_ID))
                    .thenReturn(Optional.of(notification));

            // When
            NotificationDTO result = notificationService.getNotification(TENANT_ID, USER_ID, NOTIFICATION_ID);

            // Then
            assertThat(result.getId()).isEqualTo(NOTIFICATION_ID);
            assertThat(result.getTitle()).isEqualTo("Test Notification");
        }

        @Test
        @DisplayName("Should throw when notification not found")
        void getNotification_NotFound_ThrowsException() {
            // Given
            when(notificationRepository.findByIdAndTenantIdAndUserId(NOTIFICATION_ID, TENANT_ID, USER_ID))
                    .thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> notificationService.getNotification(TENANT_ID, USER_ID, NOTIFICATION_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(NOTIFICATION_ID);
        }
    }

    // ==================== Unread Count Tests ====================

    @Nested
    @DisplayName("Unread Count Tests")
    class UnreadCountTests {

        @Test
        @DisplayName("Should return unread count with breakdown")
        void getUnreadCount_ReturnsBreakdown() {
            // Given
            when(notificationRepository.countByTenantIdAndUserIdAndIsReadFalseAndIsArchivedFalse(TENANT_ID, USER_ID))
                    .thenReturn(10L);

            when(notificationRepository.countByTenantIdAndUserIdAndIsReadFalseAndIsArchivedFalseAndType(
                    eq(TENANT_ID), eq(USER_ID), any(Notification.NotificationType.class)
            )).thenReturn(0L);

            when(notificationRepository.countByTenantIdAndUserIdAndIsReadFalseAndIsArchivedFalseAndType(
                    TENANT_ID, USER_ID, Notification.NotificationType.COMMENT
            )).thenReturn(5L);

            when(notificationRepository.existsByTenantIdAndUserIdAndIsReadFalseAndPriority(
                    TENANT_ID, USER_ID, Notification.NotificationPriority.URGENT
            )).thenReturn(true);

            when(notificationRepository.countByTenantIdAndUserIdAndIsReadFalseAndIsArchivedFalseAndPriority(
                    eq(TENANT_ID), eq(USER_ID), any(Notification.NotificationPriority.class)
            )).thenReturn(0L);

            // When
            NotificationCountDTO result = notificationService.getUnreadCount(TENANT_ID, USER_ID);

            // Then
            assertThat(result.getUnreadCount()).isEqualTo(10);
            assertThat(result.isHasUrgent()).isTrue();
        }
    }

    // ==================== Mark As Read Tests ====================

    @Nested
    @DisplayName("Mark As Read Tests")
    class MarkAsReadTests {

        @Test
        @DisplayName("Should mark notification as read")
        void markAsRead_ValidId_MarksRead() {
            // Given
            Notification notification = createNotification();
            notification.setIsRead(false);

            when(notificationRepository.findByIdAndTenantIdAndUserId(NOTIFICATION_ID, TENANT_ID, USER_ID))
                    .thenReturn(Optional.of(notification));
            when(notificationRepository.save(any(Notification.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When
            NotificationDTO result = notificationService.markAsRead(TENANT_ID, USER_ID, NOTIFICATION_ID);

            // Then
            assertThat(result.getIsRead()).isTrue();
            assertThat(result.getReadAt()).isNotNull();
        }

        @Test
        @DisplayName("Should not update already read notification")
        void markAsRead_AlreadyRead_NoChange() {
            // Given
            Notification notification = createNotification();
            notification.setIsRead(true);
            notification.setReadAt(Instant.now().minus(1, ChronoUnit.HOURS));

            when(notificationRepository.findByIdAndTenantIdAndUserId(NOTIFICATION_ID, TENANT_ID, USER_ID))
                    .thenReturn(Optional.of(notification));

            // When
            NotificationDTO result = notificationService.markAsRead(TENANT_ID, USER_ID, NOTIFICATION_ID);

            // Then
            assertThat(result.getIsRead()).isTrue();
            verify(notificationRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should mark all notifications as read")
        void markAllAsRead_MarksAll() {
            // Given
            when(notificationRepository.markAllAsRead(eq(TENANT_ID), eq(USER_ID), any(Instant.class)))
                    .thenReturn(15L);

            // When
            long count = notificationService.markAllAsRead(TENANT_ID, USER_ID);

            // Then
            assertThat(count).isEqualTo(15);
        }

        @Test
        @DisplayName("Should mark notifications by type as read")
        void markTypeAsRead_ValidType_MarksAll() {
            // Given
            when(notificationRepository.markAsReadByType(
                    eq(TENANT_ID), eq(USER_ID), eq(Notification.NotificationType.COMMENT), any(Instant.class)
            )).thenReturn(8L);

            // When
            long count = notificationService.markTypeAsRead(TENANT_ID, USER_ID, Notification.NotificationType.COMMENT);

            // Then
            assertThat(count).isEqualTo(8);
        }
    }

    // ==================== Archive Tests ====================

    @Nested
    @DisplayName("Archive Tests")
    class ArchiveTests {

        @Test
        @DisplayName("Should archive notification")
        void archiveNotification_ValidId_Archives() {
            // Given
            Notification notification = createNotification();
            when(notificationRepository.findByIdAndTenantIdAndUserId(NOTIFICATION_ID, TENANT_ID, USER_ID))
                    .thenReturn(Optional.of(notification));
            when(notificationRepository.save(any(Notification.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When
            NotificationDTO result = notificationService.archiveNotification(TENANT_ID, USER_ID, NOTIFICATION_ID);

            // Then
            assertThat(result.getIsArchived()).isTrue();
            assertThat(result.getArchivedAt()).isNotNull();
            assertThat(result.getIsRead()).isTrue(); // Should also mark as read
        }

        @Test
        @DisplayName("Should unarchive notification")
        void unarchiveNotification_ValidId_Unarchives() {
            // Given
            Notification notification = createNotification();
            notification.setIsArchived(true);

            when(notificationRepository.findByIdAndTenantIdAndUserId(NOTIFICATION_ID, TENANT_ID, USER_ID))
                    .thenReturn(Optional.of(notification));
            when(notificationRepository.save(any(Notification.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When
            NotificationDTO result = notificationService.unarchiveNotification(TENANT_ID, USER_ID, NOTIFICATION_ID);

            // Then
            assertThat(result.getIsArchived()).isFalse();
            assertThat(result.getArchivedAt()).isNull();
        }
    }

    // ==================== Delete Tests ====================

    @Nested
    @DisplayName("Delete Tests")
    class DeleteTests {

        @Test
        @DisplayName("Should delete notification")
        void deleteNotification_ValidId_Deletes() {
            // Given
            Notification notification = createNotification();
            when(notificationRepository.findByIdAndTenantIdAndUserId(NOTIFICATION_ID, TENANT_ID, USER_ID))
                    .thenReturn(Optional.of(notification));

            // When
            notificationService.deleteNotification(TENANT_ID, USER_ID, NOTIFICATION_ID);

            // Then
            verify(notificationRepository).delete(notification);
        }

        @Test
        @DisplayName("Should throw when notification not found for deletion")
        void deleteNotification_NotFound_ThrowsException() {
            // Given
            when(notificationRepository.findByIdAndTenantIdAndUserId(NOTIFICATION_ID, TENANT_ID, USER_ID))
                    .thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> notificationService.deleteNotification(TENANT_ID, USER_ID, NOTIFICATION_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ==================== Bulk Operations Tests ====================

    @Nested
    @DisplayName("Bulk Operations Tests")
    class BulkOperationsTests {

        @Test
        @DisplayName("Should perform bulk mark read")
        void bulkOperation_MarkRead_Success() {
            // Given
            List<String> ids = List.of("id1", "id2", "id3");
            List<Notification> notifications = ids.stream()
                    .map(id -> {
                        Notification n = createNotification();
                        n.setId(id);
                        n.setIsRead(false);
                        return n;
                    })
                    .toList();

            BulkNotificationRequest request = new BulkNotificationRequest();
            request.setNotificationIds(ids);
            request.setOperation(BulkNotificationRequest.BulkOperation.MARK_READ);

            when(notificationRepository.findByIdInAndTenantIdAndUserId(ids, TENANT_ID, USER_ID))
                    .thenReturn(new ArrayList<>(notifications));

            // When
            int count = notificationService.bulkOperation(TENANT_ID, USER_ID, request);

            // Then
            assertThat(count).isEqualTo(3);
            verify(notificationRepository).saveAll(anyList());
        }

        @Test
        @DisplayName("Should perform bulk delete")
        void bulkOperation_Delete_Success() {
            // Given
            List<String> ids = List.of("id1", "id2");
            List<Notification> notifications = ids.stream()
                    .map(id -> {
                        Notification n = createNotification();
                        n.setId(id);
                        return n;
                    })
                    .toList();

            BulkNotificationRequest request = new BulkNotificationRequest();
            request.setNotificationIds(ids);
            request.setOperation(BulkNotificationRequest.BulkOperation.DELETE);

            when(notificationRepository.findByIdInAndTenantIdAndUserId(ids, TENANT_ID, USER_ID))
                    .thenReturn(new ArrayList<>(notifications));

            // When
            int count = notificationService.bulkOperation(TENANT_ID, USER_ID, request);

            // Then
            assertThat(count).isEqualTo(2);
            verify(notificationRepository).deleteAll(anyList());
        }

        @Test
        @DisplayName("Should return 0 when no notifications found")
        void bulkOperation_NoNotifications_ReturnsZero() {
            // Given
            BulkNotificationRequest request = new BulkNotificationRequest();
            request.setNotificationIds(List.of("id1"));
            request.setOperation(BulkNotificationRequest.BulkOperation.MARK_READ);

            when(notificationRepository.findByIdInAndTenantIdAndUserId(anyList(), eq(TENANT_ID), eq(USER_ID)))
                    .thenReturn(Collections.emptyList());

            // When
            int count = notificationService.bulkOperation(TENANT_ID, USER_ID, request);

            // Then
            assertThat(count).isEqualTo(0);
        }
    }

    // ==================== Preferences Tests ====================

    @Nested
    @DisplayName("Preferences Tests")
    class PreferencesTests {

        @Test
        @DisplayName("Should get user preferences")
        void getPreferences_ReturnsPreferences() {
            // Given
            NotificationPreference prefs = createDefaultPreference(USER_ID);
            when(preferenceRepository.findByTenantIdAndUserId(TENANT_ID, USER_ID))
                    .thenReturn(Optional.of(prefs));

            // When
            NotificationPreferenceDTO result = notificationService.getPreferences(TENANT_ID, USER_ID);

            // Then
            assertThat(result.getUserId()).isEqualTo(USER_ID);
            assertThat(result.isEmailEnabled()).isTrue();
        }

        @Test
        @DisplayName("Should create default preferences if none exist")
        void getPreferences_NoPrefs_CreatesDefault() {
            // Given
            when(preferenceRepository.findByTenantIdAndUserId(TENANT_ID, USER_ID))
                    .thenReturn(Optional.empty());

            NotificationPreference newPrefs = createDefaultPreference(USER_ID);
            when(preferenceRepository.save(any(NotificationPreference.class)))
                    .thenReturn(newPrefs);

            // When
            NotificationPreferenceDTO result = notificationService.getPreferences(TENANT_ID, USER_ID);

            // Then
            assertThat(result).isNotNull();
            verify(preferenceRepository).save(any(NotificationPreference.class));
        }

        @Test
        @DisplayName("Should update preferences")
        void updatePreferences_ValidUpdates_Updates() {
            // Given
            NotificationPreference prefs = createDefaultPreference(USER_ID);
            when(preferenceRepository.findByTenantIdAndUserId(TENANT_ID, USER_ID))
                    .thenReturn(Optional.of(prefs));
            when(preferenceRepository.save(any(NotificationPreference.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            Map<String, Object> updates = Map.of(
                    "emailEnabled", false,
                    "mentionsOnly", true
            );

            // When
            NotificationPreferenceDTO result = notificationService.updatePreferences(TENANT_ID, USER_ID, updates);

            // Then
            assertThat(result.isEmailEnabled()).isFalse();
            assertThat(result.isMentionsOnly()).isTrue();
        }
    }

    // ==================== Mute Resource Tests ====================

    @Nested
    @DisplayName("Mute Resource Tests")
    class MuteResourceTests {

        @Test
        @DisplayName("Should mute resource")
        void muteResource_ValidResource_Mutes() {
            // Given
            NotificationPreference prefs = createDefaultPreference(USER_ID);
            when(preferenceRepository.findByTenantIdAndUserId(TENANT_ID, USER_ID))
                    .thenReturn(Optional.of(prefs));

            // When
            notificationService.muteResource(TENANT_ID, USER_ID, "DOCUMENT", "doc-123", null);

            // Then
            verify(preferenceRepository).save(argThat(p ->
                    p.getMutedResources().containsKey("DOCUMENT:doc-123")));
        }

        @Test
        @DisplayName("Should unmute resource")
        void unmuteResource_ValidResource_Unmutes() {
            // Given
            NotificationPreference prefs = createDefaultPreference(USER_ID);
            NotificationPreference.MutedResource muted = NotificationPreference.MutedResource.builder()
                    .resourceType("DOCUMENT")
                    .resourceId("doc-123")
                    .mutedAt(Instant.now())
                    .build();
            prefs.getMutedResources().put("DOCUMENT:doc-123", muted);

            when(preferenceRepository.findByTenantIdAndUserId(TENANT_ID, USER_ID))
                    .thenReturn(Optional.of(prefs));

            // When
            notificationService.unmuteResource(TENANT_ID, USER_ID, "DOCUMENT", "doc-123");

            // Then
            verify(preferenceRepository).save(argThat(p ->
                    !p.getMutedResources().containsKey("DOCUMENT:doc-123")));
        }
    }

    // ==================== Cleanup Tests ====================

    @Nested
    @DisplayName("Cleanup Tests")
    class CleanupTests {

        @Test
        @DisplayName("Should delete notifications by resource")
        void deleteByResource_ValidResource_Deletes() {
            // Given
            when(notificationRepository.deleteByTenantIdAndResourceTypeAndResourceId(
                    TENANT_ID, "DOCUMENT", "doc-123"
            )).thenReturn(5L);

            // When
            long count = notificationService.deleteByResource(TENANT_ID, "DOCUMENT", "doc-123");

            // Then
            assertThat(count).isEqualTo(5);
        }

        @Test
        @DisplayName("Should delete all notifications for user")
        void deleteAllForUser_ValidUser_DeletesAll() {
            // Given/When
            notificationService.deleteAllForUser(TENANT_ID, USER_ID);

            // Then
            verify(notificationRepository).deleteByTenantIdAndUserId(TENANT_ID, USER_ID);
            verify(preferenceRepository).deleteByTenantIdAndUserId(TENANT_ID, USER_ID);
        }

        @Test
        @DisplayName("Should cleanup old archived notifications")
        void cleanupOldNotifications_DeletesOld() {
            // Given
            when(notificationRepository.deleteByCreatedAtBeforeAndIsArchivedTrue(any(Instant.class)))
                    .thenReturn(100L);

            // When
            long count = notificationService.cleanupOldNotifications(30);

            // Then
            assertThat(count).isEqualTo(100);
        }
    }

    // ==================== Helper Methods ====================

    private Notification createNotification() {
        return Notification.builder()
                .id(NOTIFICATION_ID)
                .tenantId(TENANT_ID)
                .userId(USER_ID)
                .title("Test Notification")
                .message("This is a test message")
                .type(Notification.NotificationType.SHARE)
                .priority(Notification.NotificationPriority.NORMAL)
                .isRead(false)
                .isArchived(false)
                .createdAt(Instant.now())
                .requestedChannels(Notification.DeliveryChannels.builder()
                        .email(true)
                        .push(true)
                        .inApp(true)
                        .build())
                .build();
    }

    private NotificationPreference createDefaultPreference(String userId) {
        return NotificationPreference.builder()
                .id("pref-" + userId)
                .tenantId(TENANT_ID)
                .userId(userId)
                .emailEnabled(true)
                .pushEnabled(true)
                .inAppEnabled(true)
                .mentionsOnly(false)
                .digestSettings(new NotificationPreference.DigestSettings())
                .quietHours(new NotificationPreference.QuietHours())
                .typePreferences(new HashMap<>())
                .mutedResources(new HashMap<>())
                .build();
    }
}
