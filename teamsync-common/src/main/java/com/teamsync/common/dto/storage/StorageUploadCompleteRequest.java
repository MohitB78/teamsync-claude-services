package com.teamsync.common.dto.storage;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request to complete an upload session with Storage Service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageUploadCompleteRequest {

    @NotBlank(message = "Session ID is required")
    private String sessionId;

    // For multipart uploads
    private List<PartETag> parts;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PartETag {
        private Integer partNumber;
        private String etag;
    }
}
