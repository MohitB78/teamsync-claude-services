package com.teamsync.settings.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Drive-specific settings.
 * These settings are per-user per-drive preferences.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "drive_settings")
@CompoundIndexes({
    @CompoundIndex(name = "tenant_user_drive_idx", def = "{'tenantId': 1, 'userId': 1, 'driveId': 1}", unique = true),
    @CompoundIndex(name = "tenant_drive_idx", def = "{'tenantId': 1, 'driveId': 1}")
})
public class DriveSettings {

    @Id
    private String id;

    private String tenantId;
    private String userId;
    private String driveId;

    // View Settings
    @Builder.Default
    private String defaultView = "grid";

    @Builder.Default
    private String sortBy = "name";

    @Builder.Default
    private String sortOrder = "asc";

    @Builder.Default
    private boolean showHiddenFiles = false;

    @Builder.Default
    private boolean showThumbnails = true;

    @Builder.Default
    private int itemsPerPage = 50;

    // Column Settings (for list view)
    @Builder.Default
    private List<String> visibleColumns = new ArrayList<>(List.of("name", "modified", "size", "owner"));

    @Builder.Default
    private List<String> columnOrder = new ArrayList<>(List.of("name", "modified", "size", "owner"));

    // Quick Access
    @Builder.Default
    private List<String> pinnedFolders = new ArrayList<>();

    @Builder.Default
    private List<String> favoriteDocuments = new ArrayList<>();

    // Filter Preferences
    @Builder.Default
    private List<String> defaultFilters = new ArrayList<>();

    @Builder.Default
    private boolean rememberLastFolder = true;

    private String lastFolderId;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
