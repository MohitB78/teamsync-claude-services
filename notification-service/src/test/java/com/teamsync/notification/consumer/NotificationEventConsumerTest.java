package com.teamsync.notification.consumer;

import com.teamsync.notification.dto.CreateNotificationRequest;
import com.teamsync.notification.dto.NotificationDTO;
import com.teamsync.notification.event.SendNotificationEvent;
import com.teamsync.notification.model.Notification;
import com.teamsync.notification.service.NotificationService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for NotificationEventConsumer.
 * Tests Kafka event handling and notification creation.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Notification Event Consumer Tests")
class NotificationEventConsumerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationEventConsumer consumer;

    @Captor
    private ArgumentCaptor<CreateNotificationRequest> requestCaptor;

    private static final String TENANT_ID = "tenant-123";
    private static final String USER_ID = "user-456";
    private static final String SENDER_ID = "sender-789";

    // ==================== Send Notification Event Tests ====================

    @Nested
    @DisplayName("Send Notification Event Tests")
    class SendNotificationEventTests {

        @Test
        @DisplayName("Should handle send notification event")
        void handleSendNotification_ValidEvent_CreatesNotification() {
            // Given
            SendNotificationEvent event = SendNotificationEvent.builder()
                    .tenantId(TENANT_ID)
                    .recipientIds(Set.of(USER_ID))
                    .type(Notification.NotificationType.SHARE)
                    .priority(Notification.NotificationPriority.NORMAL)
                    .title("Document shared")
                    .message("A document has been shared with you")
                    .senderId(SENDER_ID)
                    .senderName("Test Sender")
                    .resourceType("DOCUMENT")
                    .resourceId("doc-123")
                    .actionUrl("/documents/doc-123")
                    .build();

            when(notificationService.createNotifications(
                    eq(TENANT_ID), any(CreateNotificationRequest.class), eq(SENDER_ID), anyString()
            )).thenReturn(List.of(new NotificationDTO()));

            // When
            consumer.handleSendNotification(event);

            // Then
            verify(notificationService).createNotifications(
                    eq(TENANT_ID), requestCaptor.capture(), eq(SENDER_ID), eq("Test Sender"));

            CreateNotificationRequest capturedRequest = requestCaptor.getValue();
            assertThat(capturedRequest.getUserIds()).contains(USER_ID);
            assertThat(capturedRequest.getType()).isEqualTo(Notification.NotificationType.SHARE);
            assertThat(capturedRequest.getTitle()).isEqualTo("Document shared");
            assertThat(capturedRequest.getResourceType()).isEqualTo("DOCUMENT");
            assertThat(capturedRequest.getResourceId()).isEqualTo("doc-123");
        }

        @Test
        @DisplayName("Should handle event with missing tenant ID")
        void handleSendNotification_MissingTenantId_LogsWarning() {
            // Given
            SendNotificationEvent event = SendNotificationEvent.builder()
                    .tenantId(null) // Missing tenant ID
                    .recipientIds(Set.of(USER_ID))
                    .type(Notification.NotificationType.SHARE)
                    .title("Test")
                    .message("Test message")
                    .build();

            // When
            consumer.handleSendNotification(event);

            // Then
            verify(notificationService, never()).createNotifications(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should handle event with empty recipients")
        void handleSendNotification_EmptyRecipients_LogsWarning() {
            // Given
            SendNotificationEvent event = SendNotificationEvent.builder()
                    .tenantId(TENANT_ID)
                    .recipientIds(Collections.emptySet())
                    .type(Notification.NotificationType.SHARE)
                    .title("Test")
                    .message("Test message")
                    .build();

            // When
            consumer.handleSendNotification(event);

            // Then
            verify(notificationService, never()).createNotifications(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should handle event with all channels enabled")
        void handleSendNotification_AllChannels_SetsAllFlags() {
            // Given
            SendNotificationEvent event = SendNotificationEvent.builder()
                    .tenantId(TENANT_ID)
                    .recipientIds(Set.of(USER_ID))
                    .type(Notification.NotificationType.WORKFLOW_APPROVAL_REQUIRED)
                    .priority(Notification.NotificationPriority.HIGH)
                    .title("Approval Required")
                    .message("Your approval is required")
                    .senderId(SENDER_ID)
                    .senderName("Workflow System")
                    .sendEmail(true)
                    .sendPush(true)
                    .sendInApp(true)
                    .build();

            when(notificationService.createNotifications(any(), any(), any(), any()))
                    .thenReturn(List.of(new NotificationDTO()));

            // When
            consumer.handleSendNotification(event);

            // Then
            verify(notificationService).createNotifications(eq(TENANT_ID), requestCaptor.capture(), any(), any());

            CreateNotificationRequest capturedRequest = requestCaptor.getValue();
            assertThat(capturedRequest.isSendEmail()).isTrue();
            assertThat(capturedRequest.isSendPush()).isTrue();
            assertThat(capturedRequest.isSendInApp()).isTrue();
        }
    }

    // ==================== Sharing Event Tests ====================

    @Nested
    @DisplayName("Sharing Event Tests")
    class SharingEventTests {

        @Test
        @DisplayName("Should handle sharing created event")
        void handleSharingCreated_ValidEvent_CreatesNotification() {
            // Given
            Map<String, Object> event = Map.of(
                    "tenantId", TENANT_ID,
                    "sharedWithUserId", USER_ID,
                    "resourceType", "DOCUMENT",
                    "resourceId", "doc-123",
                    "resourceName", "Important Document.pdf",
                    "sharedByUserId", SENDER_ID,
                    "sharedByUserName", "John Doe",
                    "permission", "VIEW"
            );

            when(notificationService.createNotifications(any(), any(), any(), any()))
                    .thenReturn(List.of(new NotificationDTO()));

            // When
            consumer.handleSharingCreated(event);

            // Then
            verify(notificationService).createNotifications(
                    eq(TENANT_ID), requestCaptor.capture(), eq(SENDER_ID), eq("John Doe"));

            CreateNotificationRequest capturedRequest = requestCaptor.getValue();
            assertThat(capturedRequest.getUserIds()).contains(USER_ID);
            assertThat(capturedRequest.getType()).isEqualTo(Notification.NotificationType.SHARE);
            assertThat(capturedRequest.getTitle()).contains("Document shared");
            assertThat(capturedRequest.getResourceId()).isEqualTo("doc-123");
        }

        @Test
        @DisplayName("Should handle folder sharing event")
        void handleSharingCreated_FolderShare_CreatesNotification() {
            // Given
            Map<String, Object> event = Map.of(
                    "tenantId", TENANT_ID,
                    "sharedWithUserId", USER_ID,
                    "resourceType", "FOLDER",
                    "resourceId", "folder-456",
                    "resourceName", "Project Files",
                    "sharedByUserId", SENDER_ID,
                    "sharedByUserName", "Jane Smith",
                    "permission", "EDIT"
            );

            when(notificationService.createNotifications(any(), any(), any(), any()))
                    .thenReturn(List.of(new NotificationDTO()));

            // When
            consumer.handleSharingCreated(event);

            // Then
            verify(notificationService).createNotifications(
                    eq(TENANT_ID), requestCaptor.capture(), any(), any());

            CreateNotificationRequest capturedRequest = requestCaptor.getValue();
            assertThat(capturedRequest.getTitle()).contains("Folder shared");
        }
    }

    // ==================== Document Upload Event Tests ====================

    @Nested
    @DisplayName("Document Upload Event Tests")
    class DocumentUploadEventTests {

        @Test
        @DisplayName("Should handle document uploaded event")
        void handleDocumentUploaded_ValidEvent_CreatesNotification() {
            // Given
            Map<String, Object> event = Map.of(
                    "tenantId", TENANT_ID,
                    "uploaderId", SENDER_ID,
                    "uploaderName", "Upload User",
                    "documentId", "doc-789",
                    "documentName", "Report.pdf",
                    "folderId", "folder-123",
                    "folderName", "Reports",
                    "notifyUserIds", List.of(USER_ID, "user-2")
            );

            when(notificationService.createNotifications(any(), any(), any(), any()))
                    .thenReturn(List.of(new NotificationDTO()));

            // When
            consumer.handleDocumentUploaded(event);

            // Then
            verify(notificationService).createNotifications(
                    eq(TENANT_ID), requestCaptor.capture(), eq(SENDER_ID), eq("Upload User"));

            CreateNotificationRequest capturedRequest = requestCaptor.getValue();
            assertThat(capturedRequest.getUserIds()).containsExactlyInAnyOrder(USER_ID, "user-2");
            assertThat(capturedRequest.getType()).isEqualTo(Notification.NotificationType.DOCUMENT_UPLOADED);
        }

        @Test
        @DisplayName("Should not create notification if no users to notify")
        void handleDocumentUploaded_NoUsersToNotify_SkipsNotification() {
            // Given
            Map<String, Object> event = Map.of(
                    "tenantId", TENANT_ID,
                    "uploaderId", SENDER_ID,
                    "uploaderName", "Upload User",
                    "documentId", "doc-789",
                    "documentName", "Report.pdf"
                    // No notifyUserIds
            );

            // When
            consumer.handleDocumentUploaded(event);

            // Then
            verify(notificationService, never()).createNotifications(any(), any(), any(), any());
        }
    }

    // ==================== Workflow Event Tests ====================

    @Nested
    @DisplayName("Workflow Event Tests")
    class WorkflowEventTests {

        @Test
        @DisplayName("Should handle workflow completed event")
        void handleWorkflowCompleted_ValidEvent_CreatesNotification() {
            // Given
            Map<String, Object> event = Map.of(
                    "tenantId", TENANT_ID,
                    "initiatorId", USER_ID,
                    "workflowId", "wf-123",
                    "workflowName", "Document Approval",
                    "status", "APPROVED",
                    "documentId", "doc-456",
                    "documentName", "Contract.pdf"
            );

            when(notificationService.createNotifications(any(), any(), any(), any()))
                    .thenReturn(List.of(new NotificationDTO()));

            // When
            consumer.handleWorkflowCompleted(event);

            // Then
            verify(notificationService).createNotifications(
                    eq(TENANT_ID), requestCaptor.capture(), eq("system"), eq("Workflow System"));

            CreateNotificationRequest capturedRequest = requestCaptor.getValue();
            assertThat(capturedRequest.getUserIds()).contains(USER_ID);
            assertThat(capturedRequest.getType()).isEqualTo(Notification.NotificationType.WORKFLOW_COMPLETED);
            assertThat(capturedRequest.getTitle()).contains("completed");
        }

        @Test
        @DisplayName("Should handle workflow failed event")
        void handleWorkflowCompleted_FailedStatus_CreatesErrorNotification() {
            // Given
            Map<String, Object> event = Map.of(
                    "tenantId", TENANT_ID,
                    "initiatorId", USER_ID,
                    "workflowId", "wf-123",
                    "workflowName", "Document Approval",
                    "status", "FAILED",
                    "errorMessage", "Approval timeout"
            );

            when(notificationService.createNotifications(any(), any(), any(), any()))
                    .thenReturn(List.of(new NotificationDTO()));

            // When
            consumer.handleWorkflowCompleted(event);

            // Then
            verify(notificationService).createNotifications(
                    eq(TENANT_ID), requestCaptor.capture(), any(), any());

            CreateNotificationRequest capturedRequest = requestCaptor.getValue();
            assertThat(capturedRequest.getType()).isEqualTo(Notification.NotificationType.WORKFLOW_FAILED);
        }
    }

    // ==================== Storage Quota Event Tests ====================

    @Nested
    @DisplayName("Storage Quota Event Tests")
    class StorageQuotaEventTests {

        @Test
        @DisplayName("Should handle quota warning event")
        void handleStorageQuotaUpdated_WarningThreshold_CreatesWarning() {
            // Given
            Map<String, Object> event = Map.of(
                    "tenantId", TENANT_ID,
                    "userId", USER_ID,
                    "usedStorageBytes", 8589934592L, // 8GB
                    "quotaLimitBytes", 10737418240L, // 10GB
                    "percentUsed", 80
            );

            when(notificationService.createNotifications(any(), any(), any(), any()))
                    .thenReturn(List.of(new NotificationDTO()));

            // When
            consumer.handleStorageQuotaUpdated(event);

            // Then
            verify(notificationService).createNotifications(
                    eq(TENANT_ID), requestCaptor.capture(), eq("system"), eq("System"));

            CreateNotificationRequest capturedRequest = requestCaptor.getValue();
            assertThat(capturedRequest.getType()).isEqualTo(Notification.NotificationType.QUOTA_WARNING);
            assertThat(capturedRequest.getPriority()).isEqualTo(Notification.NotificationPriority.HIGH);
        }

        @Test
        @DisplayName("Should handle quota exceeded event")
        void handleStorageQuotaUpdated_Exceeded_CreatesUrgentNotification() {
            // Given
            Map<String, Object> event = Map.of(
                    "tenantId", TENANT_ID,
                    "userId", USER_ID,
                    "usedStorageBytes", 11811160064L, // 11GB
                    "quotaLimitBytes", 10737418240L, // 10GB
                    "percentUsed", 110
            );

            when(notificationService.createNotifications(any(), any(), any(), any()))
                    .thenReturn(List.of(new NotificationDTO()));

            // When
            consumer.handleStorageQuotaUpdated(event);

            // Then
            verify(notificationService).createNotifications(
                    eq(TENANT_ID), requestCaptor.capture(), any(), any());

            CreateNotificationRequest capturedRequest = requestCaptor.getValue();
            assertThat(capturedRequest.getType()).isEqualTo(Notification.NotificationType.QUOTA_EXCEEDED);
            assertThat(capturedRequest.getPriority()).isEqualTo(Notification.NotificationPriority.URGENT);
        }

        @Test
        @DisplayName("Should not notify when quota usage is low")
        void handleStorageQuotaUpdated_LowUsage_NoNotification() {
            // Given
            Map<String, Object> event = Map.of(
                    "tenantId", TENANT_ID,
                    "userId", USER_ID,
                    "usedStorageBytes", 1073741824L, // 1GB
                    "quotaLimitBytes", 10737418240L, // 10GB
                    "percentUsed", 10
            );

            // When
            consumer.handleStorageQuotaUpdated(event);

            // Then
            verify(notificationService, never()).createNotifications(any(), any(), any(), any());
        }
    }

    // ==================== Error Handling Tests ====================

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle service exception gracefully")
        void handleEvent_ServiceException_LogsError() {
            // Given
            SendNotificationEvent event = SendNotificationEvent.builder()
                    .tenantId(TENANT_ID)
                    .recipientIds(Set.of(USER_ID))
                    .type(Notification.NotificationType.SHARE)
                    .title("Test")
                    .message("Test message")
                    .senderId(SENDER_ID)
                    .senderName("Test Sender")
                    .build();

            when(notificationService.createNotifications(any(), any(), any(), any()))
                    .thenThrow(new RuntimeException("Database error"));

            // When/Then - Should not throw, just log
            assertThatNoException().isThrownBy(() -> consumer.handleSendNotification(event));
        }

        @Test
        @DisplayName("Should handle null event gracefully")
        void handleSendNotification_NullEvent_LogsWarning() {
            // When
            consumer.handleSendNotification(null);

            // Then
            verify(notificationService, never()).createNotifications(any(), any(), any(), any());
        }
    }
}
