package com.teamsync.storage.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to initiate a file upload.
 *
 * SECURITY FIX (Round 15 #M19): Added @Size and @Pattern constraints to prevent DoS attacks.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadInitRequest {

    @NotBlank(message = "Filename is required")
    @Size(max = 255, message = "Filename must not exceed 255 characters")
    @Pattern(regexp = "^(?!.*\\.\\.)(?!\\.)[^/\\\\:*?\"<>|\\x00-\\x1f]+(?<!\\.)$",
             message = "Filename contains invalid characters or path traversal sequences")
    private String filename;

    @NotNull(message = "File size is required")
    @Min(value = 1, message = "File size must be greater than 0")
    @Max(value = 10737418240L, message = "File size must not exceed 10GB")
    private Long fileSize;

    @Size(max = 255, message = "Content type must not exceed 255 characters")
    @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9!#$&\\-^_.+]*\\/[a-zA-Z0-9][a-zA-Z0-9!#$&\\-^_.+]*$",
             message = "Invalid content type format")
    private String contentType;  // Optional, will be detected if not provided

    @Size(max = 64, message = "Folder ID must not exceed 64 characters")
    private String folderId;     // Target folder

    private Boolean useMultipart;  // Force multipart upload

    @Min(value = 5242880, message = "Chunk size must be at least 5MB")
    @Max(value = 104857600, message = "Chunk size must not exceed 100MB")
    private Integer chunkSize;   // Custom chunk size
}
