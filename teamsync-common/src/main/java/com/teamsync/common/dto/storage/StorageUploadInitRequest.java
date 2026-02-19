package com.teamsync.common.dto.storage;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to initialize an upload session with Storage Service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageUploadInitRequest {

    @NotBlank(message = "Filename is required")
    private String filename;

    @NotNull(message = "File size is required")
    @Min(value = 1, message = "File size must be greater than 0")
    private Long fileSize;

    private String contentType;

    private Boolean useMultipart;

    private Integer chunkSize;
}
