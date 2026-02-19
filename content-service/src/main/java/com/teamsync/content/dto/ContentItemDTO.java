package com.teamsync.content.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Unified DTO representing both folders and documents.
 * Type discriminator determines which fields are populated.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContentItemDTO {

    // Type discriminator
    private ContentType type;

    // Common fields (present in both folders and documents)
    private String id;
    private String tenantId;
    private String driveId;
    private String name;
    private String description;
    private Map<String, Object> metadata;
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
    private Instant createdAt;
    private Instant updatedAt;
    private Instant accessedAt;

    // Folder-specific fields
    private String parentId;           // Folders have parent, documents have folderId
    private String path;
    private Integer depth;
    private List<String> ancestorIds;
    private String color;
    private String icon;
    private Integer folderCount;       // Only for folders
    private Integer documentCount;     // Only for folders
    private Long totalSize;            // Total size for folders

    // Document-specific fields
    private String folderId;           // Parent folder for documents
    private String contentType;        // MIME type
    private Long fileSize;             // Individual file size
    private String extension;
    private String documentTypeId;
    private String documentTypeName;
    private Integer versionCount;
    private Long entityVersion;        // Optimistic locking version (for concurrent edit detection)
    private Boolean isLocked;
    private String lockedBy;
    private Instant lockedAt;
    private Boolean hasThumbnail;
    private String thumbnailUrl;
    private String downloadUrl;

    // Computed fields (common)
    private String formattedSize;
    private String breadcrumb;

    public enum ContentType {
        FOLDER,
        DOCUMENT
    }

    /**
     * Helper method to check if this is a folder
     */
    public boolean isFolder() {
        return type == ContentType.FOLDER;
    }

    /**
     * Helper method to check if this is a document
     */
    public boolean isDocument() {
        return type == ContentType.DOCUMENT;
    }
}
