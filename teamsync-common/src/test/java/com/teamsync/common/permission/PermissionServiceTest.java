package com.teamsync.common.permission;

import com.teamsync.common.context.TenantContext;
import com.teamsync.common.dto.ApiResponse;
import com.teamsync.common.exception.AccessDeniedException;
import com.teamsync.common.model.Permission;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for Permission Service functionality.
 * Covers:
 * - Two-level permission model: Drive RBAC + Share-level granular
 * - O(1) cached permission checks
 * - Personal drive ownership (quick local check)
 * - Request-scoped caching
 * - Permission client integration
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Permission Service Tests")
class PermissionServiceTest {

    @Mock
    private PermissionClient permissionClient;

    @InjectMocks
    private PermissionService permissionService;

    private MockedStatic<TenantContext> tenantContextMock;

    private static final String TENANT_ID = "tenant-123";
    private static final String DRIVE_ID = "drive-456";
    private static final String USER_ID = "user-789";

    @BeforeEach
    void setUp() {
        tenantContextMock = mockStatic(TenantContext.class);
        tenantContextMock.when(TenantContext::getTenantId).thenReturn(TENANT_ID);
        tenantContextMock.when(TenantContext::getDriveId).thenReturn(DRIVE_ID);
        tenantContextMock.when(TenantContext::getUserId).thenReturn(USER_ID);

        // Clear request cache before each test
        PermissionService.clearRequestCache();
    }

    @AfterEach
    void tearDown() {
        tenantContextMock.close();
        PermissionService.clearRequestCache();
    }

    private PermissionCheckResponse createFullAccessResponse() {
        return PermissionCheckResponse.builder()
                .hasAccess(true)
                .hasPermission(true)
                .isOwner(true)
                .permissions(EnumSet.allOf(Permission.class))
                .build();
    }

    private PermissionCheckResponse createReadOnlyResponse() {
        return PermissionCheckResponse.builder()
                .hasAccess(true)
                .hasPermission(true)
                .isOwner(false)
                .permissions(EnumSet.of(Permission.READ))
                .build();
    }

    private PermissionCheckResponse createNoAccessResponse() {
        return PermissionCheckResponse.builder()
                .hasAccess(false)
                .hasPermission(false)
                .isOwner(false)
                .permissions(EnumSet.noneOf(Permission.class))
                .build();
    }

    @Nested
    @DisplayName("Personal Drive Owner Tests (O(1) Local Check)")
    class PersonalDriveOwnerTests {

        @Test
        @DisplayName("Should grant full access to personal drive owner without network call")
        void personalDriveOwner_GrantsFullAccess_NoNetworkCall() {
            // Given - personal drive format: "personal-{userId}"
            String personalDriveId = "personal-" + USER_ID;
            tenantContextMock.when(TenantContext::getDriveId).thenReturn(personalDriveId);

            // When
            PermissionCheckResponse response = permissionService.checkPermission(USER_ID, personalDriveId, Permission.WRITE);

            // Then - should have owner access
            assertThat(response.isHasAccess()).isTrue();
            assertThat(response.isHasPermission()).isTrue();
            assertThat(response.isOwner()).isTrue();
            assertThat(response.getPermissions()).containsAll(EnumSet.allOf(Permission.class));

            // Should NOT call permission client (local check only)
            verifyNoInteractions(permissionClient);
        }

        @Test
        @DisplayName("Should grant all permissions to personal drive owner")
        void personalDriveOwner_HasAllPermissions() {
            // Given
            String personalDriveId = "personal-" + USER_ID;
            tenantContextMock.when(TenantContext::getDriveId).thenReturn(personalDriveId);

            // When
            Set<Permission> permissions = permissionService.getPermissions(USER_ID, personalDriveId);

            // Then
            assertThat(permissions).containsAll(EnumSet.allOf(Permission.class));
            verifyNoInteractions(permissionClient);
        }

        @Test
        @DisplayName("Should identify user as owner of personal drive")
        void personalDriveOwner_IsOwner() {
            // Given
            String personalDriveId = "personal-" + USER_ID;
            tenantContextMock.when(TenantContext::getDriveId).thenReturn(personalDriveId);

            // When
            boolean isOwner = permissionService.isOwner(USER_ID, personalDriveId);

            // Then
            assertThat(isOwner).isTrue();
            verifyNoInteractions(permissionClient);
        }

        @Test
        @DisplayName("Should NOT identify user as owner of another user's personal drive")
        void otherUserPersonalDrive_NotOwner() {
            // Given
            String otherUserDriveId = "personal-other-user";

            ApiResponse<PermissionCheckResponse> mockResponse = ApiResponse.<PermissionCheckResponse>builder()
                    .success(true)
                    .data(createNoAccessResponse())
                    .build();
            when(permissionClient.checkPermission(any())).thenReturn(mockResponse);

            // When
            boolean isOwner = permissionService.isOwner(USER_ID, otherUserDriveId);

            // Then
            assertThat(isOwner).isFalse();
            verify(permissionClient).checkPermission(any()); // Network call required
        }
    }

    @Nested
    @DisplayName("Department Drive Permission Tests")
    class DepartmentDriveTests {

        @Test
        @DisplayName("Should check permission via permission client for department drive")
        void departmentDrive_CallsPermissionClient() {
            // Given
            String departmentDriveId = "dept-engineering";

            ApiResponse<PermissionCheckResponse> mockResponse = ApiResponse.<PermissionCheckResponse>builder()
                    .success(true)
                    .data(createReadOnlyResponse())
                    .build();
            when(permissionClient.checkPermission(any())).thenReturn(mockResponse);

            // When
            boolean hasReadPermission = permissionService.hasPermission(USER_ID, departmentDriveId, Permission.READ);

            // Then
            assertThat(hasReadPermission).isTrue();
            verify(permissionClient).checkPermission(any(PermissionCheckRequest.class));
        }

        @Test
        @DisplayName("Should deny access when user has no role in department drive")
        void departmentDrive_NoRole_DeniesAccess() {
            // Given
            String departmentDriveId = "dept-restricted";

            ApiResponse<PermissionCheckResponse> mockResponse = ApiResponse.<PermissionCheckResponse>builder()
                    .success(true)
                    .data(createNoAccessResponse())
                    .build();
            when(permissionClient.checkPermission(any())).thenReturn(mockResponse);

            // When
            boolean hasAccess = permissionService.hasAccess(USER_ID, departmentDriveId);

            // Then
            assertThat(hasAccess).isFalse();
        }

        @Test
        @DisplayName("Should check specific permission level")
        void departmentDrive_ChecksSpecificPermission() {
            // Given - user has READ but not WRITE
            String departmentDriveId = "dept-engineering";

            ApiResponse<PermissionCheckResponse> readResponse = ApiResponse.<PermissionCheckResponse>builder()
                    .success(true)
                    .data(PermissionCheckResponse.builder()
                            .hasAccess(true)
                            .hasPermission(true) // Has READ
                            .isOwner(false)
                            .permissions(EnumSet.of(Permission.READ))
                            .build())
                    .build();

            ApiResponse<PermissionCheckResponse> writeResponse = ApiResponse.<PermissionCheckResponse>builder()
                    .success(true)
                    .data(PermissionCheckResponse.builder()
                            .hasAccess(true)
                            .hasPermission(false) // No WRITE
                            .isOwner(false)
                            .permissions(EnumSet.of(Permission.READ))
                            .build())
                    .build();

            when(permissionClient.checkPermission(argThat(req ->
                    req != null && req.getRequiredPermission() == Permission.READ)))
                    .thenReturn(readResponse);
            when(permissionClient.checkPermission(argThat(req ->
                    req != null && req.getRequiredPermission() == Permission.WRITE)))
                    .thenReturn(writeResponse);

            // When
            boolean hasRead = permissionService.hasPermission(USER_ID, departmentDriveId, Permission.READ);
            PermissionService.clearRequestCache(); // Clear cache between checks
            boolean hasWrite = permissionService.hasPermission(USER_ID, departmentDriveId, Permission.WRITE);

            // Then
            assertThat(hasRead).isTrue();
            assertThat(hasWrite).isFalse();
        }
    }

    @Nested
    @DisplayName("Request-Scoped Cache Tests (O(1) Cached)")
    class RequestScopedCacheTests {

        @Test
        @DisplayName("Should cache permission check results within request")
        void cacheResults_WithinRequest() {
            // Given
            ApiResponse<PermissionCheckResponse> mockResponse = ApiResponse.<PermissionCheckResponse>builder()
                    .success(true)
                    .data(createReadOnlyResponse())
                    .build();
            when(permissionClient.checkPermission(any())).thenReturn(mockResponse);

            // When - multiple checks for same permission
            permissionService.hasPermission(USER_ID, DRIVE_ID, Permission.READ);
            permissionService.hasPermission(USER_ID, DRIVE_ID, Permission.READ);
            permissionService.hasPermission(USER_ID, DRIVE_ID, Permission.READ);

            // Then - should only call client once (cached)
            verify(permissionClient, times(1)).checkPermission(any());
        }

        @Test
        @DisplayName("Should cache different permission checks separately")
        void cacheResults_DifferentPermissions() {
            // Given
            ApiResponse<PermissionCheckResponse> readResponse = ApiResponse.<PermissionCheckResponse>builder()
                    .success(true)
                    .data(createReadOnlyResponse())
                    .build();
            when(permissionClient.checkPermission(any())).thenReturn(readResponse);

            // When - check different permissions
            permissionService.hasPermission(USER_ID, DRIVE_ID, Permission.READ);
            permissionService.hasPermission(USER_ID, DRIVE_ID, Permission.WRITE);

            // Then - should call client twice (different cache keys)
            verify(permissionClient, times(2)).checkPermission(any());
        }

        @Test
        @DisplayName("Should clear cache between requests")
        void clearCache_BetweenRequests() {
            // Given
            ApiResponse<PermissionCheckResponse> mockResponse = ApiResponse.<PermissionCheckResponse>builder()
                    .success(true)
                    .data(createReadOnlyResponse())
                    .build();
            when(permissionClient.checkPermission(any())).thenReturn(mockResponse);

            // When - first request
            permissionService.hasPermission(USER_ID, DRIVE_ID, Permission.READ);

            // Clear cache (simulating end of request)
            PermissionService.clearRequestCache();

            // Second request
            permissionService.hasPermission(USER_ID, DRIVE_ID, Permission.READ);

            // Then - should call client twice (cache was cleared)
            verify(permissionClient, times(2)).checkPermission(any());
        }
    }

    @Nested
    @DisplayName("Require Permission Tests")
    class RequirePermissionTests {

        @Test
        @DisplayName("Should pass when user has required permission")
        void requirePermission_HasPermission_Passes() {
            // Given
            ApiResponse<PermissionCheckResponse> mockResponse = ApiResponse.<PermissionCheckResponse>builder()
                    .success(true)
                    .data(createFullAccessResponse())
                    .build();
            when(permissionClient.checkPermission(any())).thenReturn(mockResponse);

            // When/Then - should not throw
            permissionService.requirePermission(USER_ID, DRIVE_ID, Permission.WRITE);
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when permission missing")
        void requirePermission_NoPermission_ThrowsException() {
            // Given
            ApiResponse<PermissionCheckResponse> mockResponse = ApiResponse.<PermissionCheckResponse>builder()
                    .success(true)
                    .data(createNoAccessResponse())
                    .build();
            when(permissionClient.checkPermission(any())).thenReturn(mockResponse);

            // When/Then
            assertThatThrownBy(() ->
                    permissionService.requirePermission(USER_ID, DRIVE_ID, Permission.DELETE))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining(USER_ID)
                    .hasMessageContaining("DELETE")
                    .hasMessageContaining(DRIVE_ID);
        }

        @Test
        @DisplayName("Should use context when no params provided")
        void requirePermission_UsesContext() {
            // Given
            ApiResponse<PermissionCheckResponse> mockResponse = ApiResponse.<PermissionCheckResponse>builder()
                    .success(true)
                    .data(createFullAccessResponse())
                    .build();
            when(permissionClient.checkPermission(any())).thenReturn(mockResponse);

            // When
            permissionService.requirePermission(Permission.READ);

            // Then - should use TenantContext values
            verify(permissionClient).checkPermission(argThat(req ->
                    req.getUserId().equals(USER_ID) && req.getDriveId().equals(DRIVE_ID)));
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should deny access on permission client error")
        void permissionClientError_DeniesAccess() {
            // Given
            when(permissionClient.checkPermission(any()))
                    .thenThrow(new RuntimeException("Service unavailable"));

            // When
            boolean hasAccess = permissionService.hasAccess(USER_ID, DRIVE_ID);

            // Then - fail closed (deny on error)
            assertThat(hasAccess).isFalse();
        }

        @Test
        @DisplayName("Should deny access on null response")
        void nullResponse_DeniesAccess() {
            // Given
            when(permissionClient.checkPermission(any())).thenReturn(null);

            // When
            boolean hasAccess = permissionService.hasAccess(USER_ID, DRIVE_ID);

            // Then
            assertThat(hasAccess).isFalse();
        }

        @Test
        @DisplayName("Should deny access on unsuccessful response")
        void unsuccessfulResponse_DeniesAccess() {
            // Given
            ApiResponse<PermissionCheckResponse> mockResponse = ApiResponse.<PermissionCheckResponse>builder()
                    .success(false)
                    .error("Error checking permissions")
                    .build();
            when(permissionClient.checkPermission(any())).thenReturn(mockResponse);

            // When
            boolean hasAccess = permissionService.hasAccess(USER_ID, DRIVE_ID);

            // Then
            assertThat(hasAccess).isFalse();
        }
    }

    @Nested
    @DisplayName("Cache Warming and Invalidation Tests")
    class CacheManagementTests {

        @Test
        @DisplayName("Should warm cache for user on login")
        void warmCache_OnLogin() {
            // When
            permissionService.warmCache(USER_ID);

            // Then
            verify(permissionClient).warmCache(USER_ID);
        }

        @Test
        @DisplayName("Should handle warm cache failure gracefully")
        void warmCache_Failure_NoException() {
            // Given
            doThrow(new RuntimeException("Service error")).when(permissionClient).warmCache(USER_ID);

            // When/Then - should not throw
            permissionService.warmCache(USER_ID);
        }

        @Test
        @DisplayName("Should invalidate user cache")
        void invalidateUserCache_Success() {
            // When
            permissionService.invalidateUserCache(USER_ID);

            // Then
            verify(permissionClient).invalidateUserCache(USER_ID);
        }

        @Test
        @DisplayName("Should invalidate drive cache")
        void invalidateDriveCache_Success() {
            // When
            permissionService.invalidateDriveCache(DRIVE_ID);

            // Then
            verify(permissionClient).invalidateDriveCache(DRIVE_ID);
        }
    }

    @Nested
    @DisplayName("Get Permissions Tests")
    class GetPermissionsTests {

        @Test
        @DisplayName("Should return all permissions for user")
        void getPermissions_ReturnsAllPermissions() {
            // Given
            Set<Permission> expectedPermissions = EnumSet.of(Permission.READ, Permission.WRITE, Permission.SHARE);
            ApiResponse<PermissionCheckResponse> mockResponse = ApiResponse.<PermissionCheckResponse>builder()
                    .success(true)
                    .data(PermissionCheckResponse.builder()
                            .hasAccess(true)
                            .permissions(expectedPermissions)
                            .build())
                    .build();
            when(permissionClient.checkPermission(any())).thenReturn(mockResponse);

            // When
            Set<Permission> permissions = permissionService.getPermissions(USER_ID, DRIVE_ID);

            // Then
            assertThat(permissions).containsExactlyInAnyOrderElementsOf(expectedPermissions);
        }

        @Test
        @DisplayName("Should use context when no params provided")
        void getPermissions_UsesContext() {
            // Given
            ApiResponse<PermissionCheckResponse> mockResponse = ApiResponse.<PermissionCheckResponse>builder()
                    .success(true)
                    .data(createReadOnlyResponse())
                    .build();
            when(permissionClient.checkPermission(any())).thenReturn(mockResponse);

            // When
            Set<Permission> permissions = permissionService.getPermissions();

            // Then
            assertThat(permissions).contains(Permission.READ);
            verify(permissionClient).checkPermission(argThat(req ->
                    req.getUserId().equals(USER_ID) && req.getDriveId().equals(DRIVE_ID)));
        }
    }

    @Nested
    @DisplayName("Two-Level Model Integration Tests")
    class TwoLevelModelTests {

        @Test
        @DisplayName("Level 1: Drive RBAC should be checked first")
        void driveRbac_CheckedFirst() {
            // Given - personal drive check (Level 1) should bypass Level 2
            String personalDriveId = "personal-" + USER_ID;
            tenantContextMock.when(TenantContext::getDriveId).thenReturn(personalDriveId);

            // When
            boolean hasPermission = permissionService.hasPermission(USER_ID, personalDriveId, Permission.MANAGE_ROLES);

            // Then - should have permission without calling permission client
            assertThat(hasPermission).isTrue();
            verifyNoInteractions(permissionClient);
        }

        @Test
        @DisplayName("Level 2: Share-level granular permissions via permission client")
        void shareLevelPermissions_ViaClient() {
            // Given - department drive requires Level 2 check
            String deptDriveId = "dept-123";
            ApiResponse<PermissionCheckResponse> mockResponse = ApiResponse.<PermissionCheckResponse>builder()
                    .success(true)
                    .data(PermissionCheckResponse.builder()
                            .hasAccess(true)
                            .hasPermission(true)
                            .isOwner(false)
                            .permissions(EnumSet.of(Permission.READ, Permission.WRITE))
                            .build())
                    .build();
            when(permissionClient.checkPermission(any())).thenReturn(mockResponse);

            // When
            boolean hasPermission = permissionService.hasPermission(USER_ID, deptDriveId, Permission.WRITE);

            // Then
            assertThat(hasPermission).isTrue();
            verify(permissionClient).checkPermission(any());
        }
    }
}
