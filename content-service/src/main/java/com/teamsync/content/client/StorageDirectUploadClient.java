package com.teamsync.content.client;

import com.teamsync.common.context.TenantContext;
import com.teamsync.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Client for direct file uploads to Storage Service.
 * Uses RestClient with multipart support for streaming uploads.
 */
@Component
@Slf4j
public class StorageDirectUploadClient {

    private final RestClient restClient;

    public StorageDirectUploadClient(
            RestClient.Builder restClientBuilder,
            @Value("${teamsync.services.storage-url:http://localhost:9083}") String storageServiceUrl) {

        this.restClient = restClientBuilder
                .baseUrl(storageServiceUrl)
                .build();
    }

    /**
     * Response from direct upload endpoint.
     */
    public record DirectUploadResponse(
            String bucket,
            String storageKey,
            Long fileSize,
            String contentType
    ) {}

    /**
     * Upload file directly to storage service.
     * Streams the file through Content Service → Storage Service → MinIO.
     */
    public ApiResponse<DirectUploadResponse> uploadFile(MultipartFile file, String contentType) {
        log.info("Direct upload to storage: {} size: {}", file.getOriginalFilename(), file.getSize());

        try {
            // Build multipart body
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("file", new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            }).contentType(MediaType.parseMediaType(contentType));

            builder.part("contentType", contentType);

            // Make the multipart POST request with tenant headers
            String tenantId = TenantContext.getTenantId();
            String userId = TenantContext.getUserId();
            String driveId = TenantContext.getDriveId();

            log.info("Forwarding request to storage-service with context - tenantId: {}, userId: {}, driveId: {}",
                    tenantId, userId, driveId);

            if (userId == null || userId.isBlank()) {
                log.error("CRITICAL: userId is null/blank when forwarding to storage-service. Authentication will fail.");
            }

            return restClient.post()
                    .uri("/api/storage/upload/direct")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .headers(headers -> {
                        if (tenantId != null) headers.set("X-Tenant-ID", tenantId);
                        if (userId != null) headers.set("X-User-ID", userId);
                        if (driveId != null) headers.set("X-Drive-ID", driveId);
                    })
                    .body(builder.build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<DirectUploadResponse>>() {});

        } catch (IOException e) {
            log.error("Failed to read file bytes for upload: {}", e.getMessage());
            throw new RuntimeException("Failed to read file for upload", e);
        }
    }
}
