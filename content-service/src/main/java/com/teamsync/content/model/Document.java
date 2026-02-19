package com.teamsync.content.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * MongoDB document entity representing a file stored in the TeamSync content service.
 *
 * <h2>Overview</h2>
 * <p>A Document represents a file with its metadata, storage location, and lifecycle state.
 * Documents are organized within drives (personal or department) and optionally within folders.
 *
 * <h2>Storage Architecture</h2>
 * <ul>
 *   <li>{@code storageBucket} - S3/GCS/MinIO bucket name</li>
 *   <li>{@code storageKey} - Unique object key within the bucket (globally unique)</li>
 *   <li>{@code storageTier} - Current tier: HOT, WARM, COLD, or ARCHIVE</li>
 * </ul>
 *
 * <h2>Document Lifecycle</h2>
 * <pre>
 * PENDING_UPLOAD → ACTIVE → TRASHED → DELETED
 *                    ↓
 *                ARCHIVED
 * </pre>
 *
 * <h2>Version Control</h2>
 * <p>Two versioning systems are used:
 * <ul>
 *   <li>{@code entityVersion} - Optimistic locking for concurrent edit detection (MongoDB @Version)</li>
 *   <li>{@code currentVersion} - Document revision number for content versioning</li>
 * </ul>
 *
 * <h2>Compound Indexes</h2>
 * <p>Optimized for common query patterns:
 * <ul>
 *   <li>{@code tenant_drive_idx} - Base isolation filter</li>
 *   <li>{@code tenant_drive_folder_idx} - Listing documents in a folder</li>
 *   <li>{@code tenant_drive_type_idx} - Filtering by business document type</li>
 *   <li>{@code tenant_drive_name_idx} - Name search and duplicate detection</li>
 *   <li>{@code tenant_starred_idx} - Quick access to starred documents</li>
 *   <li>{@code storage_key_idx} - Unique storage reference lookup</li>
 * </ul>
 *
 * <h2>Async Processing Fields</h2>
 * <p>Several fields track background processing status:
 * <ul>
 *   <li>{@code virusScanStatus} - Virus scan: PENDING, CLEAN, INFECTED, FAILED</li>
 *   <li>{@code extractedText} - Full-text content for search indexing</li>
 *   <li>{@code aiMetadata} - AI-extracted metadata (entities, classification)</li>
 *   <li>{@code thumbnailKey} - Preview image storage location</li>
 * </ul>
 *
 * @author TeamSync Platform Team
 * @version 1.0.0
 * @since Spring Boot 4.0.0
 * @see Folder
 * @see DocumentVersion
 */
@org.springframework.data.mongodb.core.mapping.Document(collection = "documents")
@CompoundIndexes({
        @CompoundIndex(name = "tenant_drive_idx", def = "{'tenantId': 1, 'driveId': 1}"),
        @CompoundIndex(name = "tenant_drive_folder_idx", def = "{'tenantId': 1, 'driveId': 1, 'folderId': 1}"),
        // Optimized for content listing queries that filter by status
        @CompoundIndex(name = "tenant_drive_folder_status_idx", def = "{'tenantId': 1, 'driveId': 1, 'folderId': 1, 'status': 1}"),
        // Optimized for sorted content listing with cursor pagination
        @CompoundIndex(name = "tenant_drive_folder_status_name_idx", def = "{'tenantId': 1, 'driveId': 1, 'folderId': 1, 'status': 1, 'name': 1}"),
        @CompoundIndex(name = "tenant_drive_type_idx", def = "{'tenantId': 1, 'driveId': 1, 'documentTypeId': 1}"),
        @CompoundIndex(name = "tenant_drive_name_idx", def = "{'tenantId': 1, 'driveId': 1, 'name': 1}"),
        @CompoundIndex(name = "tenant_starred_idx", def = "{'tenantId': 1, 'isStarred': 1}"),
        @CompoundIndex(name = "storage_key_idx", def = "{'storageKey': 1}", unique = true),
        /**
         * SECURITY FIX: Unique constraint to prevent duplicate document names in the same folder.
         * Prevents race condition where concurrent uploads could create duplicate names.
         * Note: This applies to all statuses. For partial filter (excluding TRASHED),
         * see DocumentIndexConfig which creates the index programmatically.
         */
        @CompoundIndex(name = "unique_name_in_folder_idx",
                       def = "{'tenantId': 1, 'driveId': 1, 'folderId': 1, 'name': 1, 'status': 1}",
                       unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Document {

    @Id
    private String id;

    /**
     * Optimistic locking version - auto-incremented by MongoDB on each update.
     * Different from currentVersion which tracks document revision history.
     * Used to detect concurrent edits and prevent lost updates.
     */
    @Version
    private Long entityVersion;

    @Field("tenantId")
    private String tenantId;

    @Field("driveId")
    private String driveId;

    @Field("folderId")
    private String folderId;  // null for root level

    @Field("name")
    private String name;

    @Field("description")
    private String description;

    @Field("contentType")
    private String contentType;

    @Field("fileSize")
    private Long fileSize;

    @Field("storageKey")
    private String storageKey;  // S3/GCS/MinIO key

    @Field("storageBucket")
    private String storageBucket;

    @Field("storageTier")
    private String storageTier;  // HOT, WARM, COLD, ARCHIVE

    @Field("extension")
    private String extension;

    @Field("checksum")
    private String checksum;  // MD5 or SHA-256

    // Business Document Type
    @Field("documentTypeId")
    private String documentTypeId;

    @Field("metadata")
    private Map<String, Object> metadata;  // Custom metadata from document type

    // Version tracking
    @Field("currentVersion")
    @Builder.Default
    private Integer currentVersion = 1;

    @Field("currentVersionId")
    private String currentVersionId;

    @Field("versionCount")
    @Builder.Default
    private Integer versionCount = 1;

    // Organization
    @Field("tags")
    private List<String> tags;

    @Field("isStarred")
    @Builder.Default
    private Boolean isStarred = false;

    @Field("isPinned")
    @Builder.Default
    private Boolean isPinned = false;

    // Ownership
    @Field("ownerId")
    private String ownerId;

    @Field("createdBy")
    private String createdBy;

    @Field("lastModifiedBy")
    private String lastModifiedBy;

    // Status
    @Field("status")
    @Builder.Default
    private DocumentStatus status = DocumentStatus.ACTIVE;

    // Upload session tracking (for PENDING_UPLOAD status)
    @Field("uploadSessionId")
    private String uploadSessionId;

    @Field("isLocked")
    @Builder.Default
    private Boolean isLocked = false;

    @Field("lockedBy")
    private String lockedBy;

    @Field("lockedAt")
    private Instant lockedAt;

    // Text extraction for search
    @Field("extractedText")
    private String extractedText;

    @Field("textExtractedAt")
    private Instant textExtractedAt;

    // Thumbnail
    @Field("thumbnailKey")
    private String thumbnailKey;

    @Field("hasThumbnail")
    @Builder.Default
    private Boolean hasThumbnail = false;

    // AI metadata extraction
    @Field("aiMetadata")
    private Map<String, Object> aiMetadata;

    @Field("aiProcessedAt")
    private Instant aiProcessedAt;

    // Virus scan
    @Field("virusScanStatus")
    private String virusScanStatus;  // PENDING, CLEAN, INFECTED, FAILED

    @Field("virusScanAt")
    private Instant virusScanAt;

    // Timestamps
    @CreatedDate
    @Field("createdAt")
    private Instant createdAt;

    @LastModifiedDate
    @Field("updatedAt")
    private Instant updatedAt;

    @Field("accessedAt")
    private Instant accessedAt;

    /**
     * Document lifecycle status.
     *
     * <p>Status transitions:
     * <pre>
     * PENDING_UPLOAD → ACTIVE (on upload complete)
     * ACTIVE → TRASHED (soft delete)
     * TRASHED → ACTIVE (restore)
     * TRASHED → DELETED (permanent delete)
     * ACTIVE → ARCHIVED (lifecycle policy)
     * </pre>
     */
    public enum DocumentStatus {
        /** Document metadata created but file upload not yet complete. Cleaned up after 24h. */
        PENDING_UPLOAD,
        /** Normal state - document is visible and accessible. */
        ACTIVE,
        /** Soft-deleted, in trash with 30-day retention before permanent deletion. */
        TRASHED,
        /** Archived for long-term storage, moved to cold/archive storage tier. */
        ARCHIVED,
        /** Permanently deleted, file removed from storage. */
        DELETED
    }
}
