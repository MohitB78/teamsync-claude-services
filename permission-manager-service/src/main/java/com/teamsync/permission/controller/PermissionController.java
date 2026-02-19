package com.teamsync.permission.controller;

import com.teamsync.common.context.TenantContext;
import com.teamsync.common.dto.ApiResponse;
import com.teamsync.common.dto.CursorPage;
import com.teamsync.common.exception.AccessDeniedException;
import com.teamsync.common.model.Permission;
import com.teamsync.permission.dto.*;
import com.teamsync.permission.service.PermissionCacheService;
import com.teamsync.permission.service.PermissionManagerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * REST API for Permission Manager Service.
 * Provides drive-level RBAC with O(1) permission checks.
 *
 * SECURITY FIX (Round 15 #H7): Added @PreAuthorize("isAuthenticated()") for defense-in-depth.
 * SECURITY FIX (Round 15 #H21): Added @Validated and path variable validation.
 */
@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
@Validated
@Slf4j
@PreAuthorize("isAuthenticated()")
@Tag(name = "Permission Manager", description = "Drive-level RBAC with Redis-cached O(1) permission checks")
public class PermissionController {

    /**
     * SECURITY FIX (Round 15 #H21): Valid ID pattern for path variables.
     * Prevents NoSQL injection and path traversal attacks.
     */
    private static final String VALID_ID_PATTERN = "^[a-zA-Z0-9-]{1,64}$";

    private final PermissionManagerService permissionService;
    private final PermissionCacheService cacheService;

    // ============== PERMISSION CHECKS ==============

    @PostMapping("/check")
    @Operation(summary = "Check user permission on a drive",
            description = "O(1) permission check with Redis caching. SECURITY: Users can only check their own permissions unless they have MANAGE_USERS on the drive.")
    public ResponseEntity<ApiResponse<PermissionCheckResponse>> checkPermission(
            @Valid @RequestBody PermissionCheckRequest request) {

        String requesterId = TenantContext.getUserId();

        // SECURITY FIX (Round 7): Prevent permission enumeration via POST endpoint
        // Users can only check their own permissions, or must have MANAGE_USERS on the drive
        if (!request.getUserId().equals(requesterId)) {
            // Check if requester has MANAGE_USERS permission on the target drive
            PermissionCheckResponse requesterCheck = permissionService.checkPermission(
                    PermissionCheckRequest.builder()
                            .userId(requesterId)
                            .driveId(request.getDriveId())
                            .requiredPermission(Permission.MANAGE_USERS)
                            .build());
            boolean canManageUsers = requesterCheck.isHasAccess() &&
                    requesterCheck.getPermissions() != null &&
                    requesterCheck.getPermissions().contains(Permission.MANAGE_USERS);
            if (!canManageUsers) {
                log.warn("SECURITY: Permission enumeration attempt via POST /check: user {} tried to query permissions for user {} on drive {}",
                        requesterId, request.getUserId(), request.getDriveId());
                throw new AccessDeniedException("Cannot query permissions of other users");
            }
        }

        PermissionCheckResponse response = permissionService.checkPermission(request);

        return ResponseEntity.ok(ApiResponse.<PermissionCheckResponse>builder()
                .success(true)
                .data(response)
                .build());
    }

    @GetMapping("/check/{userId}/{driveId}")
    @Operation(summary = "Quick check if user has access to drive",
            description = "SECURITY: Users can only query their own permissions unless they have MANAGE_USERS on the drive")
    public ResponseEntity<ApiResponse<PermissionCheckResponse>> quickCheck(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid user ID format")
            String userId,
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid drive ID format")
            String driveId,
            @RequestParam(required = false) Permission permission) {

        String requesterId = TenantContext.getUserId();

        // SECURITY FIX: Prevent permission enumeration attacks
        // Users can only check their own permissions, or must have MANAGE_USERS on the drive
        if (!userId.equals(requesterId)) {
            // Check if requester has MANAGE_USERS permission on the target drive
            PermissionCheckResponse requesterCheck = permissionService.checkPermission(
                    PermissionCheckRequest.builder()
                            .userId(requesterId)
                            .driveId(driveId)
                            .requiredPermission(Permission.MANAGE_USERS)
                            .build());
            boolean canManageUsers = requesterCheck.isHasAccess() &&
                    requesterCheck.getPermissions() != null &&
                    requesterCheck.getPermissions().contains(Permission.MANAGE_USERS);
            if (!canManageUsers) {
                log.warn("Permission enumeration attempt: user {} tried to query permissions for user {} on drive {}",
                        requesterId, userId, driveId);
                throw new AccessDeniedException("Cannot query permissions of other users");
            }
        }

        PermissionCheckRequest request = PermissionCheckRequest.builder()
                .userId(userId)
                .driveId(driveId)
                .requiredPermission(permission)
                .build();

        PermissionCheckResponse response = permissionService.checkPermission(request);

        return ResponseEntity.ok(ApiResponse.<PermissionCheckResponse>builder()
                .success(true)
                .data(response)
                .build());
    }

    @GetMapping("/has-access/{userId}/{driveId}")
    @Operation(summary = "Check if user has any access to drive (boolean)",
            description = "SECURITY: Users can only query their own access unless they have MANAGE_USERS on the drive")
    public ResponseEntity<ApiResponse<Boolean>> hasAccess(
            @PathVariable String userId,
            @PathVariable String driveId) {

        String requesterId = TenantContext.getUserId();

        // SECURITY FIX: Prevent access enumeration attacks
        if (!userId.equals(requesterId)) {
            PermissionCheckResponse requesterCheck = permissionService.checkPermission(
                    PermissionCheckRequest.builder()
                            .userId(requesterId)
                            .driveId(driveId)
                            .requiredPermission(Permission.MANAGE_USERS)
                            .build());
            boolean canManageUsers = requesterCheck.isHasAccess() &&
                    requesterCheck.getPermissions() != null &&
                    requesterCheck.getPermissions().contains(Permission.MANAGE_USERS);
            if (!canManageUsers) {
                log.warn("Access enumeration attempt: user {} tried to query access for user {} on drive {}",
                        requesterId, userId, driveId);
                throw new AccessDeniedException("Cannot query access of other users");
            }
        }

        boolean hasAccess = permissionService.hasAccess(userId, driveId);

        return ResponseEntity.ok(ApiResponse.<Boolean>builder()
                .success(true)
                .data(hasAccess)
                .build());
    }

    // ============== DRIVE MANAGEMENT ==============

    @PostMapping("/drives")
    @Operation(summary = "Create a new drive",
            description = "SECURITY: Personal drives can only be created for self. Department/shared drives require tenant admin privileges.")
    public ResponseEntity<ApiResponse<DriveDTO>> createDrive(
            @Valid @RequestBody CreateDriveRequest request) {

        String requesterId = TenantContext.getUserId();

        // SECURITY FIX: Authorization check for drive creation
        switch (request.getType()) {
            case PERSONAL:
                // Users can only create their own personal drive
                if (request.getOwnerId() != null && !request.getOwnerId().equals(requesterId)) {
                    log.warn("Unauthorized personal drive creation: user {} tried to create drive for user {}",
                            requesterId, request.getOwnerId());
                    throw new AccessDeniedException("Cannot create personal drive for another user");
                }
                // Set owner to requester if not specified
                if (request.getOwnerId() == null) {
                    request.setOwnerId(requesterId);
                }
                break;

            case DEPARTMENT:
                // Department drives require admin privileges
                // Check if user has any MANAGE_ROLES permission (admin indicator)
                // In a real implementation, this would check against a tenant admin role
                boolean hasAdminPrivilege = permissionService.isTenantAdmin(requesterId);
                if (!hasAdminPrivilege) {
                    log.warn("Unauthorized drive creation: user {} tried to create {} drive without admin privileges",
                            requesterId, request.getType());
                    throw new AccessDeniedException("Only tenant administrators can create " + request.getType() + " drives");
                }
                break;
        }

        DriveDTO drive = permissionService.createDrive(request);

        return ResponseEntity.ok(ApiResponse.<DriveDTO>builder()
                .success(true)
                .data(drive)
                .build());
    }

    @GetMapping("/drives/{driveId}")
    @Operation(summary = "Get drive details",
            description = "SECURITY: Requires at least READ access to the drive")
    public ResponseEntity<ApiResponse<DriveDTO>> getDrive(
            @PathVariable String driveId) {

        String requesterId = TenantContext.getUserId();

        // SECURITY FIX (Round 7): Verify user has access to the drive before revealing details
        // This prevents drive enumeration attacks where users can discover drive names,
        // owners, departments, quotas, etc. for drives they don't have access to
        boolean hasAccess = permissionService.hasAccess(requesterId, driveId);
        if (!hasAccess) {
            log.warn("SECURITY: Drive enumeration attempt: user {} tried to access drive details for {}",
                    requesterId, driveId);
            throw new AccessDeniedException("Access denied to drive");
        }

        DriveDTO drive = permissionService.getDrive(driveId);

        return ResponseEntity.ok(ApiResponse.<DriveDTO>builder()
                .success(true)
                .data(drive)
                .build());
    }

    @GetMapping("/drives/user/{userId}")
    @Operation(summary = "Get all drives accessible by a user (legacy - use paginated version for large datasets)",
            description = "SECURITY: Users can only query their own drives unless they are tenant admin. Returns drives without pagination, limited to 100 items.")
    @Deprecated
    public ResponseEntity<ApiResponse<List<DriveDTO>>> getUserDrives(
            @PathVariable String userId) {

        String requesterId = TenantContext.getUserId();

        // SECURITY FIX (Round 7): Prevent drive list enumeration for other users
        // This prevents attackers from discovering what drives another user has access to
        if (!userId.equals(requesterId)) {
            boolean isAdmin = permissionService.isTenantAdmin(requesterId);
            if (!isAdmin) {
                log.warn("SECURITY: User drive enumeration attempt: user {} tried to list drives for user {}",
                        requesterId, userId);
                throw new AccessDeniedException("Cannot query drives of other users");
            }
        }

        List<DriveDTO> drives = permissionService.getUserDrives(userId);

        // SECURITY: Hard limit to prevent OOM from unbounded lists
        boolean truncated = false;
        if (drives.size() > 100) {
            drives = drives.subList(0, 100);
            truncated = true;
            log.warn("Deprecated endpoint /drives/user/{} returned truncated results ({} total). Use paginated endpoint instead.",
                    userId, drives.size());
        }

        return ResponseEntity.ok()
                .header("Warning", "299 - \"Deprecated: Use /drives/user/{userId}/paginated\"")
                .header("X-Truncated", String.valueOf(truncated))
                .body(ApiResponse.<List<DriveDTO>>builder()
                        .success(true)
                        .data(drives)
                        .build());
    }

    @GetMapping("/drives/user/{userId}/paginated")
    @Operation(summary = "Get drives accessible by a user with cursor-based pagination",
            description = "SECURITY: Users can only query their own drives unless they are tenant admin. Efficient paginated access to user drives.")
    public ResponseEntity<ApiResponse<CursorPage<DriveDTO>>> getUserDrivesPaginated(
            @PathVariable String userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit) {

        String requesterId = TenantContext.getUserId();

        // SECURITY FIX (Round 7): Prevent drive list enumeration for other users
        if (!userId.equals(requesterId)) {
            boolean isAdmin = permissionService.isTenantAdmin(requesterId);
            if (!isAdmin) {
                log.warn("SECURITY: User drive enumeration attempt (paginated): user {} tried to list drives for user {}",
                        requesterId, userId);
                throw new AccessDeniedException("Cannot query drives of other users");
            }
        }

        // SECURITY FIX (Round 9): Validate limit to prevent negative values and enforce max
        int validatedLimit = Math.max(1, Math.min(limit, 100));
        CursorPage<DriveDTO> drives = permissionService.getUserDrivesPaginated(userId, cursor, validatedLimit);

        return ResponseEntity.ok(ApiResponse.<CursorPage<DriveDTO>>builder()
                .success(true)
                .data(drives)
                .build());
    }

    // ============== ROLE ASSIGNMENT ==============

    @PostMapping("/assign")
    @Operation(summary = "Assign a role to a user for a drive",
            description = "SECURITY: Requires MANAGE_USERS permission on the target drive")
    public ResponseEntity<ApiResponse<DriveAssignmentDTO>> assignRole(
            @Valid @RequestBody AssignRoleRequest request) {

        String requesterId = TenantContext.getUserId();

        // SECURITY: Require MANAGE_USERS permission on the target drive
        permissionService.requirePermission(requesterId, request.getDriveId(), Permission.MANAGE_USERS);

        DriveAssignmentDTO assignment = permissionService.assignRole(request);

        return ResponseEntity.ok(ApiResponse.<DriveAssignmentDTO>builder()
                .success(true)
                .data(assignment)
                .build());
    }

    @DeleteMapping("/revoke/{userId}/{driveId}")
    @Operation(summary = "Revoke user access to a drive",
            description = "SECURITY: Requires MANAGE_USERS permission on the target drive")
    public ResponseEntity<ApiResponse<Void>> revokeAccess(
            @PathVariable String userId,
            @PathVariable String driveId) {

        String requesterId = TenantContext.getUserId();

        // SECURITY FIX (Round 7): Require MANAGE_USERS permission to revoke access
        // Without this check, any authenticated user could revoke access for any user on any drive
        permissionService.requirePermission(requesterId, driveId, Permission.MANAGE_USERS);

        permissionService.revokeAccess(userId, driveId);

        log.info("User {} revoked access for user {} on drive {}", requesterId, userId, driveId);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .build());
    }

    @GetMapping("/drives/{driveId}/users")
    @Operation(summary = "Get all users with access to a drive (legacy - use paginated version for large datasets)",
            description = "SECURITY: Requires MANAGE_USERS permission on the drive. Returns users without pagination, limited to 100 items.")
    @Deprecated
    public ResponseEntity<ApiResponse<List<DriveAssignmentDTO>>> getDriveUsers(
            @PathVariable String driveId) {

        String requesterId = TenantContext.getUserId();

        // SECURITY FIX (Round 7): Only users with MANAGE_USERS can list all users on a drive
        // This prevents enumeration of who has access to sensitive drives
        permissionService.requirePermission(requesterId, driveId, Permission.MANAGE_USERS);

        List<DriveAssignmentDTO> users = permissionService.getDriveUsers(driveId);

        // SECURITY: Hard limit to prevent OOM from unbounded lists
        boolean truncated = false;
        if (users.size() > 100) {
            users = users.subList(0, 100);
            truncated = true;
            log.warn("Deprecated endpoint /drives/{}/users returned truncated results ({} total). Use paginated endpoint instead.",
                    driveId, users.size());
        }

        return ResponseEntity.ok()
                .header("Warning", "299 - \"Deprecated: Use /drives/{driveId}/users/paginated\"")
                .header("X-Truncated", String.valueOf(truncated))
                .body(ApiResponse.<List<DriveAssignmentDTO>>builder()
                        .success(true)
                        .data(users)
                        .build());
    }

    @GetMapping("/drives/{driveId}/users/paginated")
    @Operation(summary = "Get users with access to a drive with cursor-based pagination",
            description = "SECURITY: Requires MANAGE_USERS permission on the drive. Efficient paginated access to drive users.")
    public ResponseEntity<ApiResponse<CursorPage<DriveAssignmentDTO>>> getDriveUsersPaginated(
            @PathVariable String driveId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit) {

        String requesterId = TenantContext.getUserId();

        // SECURITY FIX (Round 7): Only users with MANAGE_USERS can list all users on a drive
        permissionService.requirePermission(requesterId, driveId, Permission.MANAGE_USERS);

        // SECURITY FIX (Round 9): Validate limit to prevent negative values and enforce max
        int validatedLimit = Math.max(1, Math.min(limit, 100));
        CursorPage<DriveAssignmentDTO> users = permissionService.getDriveUsersPaginated(driveId, cursor, validatedLimit);

        return ResponseEntity.ok(ApiResponse.<CursorPage<DriveAssignmentDTO>>builder()
                .success(true)
                .data(users)
                .build());
    }

    // ============== ROLE MANAGEMENT ==============

    /**
     * Get all roles for the tenant.
     * SECURITY FIX (Round 7): Requires tenant admin privileges to prevent role enumeration.
     * Regular users should not see all roles - only roles they are assigned to.
     */
    @GetMapping("/roles")
    @Operation(summary = "Get all roles for the tenant",
            description = "SECURITY: Requires tenant admin privileges (MANAGE_ROLES on any drive)")
    public ResponseEntity<ApiResponse<List<DriveRoleDTO>>> getTenantRoles() {

        String requesterId = TenantContext.getUserId();

        // SECURITY FIX (Round 7): Only tenant admins can list all roles
        // This prevents role enumeration attacks where attackers discover privileged roles
        boolean isAdmin = permissionService.isTenantAdmin(requesterId);
        if (!isAdmin) {
            log.warn("SECURITY: Non-admin user {} attempted to list all tenant roles", requesterId);
            throw new AccessDeniedException("Only tenant administrators can view all roles");
        }

        List<DriveRoleDTO> roles = permissionService.getTenantRoles();

        return ResponseEntity.ok(ApiResponse.<List<DriveRoleDTO>>builder()
                .success(true)
                .data(roles)
                .build());
    }

    @PutMapping("/roles/{roleId}/permissions")
    @Operation(summary = "Update role permissions",
            description = "SECURITY: Requires tenant admin privileges (MANAGE_ROLES on any drive)")
    public ResponseEntity<ApiResponse<DriveRoleDTO>> updateRolePermissions(
            @PathVariable String roleId,
            @RequestBody Set<Permission> permissions) {

        String requesterId = TenantContext.getUserId();
        String tenantId = TenantContext.getTenantId();

        // SECURITY FIX: Only tenant admins can modify role permissions
        // This prevents privilege escalation attacks
        boolean isAdmin = permissionService.isTenantAdmin(requesterId);
        if (!isAdmin) {
            log.warn("Unauthorized role permission update: user {} tried to update role {}",
                    requesterId, roleId);
            throw new AccessDeniedException("Only tenant administrators can modify role permissions");
        }

        // SECURITY FIX (Round 7): Validate roleId belongs to current tenant
        // This prevents cross-tenant role modification attacks
        if (!permissionService.isRoleInTenant(roleId, tenantId)) {
            log.warn("SECURITY: Cross-tenant role update attempt - user {} (tenant {}) tried to update role {}",
                    requesterId, tenantId, roleId);
            throw new AccessDeniedException("Role does not belong to your tenant");
        }

        DriveRoleDTO role = permissionService.updateRolePermissions(roleId, permissions);

        return ResponseEntity.ok(ApiResponse.<DriveRoleDTO>builder()
                .success(true)
                .data(role)
                .build());
    }

    // ============== CACHE MANAGEMENT ==============
    // SECURITY FIX (Round 7): Cache management endpoints are sensitive operations
    // that can be used for DoS (cache flushing) or timing attacks (monitoring invalidations).
    // These endpoints now require appropriate authorization.

    @PostMapping("/cache/warm/{userId}")
    @Operation(summary = "Warm the cache for a user (call on login)",
            description = "SECURITY: Users can only warm their own cache. Tenant admins can warm any user's cache.")
    public ResponseEntity<ApiResponse<Void>> warmCache(
            @PathVariable String userId) {

        String requesterId = TenantContext.getUserId();

        // SECURITY FIX (Round 7): Prevent cache manipulation for other users
        // Users can only warm their own cache unless they are tenant admin
        if (!userId.equals(requesterId)) {
            boolean isAdmin = permissionService.isTenantAdmin(requesterId);
            if (!isAdmin) {
                log.warn("SECURITY: Cache warm attempt denied: user {} tried to warm cache for user {}",
                        requesterId, userId);
                throw new AccessDeniedException("Cannot warm cache for other users");
            }
        }

        permissionService.warmUserCache(userId);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .build());
    }

    @DeleteMapping("/cache/invalidate/user/{userId}")
    @Operation(summary = "Invalidate all cached permissions for a user",
            description = "SECURITY: Requires tenant admin privileges to prevent DoS via cache flushing")
    public ResponseEntity<ApiResponse<Void>> invalidateUserCache(
            @PathVariable String userId) {

        String requesterId = TenantContext.getUserId();

        // SECURITY FIX (Round 7): Only tenant admins can invalidate user cache
        // This prevents DoS attacks via repeated cache invalidation
        boolean isAdmin = permissionService.isTenantAdmin(requesterId);
        if (!isAdmin) {
            log.warn("SECURITY: Cache invalidation denied: user {} tried to invalidate cache for user {}",
                    requesterId, userId);
            throw new AccessDeniedException("Only tenant administrators can invalidate user cache");
        }

        String tenantId = TenantContext.getTenantId();
        cacheService.invalidateUser(tenantId, userId);
        log.info("Tenant admin {} invalidated cache for user {} in tenant {}", requesterId, userId, tenantId);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .build());
    }

    @DeleteMapping("/cache/invalidate/drive/{driveId}")
    @Operation(summary = "Invalidate all cached permissions for a drive",
            description = "SECURITY: Requires MANAGE_USERS permission on the drive or tenant admin privileges")
    public ResponseEntity<ApiResponse<Void>> invalidateDriveCache(
            @PathVariable String driveId) {

        String requesterId = TenantContext.getUserId();

        // SECURITY FIX (Round 7): Require MANAGE_USERS permission or tenant admin
        // This prevents DoS attacks via repeated cache invalidation
        boolean canInvalidate = permissionService.isTenantAdmin(requesterId);
        if (!canInvalidate) {
            // Check if user has MANAGE_USERS on the specific drive
            PermissionCheckResponse permCheck = permissionService.checkPermission(
                    PermissionCheckRequest.builder()
                            .userId(requesterId)
                            .driveId(driveId)
                            .requiredPermission(Permission.MANAGE_USERS)
                            .build());
            canInvalidate = permCheck.isHasAccess() &&
                    permCheck.getPermissions() != null &&
                    permCheck.getPermissions().contains(Permission.MANAGE_USERS);
        }

        if (!canInvalidate) {
            log.warn("SECURITY: Drive cache invalidation denied: user {} tried to invalidate cache for drive {}",
                    requesterId, driveId);
            throw new AccessDeniedException("Insufficient permissions to invalidate drive cache");
        }

        String tenantId = TenantContext.getTenantId();
        cacheService.invalidateDrive(tenantId, driveId);
        log.info("User {} invalidated cache for drive {} in tenant {}", requesterId, driveId, tenantId);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .build());
    }

    @GetMapping("/cache/stats")
    @Operation(summary = "Get cache statistics",
            description = "SECURITY: Requires tenant admin privileges to view cache metrics")
    public ResponseEntity<ApiResponse<PermissionCacheService.CacheStats>> getCacheStats() {

        String requesterId = TenantContext.getUserId();

        // SECURITY FIX (Round 7): Only tenant admins can view cache stats
        // Cache stats could reveal information about system usage patterns
        boolean isAdmin = permissionService.isTenantAdmin(requesterId);
        if (!isAdmin) {
            log.warn("SECURITY: Cache stats access denied for user {}", requesterId);
            throw new AccessDeniedException("Only tenant administrators can view cache statistics");
        }

        PermissionCacheService.CacheStats stats = cacheService.getStats();

        return ResponseEntity.ok(ApiResponse.<PermissionCacheService.CacheStats>builder()
                .success(true)
                .data(stats)
                .build());
    }

    // ============== BULK OPERATIONS ==============

    /**
     * Maximum number of users that can be assigned in a single bulk operation.
     * SECURITY (Round 7): Prevents DoS via extremely large bulk requests.
     */
    private static final int MAX_BULK_USERS = 100;

    @PostMapping("/bulk/department/{driveId}")
    @Operation(summary = "Assign default role to all members of a department",
            description = "SECURITY: Requires MANAGE_USERS permission on the target drive. Max 100 users per request.")
    public ResponseEntity<ApiResponse<Integer>> assignDepartmentMembers(
            @PathVariable String driveId,
            @RequestParam String departmentId,
            @RequestParam String defaultRoleId,
            @RequestBody List<String> userIds) {

        String requesterId = TenantContext.getUserId();
        String tenantId = TenantContext.getTenantId();

        // SECURITY FIX (Round 7): Limit bulk operation size to prevent DoS
        if (userIds != null && userIds.size() > MAX_BULK_USERS) {
            log.warn("SECURITY: Bulk operation size limit exceeded - user {} tried to assign {} users (max: {})",
                    requesterId, userIds.size(), MAX_BULK_USERS);
            throw new IllegalArgumentException("Cannot assign more than " + MAX_BULK_USERS + " users at once");
        }

        // SECURITY: Require MANAGE_USERS permission on the target drive
        permissionService.requirePermission(requesterId, driveId, Permission.MANAGE_USERS);

        // SECURITY FIX (Round 7): Validate departmentId belongs to current tenant
        // This prevents cross-organization user assignment attacks
        if (!permissionService.isDepartmentInTenant(departmentId, tenantId)) {
            log.warn("SECURITY: Cross-tenant department assignment attempt - user {} (tenant {}) tried to use department {}",
                    requesterId, tenantId, departmentId);
            throw new AccessDeniedException("Department does not belong to your organization");
        }

        // SECURITY FIX (Round 7): Validate defaultRoleId belongs to current tenant
        if (!permissionService.isRoleInTenant(defaultRoleId, tenantId)) {
            log.warn("SECURITY: Cross-tenant role assignment attempt - user {} (tenant {}) tried to assign role {}",
                    requesterId, tenantId, defaultRoleId);
            throw new AccessDeniedException("Role does not belong to your organization");
        }

        int count = permissionService.assignDepartmentMembers(driveId, departmentId, userIds, defaultRoleId);

        return ResponseEntity.ok(ApiResponse.<Integer>builder()
                .success(true)
                .data(count)
                .build());
    }

    @DeleteMapping("/bulk/department/{userId}/{departmentId}")
    @Operation(summary = "Remove all department-based assignments for a user",
            description = "SECURITY: Only tenant admins or the user themselves can remove department access")
    public ResponseEntity<ApiResponse<Void>> removeDepartmentAccess(
            @PathVariable String userId,
            @PathVariable String departmentId) {

        String requesterId = TenantContext.getUserId();

        // SECURITY: Only tenant admins or the user themselves can remove department access
        if (!userId.equals(requesterId) && !permissionService.isTenantAdmin(requesterId)) {
            log.warn("Unauthorized department access removal: user {} tried to remove access for user {} from department {}",
                    requesterId, userId, departmentId);
            throw new AccessDeniedException("Cannot remove department access for other users");
        }

        permissionService.removeDepartmentAccess(userId, departmentId);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .build());
    }
}
