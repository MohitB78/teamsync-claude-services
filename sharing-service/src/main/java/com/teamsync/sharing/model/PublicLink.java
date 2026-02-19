package com.teamsync.sharing.model;

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
import java.util.Set;

/**
 * Represents a public link for sharing resources externally.
 *
 * SECURITY FIX (Round 15 #M32): Added @Version for optimistic locking to prevent
 * race conditions in concurrent download count updates.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "public_links")
@CompoundIndexes({
        @CompoundIndex(name = "tenant_resource_idx", def = "{'tenantId': 1, 'resourceId': 1}"),
        @CompoundIndex(name = "tenant_created_by_idx", def = "{'tenantId': 1, 'createdBy': 1}")
})
public class PublicLink {

    @Id
    private String id;

    /**
     * Version field for optimistic locking.
     * Prevents concurrent download count updates from losing increments.
     */
    @Version
    private Long version;

    @Indexed(unique = true)
    private String token;  // Short unique token for URL

    private String tenantId;
    private String driveId;

    // Resource being shared
    private String resourceId;
    private Share.ResourceType resourceType;

    // Link settings
    private String name;  // Custom name for the link
    private Set<Share.SharePermission> permissions;

    // Security
    private Boolean requirePassword;
    private String passwordHash;

    // Limits
    private Integer maxDownloads;
    private Integer downloadCount;
    private Instant expiresAt;

    // Access tracking
    private Integer accessCount;
    private Instant lastAccessedAt;
    private String lastAccessedIp;

    // Status
    private LinkStatus status;

    // Audit
    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    public enum LinkStatus {
        ACTIVE,
        EXPIRED,
        DISABLED,
        EXHAUSTED  // Max downloads reached
    }
}
