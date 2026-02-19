package com.teamsync.common.dto.storage;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response from Storage Service after completing an upload.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StorageUploadCompleteResponse {

    private String storageKey;
    private String bucket;
    private String filename;
    private String contentType;
    private Long fileSize;
    private String checksum;

    private Boolean success;
    private String message;
}
