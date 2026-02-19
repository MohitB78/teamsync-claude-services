package com.teamsync.content.dto.document;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DocumentDTO {

    private String id;
    private String tenantId;
    private String driveId;
    private String folderId;
    private String name;
    private String description;
    private String contentType;
    private Long fileSize;
    private String extension;
    private String documentTypeId;
    private String documentTypeName;
    private Map<String, Object> metadata;
    private Integer versionCount;
    private Long entityVersion;  // Optimistic locking version (for concurrent edit detection)
    private List<String> tags;
    private Boolean isStarred;
    private Boolean isPinned;
    private String ownerId;
    private String ownerName;
    private String createdBy;
    private String createdByName;
    private String lastModifiedBy;
    private String lastModifiedByName;
    private String status;
    private Boolean isLocked;
    private String lockedBy;
    private Instant lockedAt;
    private Boolean hasThumbnail;
    private String thumbnailUrl;
    private String downloadUrl;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant accessedAt;

    // Computed fields
    private String formattedSize;
    private String breadcrumb;
}
