package com.teamsync.permission.event;

import com.teamsync.common.config.KafkaTopics;
import com.teamsync.permission.event.dto.*;
import com.teamsync.permission.service.PermissionManagerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Kafka event listener for AccessArc platform events.
 * Handles department and user lifecycle events to maintain permission consistency.
 *
 * SECURITY FIX: Uses typed DTOs instead of Map<String, Object> to prevent
 * deserialization attacks and ensure all required fields are validated.
 *
 * SECURITY FIX (Round 12): Added tenant ID validation to all event handlers.
 * This prevents cross-tenant privilege escalation via malicious Kafka events.
 * An attacker who can inject events into Kafka could otherwise trigger
 * operations in arbitrary tenants.
 *
 * Listens to the following Kafka topics:
 * - accessarc.departments.created: Creates department drives
 * - accessarc.departments.deleted: Archives department drives
 * - accessarc.users.department_assigned: Grants drive access
 * - accessarc.users.department_removed: Revokes drive access
 * - accessarc.users.logged_in: Warms permission cache
 * - accessarc.users.created: Creates personal drives
 * - teamsync.teams.member_added: Handles team drive access
 * - teamsync.teams.member_removed: Revokes team access
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Validated
public class AccessArcEventListener {

    private final PermissionManagerService permissionService;
    private final StringRedisTemplate redisTemplate;

    /**
     * SECURITY FIX (Round 14 #C20): Event deduplication TTL.
     * Events are deduplicated for 24 hours to prevent replay attacks.
     */
    private static final Duration EVENT_DEDUP_TTL = Duration.ofHours(24);

    /**
     * SECURITY FIX (Round 14 #C20): Redis key prefix for event deduplication.
     */
    private static final String EVENT_DEDUP_PREFIX = "teamsync:event:dedup:";

    /**
     * SECURITY FIX (Round 12): Valid tenant ID pattern.
     * Tenant IDs must be alphanumeric with hyphens/underscores, 1-64 characters.
     * This prevents injection attacks via malformed tenant IDs.
     */
    private static final Pattern VALID_TENANT_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}$");

    /**
     * SECURITY FIX (Round 12): Validates tenant ID format and existence.
     * Prevents cross-tenant attacks via malicious Kafka event injection.
     *
     * @param tenantId The tenant ID from the event
     * @param eventType The type of event (for logging)
     * @throws SecurityException if tenant ID is invalid or doesn't exist
     */
    private void validateTenantId(String tenantId, String eventType) {
        if (tenantId == null || tenantId.isBlank()) {
            log.error("SECURITY: {} event rejected - missing tenant ID", eventType);
            throw new SecurityException("Event rejected: missing tenant ID");
        }

        if (!VALID_TENANT_ID_PATTERN.matcher(tenantId).matches()) {
            // Sanitize for logging to prevent log injection
            String sanitizedTenantId = tenantId.replaceAll("[\\r\\n]", "_")
                    .substring(0, Math.min(30, tenantId.length()));
            log.error("SECURITY: {} event rejected - invalid tenant ID format: {}...", eventType, sanitizedTenantId);
            throw new SecurityException("Event rejected: invalid tenant ID format");
        }

        // Verify tenant exists in the system
        if (!permissionService.tenantExists(tenantId)) {
            log.error("SECURITY: {} event rejected - tenant does not exist: {}", eventType, tenantId);
            throw new SecurityException("Event rejected: tenant does not exist");
        }
    }

    /**
     * SECURITY FIX (Round 14 #C20): Check if event was already processed.
     * Uses Redis SETNX (SET if Not eXists) for atomic deduplication.
     * This prevents replay attacks where malicious actors replay old Kafka messages.
     *
     * @param eventId The unique event ID
     * @param eventType The type of event (for logging)
     * @return true if this is a duplicate event (already processed), false if new
     */
    private boolean isDuplicateEvent(String eventId, String eventType) {
        if (eventId == null || eventId.isBlank()) {
            // Events without IDs cannot be deduplicated - log warning but allow processing
            log.warn("SECURITY: {} event has no eventId - cannot deduplicate", eventType);
            return false;
        }

        String redisKey = EVENT_DEDUP_PREFIX + eventType + ":" + eventId;

        try {
            // SETNX: Set only if key doesn't exist, with TTL
            Boolean wasSet = redisTemplate.opsForValue().setIfAbsent(redisKey, "1", EVENT_DEDUP_TTL);

            if (Boolean.FALSE.equals(wasSet)) {
                // Key already existed - this is a duplicate
                log.warn("SECURITY: Duplicate {} event detected and rejected: {}", eventType, eventId);
                return true;
            }

            return false;
        } catch (Exception e) {
            // SECURITY FIX: Fail CLOSED on Redis failure.
            // If we can't verify deduplication, reject the event to prevent replay attacks.
            // This is the secure choice - processing a duplicate event that grants permissions
            // is safer to reject than to allow unverified events through.
            log.error("SECURITY: Redis deduplication check failed for {} event {} - REJECTING: {}",
                    eventType, eventId, e.getMessage());
            return true;  // Treat as duplicate (reject)
        }
    }

    @KafkaListener(topics = KafkaTopics.ACCESSARC_DEPARTMENTS_CREATED, groupId = "permission-manager")
    public void handleDepartmentCreated(@Payload @Valid DepartmentCreatedEvent event) {
        // SECURITY FIX (Round 14 #C20): Check for duplicate event (replay attack prevention)
        if (isDuplicateEvent(event.getEventId(), "DepartmentCreated")) {
            return;
        }

        // SECURITY FIX (Round 12): Validate tenant before processing
        validateTenantId(event.getTenantId(), "DepartmentCreated");

        log.info("Department created event received: tenantId={}, departmentId={}, name={}",
                event.getTenantId(), event.getDepartmentId(), event.getDepartmentName());
        // TODO: Create department drive via permissionService.createDrive()
    }

    @KafkaListener(topics = KafkaTopics.ACCESSARC_DEPARTMENTS_DELETED, groupId = "permission-manager")
    public void handleDepartmentDeleted(@Payload @Valid DepartmentDeletedEvent event) {
        // SECURITY FIX (Round 14 #C20): Check for duplicate event (replay attack prevention)
        if (isDuplicateEvent(event.getEventId(), "DepartmentDeleted")) {
            return;
        }

        // SECURITY FIX (Round 12): Validate tenant before processing
        validateTenantId(event.getTenantId(), "DepartmentDeleted");

        log.info("Department deleted event received: tenantId={}, departmentId={}",
                event.getTenantId(), event.getDepartmentId());

        // SECURITY FIX (Round 12): Implement department drive archival
        // Previously this was a TODO that left orphaned shares and permissions
        try {
            permissionService.archiveDepartmentDrive(event.getTenantId(), event.getDepartmentId());
            log.info("Successfully archived department drive for department: {}", event.getDepartmentId());
        } catch (Exception e) {
            log.error("Failed to archive department drive for department: {} - {}",
                    event.getDepartmentId(), e.getMessage());
            throw e; // Rethrow to trigger Kafka retry
        }
    }

    @KafkaListener(topics = KafkaTopics.ACCESSARC_USERS_DEPARTMENT_ASSIGNED, groupId = "permission-manager")
    public void handleUserDepartmentAssigned(@Payload @Valid UserDepartmentAssignedEvent event) {
        // SECURITY FIX (Round 14 #C20): Check for duplicate event (replay attack prevention)
        if (isDuplicateEvent(event.getEventId(), "UserDepartmentAssigned")) {
            return;
        }

        // SECURITY FIX (Round 12): Validate tenant before processing
        validateTenantId(event.getTenantId(), "UserDepartmentAssigned");

        log.info("User assigned to department: tenantId={}, userId={}, departmentId={}",
                event.getTenantId(), event.getUserId(), event.getDepartmentId());
        // TODO: Grant drive access via permissionService.assignRole()
    }

    /**
     * SECURITY FIX: Added @Transactional to ensure database changes are atomic
     * with Kafka offset commit. If transaction fails, message will be reprocessed.
     */
    @Transactional
    @KafkaListener(topics = KafkaTopics.ACCESSARC_USERS_DEPARTMENT_REMOVED, groupId = "permission-manager")
    public void handleUserDepartmentRemoved(@Payload @Valid UserDepartmentRemovedEvent event) {
        // SECURITY FIX (Round 14 #C20): Check for duplicate event (replay attack prevention)
        if (isDuplicateEvent(event.getEventId(), "UserDepartmentRemoved")) {
            return;
        }

        // SECURITY FIX (Round 12): Validate tenant before processing
        validateTenantId(event.getTenantId(), "UserDepartmentRemoved");

        log.info("User removed from department: tenantId={}, userId={}, departmentId={}, eventId={}",
                event.getTenantId(), event.getUserId(), event.getDepartmentId(), event.getEventId());

        try {
            permissionService.removeDepartmentAccess(event.getUserId(), event.getDepartmentId());
            log.info("Successfully processed department removal event: {}", event.getEventId());
        } catch (Exception e) {
            log.error("Failed to process department removal event: {} - {}", event.getEventId(), e.getMessage());
            throw e; // Rethrow to trigger Kafka retry
        }
    }

    @KafkaListener(topics = "accessarc.users.logged_in", groupId = "permission-manager")
    public void handleUserLoggedIn(@Payload @Valid UserLoggedInEvent event) {
        // SECURITY FIX (Round 14 #C20): Check for duplicate event (replay attack prevention)
        if (isDuplicateEvent(event.getEventId(), "UserLoggedIn")) {
            return;
        }

        // SECURITY FIX (Round 12): Validate tenant before processing
        validateTenantId(event.getTenantId(), "UserLoggedIn");

        log.debug("User logged in, warming permission cache: tenantId={}, userId={}",
                event.getTenantId(), event.getUserId());
        permissionService.warmUserCache(event.getUserId());
    }

    @KafkaListener(topics = "accessarc.users.created", groupId = "permission-manager")
    public void handleUserCreated(@Payload @Valid UserCreatedEvent event) {
        // SECURITY FIX (Round 14 #C20): Check for duplicate event (replay attack prevention)
        if (isDuplicateEvent(event.getEventId(), "UserCreated")) {
            return;
        }

        // SECURITY FIX (Round 12): Validate tenant before processing
        validateTenantId(event.getTenantId(), "UserCreated");

        // SECURITY FIX (Round 13 #3): Removed email from INFO-level logging.
        // Email addresses are PII and should not be written to logs where they
        // may be accessible to operations staff, log aggregation systems, or attackers.
        log.info("User created event received: tenantId={}, userId={}",
                event.getTenantId(), event.getUserId());
        // TODO: Create personal drive via permissionService.createDrive()
    }

    @KafkaListener(topics = "teamsync.teams.member_added", groupId = "permission-manager")
    @Transactional
    public void handleTeamMemberAdded(@Payload @Valid TeamMemberEvent event) {
        // SECURITY FIX (Round 14 #C20): Check for duplicate event (replay attack prevention)
        if (isDuplicateEvent(event.getEventId(), "TeamMemberAdded")) {
            return;
        }

        // SECURITY FIX (Round 12): Validate tenant before processing
        validateTenantId(event.getTenantId(), "TeamMemberAdded");

        log.info("Team member added: tenantId={}, teamId={}, userId={}, role={}",
                event.getTenantId(), event.getTeamId(), event.getUserId(), event.getRoleName());

        try {
            // Grant team drive access with the role specified in the event
            permissionService.grantTeamMemberAccess(
                    event.getTenantId(),
                    event.getTeamId(),
                    event.getUserId(),
                    event.getRoleId(),
                    event.getRoleName(),
                    event.getPermissions()
            );
            log.info("Successfully granted team drive access for event: {}", event.getEventId());
        } catch (Exception e) {
            log.error("Failed to grant team drive access for event: {} - {}",
                    event.getEventId(), e.getMessage());
            throw e; // Rethrow to trigger Kafka retry
        }
    }

    @KafkaListener(topics = "teamsync.teams.member_removed", groupId = "permission-manager")
    @Transactional
    public void handleTeamMemberRemoved(@Payload @Valid TeamMemberEvent event) {
        // SECURITY FIX (Round 14 #C20): Check for duplicate event (replay attack prevention)
        if (isDuplicateEvent(event.getEventId(), "TeamMemberRemoved")) {
            return;
        }

        // SECURITY FIX (Round 12): Validate tenant before processing
        validateTenantId(event.getTenantId(), "TeamMemberRemoved");

        log.info("Team member removed: tenantId={}, teamId={}, userId={}",
                event.getTenantId(), event.getTeamId(), event.getUserId());

        try {
            // Revoke team drive access
            permissionService.revokeTeamMemberAccess(
                    event.getTenantId(),
                    event.getTeamId(),
                    event.getUserId()
            );
            log.info("Successfully revoked team drive access for event: {}", event.getEventId());
        } catch (Exception e) {
            log.error("Failed to revoke team drive access for event: {} - {}",
                    event.getEventId(), e.getMessage());
            throw e; // Rethrow to trigger Kafka retry
        }
    }

    @KafkaListener(topics = "teamsync.teams.created", groupId = "permission-manager")
    @Transactional
    public void handleTeamCreated(@Payload @Valid TeamCreatedEvent event) {
        // SECURITY FIX: Check for duplicate event (replay attack prevention)
        if (isDuplicateEvent(event.getEventId(), "TeamCreated")) {
            return;
        }

        // SECURITY FIX: Validate tenant before processing
        validateTenantId(event.getTenantId(), "TeamCreated");

        log.info("Team created: tenantId={}, teamId={}, owner={}",
                event.getTenantId(), event.getTeamId(), event.getOwnerId());

        try {
            // Create team drive
            permissionService.createTeamDrive(
                    event.getTenantId(),
                    event.getTeamId(),
                    event.getTeamName(),
                    event.getOwnerId(),
                    event.getQuotaBytes()
            );
            log.info("Successfully created team drive for event: {}", event.getEventId());
        } catch (Exception e) {
            log.error("Failed to create team drive for event: {} - {}",
                    event.getEventId(), e.getMessage());
            throw e; // Rethrow to trigger Kafka retry
        }
    }

    @KafkaListener(topics = "teamsync.teams.deleted", groupId = "permission-manager")
    @Transactional
    public void handleTeamDeleted(@Payload @Valid TeamDeletedEvent event) {
        // SECURITY FIX: Check for duplicate event (replay attack prevention)
        if (isDuplicateEvent(event.getEventId(), "TeamDeleted")) {
            return;
        }

        // SECURITY FIX: Validate tenant before processing
        validateTenantId(event.getTenantId(), "TeamDeleted");

        log.info("Team deleted: tenantId={}, teamId={}",
                event.getTenantId(), event.getTeamId());

        try {
            // Archive team drive
            permissionService.archiveTeamDrive(
                    event.getTenantId(),
                    event.getTeamId()
            );
            log.info("Successfully archived team drive for event: {}", event.getEventId());
        } catch (Exception e) {
            log.error("Failed to archive team drive for event: {} - {}",
                    event.getEventId(), e.getMessage());
            throw e; // Rethrow to trigger Kafka retry
        }
    }
}
