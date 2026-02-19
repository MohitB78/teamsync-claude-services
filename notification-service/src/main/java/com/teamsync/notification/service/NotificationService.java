package com.teamsync.notification.service;

import com.teamsync.common.exception.ResourceNotFoundException;
import com.teamsync.notification.dto.*;
import com.teamsync.notification.model.Notification;
import com.teamsync.notification.model.NotificationPreference;
import com.teamsync.notification.repository.NotificationRepository;
import com.teamsync.notification.repository.NotificationPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing notifications.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final EmailService emailService;

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_EXPIRY_DAYS = 30;

    // ==================== Notification CRUD ====================

    /**
     * Create notifications for multiple users.
     */
    @Transactional
    public List<NotificationDTO> createNotifications(String tenantId, CreateNotificationRequest request,
                                                      String currentUserId, String currentUserName) {
        log.info("Creating notifications for {} users in tenant: {}", request.getUserIds().size(), tenantId);

        List<Notification> notifications = new ArrayList<>();

        for (String userId : request.getUserIds()) {
            // Skip notifying yourself
            if (userId.equals(currentUserId)) {
                continue;
            }

            // Check user preferences
            NotificationPreference prefs = getOrCreatePreferences(tenantId, userId);

            // Check if notification type is enabled
            if (!prefs.isTypeEnabled(request.getType())) {
                log.debug("Notification type {} disabled for user: {}", request.getType(), userId);
                continue;
            }

            // Check if resource is muted
            if (request.getResourceType() != null && request.getResourceId() != null) {
                if (prefs.isResourceMuted(request.getResourceType(), request.getResourceId())) {
                    log.debug("Resource {}:{} muted for user: {}", request.getResourceType(),
                            request.getResourceId(), userId);
                    continue;
                }
            }

            // Determine delivery channels based on preferences
            Notification.DeliveryChannels channels = determineChannels(prefs, request);

            Notification notification = Notification.builder()
                    .tenantId(tenantId)
                    .userId(userId)
                    .title(request.getTitle())
                    .message(request.getMessage())
                    .type(request.getType())
                    .priority(request.getPriority())
                    .category(request.getCategory())
                    .resourceType(request.getResourceType())
                    .resourceId(request.getResourceId())
                    .resourceName(request.getResourceName())
                    .actionUrl(request.getActionUrl())
                    .actionData(request.getActionData())
                    .senderId(request.getSenderId() != null ? request.getSenderId() : currentUserId)
                    .senderName(request.getSenderName() != null ? request.getSenderName() : currentUserName)
                    .senderAvatarUrl(request.getSenderAvatarUrl())
                    .requestedChannels(channels)
                    .groupKey(request.getGroupKey())
                    .expiresAt(request.getExpiresAt() != null ? request.getExpiresAt() :
                            Instant.now().plus(DEFAULT_EXPIRY_DAYS, ChronoUnit.DAYS))
                    .inAppSentAt(Instant.now())
                    .build();

            notifications.add(notification);
        }

        if (notifications.isEmpty()) {
            log.debug("No notifications to create after filtering");
            return Collections.emptyList();
        }

        List<Notification> saved = notificationRepository.saveAll(notifications);
        log.info("Created {} notifications", saved.size());

        // Trigger async delivery for email/push
        triggerDelivery(saved);

        return saved.stream()
                .map(NotificationDTO::fromEntity)
                .toList();
    }

    /**
     * Get notifications for user with filtering.
     */
    public Page<NotificationDTO> getNotifications(String tenantId, String userId,
                                                   boolean unreadOnly, boolean includeArchived,
                                                   Notification.NotificationType type,
                                                   int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE));
        Page<Notification> notifications;

        if (type != null) {
            notifications = notificationRepository.findByTenantIdAndUserIdAndTypeOrderByCreatedAtDesc(
                    tenantId, userId, type, pageable);
        } else if (unreadOnly && !includeArchived) {
            notifications = notificationRepository.findByTenantIdAndUserIdAndIsReadFalseAndIsArchivedFalseOrderByCreatedAtDesc(
                    tenantId, userId, pageable);
        } else if (unreadOnly) {
            notifications = notificationRepository.findByTenantIdAndUserIdAndIsReadFalseOrderByCreatedAtDesc(
                    tenantId, userId, pageable);
        } else if (!includeArchived) {
            notifications = notificationRepository.findByTenantIdAndUserIdAndIsArchivedFalseOrderByCreatedAtDesc(
                    tenantId, userId, pageable);
        } else {
            notifications = notificationRepository.findByTenantIdAndUserIdOrderByCreatedAtDesc(
                    tenantId, userId, pageable);
        }

        return notifications.map(NotificationDTO::fromEntity);
    }

    /**
     * Get archived notifications.
     */
    public Page<NotificationDTO> getArchivedNotifications(String tenantId, String userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE));
        return notificationRepository.findByTenantIdAndUserIdAndIsArchivedTrueOrderByCreatedAtDesc(
                tenantId, userId, pageable).map(NotificationDTO::fromEntity);
    }

    /**
     * Get notification by ID.
     */
    public NotificationDTO getNotification(String tenantId, String userId, String notificationId) {
        Notification notification = notificationRepository.findByIdAndTenantIdAndUserId(
                        notificationId, tenantId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));

        return NotificationDTO.fromEntity(notification);
    }

    /**
     * Get unread count with breakdown.
     */
    public NotificationCountDTO getUnreadCount(String tenantId, String userId) {
        long totalUnread = notificationRepository.countByTenantIdAndUserIdAndIsReadFalseAndIsArchivedFalse(
                tenantId, userId);

        // Get count by type
        Map<String, Long> countByType = new HashMap<>();
        for (Notification.NotificationType type : Notification.NotificationType.values()) {
            long count = notificationRepository.countByTenantIdAndUserIdAndIsReadFalseAndIsArchivedFalseAndType(
                    tenantId, userId, type);
            if (count > 0) {
                countByType.put(type.name(), count);
            }
        }

        // Check for urgent
        boolean hasUrgent = notificationRepository.existsByTenantIdAndUserIdAndIsReadFalseAndPriority(
                tenantId, userId, Notification.NotificationPriority.URGENT);

        // Get count by priority
        Map<String, Long> countByPriority = new HashMap<>();
        for (Notification.NotificationPriority priority : Notification.NotificationPriority.values()) {
            long count = notificationRepository.countByTenantIdAndUserIdAndIsReadFalseAndIsArchivedFalseAndPriority(
                    tenantId, userId, priority);
            if (count > 0) {
                countByPriority.put(priority.name(), count);
            }
        }

        return NotificationCountDTO.builder()
                .unreadCount(totalUnread)
                .countByType(countByType)
                .countByPriority(countByPriority)
                .hasUrgent(hasUrgent)
                .build();
    }

    // ==================== Read/Archive Operations ====================

    /**
     * Mark notification as read.
     */
    @Transactional
    public NotificationDTO markAsRead(String tenantId, String userId, String notificationId) {
        Notification notification = notificationRepository.findByIdAndTenantIdAndUserId(
                        notificationId, tenantId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));

        if (Boolean.TRUE.equals(notification.getIsRead())) {
            return NotificationDTO.fromEntity(notification);
        }

        notification.setIsRead(true);
        notification.setReadAt(Instant.now());

        Notification saved = notificationRepository.save(notification);
        log.debug("Marked notification {} as read", notificationId);

        return NotificationDTO.fromEntity(saved);
    }

    /**
     * Mark notification as unread.
     */
    @Transactional
    public NotificationDTO markAsUnread(String tenantId, String userId, String notificationId) {
        Notification notification = notificationRepository.findByIdAndTenantIdAndUserId(
                        notificationId, tenantId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));

        notification.setIsRead(false);
        notification.setReadAt(null);

        Notification saved = notificationRepository.save(notification);
        log.debug("Marked notification {} as unread", notificationId);

        return NotificationDTO.fromEntity(saved);
    }

    /**
     * Mark all notifications as read.
     */
    @Transactional
    public long markAllAsRead(String tenantId, String userId) {
        long count = notificationRepository.markAllAsRead(tenantId, userId, Instant.now());
        log.info("Marked {} notifications as read for user: {}", count, userId);
        return count;
    }

    /**
     * Mark all of a specific type as read.
     */
    @Transactional
    public long markTypeAsRead(String tenantId, String userId, Notification.NotificationType type) {
        long count = notificationRepository.markAsReadByType(tenantId, userId, type, Instant.now());
        log.info("Marked {} {} notifications as read for user: {}", count, type, userId);
        return count;
    }

    /**
     * Archive notification.
     */
    @Transactional
    public NotificationDTO archiveNotification(String tenantId, String userId, String notificationId) {
        Notification notification = notificationRepository.findByIdAndTenantIdAndUserId(
                        notificationId, tenantId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));

        notification.setIsArchived(true);
        notification.setArchivedAt(Instant.now());
        notification.setIsRead(true);
        notification.setReadAt(Instant.now());

        Notification saved = notificationRepository.save(notification);
        log.debug("Archived notification {}", notificationId);

        return NotificationDTO.fromEntity(saved);
    }

    /**
     * Unarchive notification.
     */
    @Transactional
    public NotificationDTO unarchiveNotification(String tenantId, String userId, String notificationId) {
        Notification notification = notificationRepository.findByIdAndTenantIdAndUserId(
                        notificationId, tenantId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));

        notification.setIsArchived(false);
        notification.setArchivedAt(null);

        Notification saved = notificationRepository.save(notification);
        log.debug("Unarchived notification {}", notificationId);

        return NotificationDTO.fromEntity(saved);
    }

    /**
     * Delete notification.
     */
    @Transactional
    public void deleteNotification(String tenantId, String userId, String notificationId) {
        Notification notification = notificationRepository.findByIdAndTenantIdAndUserId(
                        notificationId, tenantId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));

        notificationRepository.delete(notification);
        log.debug("Deleted notification {}", notificationId);
    }

    /**
     * Bulk notification operations.
     */
    @Transactional
    public int bulkOperation(String tenantId, String userId, BulkNotificationRequest request) {
        List<Notification> notifications = notificationRepository.findByIdInAndTenantIdAndUserId(
                request.getNotificationIds(), tenantId, userId);

        if (notifications.isEmpty()) {
            return 0;
        }

        Instant now = Instant.now();
        for (Notification notification : notifications) {
            switch (request.getOperation()) {
                case MARK_READ -> {
                    notification.setIsRead(true);
                    notification.setReadAt(now);
                }
                case MARK_UNREAD -> {
                    notification.setIsRead(false);
                    notification.setReadAt(null);
                }
                case ARCHIVE -> {
                    notification.setIsArchived(true);
                    notification.setArchivedAt(now);
                }
                case UNARCHIVE -> {
                    notification.setIsArchived(false);
                    notification.setArchivedAt(null);
                }
                case DELETE -> {
                    // Will be handled separately
                }
            }
        }

        if (request.getOperation() == BulkNotificationRequest.BulkOperation.DELETE) {
            notificationRepository.deleteAll(notifications);
        } else {
            notificationRepository.saveAll(notifications);
        }

        log.info("Bulk operation {} on {} notifications for user: {}",
                request.getOperation(), notifications.size(), userId);

        return notifications.size();
    }

    // ==================== Preferences ====================

    /**
     * Get user preferences.
     */
    public NotificationPreferenceDTO getPreferences(String tenantId, String userId) {
        NotificationPreference prefs = getOrCreatePreferences(tenantId, userId);
        return NotificationPreferenceDTO.fromEntity(prefs);
    }

    /**
     * Update user preferences.
     */
    @Transactional
    public NotificationPreferenceDTO updatePreferences(String tenantId, String userId,
                                                        Map<String, Object> updates) {
        NotificationPreference prefs = getOrCreatePreferences(tenantId, userId);

        // Apply updates
        applyPreferenceUpdates(prefs, updates);

        NotificationPreference saved = preferenceRepository.save(prefs);
        log.info("Updated notification preferences for user: {}", userId);

        return NotificationPreferenceDTO.fromEntity(saved);
    }

    /**
     * Mute a resource (no notifications).
     */
    @Transactional
    public void muteResource(String tenantId, String userId, String resourceType,
                             String resourceId, Instant expiresAt) {
        NotificationPreference prefs = getOrCreatePreferences(tenantId, userId);

        String key = resourceType + ":" + resourceId;
        NotificationPreference.MutedResource muted = NotificationPreference.MutedResource.builder()
                .resourceType(resourceType)
                .resourceId(resourceId)
                .mutedAt(Instant.now())
                .muteExpiresAt(expiresAt)
                .build();

        prefs.getMutedResources().put(key, muted);
        preferenceRepository.save(prefs);

        log.info("Muted resource {}:{} for user: {}", resourceType, resourceId, userId);
    }

    /**
     * Unmute a resource.
     */
    @Transactional
    public void unmuteResource(String tenantId, String userId, String resourceType, String resourceId) {
        NotificationPreference prefs = preferenceRepository.findByTenantIdAndUserId(tenantId, userId)
                .orElse(null);

        if (prefs == null) return;

        String key = resourceType + ":" + resourceId;
        prefs.getMutedResources().remove(key);
        preferenceRepository.save(prefs);

        log.info("Unmuted resource {}:{} for user: {}", resourceType, resourceId, userId);
    }

    // ==================== Cleanup Operations ====================

    /**
     * Delete notifications for a resource.
     */
    @Transactional
    public long deleteByResource(String tenantId, String resourceType, String resourceId) {
        long count = notificationRepository.deleteByTenantIdAndResourceTypeAndResourceId(
                tenantId, resourceType, resourceId);
        log.info("Deleted {} notifications for resource {}:{}", count, resourceType, resourceId);
        return count;
    }

    /**
     * Delete all notifications for a user (GDPR).
     */
    @Transactional
    public void deleteAllForUser(String tenantId, String userId) {
        notificationRepository.deleteByTenantIdAndUserId(tenantId, userId);
        preferenceRepository.deleteByTenantIdAndUserId(tenantId, userId);
        log.info("Deleted all notifications and preferences for user: {}", userId);
    }

    /**
     * Cleanup old archived notifications.
     */
    @Transactional
    public long cleanupOldNotifications(int daysOld) {
        Instant cutoff = Instant.now().minus(daysOld, ChronoUnit.DAYS);
        long count = notificationRepository.deleteByCreatedAtBeforeAndIsArchivedTrue(cutoff);
        log.info("Cleaned up {} old archived notifications", count);
        return count;
    }

    // ==================== Helper Methods ====================

    private NotificationPreference getOrCreatePreferences(String tenantId, String userId) {
        return preferenceRepository.findByTenantIdAndUserId(tenantId, userId)
                .orElseGet(() -> {
                    log.debug("Creating default preferences for user: {}", userId);
                    NotificationPreference prefs = NotificationPreference.builder()
                            .tenantId(tenantId)
                            .userId(userId)
                            .build();
                    return preferenceRepository.save(prefs);
                });
    }

    private Notification.DeliveryChannels determineChannels(NotificationPreference prefs,
                                                             CreateNotificationRequest request) {
        return Notification.DeliveryChannels.builder()
                .email(request.isSendEmail() && prefs.isEmailEnabledForType(request.getType()))
                .push(request.isSendPush() && prefs.isPushEnabledForType(request.getType()))
                .inApp(request.isSendInApp() && prefs.isInAppEnabledForType(request.getType()))
                .build();
    }

    @Async
    protected void triggerDelivery(List<Notification> notifications) {
        for (Notification notification : notifications) {
            if (notification.getRequestedChannels().isEmail()) {
                try {
                    emailService.sendNotificationEmail(notification);
                    notificationRepository.updateEmailStatus(
                            notification.getId(), true, Instant.now(), null);
                } catch (Exception e) {
                    log.error("Failed to send email for notification {}: {}",
                            notification.getId(), e.getMessage());
                    notificationRepository.updateEmailStatus(
                            notification.getId(), false, null, e.getMessage());
                }
            }
            // Push notification would be handled similarly
        }
    }

    private void applyPreferenceUpdates(NotificationPreference prefs, Map<String, Object> updates) {
        updates.forEach((key, value) -> {
            try {
                switch (key) {
                    case "emailEnabled" -> prefs.setEmailEnabled(toBoolean(value));
                    case "pushEnabled" -> prefs.setPushEnabled(toBoolean(value));
                    case "inAppEnabled" -> prefs.setInAppEnabled(toBoolean(value));
                    case "mentionsOnly" -> prefs.setMentionsOnly(toBoolean(value));

                    // Digest settings
                    case "digestSettings.enabled" -> prefs.getDigestSettings().setEnabled(toBoolean(value));
                    case "digestSettings.frequency" ->
                            prefs.getDigestSettings().setFrequency(
                                    NotificationPreference.DigestFrequency.valueOf((String) value));
                    case "digestSettings.sendTime" -> prefs.getDigestSettings().setSendTime((String) value);
                    case "digestSettings.timezone" -> prefs.getDigestSettings().setTimezone((String) value);
                    case "digestSettings.lowPriorityToDigest" ->
                            prefs.getDigestSettings().setLowPriorityToDigest(toBoolean(value));

                    // Quiet hours
                    case "quietHours.enabled" -> prefs.getQuietHours().setEnabled(toBoolean(value));
                    case "quietHours.startTime" -> prefs.getQuietHours().setStartTime((String) value);
                    case "quietHours.endTime" -> prefs.getQuietHours().setEndTime((String) value);
                    case "quietHours.timezone" -> prefs.getQuietHours().setTimezone((String) value);
                    case "quietHours.allowUrgent" -> prefs.getQuietHours().setAllowUrgent(toBoolean(value));

                    default -> {
                        // Handle type preferences: typePreferences.COMMENT.email
                        if (key.startsWith("typePreferences.")) {
                            handleTypePreferenceUpdate(prefs, key, value);
                        } else {
                            log.warn("Unknown preference key: {}", key);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to apply preference update for key '{}': {}", key, e.getMessage());
            }
        });
    }

    private void handleTypePreferenceUpdate(NotificationPreference prefs, String key, Object value) {
        String[] parts = key.split("\\.");
        if (parts.length != 3) return;

        String typeName = parts[1];
        String field = parts[2];

        NotificationPreference.TypePreference typePref = prefs.getTypePreferences()
                .computeIfAbsent(typeName, k -> new NotificationPreference.TypePreference());

        switch (field) {
            case "enabled" -> typePref.setEnabled(toBoolean(value));
            case "email" -> typePref.setEmail(toBoolean(value));
            case "push" -> typePref.setPush(toBoolean(value));
            case "inApp" -> typePref.setInApp(toBoolean(value));
        }
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof String s) return Boolean.parseBoolean(s);
        return false;
    }
}
