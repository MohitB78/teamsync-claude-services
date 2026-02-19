package com.teamsync.team.dto.portal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Limited folder view for external portal users.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortalFolderDTO {

    private String id;
    private String name;
    private String parentId;
    private String path;
    private int fileCount;
    private int folderCount;
}
