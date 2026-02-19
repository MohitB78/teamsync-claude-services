package com.teamsync.content.dto.upload;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request to complete a document upload via Content Service.
 * Content Service will activate the document and finalize with Storage Service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentUploadCompleteRequest {

    @NotBlank(message = "Document ID is required")
    private String documentId;

    @NotBlank(message = "Session ID is required")
    private String sessionId;

    // For multipart uploads - list of part ETags
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
