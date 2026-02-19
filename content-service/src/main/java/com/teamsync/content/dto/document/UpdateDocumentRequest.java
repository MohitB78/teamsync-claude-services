package com.teamsync.content.dto.document;

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
public class UpdateDocumentRequest {

    /**
     * SECURITY FIX (Round 9): Enhanced regex to prevent path traversal via ".." sequences.
     */
    @Size(min = 1, max = 255, message = "Document name must be between 1 and 255 characters")
    @jakarta.validation.constraints.Pattern(
            regexp = "^(?!.*\\.\\.)(?!\\.)[^/\\\\:*?\"<>|]+$",
            message = "Document name contains invalid characters or path traversal sequences")
    private String name;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    private String documentTypeId;

    /**
     * SECURITY FIX (Round 14 #M22): Added @Size constraint to prevent DoS via large payloads.
     */
    @Size(max = 50, message = "Metadata must not exceed 50 entries")
    private Map<String, Object> metadata;

    @Size(max = 20, message = "Cannot have more than 20 tags")
    private List<String> tags;

    private Boolean isStarred;

    private Boolean isPinned;
}
