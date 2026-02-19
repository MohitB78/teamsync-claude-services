package com.teamsync.content.dto.folder;

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
public class FolderDTO {

    private String id;
    private String tenantId;
    private String driveId;
    private String parentId;
    private String name;
    private String description;
    private String path;
    private Integer depth;
    private List<String> ancestorIds;
    private String color;
    private String icon;
    private Map<String, Object> metadata;
    private List<String> tags;
    private Integer folderCount;
    private Integer documentCount;
    private Long totalSize;
    private String formattedSize;
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

    // Computed fields
    private String breadcrumb;
    private List<BreadcrumbItem> breadcrumbItems;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BreadcrumbItem {
        private String id;
        private String name;
        private String path;
    }
}
