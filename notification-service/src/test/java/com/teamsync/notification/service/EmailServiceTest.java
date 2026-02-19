package com.teamsync.notification.service;

import com.teamsync.notification.model.Notification;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EmailService.
 * Tests email sending and template generation.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Email Service Tests")
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private MimeMessage mimeMessage;

    @InjectMocks
    private EmailService emailService;

    private static final String TENANT_ID = "tenant-123";
    private static final String USER_ID = "user-456";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "fromEmail", "noreply@teamsync.com");
        ReflectionTestUtils.setField(emailService, "fromName", "TeamSync");
        ReflectionTestUtils.setField(emailService, "appName", "TeamSync");
        ReflectionTestUtils.setField(emailService, "appUrl", "https://teamsync.example.com");
        ReflectionTestUtils.setField(emailService, "emailEnabled", true);
    }

    // ==================== Send Notification Email Tests ====================

    @Nested
    @DisplayName("Send Notification Email Tests")
    class SendNotificationEmailTests {

        @Test
        @DisplayName("Should send notification email")
        void sendNotificationEmail_ValidNotification_SendsEmail() throws Exception {
            // Given
            Notification notification = createNotification();
            notification.setRecipientEmail("user@example.com");

            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

            // When
            emailService.sendNotificationEmail(notification);

            // Then
            verify(mailSender).send(mimeMessage);
        }

        @Test
        @DisplayName("Should not send when email disabled")
        void sendNotificationEmail_EmailDisabled_DoesNotSend() {
            // Given
            ReflectionTestUtils.setField(emailService, "emailEnabled", false);
            Notification notification = createNotification();
            notification.setRecipientEmail("user@example.com");

            // When
            emailService.sendNotificationEmail(notification);

            // Then
            verify(mailSender, never()).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("Should not send when recipient email is null")
        void sendNotificationEmail_NoRecipientEmail_DoesNotSend() {
            // Given
            Notification notification = createNotification();
            notification.setRecipientEmail(null);

            // When
            emailService.sendNotificationEmail(notification);

            // Then
            verify(mailSender, never()).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("Should not send when recipient email is empty")
        void sendNotificationEmail_EmptyRecipientEmail_DoesNotSend() {
            // Given
            Notification notification = createNotification();
            notification.setRecipientEmail("");

            // When
            emailService.sendNotificationEmail(notification);

            // Then
            verify(mailSender, never()).send(any(MimeMessage.class));
        }
    }

    // ==================== Send Digest Email Tests ====================

    @Nested
    @DisplayName("Send Digest Email Tests")
    class SendDigestEmailTests {

        @Test
        @DisplayName("Should send digest email with multiple notifications")
        void sendDigestEmail_MultipleNotifications_SendsEmail() throws Exception {
            // Given
            List<Notification> notifications = List.of(
                    createNotification(),
                    createNotification(),
                    createNotification()
            );

            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

            // When
            emailService.sendDigestEmail("user@example.com", "User Name", notifications);

            // Then
            verify(mailSender).send(mimeMessage);
        }

        @Test
        @DisplayName("Should not send digest when email disabled")
        void sendDigestEmail_EmailDisabled_DoesNotSend() {
            // Given
            ReflectionTestUtils.setField(emailService, "emailEnabled", false);
            List<Notification> notifications = List.of(createNotification());

            // When
            emailService.sendDigestEmail("user@example.com", "User Name", notifications);

            // Then
            verify(mailSender, never()).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("Should not send digest when notifications list is empty")
        void sendDigestEmail_EmptyList_DoesNotSend() {
            // Given
            List<Notification> notifications = List.of();

            // When
            emailService.sendDigestEmail("user@example.com", "User Name", notifications);

            // Then
            verify(mailSender, never()).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("Should not send digest when recipient email is blank")
        void sendDigestEmail_BlankEmail_DoesNotSend() {
            // Given
            List<Notification> notifications = List.of(createNotification());

            // When
            emailService.sendDigestEmail("  ", "User Name", notifications);

            // Then
            verify(mailSender, never()).send(any(MimeMessage.class));
        }
    }

    // ==================== Template Generation Tests ====================

    @Nested
    @DisplayName("Template Generation Tests")
    class TemplateGenerationTests {

        @Test
        @DisplayName("Should include notification title in email")
        void generateEmailContent_ValidNotification_IncludesTitle() {
            // Given
            Notification notification = createNotification();
            notification.setTitle("Important Update");

            // When
            String content = emailService.generateEmailHtml(notification);

            // Then
            assertThat(content).contains("Important Update");
        }

        @Test
        @DisplayName("Should include notification message in email")
        void generateEmailContent_ValidNotification_IncludesMessage() {
            // Given
            Notification notification = createNotification();
            notification.setMessage("This is an important message about your document.");

            // When
            String content = emailService.generateEmailHtml(notification);

            // Then
            assertThat(content).contains("This is an important message about your document.");
        }

        @Test
        @DisplayName("Should include action URL when present")
        void generateEmailContent_WithActionUrl_IncludesButton() {
            // Given
            Notification notification = createNotification();
            notification.setActionUrl("/documents/doc-123");

            // When
            String content = emailService.generateEmailHtml(notification);

            // Then
            assertThat(content).contains("View Details");
            assertThat(content).contains("documents/doc-123");
        }

        @Test
        @DisplayName("Should use appropriate color for security alerts")
        void generateEmailContent_SecurityAlert_UsesRedColor() {
            // Given
            Notification notification = createNotification();
            notification.setType(Notification.NotificationType.SECURITY_ALERT);

            // When
            String content = emailService.generateEmailHtml(notification);

            // Then
            assertThat(content).contains("#d32f2f"); // Red color
        }

        @Test
        @DisplayName("Should use appropriate color for success notifications")
        void generateEmailContent_WorkflowCompleted_UsesGreenColor() {
            // Given
            Notification notification = createNotification();
            notification.setType(Notification.NotificationType.WORKFLOW_COMPLETED);

            // When
            String content = emailService.generateEmailHtml(notification);

            // Then
            assertThat(content).contains("#388e3c"); // Green color
        }

        @Test
        @DisplayName("Should use default blue color for normal notifications")
        void generateEmailContent_NormalType_UsesBlueColor() {
            // Given
            Notification notification = createNotification();
            notification.setType(Notification.NotificationType.SHARE);

            // When
            String content = emailService.generateEmailHtml(notification);

            // Then
            assertThat(content).contains("#1976d2"); // Blue color
        }

        @Test
        @DisplayName("Should include app name in email")
        void generateEmailContent_ValidNotification_IncludesAppName() {
            // Given
            Notification notification = createNotification();

            // When
            String content = emailService.generateEmailHtml(notification);

            // Then
            assertThat(content).contains("TeamSync");
        }
    }

    // ==================== Digest Template Tests ====================

    @Nested
    @DisplayName("Digest Template Tests")
    class DigestTemplateTests {

        @Test
        @DisplayName("Should include notification count in digest")
        void generateDigestContent_MultipleNotifications_IncludesCount() {
            // Given
            List<Notification> notifications = List.of(
                    createNotification(),
                    createNotification(),
                    createNotification()
            );

            // When
            String content = emailService.generateDigestEmailHtml("User", notifications);

            // Then
            assertThat(content).contains("3 new notifications");
        }

        @Test
        @DisplayName("Should include all notifications in digest")
        void generateDigestContent_MultipleNotifications_IncludesAll() {
            // Given
            Notification n1 = createNotification();
            n1.setTitle("First Notification");

            Notification n2 = createNotification();
            n2.setTitle("Second Notification");

            List<Notification> notifications = List.of(n1, n2);

            // When
            String content = emailService.generateDigestEmailHtml("User", notifications);

            // Then
            assertThat(content).contains("First Notification");
            assertThat(content).contains("Second Notification");
        }

        @Test
        @DisplayName("Should include user name in digest greeting")
        void generateDigestContent_ValidUser_IncludesGreeting() {
            // Given
            List<Notification> notifications = List.of(createNotification());

            // When
            String content = emailService.generateDigestEmailHtml("John Doe", notifications);

            // Then
            assertThat(content).contains("John Doe");
        }
    }

    // ==================== Error Handling Tests ====================

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle mail sender exception gracefully")
        void sendNotificationEmail_MailException_DoesNotThrow() {
            // Given
            Notification notification = createNotification();
            notification.setRecipientEmail("user@example.com");

            when(mailSender.createMimeMessage()).thenThrow(new RuntimeException("Mail server error"));

            // When/Then - Should not throw
            assertThatNoException().isThrownBy(() -> emailService.sendNotificationEmail(notification));
        }
    }

    // ==================== Helper Methods ====================

    private Notification createNotification() {
        return Notification.builder()
                .id("notif-123")
                .tenantId(TENANT_ID)
                .userId(USER_ID)
                .title("Test Notification")
                .message("This is a test message")
                .type(Notification.NotificationType.SHARE)
                .priority(Notification.NotificationPriority.NORMAL)
                .actionUrl("/documents/doc-123")
                .senderName("Test Sender")
                .resourceName("Test Document.pdf")
                .createdAt(Instant.now())
                .build();
    }
}
