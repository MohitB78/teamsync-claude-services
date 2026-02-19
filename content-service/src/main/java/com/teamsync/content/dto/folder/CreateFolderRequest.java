package com.teamsync.content.dto.folder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Request DTO for creating a folder.
 *
 * SECURITY FIX (Round 15 #M21): Added @Size constraints on all fields to prevent DoS attacks.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateFolderRequest {

    /**
     * SECURITY FIX (Round 9): Enhanced regex to prevent path traversal via ".." sequences.
     * Now blocks: /, \, :, *, ?, ", <, >, |, and any sequence containing ".."
     */
    @NotBlank(message = "Folder name is required")
    @Size(min = 1, max = 255, message = "Folder name must be between 1 and 255 characters")
    @Pattern(regexp = "^(?!.*\\.\\.)(?!\\.)[^/\\\\:*?\"<>|]+(?<!\\.)$",
             message = "Folder name contains invalid characters or path traversal sequences")
    private String name;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    @Size(max = 64, message = "Parent ID must not exceed 64 characters")
    private String parentId;  // null for root level

    @Size(max = 32, message = "Color must not exceed 32 characters")
    private String color;

    @Size(max = 64, message = "Icon must not exceed 64 characters")
    private String icon;

    @Size(max = 50, message = "Cannot have more than 50 metadata entries")
    private Map<String, Object> metadata;

    @Size(max = 20, message = "Cannot have more than 20 tags")
    private List<String> tags;
}
