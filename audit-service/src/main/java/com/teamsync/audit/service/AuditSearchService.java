package com.teamsync.audit.service;

import com.teamsync.audit.dto.AuditSearchRequest;
import com.teamsync.audit.dto.AuditSearchResponse;
import com.teamsync.audit.dto.AuditStats;
import com.teamsync.audit.model.AuditLog;
import com.teamsync.audit.repository.AuditLogRepository;
import com.teamsync.audit.repository.PurgeRecordRepository;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for searching and querying audit logs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditSearchService {

    private final AuditLogRepository auditLogRepository;
    private final PurgeRecordRepository purgeRecordRepository;

    /**
     * Search audit logs with filters.
     */
    @Timed(value = "audit.search", description = "Time to search audit logs")
    public AuditSearchResponse search(String tenantId, AuditSearchRequest request) {
        log.debug("Searching audit logs for tenant {} with filters: {}", tenantId, request);

        // Build pageable
        Sort sort = "asc".equalsIgnoreCase(request.getSortDirection())
                ? Sort.by(request.getSortBy()).ascending()
                : Sort.by(request.getSortBy()).descending();
        Pageable pageable = PageRequest.of(request.getPage(), request.getSize(), sort);

        // Execute search based on filters
        Page<AuditLog> page;

        if (request.getActions() != null && !request.getActions().isEmpty()) {
            page = auditLogRepository.findByTenantIdAndActionInOrderByEventTimeDesc(
                    tenantId, request.getActions(), pageable);
        } else if (request.getUserId() != null && !request.getUserId().isEmpty()) {
            page = auditLogRepository.findByTenantIdAndUserIdOrderByEventTimeDesc(
                    tenantId, request.getUserId(), pageable);
        } else if (request.getStartTime() != null && request.getEndTime() != null) {
            page = auditLogRepository.findByTenantIdAndEventTimeBetweenOrderByEventTimeDesc(
                    tenantId, request.getStartTime(), request.getEndTime(), pageable);
        } else {
            page = auditLogRepository.findByTenantIdOrderByEventTimeDesc(tenantId, pageable);
        }

        // Convert to DTOs
        List<AuditSearchResponse.AuditLogDto> items = page.getContent().stream()
                .map(AuditSearchResponse.AuditLogDto::fromEntity)
                .collect(Collectors.toList());

        return AuditSearchResponse.builder()
                .items(items)
                .totalItems(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .currentPage(page.getNumber())
                .pageSize(page.getSize())
                .hasMore(page.hasNext())
                .build();
    }

    /**
     * Get audit statistics for admin dashboard.
     */
    @Timed(value = "audit.stats", description = "Time to generate audit stats")
    public AuditStats getStats(String tenantId) {
        log.debug("Getting audit stats for tenant {}", tenantId);

        Instant now = Instant.now();
        Instant last24h = now.minus(24, ChronoUnit.HOURS);
        Instant last7d = now.minus(7, ChronoUnit.DAYS);
        Instant last30d = now.minus(30, ChronoUnit.DAYS);

        // Get counts
        long totalEvents = auditLogRepository.countByTenantId(tenantId);
        long verifiedEvents = auditLogRepository.countByTenantIdAndImmudbVerified(tenantId, true);
        long events24h = auditLogRepository.countByTenantIdAndEventTimeBetween(tenantId, last24h, now);
        long events7d = auditLogRepository.countByTenantIdAndEventTimeBetween(tenantId, last7d, now);
        long events30d = auditLogRepository.countByTenantIdAndEventTimeBetween(tenantId, last30d, now);

        // Get counts by action
        Map<String, Long> byAction = new HashMap<>();
        for (String action : List.of("DELETE", "PERMISSION_CHANGE", "SHARE", "CREATE", "UPDATE", "DOWNLOAD")) {
            long count = auditLogRepository.countByTenantIdAndAction(tenantId, action);
            if (count > 0) {
                byAction.put(action, count);
            }
        }

        // Get counts by outcome
        Map<String, Long> byOutcome = new HashMap<>();
        for (String outcome : List.of("SUCCESS", "FAILURE", "DENIED")) {
            long count = auditLogRepository.countByTenantIdAndOutcome(tenantId, outcome);
            if (count > 0) {
                byOutcome.put(outcome, count);
            }
        }

        // Get purge stats
        long purgeOps = purgeRecordRepository.countByTenantId(tenantId);

        return AuditStats.builder()
                .totalEvents(totalEvents)
                .verifiedEvents(verifiedEvents)
                .pendingEvents(totalEvents - verifiedEvents)
                .byAction(byAction)
                .byOutcome(byOutcome)
                .byResourceType(Map.of()) // Would need additional query
                .last24Hours(events24h)
                .last7Days(events7d)
                .last30Days(events30d)
                .purgeOperations(purgeOps)
                .totalPurged(0) // Would need to sum from purge records
                .generatedAt(Instant.now())
                .build();
    }

    /**
     * Get user's audit activity.
     */
    @Timed(value = "audit.user.activity", description = "Time to get user activity")
    public AuditSearchResponse getUserActivity(String tenantId, String userId, int page, int size) {
        log.debug("Getting activity for user {} in tenant {}", userId, tenantId);

        Pageable pageable = PageRequest.of(page, size, Sort.by("eventTime").descending());
        Page<AuditLog> auditPage = auditLogRepository.findByTenantIdAndUserIdOrderByEventTimeDesc(
                tenantId, userId, pageable);

        List<AuditSearchResponse.AuditLogDto> items = auditPage.getContent().stream()
                .map(AuditSearchResponse.AuditLogDto::fromEntity)
                .collect(Collectors.toList());

        return AuditSearchResponse.builder()
                .items(items)
                .totalItems(auditPage.getTotalElements())
                .totalPages(auditPage.getTotalPages())
                .currentPage(auditPage.getNumber())
                .pageSize(auditPage.getSize())
                .hasMore(auditPage.hasNext())
                .build();
    }
}
