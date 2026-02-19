package com.teamsync.audit.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Response for purge history.
 */
@Data
@Builder
public class PurgeHistoryResponse {

    private List<PurgeRecordDto> items;
    private long totalItems;
    private int totalPages;
    private int currentPage;
    private int pageSize;
    private boolean hasMore;

    @Data
    @Builder
    public static class PurgeRecordDto {
        private String id;
        private String purgedBy;
        private String purgedByEmail;
        private String purgeReason;
        private String retentionPeriod;
        private int eventsPurged;
        private String purgeTime;
        private String hashBefore;
        private String hashAfter;
        private boolean immudbPurged;
        private boolean mongodbPurged;
        private String errorMessage;
    }
}
