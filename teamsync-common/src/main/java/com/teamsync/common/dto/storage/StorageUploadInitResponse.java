package com.teamsync.common.dto.storage;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Response from Storage Service after initializing an upload session.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StorageUploadInitResponse {

    private String sessionId;
    private String uploadType;    // SIMPLE or MULTIPART

    // For simple upload
    private String uploadUrl;

    // For multipart upload
    private List<PartUploadUrl> partUrls;
    private Integer totalParts;
    private Integer chunkSize;

    // Storage details
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
