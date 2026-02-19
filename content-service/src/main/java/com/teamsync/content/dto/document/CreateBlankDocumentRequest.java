package com.teamsync.content.dto.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a blank Office document.
 *
 * <p>Used with the POST /api/documents/blank endpoint to create new
 * Word, Excel, or PowerPoint documents from blank templates.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBlankDocumentRequest {

    /**
     * The type of blank document to create.
     * Supported values: WORD, EXCEL, POWERPOINT (case-insensitive)
     */
    @NotBlank(message = "Document type is required")
    @Pattern(regexp = "^(?i)(word|excel|powerpoint|docx|xlsx|pptx)$",
            message = "Document type must be one of: word, excel, powerpoint")
    private String type;

    /**
     * The name for the new document (extension will be added automatically if missing).
     */
    @NotBlank(message = "Document name is required")
    @Size(min = 1, max = 200, message = "Document name must be between 1 and 200 characters")
    @Pattern(regexp = "^(?!.*\\.\\.)(?!\\.)[^/\\\\:*?\"<>|]+$",
            message = "Document name contains invalid characters or path traversal sequences")
    private String name;

    /**
     * The folder ID to create the document in (null for root level).
     */
    @Size(max = 64, message = "Folder ID must not exceed 64 characters")
    @Pattern(regexp = "^[a-zA-Z0-9-]*$", message = "Invalid folder ID format")
    private String folderId;
}
