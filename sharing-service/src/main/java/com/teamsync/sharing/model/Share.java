package com.teamsync.sharing.model;

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
import java.util.Set;

/**
 * Represents a share relationship between a resource and a user/team/department.
 *
 * SECURITY FIX (Round 15 #M31): Added @Version for optimistic locking to prevent
 * race conditions in concurrent share updates.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "shares")
@CompoundIndexes({
        @CompoundIndex(name = "tenant_resource_idx", def = "{'tenantId': 1, 'resourceId': 1}"),
        @CompoundIndex(name = "tenant_shared_with_idx", def = "{'tenantId': 1, 'sharedWithId': 1, 'sharedWithType': 1}"),
        // SECURITY FIX: Added tenantId to unique index to prevent cross-tenant duplicate key errors
        // and ensure duplicate detection works correctly within each tenant
        @CompoundIndex(name = "tenant_resource_sharee_idx", def = "{'tenantId': 1, 'resourceId': 1, 'sharedWithId': 1}", unique = true),
        @CompoundIndex(name = "owner_idx", def = "{'tenantId': 1, 'ownerId': 1}")
})
public class Share {

    @Id
    private String id;

    /**
     * Version field for optimistic locking.
     * Prevents concurrent updates from overwriting each other.
     */
    @Version
    private Long version;

    private String tenantId;
    private String driveId;

    // Resource being shared
    private String resourceId;
    private ResourceType resourceType;

    // Who shared it
    private String ownerId;
    private String sharedById;

    // Who it's shared with
    private String sharedWithId;
    private ShareeType sharedWithType;

    // Permissions
    private Set<SharePermission> permissions;

    // Settings
    private Boolean notifyOnAccess;
    private Boolean allowReshare;
    private Boolean requirePassword;
    private String passwordHash;

    // Expiration
    private Instant expiresAt;

    // Access tracking
    private Integer accessCount;
    private Instant lastAccessedAt;
    private String lastAccessedBy;

    // Audit
    private Instant createdAt;
    private Instant updatedAt;
    private String message;  // Optional message when sharing

    public enum ResourceType {
        DOCUMENT,
        FOLDER,
        DRIVE
    }

    public enum ShareeType {
        USER,
        TEAM,
        DEPARTMENT,
        EVERYONE  // Within tenant
    }

    public enum SharePermission {
        VIEW,
        DOWNLOAD,
        COMMENT,
        EDIT,
        DELETE,
        SHARE,
        MANAGE
    }
}
