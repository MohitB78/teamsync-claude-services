package com.teamsync.storage.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadCompleteRequest {

    @NotBlank(message = "Session ID is required")
    private String sessionId;

    // For multipart uploads
    private List<PartETag> parts;

    // Optional checksum for verification
    private String checksum;
    private String checksumAlgorithm;  // MD5, SHA256

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PartETag {
        private Integer partNumber;
        private String etag;
    }
}
