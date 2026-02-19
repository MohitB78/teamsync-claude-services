package com.teamsync.presence.client;

import com.teamsync.common.dto.ApiResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * SECURITY: HTTP Service Client for Permission Manager Service.
 * Used to verify document access before allowing presence operations.
 */
@HttpExchange("/api/permissions")
public interface PermissionClient {

    /**
     * Check if a user has access to a drive.
     * Returns permission details if accessible.
     */
    @GetExchange("/check/{userId}/{driveId}")
    ApiResponse<PermissionCheckResponse> checkPermissions(
            @PathVariable String userId,
            @PathVariable String driveId,
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-User-ID") String requesterId);

    /**
     * Permission check response DTO.
     */
    record PermissionCheckResponse(
            boolean hasAccess,
            java.util.List<String> permissions,
            String roleName,
            boolean isOwner
    ) {}
}
