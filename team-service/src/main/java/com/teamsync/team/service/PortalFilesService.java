package com.teamsync.team.service;

import com.teamsync.common.exception.AccessDeniedException;
import com.teamsync.common.exception.ResourceNotFoundException;
import com.teamsync.team.dto.portal.*;
import com.teamsync.team.model.*;
import com.teamsync.team.model.Team.TeamMember;
import com.teamsync.team.model.Team.MemberStatus;
import com.teamsync.team.repository.*;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for portal file operations.
 * External users can VIEW, UPLOAD, and CREATE_VERSION - but NOT edit or delete files.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PortalFilesService {

    private final TeamRepository teamRepository;
    private final PortalAuthService portalAuthService;
    private final WebClient.Builder webClientBuilder;

    @Value("${teamsync.content-service.url:http://content-service:9081}")
    private String contentServiceUrl;

    @Value("${teamsync.storage-service.url:http://storage-service:9083}")
    private String storageServiceUrl;

    /**
     * Get files in a team folder.
     */
    public List<PortalFileDTO> getFiles(String accessToken, String teamId, String folderId) {
        TeamContext ctx = getTeamContext(accessToken, teamId);
        verifyAccess(ctx, TeamPermission.CONTENT_VIEW);

        // Call Content Service to get files
        String driveId = "team-" + teamId;

        List<Map<String, Object>> files = webClientBuilder.build()
                .get()
                .uri(contentServiceUrl + "/api/documents", uriBuilder -> uriBuilder
                        .queryParam("driveId", driveId)
                        .queryParam("folderId", folderId != null ? folderId : "root")
                        .queryParam("type", "file")
                        .build())
                .header("X-Tenant-ID", ctx.tenantId)
                .header("X-User-ID", ctx.userId)
                .retrieve()
                .bodyToMono(List.class)
                .block();

        if (files == null) {
            return List.of();
        }

        return files.stream()
                .map(this::mapToPortalFileDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get folders in a team.
     */
    public List<PortalFolderDTO> getFolders(String accessToken, String teamId, String parentId) {
        TeamContext ctx = getTeamContext(accessToken, teamId);
        verifyAccess(ctx, TeamPermission.CONTENT_VIEW);

        String driveId = "team-" + teamId;

        List<Map<String, Object>> folders = webClientBuilder.build()
                .get()
                .uri(contentServiceUrl + "/api/documents", uriBuilder -> uriBuilder
                        .queryParam("driveId", driveId)
                        .queryParam("folderId", parentId != null ? parentId : "root")
                        .queryParam("type", "folder")
                        .build())
                .header("X-Tenant-ID", ctx.tenantId)
                .header("X-User-ID", ctx.userId)
                .retrieve()
                .bodyToMono(List.class)
                .block();

        if (folders == null) {
            return List.of();
        }

        return folders.stream()
                .map(this::mapToPortalFolderDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get download URL for a file.
     */
    public String getDownloadUrl(String accessToken, String teamId, String fileId) {
        TeamContext ctx = getTeamContext(accessToken, teamId);
        verifyAccess(ctx, TeamPermission.CONTENT_VIEW);

        String driveId = "team-" + teamId;

        // Call Content Service to get download URL
        Map<String, Object> response = webClientBuilder.build()
                .get()
                .uri(contentServiceUrl + "/api/documents/{id}/download", fileId)
                .header("X-Tenant-ID", ctx.tenantId)
                .header("X-Drive-ID", driveId)
                .header("X-User-ID", ctx.userId)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null || !response.containsKey("downloadUrl")) {
            throw new ResourceNotFoundException("Download URL not available");
        }

        return (String) response.get("downloadUrl");
    }

    /**
     * Request upload URL for a new file.
     * External users can upload new files but cannot edit existing ones.
     */
    public PortalUploadResponse requestUpload(String accessToken, String teamId, PortalUploadRequest request) {
        TeamContext ctx = getTeamContext(accessToken, teamId);
        verifyAccess(ctx, TeamPermission.CONTENT_UPLOAD);

        String driveId = "team-" + teamId;

        // Call Content Service to prepare upload
        Map<String, Object> uploadRequest = new HashMap<>();
        uploadRequest.put("filename", request.getFilename());
        uploadRequest.put("contentType", request.getContentType());
        uploadRequest.put("size", request.getSize());
        uploadRequest.put("folderId", request.getFolderId());

        Map<String, Object> response = webClientBuilder.build()
                .post()
                .uri(contentServiceUrl + "/api/documents/upload/prepare")
                .header("X-Tenant-ID", ctx.tenantId)
                .header("X-Drive-ID", driveId)
                .header("X-User-ID", ctx.userId)
                .bodyValue(uploadRequest)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null) {
            throw new RuntimeException("Failed to prepare upload");
        }

        String uploadUrl = (String) response.get("presignedUrl");
        if (uploadUrl == null) {
            uploadUrl = (String) response.get("directUploadUrl");
        }

        return PortalUploadResponse.builder()
                .uploadUrl(uploadUrl)
                .documentId((String) response.get("documentId"))
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();
    }

    /**
     * Confirm upload completion.
     */
    public PortalFileDTO confirmUpload(String accessToken, String teamId, String documentId) {
        TeamContext ctx = getTeamContext(accessToken, teamId);
        verifyAccess(ctx, TeamPermission.CONTENT_UPLOAD);

        String driveId = "team-" + teamId;

        // Call Content Service to confirm upload
        Map<String, Object> response = webClientBuilder.build()
                .post()
                .uri(contentServiceUrl + "/api/documents/{id}/upload/complete", documentId)
                .header("X-Tenant-ID", ctx.tenantId)
                .header("X-Drive-ID", driveId)
                .header("X-User-ID", ctx.userId)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null) {
            throw new ResourceNotFoundException("Document not found");
        }

        log.info("External user {} confirmed upload for document {}", ctx.userId, documentId);

        return mapToPortalFileDTO(response);
    }

    /**
     * Create a new version of an existing file.
     * External users can create new versions but cannot edit the file in place.
     */
    public PortalUploadResponse createVersion(String accessToken, String teamId, String fileId,
                                              PortalUploadRequest request) {
        TeamContext ctx = getTeamContext(accessToken, teamId);
        verifyAccess(ctx, TeamPermission.CONTENT_VERSION_CREATE);

        String driveId = "team-" + teamId;

        // Call Content Service to prepare version upload
        Map<String, Object> versionRequest = new HashMap<>();
        versionRequest.put("filename", request.getFilename());
        versionRequest.put("contentType", request.getContentType());
        versionRequest.put("size", request.getSize());

        Map<String, Object> response = webClientBuilder.build()
                .post()
                .uri(contentServiceUrl + "/api/documents/{id}/versions/prepare", fileId)
                .header("X-Tenant-ID", ctx.tenantId)
                .header("X-Drive-ID", driveId)
                .header("X-User-ID", ctx.userId)
                .bodyValue(versionRequest)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null) {
            throw new RuntimeException("Failed to prepare version upload");
        }

        String uploadUrl = (String) response.get("presignedUrl");
        if (uploadUrl == null) {
            uploadUrl = (String) response.get("directUploadUrl");
        }

        log.info("External user {} creating new version for document {}", ctx.userId, fileId);

        return PortalUploadResponse.builder()
                .uploadUrl(uploadUrl)
                .documentId(fileId)
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();
    }

    // ========================================
    // Helper Methods
    // ========================================

    private TeamContext getTeamContext(String accessToken, String teamId) {
        Claims claims = portalAuthService.parseAccessToken(accessToken);
        String tenantId = claims.get("tenantId", String.class);
        String userId = claims.getSubject();

        Team team = teamRepository.findByIdAndTenantId(teamId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found"));

        TeamMember member = team.getMembers().stream()
                .filter(m -> m.getUserId().equals(userId) && m.getStatus() == MemberStatus.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("You are not a member of this team"));

        return new TeamContext(tenantId, userId, team, member);
    }

    private void verifyAccess(TeamContext ctx, TeamPermission permission) {
        if (!ctx.member.getPermissions().contains(permission.name())) {
            throw new AccessDeniedException("You don't have permission: " + permission.name());
        }
    }

    @SuppressWarnings("unchecked")
    private PortalFileDTO mapToPortalFileDTO(Map<String, Object> doc) {
        return PortalFileDTO.builder()
                .id((String) doc.get("id"))
                .name((String) doc.get("name"))
                .type((String) doc.get("type"))
                .size(((Number) doc.getOrDefault("size", 0)).longValue())
                .mimeType((String) doc.get("mimeType"))
                .folderId((String) doc.get("folderId"))
                .folderPath((String) doc.get("folderPath"))
                .createdAt(parseInstant(doc.get("createdAt")))
                .modifiedAt(parseInstant(doc.get("modifiedAt")))
                .createdByName((String) doc.get("createdByName"))
                .build();
    }

    @SuppressWarnings("unchecked")
    private PortalFolderDTO mapToPortalFolderDTO(Map<String, Object> folder) {
        return PortalFolderDTO.builder()
                .id((String) folder.get("id"))
                .name((String) folder.get("name"))
                .parentId((String) folder.get("parentId"))
                .path((String) folder.get("path"))
                .fileCount(((Number) folder.getOrDefault("fileCount", 0)).intValue())
                .folderCount(((Number) folder.getOrDefault("folderCount", 0)).intValue())
                .build();
    }

    private Instant parseInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return Instant.parse((String) value);
        }
        return null;
    }

    private record TeamContext(String tenantId, String userId, Team team, TeamMember member) {}
}
