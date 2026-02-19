package com.teamsync.notification.service;

import com.teamsync.notification.model.Notification;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for sending notification emails.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${teamsync.notification.from-email:noreply@teamsync.com}")
    private String fromEmail;

    @Value("${teamsync.notification.from-name:TeamSync}")
    private String fromName;

    @Value("${teamsync.notification.app-name:TeamSync}")
    private String appName;

    @Value("${teamsync.notification.app-url:http://localhost:3000}")
    private String appUrl;

    @Value("${teamsync.notification.email-enabled:true}")
    private boolean emailEnabled;

    /**
     * Send notification email asynchronously.
     */
    @Async
    public void sendNotificationEmail(Notification notification) {
        if (!emailEnabled) {
            log.debug("Email sending disabled");
            return;
        }

        try {
            // Get recipient email (check notification first, then look up from user service)
            String recipientEmail = notification.getRecipientEmail();
            if (recipientEmail == null || recipientEmail.isBlank()) {
                recipientEmail = getRecipientEmail(notification.getUserId());
            }

            if (recipientEmail == null || recipientEmail.isBlank()) {
                log.warn("No email found for user: {}", notification.getUserId());
                return;
            }

            String subject = buildSubject(notification);
            String body = generateEmailHtml(notification);

            sendEmail(recipientEmail, subject, body);
            log.info("Sent notification email to user: {}", notification.getUserId());

        } catch (Exception e) {
            log.error("Failed to send notification email for notification {}: {}",
                    notification.getId(), e.getMessage(), e);
            // Don't throw - email failures shouldn't break notification flow
        }
    }

    /**
     * Send a generic email.
     */
    public void sendEmail(String to, String subject, String htmlBody) throws MessagingException, java.io.UnsupportedEncodingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail, fromName);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlBody, true);

        mailSender.send(message);
        log.debug("Email sent to: {}", to);
    }

    /**
     * Send digest email with multiple notifications.
     */
    @Async
    public void sendDigestEmail(String recipientEmail, String userName,
                                 List<Notification> notifications) {
        if (!emailEnabled) {
            log.debug("Email sending disabled");
            return;
        }

        if (notifications == null || notifications.isEmpty()) {
            return;
        }

        if (recipientEmail == null || recipientEmail.isBlank()) {
            log.warn("No recipient email for digest");
            return;
        }

        try {
            String subject = String.format("%s: You have %d new notifications", appName, notifications.size());
            String body = generateDigestEmailHtml(userName, notifications);

            sendEmail(recipientEmail, subject, body);
            log.info("Sent digest email with {} notifications to: {}", notifications.size(), recipientEmail);

        } catch (Exception e) {
            log.error("Failed to send digest email to {}: {}", recipientEmail, e.getMessage(), e);
        }
    }

    private String buildSubject(Notification notification) {
        String prefix = getSubjectPrefix(notification.getType());
        // SECURITY FIX (Round 5): Sanitize title to prevent email header injection
        // CRLF characters in subject can inject arbitrary headers (Bcc:, etc.)
        String sanitizedTitle = sanitizeEmailHeader(notification.getTitle());
        return prefix + sanitizedTitle;
    }

    /**
     * SECURITY FIX (Round 5): Sanitize input for use in email headers.
     * Removes CRLF sequences that could be used to inject additional headers.
     * Also limits length to prevent header-based DoS.
     */
    private String sanitizeEmailHeader(String input) {
        if (input == null) {
            return "";
        }
        // Remove all control characters including CR, LF, and null bytes
        // which could be used for header injection attacks
        String sanitized = input.replaceAll("[\\r\\n\\x00]", " ").trim();
        // Limit length to prevent excessively long headers
        if (sanitized.length() > 200) {
            sanitized = sanitized.substring(0, 197) + "...";
        }
        return sanitized;
    }

    private String getSubjectPrefix(Notification.NotificationType type) {
        return switch (type) {
            case SHARE, DOCUMENT_SHARED -> "[Shared] ";
            case COMMENT, DOCUMENT_COMMENTED -> "[Comment] ";
            case MENTION -> "[Mention] ";
            case WORKFLOW_APPROVAL_REQUIRED -> "[Action Required] ";
            case WORKFLOW_COMPLETED -> "[Workflow] ";
            case SECURITY_ALERT -> "[Security] ";
            case QUOTA_WARNING, QUOTA_EXCEEDED -> "[Storage] ";
            default -> "";
        };
    }

    /**
     * Generate HTML email content for a single notification.
     * Public for testing purposes.
     */
    public String generateEmailHtml(Notification notification) {
        Map<String, String> templateVars = new HashMap<>();
        templateVars.put("title", escapeHtml(notification.getTitle()));
        templateVars.put("message", escapeHtml(notification.getMessage()));
        templateVars.put("senderName", escapeHtml(notification.getSenderName() != null ?
                notification.getSenderName() : appName));
        templateVars.put("actionUrl", notification.getActionUrl() != null ?
                appUrl + notification.getActionUrl() : appUrl);
        templateVars.put("resourceName", escapeHtml(notification.getResourceName() != null ?
                notification.getResourceName() : ""));
        templateVars.put("notificationType", formatNotificationType(notification.getType()));
        templateVars.put("appUrl", appUrl);
        templateVars.put("appName", appName);

        return buildHtmlEmail(templateVars, notification.getType());
    }

    private String buildHtmlEmail(Map<String, String> vars, Notification.NotificationType type) {
        String accentColor = getAccentColor(type);

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
            </head>
            <body style="margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif; background-color: #f5f5f5;">
                <table width="100%%" cellpadding="0" cellspacing="0" style="background-color: #f5f5f5; padding: 20px 0;">
                    <tr>
                        <td align="center">
                            <table width="600" cellpadding="0" cellspacing="0" style="background-color: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 4px rgba(0,0,0,0.1);">
                                <!-- Header -->
                                <tr>
                                    <td style="background-color: %s; padding: 20px; text-align: center;">
                                        <h1 style="margin: 0; color: #ffffff; font-size: 24px;">TeamSync</h1>
                                    </td>
                                </tr>

                                <!-- Content -->
                                <tr>
                                    <td style="padding: 30px;">
                                        <h2 style="margin: 0 0 15px 0; color: #333333; font-size: 20px;">%s</h2>
                                        <p style="margin: 0 0 20px 0; color: #666666; font-size: 16px; line-height: 1.5;">%s</p>

                                        %s

                                        <p style="margin: 20px 0 0 0; color: #999999; font-size: 14px;">
                                            From: %s
                                        </p>

                                        <!-- CTA Button -->
                                        <table width="100%%" cellpadding="0" cellspacing="0" style="margin-top: 25px;">
                                            <tr>
                                                <td align="center">
                                                    <a href="%s" style="display: inline-block; background-color: %s; color: #ffffff; padding: 12px 30px; text-decoration: none; border-radius: 5px; font-weight: 500;">
                                                        View Details
                                                    </a>
                                                </td>
                                            </tr>
                                        </table>
                                    </td>
                                </tr>

                                <!-- Footer -->
                                <tr>
                                    <td style="background-color: #f9f9f9; padding: 20px; text-align: center; border-top: 1px solid #eeeeee;">
                                        <p style="margin: 0 0 10px 0; color: #999999; font-size: 12px;">
                                            This is an automated notification from TeamSync.
                                        </p>
                                        <p style="margin: 0; color: #999999; font-size: 12px;">
                                            <a href="%s/settings/notifications" style="color: #666666;">Manage notification preferences</a>
                                        </p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
            """.formatted(
                accentColor,
                vars.get("title"),
                vars.get("message"),
                vars.get("resourceName").isEmpty() ? "" :
                        "<p style=\"margin: 0; padding: 15px; background-color: #f5f5f5; border-radius: 5px; color: #333333;\">" +
                        "<strong>Resource:</strong> " + vars.get("resourceName") + "</p>",
                vars.get("senderName"),
                vars.get("actionUrl"),
                accentColor,
                vars.get("appUrl")
        );
    }

    /**
     * Generate HTML email content for a digest email.
     * Public for testing purposes.
     */
    public String generateDigestEmailHtml(String userName, List<Notification> notifications) {
        StringBuilder notificationsList = new StringBuilder();
        for (Notification n : notifications) {
            notificationsList.append(String.format("""
                <tr>
                    <td style="padding: 15px; border-bottom: 1px solid #eeeeee;">
                        <p style="margin: 0 0 5px 0; color: #333333; font-weight: 500;">%s</p>
                        <p style="margin: 0; color: #666666; font-size: 14px;">%s</p>
                        <p style="margin: 5px 0 0 0; color: #999999; font-size: 12px;">%s</p>
                    </td>
                </tr>
                """,
                    escapeHtml(n.getTitle()),
                    escapeHtml(n.getMessage()),
                    formatNotificationType(n.getType())
            ));
        }

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
            </head>
            <body style="margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif; background-color: #f5f5f5;">
                <table width="100%%" cellpadding="0" cellspacing="0" style="background-color: #f5f5f5; padding: 20px 0;">
                    <tr>
                        <td align="center">
                            <table width="600" cellpadding="0" cellspacing="0" style="background-color: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 4px rgba(0,0,0,0.1);">
                                <!-- Header -->
                                <tr>
                                    <td style="background-color: #1976d2; padding: 20px; text-align: center;">
                                        <h1 style="margin: 0; color: #ffffff; font-size: 24px;">%s Daily Digest</h1>
                                    </td>
                                </tr>

                                <!-- Greeting -->
                                <tr>
                                    <td style="padding: 20px 20px 0 20px;">
                                        <p style="margin: 0; color: #333333; font-size: 16px;">
                                            Hi %s,
                                        </p>
                                    </td>
                                </tr>

                                <!-- Summary -->
                                <tr>
                                    <td style="padding: 10px 20px 20px 20px;">
                                        <p style="margin: 0; color: #666666; font-size: 16px;">
                                            You have <strong>%d new notifications</strong>.
                                        </p>
                                    </td>
                                </tr>

                                <!-- Notifications List -->
                                <tr>
                                    <td>
                                        <table width="100%%" cellpadding="0" cellspacing="0">
                                            %s
                                        </table>
                                    </td>
                                </tr>

                                <!-- CTA -->
                                <tr>
                                    <td style="padding: 20px; text-align: center;">
                                        <a href="%s/notifications" style="display: inline-block; background-color: #1976d2; color: #ffffff; padding: 12px 30px; text-decoration: none; border-radius: 5px; font-weight: 500;">
                                            View All Notifications
                                        </a>
                                    </td>
                                </tr>

                                <!-- Footer -->
                                <tr>
                                    <td style="background-color: #f9f9f9; padding: 20px; text-align: center; border-top: 1px solid #eeeeee;">
                                        <p style="margin: 0; color: #999999; font-size: 12px;">
                                            <a href="%s/settings/notifications" style="color: #666666;">Manage notification preferences</a>
                                        </p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
            """.formatted(
                appName,
                escapeHtml(userName != null ? userName : "there"),
                notifications.size(),
                notificationsList.toString(),
                appUrl,
                appUrl
        );
    }

    private String getAccentColor(Notification.NotificationType type) {
        return switch (type) {
            case SECURITY_ALERT, QUOTA_EXCEEDED, ERROR -> "#d32f2f";  // Red
            case WORKFLOW_APPROVAL_REQUIRED, WARNING, QUOTA_WARNING -> "#f57c00";  // Orange
            case WORKFLOW_COMPLETED, SHARE_ACCEPTED -> "#388e3c";  // Green
            default -> "#1976d2";  // Blue
        };
    }

    private String formatNotificationType(Notification.NotificationType type) {
        if (type == null) return "";
        return type.name().replace("_", " ").toLowerCase();
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * Get recipient email address.
     * In production, this would call the user service.
     */
    private String getRecipientEmail(String userId) {
        // TODO: Implement user service client to fetch email
        // For now, return null to skip email sending
        log.debug("Email lookup not implemented for user: {}", userId);
        return null;
    }
}
