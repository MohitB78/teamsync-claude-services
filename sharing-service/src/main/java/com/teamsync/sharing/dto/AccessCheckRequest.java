package com.teamsync.sharing.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.teamsync.sharing.model.Share.SharePermission;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request to check if the current authenticated user has access to a resource.
 *
 * SECURITY FIX (Round 7): User identity fields (userId, teamIds, departmentId) are now
 * deprecated and ignored. The service extracts user identity from the authenticated
 * session (TenantContext) to prevent BOLA attacks where attackers could check access
 * on behalf of other users.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AccessCheckRequest {

    @NotBlank(message = "Resource ID is required")
    private String resourceId;

    /**
     * @deprecated SECURITY: User ID is now extracted from authenticated session.
     * This field is ignored by the service. Will be removed in next major version.
     */
    @Deprecated(since = "1.1.0", forRemoval = true)
    private String userId;

    /**
     * @deprecated SECURITY: Team IDs are now extracted from authenticated session.
     * This field is ignored by the service. Will be removed in next major version.
     */
    @Deprecated(since = "1.1.0", forRemoval = true)
    private List<String> teamIds;

    /**
     * @deprecated SECURITY: Department ID is now extracted from authenticated session.
     * This field is ignored by the service. Will be removed in next major version.
     */
    @Deprecated(since = "1.1.0", forRemoval = true)
    private String departmentId;

    /**
     * The permission level to check. If null, checks for any access.
     */
    private SharePermission requiredPermission;
}
