package com.teamsync.content.mapper;

import com.teamsync.content.dto.folder.FolderDTO;
import com.teamsync.content.dto.folder.FolderTreeNode;
import com.teamsync.content.model.Folder;
import org.springframework.stereotype.Component;

@Component
public class FolderMapper {

    public FolderDTO toDTO(Folder folder) {
        if (folder == null) {
            return null;
        }

        return FolderDTO.builder()
                .id(folder.getId())
                .tenantId(folder.getTenantId())
                .driveId(folder.getDriveId())
                .parentId(folder.getParentId())
                .name(folder.getName())
                .description(folder.getDescription())
                .path(folder.getPath())
                .depth(folder.getDepth())
                .ancestorIds(folder.getAncestorIds())
                .color(folder.getColor())
                .icon(folder.getIcon())
                .metadata(folder.getMetadata())
                .tags(folder.getTags())
                .folderCount(folder.getFolderCount())
                .documentCount(folder.getDocumentCount())
                .totalSize(folder.getTotalSize())
                .formattedSize(formatFileSize(folder.getTotalSize()))
                .isStarred(folder.getIsStarred())
                .isPinned(folder.getIsPinned())
                .ownerId(folder.getOwnerId())
                .createdBy(folder.getCreatedBy())
                .lastModifiedBy(folder.getLastModifiedBy())
                .status(folder.getStatus() != null ? folder.getStatus().name() : null)
                .createdAt(folder.getCreatedAt())
                .updatedAt(folder.getUpdatedAt())
                .accessedAt(folder.getAccessedAt())
                .build();
    }

    public FolderTreeNode toTreeNode(Folder folder) {
        if (folder == null) {
            return null;
        }

        return FolderTreeNode.builder()
                .id(folder.getId())
                .name(folder.getName())
                .path(folder.getPath())
                .parentId(folder.getParentId())
                .depth(folder.getDepth())
                .color(folder.getColor())
                .icon(folder.getIcon())
                .folderCount(folder.getFolderCount())
                .documentCount(folder.getDocumentCount())
                .hasChildren(folder.getFolderCount() != null && folder.getFolderCount() > 0)
                .isExpanded(false)
                .build();
    }

    public static String formatFileSize(Long bytes) {
        if (bytes == null || bytes == 0) {
            return "0 B";
        }

        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = 0;
        double size = bytes;

        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }

        if (unitIndex == 0) {
            return String.format("%d %s", bytes, units[unitIndex]);
        }

        return String.format("%.2f %s", size, units[unitIndex]);
    }
}
