package com.teamsync.sharing.client;

import com.teamsync.common.dto.ApiResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * HTTP Service Client for Content Service.
 * Uses Spring Boot 4's declarative HTTP interface.
 *
 * SECURITY FIX: Used to look up the actual resource owner for sharing operations,
 * rather than assuming the current user is the owner.
 */
@HttpExchange("/api/documents")
public interface ContentServiceClient {

    /**
     * Get basic document info including owner ID.
     *
     * @param documentId the document ID
     * @param tenantId the tenant ID header
     * @param driveId the drive ID header
     * @return the document info containing the owner ID
     */
    @GetExchange("/{documentId}/info")
    ApiResponse<ResourceInfo> getDocumentInfo(
            @PathVariable String documentId,
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Drive-ID") String driveId);

    /**
     * Get basic folder info including owner ID.
     *
     * @param folderId the folder ID
     * @param tenantId the tenant ID header
     * @param driveId the drive ID header
     * @return the folder info containing the owner ID
     */
    @GetExchange("/folders/{folderId}/info")
    ApiResponse<ResourceInfo> getFolderInfo(
            @PathVariable String folderId,
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Drive-ID") String driveId);

    /**
     * Minimal resource info needed for sharing operations.
     */
    record ResourceInfo(
            String id,
            String name,
            String ownerId,
            String ownerName,
            String driveId,
            String tenantId
    ) {}
}
