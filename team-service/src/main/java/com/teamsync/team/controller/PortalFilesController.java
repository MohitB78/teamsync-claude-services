package com.teamsync.team.controller;

import com.teamsync.common.dto.ApiResponse;
import com.teamsync.team.dto.portal.*;
import com.teamsync.team.service.PortalFilesService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Portal controller for file operations.
 * External users can VIEW, UPLOAD, and CREATE_VERSION - but NOT edit or delete files.
 */
@RestController
@RequestMapping("/portal/teams/{teamId}")
@RequiredArgsConstructor
@Validated
@Slf4j
public class PortalFilesController {

    private static final String VALID_ID_PATTERN = "^[a-zA-Z0-9-]{1,64}$";

    private final PortalFilesService portalFilesService;

    // ============== FILE LISTING ==============

    /**
     * Get files in a team folder.
     */
    @GetMapping("/files")
    public ResponseEntity<ApiResponse<List<PortalFileDTO>>> getFiles(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid team ID format")
            String teamId,
            @RequestParam(required = false) String folderId) {

        log.debug("GET /portal/teams/{}/files?folderId={}", teamId, folderId);

        String token = extractToken(authHeader);
        List<PortalFileDTO> files = portalFilesService.getFiles(token, teamId, folderId);

        return ResponseEntity.ok(ApiResponse.<List<PortalFileDTO>>builder()
                .success(true)
                .data(files)
                .build());
    }

    /**
     * Get folders in a team.
     */
    @GetMapping("/folders")
    public ResponseEntity<ApiResponse<List<PortalFolderDTO>>> getFolders(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid team ID format")
            String teamId,
            @RequestParam(required = false) String parentId) {

        log.debug("GET /portal/teams/{}/folders?parentId={}", teamId, parentId);

        String token = extractToken(authHeader);
        List<PortalFolderDTO> folders = portalFilesService.getFolders(token, teamId, parentId);

        return ResponseEntity.ok(ApiResponse.<List<PortalFolderDTO>>builder()
                .success(true)
                .data(folders)
                .build());
    }

    // ============== FILE DOWNLOAD ==============

    /**
     * Get download URL for a file.
     */
    @GetMapping("/files/{fileId}/download")
    public ResponseEntity<ApiResponse<Map<String, String>>> getDownloadUrl(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid team ID format")
            String teamId,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid file ID format")
            String fileId) {

        log.debug("GET /portal/teams/{}/files/{}/download", teamId, fileId);

        String token = extractToken(authHeader);
        String downloadUrl = portalFilesService.getDownloadUrl(token, teamId, fileId);

        return ResponseEntity.ok(ApiResponse.<Map<String, String>>builder()
                .success(true)
                .data(Map.of("downloadUrl", downloadUrl))
                .build());
    }

    // ============== FILE UPLOAD ==============

    /**
     * Request upload URL for a new file.
     * External users can upload new files to the team drive.
     */
    @PostMapping("/files/upload")
    public ResponseEntity<ApiResponse<PortalUploadResponse>> requestUpload(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid team ID format")
            String teamId,
            @Valid @RequestBody PortalUploadRequest request) {

        log.info("POST /portal/teams/{}/files/upload - filename: {}", teamId, request.getFilename());

        String token = extractToken(authHeader);
        PortalUploadResponse response = portalFilesService.requestUpload(token, teamId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<PortalUploadResponse>builder()
                        .success(true)
                        .data(response)
                        .message("Upload URL generated")
                        .build());
    }

    /**
     * Confirm upload completion.
     */
    @PostMapping("/files/{documentId}/confirm")
    public ResponseEntity<ApiResponse<PortalFileDTO>> confirmUpload(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid team ID format")
            String teamId,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid document ID format")
            String documentId) {

        log.info("POST /portal/teams/{}/files/{}/confirm", teamId, documentId);

        String token = extractToken(authHeader);
        PortalFileDTO file = portalFilesService.confirmUpload(token, teamId, documentId);

        return ResponseEntity.ok(ApiResponse.<PortalFileDTO>builder()
                .success(true)
                .data(file)
                .message("Upload confirmed")
                .build());
    }

    // ============== VERSION CREATION ==============

    /**
     * Create a new version of an existing file.
     * External users can create new versions but cannot edit the file in place.
     */
    @PostMapping("/files/{fileId}/versions")
    public ResponseEntity<ApiResponse<PortalUploadResponse>> createVersion(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid team ID format")
            String teamId,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid file ID format")
            String fileId,
            @Valid @RequestBody PortalUploadRequest request) {

        log.info("POST /portal/teams/{}/files/{}/versions - filename: {}", teamId, fileId, request.getFilename());

        String token = extractToken(authHeader);
        PortalUploadResponse response = portalFilesService.createVersion(token, teamId, fileId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<PortalUploadResponse>builder()
                        .success(true)
                        .data(response)
                        .message("Version upload URL generated")
                        .build());
    }

    // ============== HELPER METHODS ==============

    private String extractToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        throw new IllegalArgumentException("Invalid Authorization header");
    }
}
