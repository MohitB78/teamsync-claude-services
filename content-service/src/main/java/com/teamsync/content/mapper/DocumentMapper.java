package com.teamsync.content.mapper;

import com.teamsync.content.dto.document.DocumentDTO;
import com.teamsync.content.model.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DocumentMapper {

    @Value("${teamsync.api.base-url:}")
    private String apiBaseUrl;

    public DocumentDTO toDTO(Document document) {
        if (document == null) {
            return null;
        }

        // Generate download URL for the document
        // Format: /api/documents/{id}/download
        String downloadUrl = null;
        if (document.getStorageKey() != null) {
            downloadUrl = (apiBaseUrl.isEmpty() ? "" : apiBaseUrl) + "/api/documents/" + document.getId() + "/download";
        }

        return DocumentDTO.builder()
                .id(document.getId())
                .tenantId(document.getTenantId())
                .driveId(document.getDriveId())
                .folderId(document.getFolderId())
                .name(document.getName())
                .description(document.getDescription())
                .contentType(document.getContentType())
                .fileSize(document.getFileSize())
                .extension(document.getExtension())
                .documentTypeId(document.getDocumentTypeId())
                .metadata(document.getMetadata())
                .versionCount(document.getVersionCount())
                .entityVersion(document.getEntityVersion())
                .tags(document.getTags())
                .isStarred(document.getIsStarred())
                .isPinned(document.getIsPinned())
                .ownerId(document.getOwnerId())
                .createdBy(document.getCreatedBy())
                .lastModifiedBy(document.getLastModifiedBy())
                .status(document.getStatus() != null ? document.getStatus().name() : null)
                .isLocked(document.getIsLocked())
                .lockedBy(document.getLockedBy())
                .lockedAt(document.getLockedAt())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .accessedAt(document.getAccessedAt())
                .formattedSize(FolderMapper.formatFileSize(document.getFileSize()))
                .downloadUrl(downloadUrl)
                .build();
    }
}
