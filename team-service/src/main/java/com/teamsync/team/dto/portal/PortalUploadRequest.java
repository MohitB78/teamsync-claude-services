package com.teamsync.team.dto.portal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Upload request for external portal users.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortalUploadRequest {

    @NotBlank(message = "Filename is required")
    private String filename;

    @NotBlank(message = "Content type is required")
    private String contentType;

    @Positive(message = "Size must be positive")
    private long size;

    /**
     * Optional folder to upload to.
     */
    private String folderId;
}
