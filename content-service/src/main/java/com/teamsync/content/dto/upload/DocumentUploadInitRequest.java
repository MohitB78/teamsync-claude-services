package com.teamsync.content.dto.upload;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Request to initialize a document upload via Content Service.
 * Content Service will create a PENDING document and coordinate with Storage Service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentUploadInitRequest {

    @NotBlank(message = "Filename is required")
    private String filename;

    @NotNull(message = "File size is required")
    @Min(value = 1, message = "File size must be greater than 0")
    private Long fileSize;

    private String contentType;

    // Target folder (null for root)
    private String folderId;

    // Document metadata
    private String description;
    private String documentTypeId;
    private Map<String, Object> metadata;
    private List<String> tags;

    // Upload options
    private Boolean useMultipart;
    private Integer chunkSize;
}
