package com.teamsync.common.permission;

import com.teamsync.common.dto.ApiResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * HTTP Service Client for Permission Manager Service.
 * Uses Spring Boot's declarative HTTP interface (replaces OpenFeign).
 */
@HttpExchange("/api/permissions")
public interface PermissionClient {

    /**
     * Full permission check with all options.
     * O(1) with Redis caching on the permission-manager side.
     */
    @PostExchange("/check")
    ApiResponse<PermissionCheckResponse> checkPermission(@RequestBody PermissionCheckRequest request);

    /**
     * Quick check if user has access to a drive.
     */
    @GetExchange("/has-access/{userId}/{driveId}")
    ApiResponse<Boolean> hasAccess(
            @PathVariable("userId") String userId,
            @PathVariable("driveId") String driveId);

    /**
     * Warm the cache for a user (call on login).
     */
    @PostExchange("/cache/warm/{userId}")
    ApiResponse<Void> warmCache(@PathVariable("userId") String userId);

    /**
     * Invalidate cache for a user.
     */
    @PostExchange("/cache/invalidate/user/{userId}")
    ApiResponse<Void> invalidateUserCache(@PathVariable("userId") String userId);

    /**
     * Invalidate cache for a drive.
     */
    @PostExchange("/cache/invalidate/drive/{driveId}")
    ApiResponse<Void> invalidateDriveCache(@PathVariable("driveId") String driveId);
}
