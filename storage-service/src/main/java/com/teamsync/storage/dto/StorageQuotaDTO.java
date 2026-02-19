package com.teamsync.storage.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StorageQuotaDTO {

    private String tenantId;
    private String driveId;

    // Storage usage
    private Long quotaLimit;
    private Long usedStorage;
    private Long availableStorage;
    private Double usagePercentage;

    // Formatted values
    private String quotaLimitFormatted;
    private String usedStorageFormatted;
    private String availableStorageFormatted;

    // File counts
    private Long maxFileCount;
    private Long currentFileCount;
    private Long maxFileSizeBytes;
    private String maxFileSizeFormatted;

    // Storage by tier
    private Long hotStorageUsed;
    private Long warmStorageUsed;
    private Long coldStorageUsed;
    private Long archiveStorageUsed;

    // Status
    private Boolean isQuotaExceeded;
    private Boolean isNearQuotaLimit;  // > 90%
}
