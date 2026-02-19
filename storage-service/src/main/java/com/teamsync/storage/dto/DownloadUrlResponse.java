package com.teamsync.storage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DownloadUrlResponse {

    private String url;
    private String filename;
    private String contentType;
    private Long fileSize;
    private Integer expiresInSeconds;
}
