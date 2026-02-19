package com.teamsync.team.dto.portal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Limited file view for external portal users.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortalFileDTO {

    private String id;
    private String name;
    private String type;
    private long size;
    private String mimeType;
    private String folderId;
    private String folderPath;
    private Instant createdAt;
    private Instant modifiedAt;
    private String createdByName;
}
