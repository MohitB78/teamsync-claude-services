package com.teamsync.sharing.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.teamsync.sharing.model.PublicLink.LinkStatus;
import com.teamsync.sharing.model.Share.ResourceType;
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
public class PublicLinkDTO {

    private String id;
    private String token;
    private String url;  // Full URL

    private String resourceId;
    private ResourceType resourceType;
    private String resourceName;

    private String name;
    private Set<SharePermission> permissions;

    private Boolean requirePassword;
    private Integer maxDownloads;
    private Integer downloadCount;
    private Integer downloadsRemaining;
    private Instant expiresAt;

    private Integer accessCount;
    private Instant lastAccessedAt;

    private LinkStatus status;

    private String createdBy;
    private String createdByName;
    private Instant createdAt;
    private Instant updatedAt;
}
