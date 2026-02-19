package com.teamsync.sharing.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.teamsync.sharing.model.Share.ResourceType;
import com.teamsync.sharing.model.Share.ShareeType;
import com.teamsync.sharing.model.Share.SharePermission;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ShareDTO {

    private String id;
    private String tenantId;
    private String driveId;

    // Resource
    private String resourceId;
    private ResourceType resourceType;
    private String resourceName;

    // Owner
    private String ownerId;
    private String ownerName;

    // Shared by
    private String sharedById;
    private String sharedByName;

    // Shared with
    private String sharedWithId;
    private ShareeType sharedWithType;
    private String sharedWithName;
    private String sharedWithEmail;

    // Permissions
    private Set<SharePermission> permissions;

    // Settings
    private Boolean notifyOnAccess;
    private Boolean allowReshare;
    private Boolean requirePassword;
    private Instant expiresAt;

    // Access info
    private Integer accessCount;
    private Instant lastAccessedAt;

    // Audit
    private Instant createdAt;
    private Instant updatedAt;
    private String message;
}
