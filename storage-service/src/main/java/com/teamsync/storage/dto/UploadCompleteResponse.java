package com.teamsync.storage.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UploadCompleteResponse {

    private String storageKey;
    private String bucket;
    private String filename;
    private String contentType;
    private Long fileSize;
    private String checksum;

    // Ready to create document with this info
    private Boolean success;
    private String message;
}
