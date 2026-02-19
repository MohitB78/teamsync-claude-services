package com.teamsync.sharing.dto;

import com.teamsync.sharing.model.Share.ResourceType;
import com.teamsync.sharing.model.Share.SharePermission;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;

/**
 * Request DTO for creating a public link.
 *
 * SECURITY FIX (Round 11): Added @Size and @Max constraints to prevent DoS attacks
 * via excessively long strings or unreasonable maxDownloads values.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePublicLinkRequest {

    @NotBlank(message = "Resource ID is required")
    @Size(max = 64, message = "Resource ID must not exceed 64 characters")
    private String resourceId;

    @NotNull(message = "Resource type is required")
    private ResourceType resourceType;

    @Size(max = 255, message = "Link name must not exceed 255 characters")
    private String name;  // Custom name for the link

    @Size(max = 10, message = "Cannot grant more than 10 permissions")
    private Set<SharePermission> permissions;  // Defaults to VIEW, DOWNLOAD

    @Size(max = 128, message = "Password must not exceed 128 characters")
    private String password;

    @Min(value = 1, message = "Max downloads must be at least 1")
    @Max(value = 100000, message = "Max downloads must not exceed 100000")
    private Integer maxDownloads;

    private Instant expiresAt;
}
