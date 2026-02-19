package com.teamsync.storage.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Tracks an upload session for chunked/multipart uploads.
 *
 * SECURITY FIX (Round 15 #M34): Added @Version for optimistic locking to prevent
 * race conditions when updating chunk status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "upload_sessions")
@CompoundIndexes({
        @CompoundIndex(name = "tenant_drive_idx", def = "{'tenantId': 1, 'driveId': 1}"),
        @CompoundIndex(name = "tenant_user_idx", def = "{'tenantId': 1, 'userId': 1}")
})
public class UploadSession {

    @Id
    private String id;

    /**
     * Version field for optimistic locking.
     * Prevents concurrent chunk updates from corrupting session state.
     */
    @Version
    private Long version;

    private String tenantId;
    private String driveId;
    private String userId;

    // Upload details
    private String filename;
    private String contentType;
    private Long totalSize;
    private Long uploadedSize;

    // Storage location
    private String bucket;
    private String storageKey;

    // Multipart upload
    private String uploadId;  // Cloud provider upload ID
    private Integer totalParts;
    private List<UploadPart> completedParts;

    // Chunked upload tracking
    private Integer chunkSize;
    private Map<Integer, String> chunkETags;

    // Status
    private UploadStatus status;

    @Indexed(expireAfter = "24h")
    private Instant expiresAt;
    private Instant createdAt;
    private Instant updatedAt;

    public enum UploadStatus {
        INITIATED,
        IN_PROGRESS,
        COMPLETING,
        COMPLETED,
        FAILED,
        CANCELLED,
        EXPIRED
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UploadPart {
        private Integer partNumber;
        private String etag;
        private Long size;
        private Instant uploadedAt;
    }
}
