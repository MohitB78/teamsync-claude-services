package com.teamsync.notification.repository;

import com.teamsync.notification.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Meta;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for notifications.
 */
@Repository
public interface NotificationRepository extends MongoRepository<Notification, String> {

    /**
     * Find notification by ID ensuring tenant isolation.
     */
    Optional<Notification> findByIdAndTenantIdAndUserId(String id, String tenantId, String userId);

    /**
     * Find all notifications for a user.
     * SECURITY FIX (Round 15 #M6): 5s timeout for API-facing queries.
     */
    @Meta(maxExecutionTimeMs = 5000)
    Page<Notification> findByTenantIdAndUserIdOrderByCreatedAtDesc(
            String tenantId, String userId, Pageable pageable);

    /**
     * Find unread notifications for a user.
     * SECURITY FIX (Round 15 #M6): 5s timeout for API-facing queries.
     */
    @Meta(maxExecutionTimeMs = 5000)
    Page<Notification> findByTenantIdAndUserIdAndIsReadFalseOrderByCreatedAtDesc(
            String tenantId, String userId, Pageable pageable);

    /**
     * Find non-archived notifications for a user.
     */
    Page<Notification> findByTenantIdAndUserIdAndIsArchivedFalseOrderByCreatedAtDesc(
            String tenantId, String userId, Pageable pageable);

    /**
     * Find unread, non-archived notifications.
     */
    Page<Notification> findByTenantIdAndUserIdAndIsReadFalseAndIsArchivedFalseOrderByCreatedAtDesc(
            String tenantId, String userId, Pageable pageable);

    /**
     * Find notifications by type.
     */
    Page<Notification> findByTenantIdAndUserIdAndTypeOrderByCreatedAtDesc(
            String tenantId, String userId, Notification.NotificationType type, Pageable pageable);

    /**
     * Find archived notifications.
     */
    Page<Notification> findByTenantIdAndUserIdAndIsArchivedTrueOrderByCreatedAtDesc(
            String tenantId, String userId, Pageable pageable);

    /**
     * Find notifications by resource.
     */
    List<Notification> findByTenantIdAndResourceTypeAndResourceId(
            String tenantId, String resourceType, String resourceId);

    /**
     * Count unread notifications.
     */
    long countByTenantIdAndUserIdAndIsReadFalseAndIsArchivedFalse(String tenantId, String userId);

    /**
     * Count unread by type.
     */
    long countByTenantIdAndUserIdAndIsReadFalseAndIsArchivedFalseAndType(
            String tenantId, String userId, Notification.NotificationType type);

    /**
     * Count unread urgent notifications.
     */
    long countByTenantIdAndUserIdAndIsReadFalseAndIsArchivedFalseAndPriority(
            String tenantId, String userId, Notification.NotificationPriority priority);

    /**
     * Check if user has unread urgent notifications.
     */
    boolean existsByTenantIdAndUserIdAndIsReadFalseAndPriority(
            String tenantId, String userId, Notification.NotificationPriority priority);

    /**
     * Find notifications in a list of IDs (for bulk operations).
     */
    List<Notification> findByIdInAndTenantIdAndUserId(List<String> ids, String tenantId, String userId);

    /**
     * Mark notification as read.
     */
    @Query("{ '_id': ?0, 'tenantId': ?1, 'userId': ?2 }")
    @Update("{ '$set': { 'isRead': true, 'readAt': ?3 } }")
    long markAsRead(String id, String tenantId, String userId, Instant readAt);

    /**
     * Mark all notifications as read for user.
     */
    @Query("{ 'tenantId': ?0, 'userId': ?1, 'isRead': false }")
    @Update("{ '$set': { 'isRead': true, 'readAt': ?2 } }")
    long markAllAsRead(String tenantId, String userId, Instant readAt);

    /**
     * Mark notifications as read by type.
     */
    @Query("{ 'tenantId': ?0, 'userId': ?1, 'type': ?2, 'isRead': false }")
    @Update("{ '$set': { 'isRead': true, 'readAt': ?3 } }")
    long markAsReadByType(String tenantId, String userId, Notification.NotificationType type, Instant readAt);

    /**
     * Archive notification.
     */
    @Query("{ '_id': ?0, 'tenantId': ?1, 'userId': ?2 }")
    @Update("{ '$set': { 'isArchived': true, 'archivedAt': ?3 } }")
    long archive(String id, String tenantId, String userId, Instant archivedAt);

    /**
     * Unarchive notification.
     */
    @Query("{ '_id': ?0, 'tenantId': ?1, 'userId': ?2 }")
    @Update("{ '$set': { 'isArchived': false, 'archivedAt': null } }")
    long unarchive(String id, String tenantId, String userId);

    /**
     * Find old notifications for cleanup (batched deletion).
     * SECURITY FIX (Round 14 #M3): Changed from unbounded delete to batched find+delete
     * to prevent long-running operations that could cause timeouts or resource exhaustion.
     * SECURITY FIX (Round 15 #M6): 15s timeout for batch/cleanup jobs.
     *
     * @param before the cutoff date
     * @param pageable pagination for batch processing
     * @return list of notification IDs to delete in batch
     */
    @Meta(maxExecutionTimeMs = 15000)
    @Query(value = "{ 'createdAt': { $lt: ?0 }, 'isArchived': true }", fields = "{ '_id': 1 }")
    List<Notification> findOldArchivedNotificationsForCleanup(Instant before, Pageable pageable);

    /**
     * Delete old notifications (for cleanup job).
     * @deprecated Use {@link #findOldArchivedNotificationsForCleanup} with batched deleteAllById instead.
     * This method can cause long-running unbounded operations.
     */
    @Deprecated(forRemoval = true)
    long deleteByCreatedAtBeforeAndIsArchivedTrue(Instant before);

    /**
     * Delete notifications for a resource (when resource is deleted).
     */
    long deleteByTenantIdAndResourceTypeAndResourceId(String tenantId, String resourceType, String resourceId);

    /**
     * Delete all notifications for a user (GDPR).
     */
    long deleteByTenantIdAndUserId(String tenantId, String userId);

    /**
     * Delete all notifications for a tenant.
     */
    long deleteByTenantId(String tenantId);

    /**
     * Find notifications pending email delivery within a tenant.
     * SECURITY FIX (Round 14 #H33): Added tenant filter and pagination to prevent DoS
     * via memory exhaustion from unbounded list queries across all tenants.
     * Also fixes missing tenant isolation - previously any tenant could query pending emails.
     */
    @Query("{ 'tenantId': ?0, 'requestedChannels.email': true, 'sentEmail': false, 'emailError': null }")
    List<Notification> findPendingEmailDeliveryByTenant(String tenantId, Pageable pageable);

    /**
     * Find notifications pending push delivery within a tenant.
     * SECURITY FIX (Round 14 #H33): Added tenant filter and pagination to prevent DoS
     * via memory exhaustion from unbounded list queries across all tenants.
     * Also fixes missing tenant isolation - previously any tenant could query pending push notifications.
     */
    @Query("{ 'tenantId': ?0, 'requestedChannels.push': true, 'sentPush': false, 'pushError': null }")
    List<Notification> findPendingPushDeliveryByTenant(String tenantId, Pageable pageable);

    /**
     * Update email delivery status.
     */
    @Query("{ '_id': ?0 }")
    @Update("{ '$set': { 'sentEmail': ?1, 'emailSentAt': ?2, 'emailError': ?3 } }")
    long updateEmailStatus(String id, boolean sent, Instant sentAt, String error);

    /**
     * Update push delivery status.
     */
    @Query("{ '_id': ?0 }")
    @Update("{ '$set': { 'sentPush': ?1, 'pushSentAt': ?2, 'pushError': ?3 } }")
    long updatePushStatus(String id, boolean sent, Instant sentAt, String error);
}
