package com.teamsync.content.dto.upload;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Response from Content Service after initializing an upload.
 * Contains both document info and storage upload details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DocumentUploadInitResponse {

    // Content Service document (in PENDING_UPLOAD status)
    private String documentId;

    // Storage Service session
    private String sessionId;
    private String uploadType;    // SIMPLE or MULTIPART

    // For simple upload - single presigned URL
    private String uploadUrl;

    // For multipart upload - list of part URLs
    private List<PartUploadUrl> partUrls;
    private Integer totalParts;
    private Integer chunkSize;

    // Storage location (needed for complete request)
    private String bucket;
    private String storageKey;

    // Expiration
    private Instant expiresAt;
    private Integer urlValiditySeconds;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PartUploadUrl {
        private Integer partNumber;
        private String uploadUrl;
    }
}
