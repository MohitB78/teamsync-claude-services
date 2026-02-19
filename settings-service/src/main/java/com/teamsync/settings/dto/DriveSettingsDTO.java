package com.teamsync.settings.dto;

import com.teamsync.settings.model.DriveSettings;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * DTO for drive settings response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriveSettingsDTO {

    private String id;
    private String tenantId;
    private String userId;
    private String driveId;
    private String defaultView;
    private String sortBy;
    private String sortOrder;
    private boolean showHiddenFiles;
    private boolean showThumbnails;
    private int itemsPerPage;
    private List<String> visibleColumns;
    private List<String> columnOrder;
    private List<String> pinnedFolders;
    private List<String> favoriteDocuments;
    private List<String> defaultFilters;
    private boolean rememberLastFolder;
    private String lastFolderId;
    private Instant updatedAt;

    public static DriveSettingsDTO fromEntity(DriveSettings entity) {
        if (entity == null) return null;

        return DriveSettingsDTO.builder()
                .id(entity.getId())
                .tenantId(entity.getTenantId())
                .userId(entity.getUserId())
                .driveId(entity.getDriveId())
                .defaultView(entity.getDefaultView())
                .sortBy(entity.getSortBy())
                .sortOrder(entity.getSortOrder())
                .showHiddenFiles(entity.isShowHiddenFiles())
                .showThumbnails(entity.isShowThumbnails())
                .itemsPerPage(entity.getItemsPerPage())
                .visibleColumns(entity.getVisibleColumns())
                .columnOrder(entity.getColumnOrder())
                .pinnedFolders(entity.getPinnedFolders())
                .favoriteDocuments(entity.getFavoriteDocuments())
                .defaultFilters(entity.getDefaultFilters())
                .rememberLastFolder(entity.isRememberLastFolder())
                .lastFolderId(entity.getLastFolderId())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
