package com.teamsync.content.client;

import com.teamsync.common.dto.ApiResponse;
import com.teamsync.common.dto.storage.StorageUploadCompleteRequest;
import com.teamsync.common.dto.storage.StorageUploadCompleteResponse;
import com.teamsync.common.dto.storage.StorageUploadInitRequest;
import com.teamsync.common.dto.storage.StorageUploadInitResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * HTTP Service Client for Storage Service.
 * Uses Spring Boot 4's declarative HTTP interface.
 *
 * Note: For multipart uploads (direct upload), use StorageDirectUploadClient instead.
 */
@HttpExchange("/api/storage")
public interface StorageServiceClient {

    /**
     * Initialize an upload session.
     */
    @PostExchange("/upload/init")
    ApiResponse<StorageUploadInitResponse> initializeUpload(@RequestBody StorageUploadInitRequest request);

    /**
     * Complete an upload session.
     */
    @PostExchange("/upload/complete")
    ApiResponse<StorageUploadCompleteResponse> completeUpload(@RequestBody StorageUploadCompleteRequest request);

    /**
     * Cancel an upload session.
     */
    @PostExchange("/upload/{sessionId}/cancel")
    ApiResponse<Void> cancelUpload(@PathVariable String sessionId);

    /**
     * Delete a file from storage.
     * Used when permanently deleting documents to ensure quota is updated.
     */
    @DeleteExchange("/files")
    ApiResponse<Void> deleteFile(@RequestParam String bucket, @RequestParam String storageKey);

    /**
     * Get storage quota for the current drive.
     */
    @GetExchange("/quota")
    ApiResponse<StorageQuotaResponse> getStorageQuota();

    /**
     * Storage quota response DTO.
     */
    record StorageQuotaResponse(
            String tenantId,
            String driveId,
            Long quotaLimit,
            Long usedStorage,
            Long availableStorage,
            Double usagePercentage,
            String quotaLimitFormatted,
            String usedStorageFormatted,
            String availableStorageFormatted,
            Boolean isQuotaExceeded,
            Boolean isNearQuotaLimit
    ) {}
}
