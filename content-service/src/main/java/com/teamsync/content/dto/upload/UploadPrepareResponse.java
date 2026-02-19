package com.teamsync.content.dto.upload;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Response from upload preparation endpoint.
 * Backend decides the upload strategy based on file size and configuration.
 *
 * Two strategies:
 * 1. DIRECT - Small files stream through backend (no presigned URL exposed)
 * 2. PRESIGNED - Large files use presigned URL via API Gateway proxy
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UploadPrepareResponse {

    /**
     * Upload strategy decided by backend.
     * DIRECT: POST file to /api/documents/upload/direct
     * PRESIGNED: PUT file to uploadUrl, then POST to /api/documents/upload/complete
     */
    public enum UploadStrategy {
        DIRECT,    // Small files - stream through backend
        PRESIGNED  // Large files - presigned URL via API Gateway proxy
    }

    private UploadStrategy strategy;

    // For DIRECT strategy - just tells frontend where to upload
    private String directUploadEndpoint;

    // For PRESIGNED strategy - full init response
    private String documentId;
    private String sessionId;
    private String uploadType;    // SIMPLE or MULTIPART
    private String uploadUrl;     // For simple upload
    private List<PartUploadUrl> partUrls;  // For multipart upload
    private Integer totalParts;
    private Integer chunkSize;
    private String bucket;
    private String storageKey;
    private Instant expiresAt;
    private Integer urlValiditySeconds;

    // Threshold info (helps frontend understand the decision)
    private Long directUploadThreshold;
    private String message;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PartUploadUrl {
        private Integer partNumber;
        private String uploadUrl;
    }

    /**
     * Create a DIRECT strategy response.
     */
    public static UploadPrepareResponse directStrategy(long threshold) {
        return UploadPrepareResponse.builder()
                .strategy(UploadStrategy.DIRECT)
                .directUploadEndpoint("/api/documents/upload/direct")
                .directUploadThreshold(threshold)
                .message("File is small enough for direct upload through backend")
                .build();
    }

    /**
     * Create a PRESIGNED strategy response from init response.
     */
    public static UploadPrepareResponse presignedStrategy(DocumentUploadInitResponse initResponse, long threshold) {
        return UploadPrepareResponse.builder()
                .strategy(UploadStrategy.PRESIGNED)
                .documentId(initResponse.getDocumentId())
                .sessionId(initResponse.getSessionId())
                .uploadType(initResponse.getUploadType())
                .uploadUrl(initResponse.getUploadUrl())
                .partUrls(mapPartUrls(initResponse.getPartUrls()))
                .totalParts(initResponse.getTotalParts())
                .chunkSize(initResponse.getChunkSize())
                .bucket(initResponse.getBucket())
                .storageKey(initResponse.getStorageKey())
                .expiresAt(initResponse.getExpiresAt())
                .urlValiditySeconds(initResponse.getUrlValiditySeconds())
                .directUploadThreshold(threshold)
                .message("File is large, use presigned URL for upload")
                .build();
    }

    private static List<PartUploadUrl> mapPartUrls(List<DocumentUploadInitResponse.PartUploadUrl> urls) {
        if (urls == null) return null;
        return urls.stream()
                .map(p -> PartUploadUrl.builder()
                        .partNumber(p.getPartNumber())
                        .uploadUrl(p.getUploadUrl())
                        .build())
                .toList();
    }
}
