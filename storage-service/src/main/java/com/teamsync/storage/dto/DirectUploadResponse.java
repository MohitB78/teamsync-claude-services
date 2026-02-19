package com.teamsync.storage.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for direct file upload (streaming through backend).
 * Used for small files where simplicity > performance.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DirectUploadResponse {

    /**
     * The storage bucket where the file was uploaded.
     */
    private String bucket;

    /**
     * The storage key (path) of the uploaded file.
     */
    private String storageKey;

    /**
     * The size of the uploaded file in bytes.
     */
    private Long fileSize;

    /**
     * The content type of the uploaded file.
     */
    private String contentType;
}
