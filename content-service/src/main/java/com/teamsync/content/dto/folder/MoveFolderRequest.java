package com.teamsync.content.dto.folder;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for moving a folder.
 *
 * SECURITY FIX (Round 15 #M23): Added @Size constraint on targetParentId to prevent DoS attacks.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MoveFolderRequest {

    // Target parent folder ID (null for root)
    @Size(max = 64, message = "Target parent ID must not exceed 64 characters")
    private String targetParentId;

    /**
     * SECURITY FIX (Round 9): Validate newName to prevent path traversal via ".." sequences.
     */
    @Size(min = 1, max = 255, message = "Folder name must be between 1 and 255 characters")
    @Pattern(regexp = "^(?!.*\\.\\.)(?!\\.)[^/\\\\:*?\"<>|]+(?<!\\.)$",
             message = "Folder name contains invalid characters or path traversal sequences")
    private String newName;
}
