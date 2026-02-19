package com.teamsync.settings.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * SECURITY: Typed request DTO for updating drive settings.
 * Replaces unsafe Map<String, Object> to prevent NoSQL injection and
 * ensure all inputs are validated.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDriveSettingsRequest {

    @Pattern(regexp = "^(grid|list|compact)$", message = "Default view must be 'grid', 'list', or 'compact'")
    private String defaultView;

    @Pattern(regexp = "^(name|date|size|type)$", message = "Sort by must be 'name', 'date', 'size', or 'type'")
    private String sortBy;

    @Pattern(regexp = "^(asc|desc)$", message = "Sort order must be 'asc' or 'desc'")
    private String sortOrder;

    private Boolean showHiddenFiles;

    private Boolean showThumbnails;

    @Min(value = 10, message = "Items per page must be at least 10")
    @Max(value = 100, message = "Items per page must not exceed 100")
    private Integer itemsPerPage;

    @Size(max = 20, message = "Visible columns list must not exceed 20 items")
    private List<@Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]*$",
                          message = "Column name must be alphanumeric") String> visibleColumns;

    @Size(max = 20, message = "Column order list must not exceed 20 items")
    private List<@Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]*$",
                          message = "Column name must be alphanumeric") String> columnOrder;

    @Size(max = 50, message = "Pinned folders list must not exceed 50 items")
    private List<@Pattern(regexp = "^[a-zA-Z0-9\\-_]+$",
                          message = "Folder ID must be alphanumeric") String> pinnedFolders;

    @Size(max = 100, message = "Favorite documents list must not exceed 100 items")
    private List<@Pattern(regexp = "^[a-zA-Z0-9\\-_]+$",
                          message = "Document ID must be alphanumeric") String> favoriteDocuments;

    @Size(max = 10, message = "Default filters list must not exceed 10 items")
    private List<@Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_:]*$",
                          message = "Filter must be alphanumeric") String> defaultFilters;

    private Boolean rememberLastFolder;

    @Pattern(regexp = "^[a-zA-Z0-9\\-_]*$", message = "Last folder ID must be alphanumeric")
    private String lastFolderId;
}
