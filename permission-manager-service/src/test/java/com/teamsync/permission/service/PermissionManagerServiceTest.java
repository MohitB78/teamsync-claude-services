package com.teamsync.permission.service;

import com.teamsync.common.context.TenantContext;
import com.teamsync.common.model.DriveType;
import com.teamsync.common.model.Permission;
import com.teamsync.permission.dto.*;
import com.teamsync.permission.model.Drive;
import com.teamsync.permission.model.DriveAssignment;
import com.teamsync.permission.model.DriveAssignment.AssignmentSource;
import com.teamsync.permission.model.DriveRole;
import com.teamsync.permission.repository.DriveAssignmentRepository;
import com.teamsync.permission.repository.DriveRepository;
import com.teamsync.permission.repository.DriveRoleRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PermissionManagerService Tests")
class PermissionManagerServiceTest {

    @Mock
    private DriveRepository driveRepository;

    @Mock
    private DriveRoleRepository roleRepository;

    @Mock
    private DriveAssignmentRepository assignmentRepository;

    @Mock
    private PermissionCacheService cacheService;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private PermissionManagerService permissionService;

    private MockedStatic<TenantContext> tenantContextMock;

    private static final String TENANT_ID = "tenant-123";
    private static final String USER_ID = "user-456";
    private static final String DRIVE_ID = "dept-finance";
    private static final String PERSONAL_DRIVE_ID = "personal-user-456";
    private static final String ROLE_ID = "role-editor";

    @BeforeEach
    void setUp() {
        tenantContextMock = mockStatic(TenantContext.class);
        tenantContextMock.when(TenantContext::getTenantId).thenReturn(TENANT_ID);
        tenantContextMock.when(TenantContext::getUserId).thenReturn(USER_ID);

        // Mock KafkaTemplate to return a completed future for async event publishing
        lenient().when(kafkaTemplate.send(anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        // Mock RedisTemplate for admin cache operations
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.delete(anyString())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        tenantContextMock.close();
    }

    @Nested
    @DisplayName("Permission Check Tests")
    class PermissionCheckTests {

        @Test
        @DisplayName("Should return cached permission when cache hit")
        void checkPermission_CacheHit_ReturnsCachedPermission() {
            // Given
            CachedPermission cached = CachedPermission.builder()
                    .userId(USER_ID)
                    .driveId(DRIVE_ID)
                    .tenantId(TENANT_ID)
                    .hasAccess(true)
                    .permissions(EnumSet.of(Permission.READ, Permission.WRITE))
                    .roleName("EDITOR")
                    .isOwner(false)
                    .cachedAt(Instant.now())
                    .build();

            when(cacheService.get(TENANT_ID, USER_ID, DRIVE_ID)).thenReturn(Optional.of(cached));

            PermissionCheckRequest request = PermissionCheckRequest.builder()
                    .userId(USER_ID)
                    .driveId(DRIVE_ID)
                    .requiredPermission(Permission.READ)
                    .build();

            // When
            PermissionCheckResponse response = permissionService.checkPermission(request);

            // Then
            assertThat(response.isHasAccess()).isTrue();
            assertThat(response.isHasPermission()).isTrue();
            assertThat(response.getPermissions()).contains(Permission.READ, Permission.WRITE);
            assertThat(response.getSource()).isEqualTo("CACHE");
            verify(assignmentRepository, never()).findByUserIdAndDriveIdAndIsActiveTrue(anyString(), anyString());
        }

        @Test
        @DisplayName("Should return full access for personal drive owner")
        void checkPermission_PersonalDriveOwner_ReturnsFullAccess() {
            // Given
            when(cacheService.get(TENANT_ID, USER_ID, PERSONAL_DRIVE_ID)).thenReturn(Optional.empty());

            PermissionCheckRequest request = PermissionCheckRequest.builder()
                    .userId(USER_ID)
                    .driveId(PERSONAL_DRIVE_ID)
                    .requiredPermission(Permission.MANAGE_ROLES)
                    .build();

            // When
            PermissionCheckResponse response = permissionService.checkPermission(request);

            // Then
            assertThat(response.isHasAccess()).isTrue();
            assertThat(response.isHasPermission()).isTrue();
            assertThat(response.isOwner()).isTrue();
            assertThat(response.getPermissions()).containsAll(EnumSet.allOf(Permission.class));
            assertThat(response.getSource()).isEqualTo("DATABASE");
            verify(cacheService).put(any(CachedPermission.class));
        }

        @Test
        @DisplayName("Should check database on cache miss and cache result")
        void checkPermission_CacheMiss_ChecksDatabaseAndCaches() {
            // Given
            when(cacheService.get(TENANT_ID, USER_ID, DRIVE_ID)).thenReturn(Optional.empty());

            DriveAssignment assignment = DriveAssignment.builder()
                    .tenantId(TENANT_ID)
                    .userId(USER_ID)
                    .driveId(DRIVE_ID)
                    .roleId(ROLE_ID)
                    .roleName("EDITOR")
                    .permissions(EnumSet.of(Permission.READ, Permission.WRITE, Permission.DELETE))
                    .source(AssignmentSource.DIRECT)
                    .isActive(true)
                    .build();

            when(assignmentRepository.findByUserIdAndDriveIdAndIsActiveTrue(USER_ID, DRIVE_ID))
                    .thenReturn(Optional.of(assignment));

            PermissionCheckRequest request = PermissionCheckRequest.builder()
                    .userId(USER_ID)
                    .driveId(DRIVE_ID)
                    .requiredPermission(Permission.WRITE)
                    .build();

            // When
            PermissionCheckResponse response = permissionService.checkPermission(request);

            // Then
            assertThat(response.isHasAccess()).isTrue();
            assertThat(response.isHasPermission()).isTrue();
            assertThat(response.getSource()).isEqualTo("DATABASE");
            verify(cacheService).put(any(CachedPermission.class));
        }

        @Test
        @DisplayName("Should return no access when no assignment exists")
        void checkPermission_NoAssignment_ReturnsNoAccess() {
            // Given
            when(cacheService.get(TENANT_ID, USER_ID, DRIVE_ID)).thenReturn(Optional.empty());
            when(assignmentRepository.findByUserIdAndDriveIdAndIsActiveTrue(USER_ID, DRIVE_ID))
                    .thenReturn(Optional.empty());

            PermissionCheckRequest request = PermissionCheckRequest.builder()
                    .userId(USER_ID)
                    .driveId(DRIVE_ID)
                    .build();

            // When
            PermissionCheckResponse response = permissionService.checkPermission(request);

            // Then
            assertThat(response.isHasAccess()).isFalse();
            assertThat(response.isHasPermission()).isFalse();
            assertThat(response.getPermissions()).isEmpty();
            // Negative result should be cached
            verify(cacheService).put(any(CachedPermission.class));
        }

        @Test
        @DisplayName("Should return false for permission not in role")
        void checkPermission_PermissionNotInRole_ReturnsFalse() {
            // Given
            CachedPermission cached = CachedPermission.builder()
                    .userId(USER_ID)
                    .driveId(DRIVE_ID)
                    .tenantId(TENANT_ID)
                    .hasAccess(true)
                    .permissions(EnumSet.of(Permission.READ)) // Only READ
                    .roleName("VIEWER")
                    .isOwner(false)
                    .cachedAt(Instant.now())
                    .build();

            when(cacheService.get(TENANT_ID, USER_ID, DRIVE_ID)).thenReturn(Optional.of(cached));

            PermissionCheckRequest request = PermissionCheckRequest.builder()
                    .userId(USER_ID)
                    .driveId(DRIVE_ID)
                    .requiredPermission(Permission.DELETE) // Requesting DELETE
                    .build();

            // When
            PermissionCheckResponse response = permissionService.checkPermission(request);

            // Then
            assertThat(response.isHasAccess()).isTrue();
            assertThat(response.isHasPermission()).isFalse(); // DELETE not in role
        }
    }

    @Nested
    @DisplayName("Role Assignment Tests")
    class RoleAssignmentTests {

        @Test
        @DisplayName("Should assign role to user successfully")
        void assignRole_Success() {
            // Given
            DriveRole role = DriveRole.builder()
                    .id(ROLE_ID)
                    .tenantId(TENANT_ID)
                    .name("EDITOR")
                    .permissions(EnumSet.of(Permission.READ, Permission.WRITE, Permission.DELETE))
                    .build();

            when(driveRepository.existsByIdAndTenantId(DRIVE_ID, TENANT_ID)).thenReturn(true);
            when(roleRepository.findByIdAndTenantId(ROLE_ID, TENANT_ID)).thenReturn(Optional.of(role));
            when(assignmentRepository.findByTenantIdAndUserIdAndDriveId(TENANT_ID, USER_ID, DRIVE_ID))
                    .thenReturn(Optional.empty());
            when(assignmentRepository.save(any(DriveAssignment.class))).thenAnswer(i -> i.getArgument(0));

            AssignRoleRequest request = AssignRoleRequest.builder()
                    .userId(USER_ID)
                    .driveId(DRIVE_ID)
                    .roleId(ROLE_ID)
                    .source(AssignmentSource.DIRECT)
                    .build();

            // When
            DriveAssignmentDTO result = permissionService.assignRole(request);

            // Then
            assertThat(result.getUserId()).isEqualTo(USER_ID);
            assertThat(result.getDriveId()).isEqualTo(DRIVE_ID);
            assertThat(result.getRoleName()).isEqualTo("EDITOR");
            assertThat(result.getPermissions()).containsAll(role.getPermissions());
            verify(cacheService).delete(TENANT_ID, USER_ID, DRIVE_ID);
        }

        @Test
        @DisplayName("Should update existing assignment when reassigning role")
        void assignRole_ExistingAssignment_Updates() {
            // Given
            DriveRole newRole = DriveRole.builder()
                    .id("role-admin")
                    .tenantId(TENANT_ID)
                    .name("ADMIN")
                    .permissions(EnumSet.of(Permission.READ, Permission.WRITE, Permission.DELETE, Permission.MANAGE_USERS))
                    .build();

            DriveAssignment existing = DriveAssignment.builder()
                    .id("assignment-1")
                    .tenantId(TENANT_ID)
                    .userId(USER_ID)
                    .driveId(DRIVE_ID)
                    .roleId(ROLE_ID)
                    .roleName("EDITOR")
                    .permissions(EnumSet.of(Permission.READ, Permission.WRITE))
                    .isActive(true)
                    .build();

            when(driveRepository.existsByIdAndTenantId(DRIVE_ID, TENANT_ID)).thenReturn(true);
            when(roleRepository.findByIdAndTenantId("role-admin", TENANT_ID)).thenReturn(Optional.of(newRole));
            when(assignmentRepository.findByTenantIdAndUserIdAndDriveId(TENANT_ID, USER_ID, DRIVE_ID))
                    .thenReturn(Optional.of(existing));
            when(assignmentRepository.save(any(DriveAssignment.class))).thenAnswer(i -> i.getArgument(0));

            AssignRoleRequest request = AssignRoleRequest.builder()
                    .userId(USER_ID)
                    .driveId(DRIVE_ID)
                    .roleId("role-admin")
                    .build();

            // When
            DriveAssignmentDTO result = permissionService.assignRole(request);

            // Then
            assertThat(result.getRoleName()).isEqualTo("ADMIN");
            assertThat(result.getPermissions()).contains(Permission.MANAGE_USERS);
            verify(cacheService).delete(TENANT_ID, USER_ID, DRIVE_ID);
        }
    }

    @Nested
    @DisplayName("Drive Creation Tests")
    class DriveCreationTests {

        @Test
        @DisplayName("Should create personal drive with owner assignment")
        void createDrive_PersonalDrive_CreatesOwnerAssignment() {
            // Given
            DriveRole ownerRole = DriveRole.createOwnerRole(TENANT_ID);
            ownerRole.setId("owner-role-id");

            when(driveRepository.existsByIdAndTenantId(anyString(), eq(TENANT_ID))).thenReturn(false);
            when(roleRepository.findByTenantIdAndIsSystemRoleTrue(TENANT_ID)).thenReturn(List.of(ownerRole));
            when(roleRepository.findByTenantIdAndNameAndDriveIdIsNull(TENANT_ID, DriveRole.ROLE_OWNER))
                    .thenReturn(Optional.of(ownerRole));
            when(driveRepository.save(any(Drive.class))).thenAnswer(i -> i.getArgument(0));
            when(assignmentRepository.save(any(DriveAssignment.class))).thenAnswer(i -> i.getArgument(0));

            CreateDriveRequest request = CreateDriveRequest.builder()
                    .name("My Drive")
                    .type(DriveType.PERSONAL)
                    .ownerId(USER_ID)
                    .build();

            // When
            DriveDTO result = permissionService.createDrive(request);

            // Then
            assertThat(result.getId()).isEqualTo("personal-" + USER_ID);
            assertThat(result.getType()).isEqualTo(DriveType.PERSONAL);
            assertThat(result.getOwnerId()).isEqualTo(USER_ID);
            verify(assignmentRepository).save(any(DriveAssignment.class));
        }

        @Test
        @DisplayName("Should create department drive without initial assignment")
        void createDrive_DepartmentDrive_NoInitialAssignment() {
            // Given
            String departmentId = "dept-finance";
            DriveRole ownerRole = DriveRole.createOwnerRole(TENANT_ID);
            ownerRole.setId("owner-role-id");

            when(driveRepository.existsByIdAndTenantId(anyString(), eq(TENANT_ID))).thenReturn(false);
            when(roleRepository.findByTenantIdAndIsSystemRoleTrue(TENANT_ID)).thenReturn(List.of(ownerRole));
            when(roleRepository.findByTenantIdAndNameAndDriveIdIsNull(TENANT_ID, DriveRole.ROLE_OWNER))
                    .thenReturn(Optional.of(ownerRole));
            when(driveRepository.save(any(Drive.class))).thenAnswer(i -> i.getArgument(0));

            CreateDriveRequest request = CreateDriveRequest.builder()
                    .name("Finance Drive")
                    .type(DriveType.DEPARTMENT)
                    .departmentId(departmentId)
                    .build();

            // When
            DriveDTO result = permissionService.createDrive(request);

            // Then
            assertThat(result.getId()).isEqualTo("dept-" + departmentId);
            assertThat(result.getType()).isEqualTo(DriveType.DEPARTMENT);
            // No owner assignment for department drives
            verify(assignmentRepository, never()).save(any(DriveAssignment.class));
        }
    }

    @Nested
    @DisplayName("Cache Warming Tests")
    class CacheWarmingTests {

        @Test
        @DisplayName("Should warm cache for user on login")
        void warmUserCache_PopulatesCache() {
            // Given
            List<DriveAssignment> assignments = List.of(
                    DriveAssignment.builder()
                            .tenantId(TENANT_ID)
                            .userId(USER_ID)
                            .driveId("personal-" + USER_ID)
                            .roleId("owner-role")
                            .roleName("OWNER")
                            .permissions(EnumSet.allOf(Permission.class))
                            .source(AssignmentSource.OWNER)
                            .isActive(true)
                            .build(),
                    DriveAssignment.builder()
                            .tenantId(TENANT_ID)
                            .userId(USER_ID)
                            .driveId("dept-finance")
                            .roleId("editor-role")
                            .roleName("EDITOR")
                            .permissions(EnumSet.of(Permission.READ, Permission.WRITE))
                            .source(AssignmentSource.DEPARTMENT)
                            .isActive(true)
                            .build()
            );

            when(assignmentRepository.findByTenantIdAndUserIdAndIsActiveTrue(TENANT_ID, USER_ID))
                    .thenReturn(assignments);

            // When
            permissionService.warmUserCache(USER_ID);

            // Then
            verify(cacheService).warmCache(argThat(list -> list.size() == 2));
        }
    }
}
