package com.teamsync.sharing.dto;

import com.teamsync.sharing.model.Share.ResourceType;
import com.teamsync.sharing.model.Share.ShareeType;
import com.teamsync.sharing.model.Share.SharePermission;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;

/**
 * Request DTO for creating a share.
 *
 * SECURITY FIX (Round 11): Added @Size constraints to prevent DoS attacks
 * via excessively long strings in message and password fields.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateShareRequest {

    @NotBlank(message = "Resource ID is required")
    @Size(max = 64, message = "Resource ID must not exceed 64 characters")
    private String resourceId;

    @NotNull(message = "Resource type is required")
    private ResourceType resourceType;

    @NotBlank(message = "Shared with ID is required")
    @Size(max = 64, message = "Shared with ID must not exceed 64 characters")
    private String sharedWithId;

    @NotNull(message = "Sharee type is required")
    private ShareeType sharedWithType;

    @NotEmpty(message = "At least one permission is required")
    @Size(max = 10, message = "Cannot grant more than 10 permissions")
    private Set<SharePermission> permissions;

    // Optional settings
    private Boolean notifyOnAccess;
    private Boolean allowReshare;

    @Size(max = 128, message = "Password must not exceed 128 characters")
    private String password;

    private Instant expiresAt;

    @Size(max = 1000, message = "Message must not exceed 1000 characters")
    private String message;
}
