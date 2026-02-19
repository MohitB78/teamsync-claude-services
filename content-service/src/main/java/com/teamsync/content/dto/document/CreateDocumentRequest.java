package com.teamsync.content.dto.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDocumentRequest {

    /**
     * SECURITY FIX (Round 9): Enhanced regex to prevent path traversal via ".." sequences.
     * Blocks: /, \, :, *, ?, ", <, >, |, and any sequence containing ".."
     * Allows filenames with extensions (e.g., document.pdf, report.v2.xlsx)
     */
    @NotBlank(message = "Document name is required")
    @Size(min = 1, max = 255, message = "Document name must be between 1 and 255 characters")
    @jakarta.validation.constraints.Pattern(
            regexp = "^(?!.*\\.\\.)(?!\\.)[^/\\\\:*?\"<>|]+$",
            message = "Document name contains invalid characters or path traversal sequences")
    private String name;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    private String folderId;  // null for root level

    @NotBlank(message = "Content type is required")
    private String contentType;

    private Long fileSize;

    private String storageKey;  // Set after upload

    private String storageBucket;

    private String documentTypeId;

    private Map<String, Object> metadata;

    private List<String> tags;
}
