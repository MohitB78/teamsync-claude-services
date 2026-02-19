package com.teamsync.audit.dto;

import com.teamsync.audit.model.AuditLog;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Response for audit log search.
 */
@Data
@Builder
public class AuditSearchResponse {

    /**
     * The audit logs matching the search criteria.
     */
    private List<AuditLogDto> items;

    /**
     * Total number of matching items.
     */
    private long totalItems;

    /**
     * Total number of pages.
     */
    private int totalPages;

    /**
     * Current page number (0-based).
     */
    private int currentPage;

    /**
     * Page size.
     */
    private int pageSize;

    /**
     * Whether there are more pages.
     */
    private boolean hasMore;

    /**
     * DTO for audit log items in search results.
     */
    @Data
    @Builder
    public static class AuditLogDto {
        private String id;
        private String tenantId;
        private String driveId;
        private String userId;
        private String userName;
        private String action;
        private String resourceType;
        private String resourceId;
        private String resourceName;
        private String outcome;
        private String failureReason;
        private String ipAddress;
        private String eventTime;
        private boolean immudbVerified;
        private String hashChain;
        private boolean piiAccessed;
        private boolean sensitiveDataAccessed;

        public static AuditLogDto fromEntity(AuditLog entity) {
            return AuditLogDto.builder()
                    .id(entity.getId())
                    .tenantId(entity.getTenantId())
                    .driveId(entity.getDriveId())
                    .userId(entity.getUserId())
                    .userName(entity.getUserName())
                    .action(entity.getAction())
                    .resourceType(entity.getResourceType())
                    .resourceId(entity.getResourceId())
                    .resourceName(entity.getResourceName())
                    .outcome(entity.getOutcome())
                    .failureReason(entity.getFailureReason())
                    .ipAddress(entity.getIpAddress())
                    .eventTime(entity.getEventTime() != null ? entity.getEventTime().toString() : null)
                    .immudbVerified(entity.isImmudbVerified())
                    .hashChain(entity.getHashChain())
                    .piiAccessed(entity.isPiiAccessed())
                    .sensitiveDataAccessed(entity.isSensitiveDataAccessed())
                    .build();
        }
    }
}
