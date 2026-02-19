package com.teamsync.sharing.dto;

import com.teamsync.sharing.model.Share.ResourceType;
import com.teamsync.sharing.model.Share.ShareeType;
import com.teamsync.sharing.model.Share.SharePermission;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Request DTO for bulk sharing operations.
 *
 * SECURITY FIX (Round 11): Added @Size constraints to prevent DoS attacks via
 * unbounded bulk operations that could exhaust server resources.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkShareRequest {

    /**
     * Maximum number of resources that can be shared in a single bulk operation.
     * This limit prevents DoS attacks through resource exhaustion.
     */
    public static final int MAX_RESOURCE_IDS = 100;

    /**
     * Maximum number of share targets (users/teams) per bulk operation.
     */
    public static final int MAX_SHARE_TARGETS = 50;

    @NotEmpty(message = "Resource IDs are required")
    @Size(max = MAX_RESOURCE_IDS, message = "Cannot share more than 100 resources at once")
    private List<String> resourceIds;

    private ResourceType resourceType;

    @NotEmpty(message = "Shares are required")
    @Size(max = MAX_SHARE_TARGETS, message = "Cannot share with more than 50 targets at once")
    private List<ShareTarget> shares;

    // Optional settings applied to all shares
    private Boolean notifyUsers;

    @Size(max = 1000, message = "Message must not exceed 1000 characters")
    private String message;

    private Instant expiresAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShareTarget {
        private String sharedWithId;
        private ShareeType sharedWithType;
        private Set<SharePermission> permissions;
    }
}
