package com.teamsync.content.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * MongoDB document entity representing a folder in the TeamSync content service.
 *
 * <h2>Overview</h2>
 * <p>Folders provide hierarchical organization for documents within a drive.
 * They support nested structures, path-based navigation, and quick ancestor queries
 * through a materialized path pattern.
 *
 * <h2>Hierarchy Model</h2>
 * <p>Folders use a combination of parent reference and materialized path:
 * <ul>
 *   <li>{@code parentId} - Direct parent reference (null for root folders)</li>
 *   <li>{@code ancestorIds} - Array of all ancestor IDs from root to parent</li>
 *   <li>{@code path} - Full path string (e.g., "/Documents/Projects/2024")</li>
 *   <li>{@code depth} - Nesting level (0 for root folders)</li>
 * </ul>
 *
 * <h3>Example Hierarchy</h3>
 * <pre>
 * / (root)
 * ├── Documents (depth=0, path="/Documents", ancestorIds=[])
 * │   ├── Projects (depth=1, path="/Documents/Projects", ancestorIds=["doc-id"])
 * │   │   └── 2024 (depth=2, path="/Documents/Projects/2024", ancestorIds=["doc-id","proj-id"])
 * </pre>
 *
 * <h2>Materialized Path Benefits</h2>
 * <ul>
 *   <li><b>Subtree queries:</b> Find all descendants with {@code ancestorIds} contains</li>
 *   <li><b>Breadcrumb:</b> Build path from root using {@code ancestorIds}</li>
 *   <li><b>Move validation:</b> Prevent cycles by checking {@code ancestorIds}</li>
 *   <li><b>Cascade operations:</b> Efficient bulk updates to subtrees</li>
 * </ul>
 *
 * <h2>Compound Indexes</h2>
 * <p>Optimized for common query patterns:
 * <ul>
 *   <li>{@code tenant_drive_idx} - Base isolation filter</li>
 *   <li>{@code tenant_drive_parent_idx} - Listing subfolders</li>
 *   <li>{@code tenant_drive_path_idx} - Unique path constraint</li>
 *   <li>{@code tenant_drive_parent_name_idx} - Sibling uniqueness</li>
 *   <li>{@code tenant_starred_idx} - Quick access to starred folders</li>
 * </ul>
 *
 * <h2>Denormalized Counts</h2>
 * <p>For performance, folders cache counts of their immediate contents:
 * <ul>
 *   <li>{@code folderCount} - Number of direct subfolders</li>
 *   <li>{@code documentCount} - Number of direct documents</li>
 *   <li>{@code totalSize} - Combined size of all documents</li>
 * </ul>
 * <p>These are updated on content operations and may become stale; use
 * for display hints but not for authoritative counts.
 *
 * @author TeamSync Platform Team
 * @version 1.0.0
 * @since Spring Boot 4.0.0
 * @see com.teamsync.content.model.Document
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "folders")
@CompoundIndexes({
        @CompoundIndex(name = "tenant_drive_idx", def = "{'tenantId': 1, 'driveId': 1}"),
        @CompoundIndex(name = "tenant_drive_parent_idx", def = "{'tenantId': 1, 'driveId': 1, 'parentId': 1}"),
        // Optimized for content listing queries that filter by status
        @CompoundIndex(name = "tenant_drive_parent_status_idx", def = "{'tenantId': 1, 'driveId': 1, 'parentId': 1, 'status': 1}"),
        // Optimized for sorted content listing with cursor pagination
        @CompoundIndex(name = "tenant_drive_parent_status_name_idx", def = "{'tenantId': 1, 'driveId': 1, 'parentId': 1, 'status': 1, 'name': 1}"),
        @CompoundIndex(name = "tenant_drive_path_idx", def = "{'tenantId': 1, 'driveId': 1, 'path': 1}", unique = true),
        @CompoundIndex(name = "tenant_drive_parent_name_idx", def = "{'tenantId': 1, 'driveId': 1, 'parentId': 1, 'name': 1}"),
        @CompoundIndex(name = "tenant_starred_idx", def = "{'tenantId': 1, 'driveId': 1, 'isStarred': 1}")
})
public class Folder {

    @Id
    private String id;

    /**
     * Version field for optimistic locking.
     * SECURITY FIX (Round 15): Prevents race conditions in concurrent folder updates.
     */
    @Version
    private Long version;

    private String tenantId;
    private String driveId;
    private String parentId;  // null for root folders

    private String name;
    private String description;

    // Full path from root (e.g., "/documents/projects/2024")
    private String path;

    // Depth level (0 for root)
    private Integer depth;

    // Materialized path for efficient ancestor queries
    // e.g., ["root-id", "parent-id", "grandparent-id"]
    private List<String> ancestorIds;

    // Folder color/icon customization
    private String color;
    private String icon;

    // Metadata
    private Map<String, Object> metadata;
    private List<String> tags;

    // Counts (denormalized for performance)
    @Builder.Default
    private Integer folderCount = 0;
    @Builder.Default
    private Integer documentCount = 0;
    @Builder.Default
    private Long totalSize = 0L;  // Total size of all documents in bytes

    // User preferences
    @Builder.Default
    private Boolean isStarred = false;
    @Builder.Default
    private Boolean isPinned = false;

    // Ownership and audit
    private String ownerId;
    private String createdBy;
    private String lastModifiedBy;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant accessedAt;

    /** Folder lifecycle status. */
    @Builder.Default
    private FolderStatus status = FolderStatus.ACTIVE;

    /**
     * Folder lifecycle status.
     *
     * <p>Status transitions:
     * <pre>
     * ACTIVE → TRASHED (soft delete)
     * TRASHED → ACTIVE (restore)
     * ACTIVE → ARCHIVED (policy-based archival)
     * </pre>
     *
     * <p>Note: Unlike documents, folders are never permanently deleted while
     * containing documents. The folder tree must be empty before final deletion.
     */
    public enum FolderStatus {
        /** Normal state - folder is visible and accessible. */
        ACTIVE,
        /** Soft-deleted, in trash. Contents are also marked as TRASHED. */
        TRASHED,
        /** Archived for long-term storage. */
        ARCHIVED
    }
}
