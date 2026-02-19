package com.teamsync.common.permission;

import com.teamsync.common.context.TenantContext;
import com.teamsync.common.exception.AccessDeniedException;
import com.teamsync.common.model.Permission;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for PermissionAspect - AOP-based permission enforcement.
 *
 * CRITICAL TESTS:
 * - Permission checking via aspect
 * - Parameter extraction from method arguments
 * - TenantContext fallback
 * - Soft check vs hard check
 * - Class-level vs method-level annotations
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Permission Aspect Tests")
class PermissionAspectTest {

    @Mock
    private PermissionService permissionService;

    @Mock
    private JoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    @InjectMocks
    private PermissionAspect permissionAspect;

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

        lenient().when(joinPoint.getSignature()).thenReturn(methodSignature);
    }

    @AfterEach
    void tearDown() {
        tenantContextMock.close();
    }

    @Nested
    @DisplayName("Permission Check Tests")
    class PermissionCheckTests {

        @Test
        @DisplayName("Should proceed when user has required permission")
        void checkPermission_HasPermission_Proceeds() {
            // Given
            RequiresPermission annotation = createAnnotation(Permission.READ, false, "", "");
            setupJoinPoint("testMethod", new String[]{}, new Object[]{});
            when(permissionService.hasPermission(USER_ID, DRIVE_ID, Permission.READ)).thenReturn(true);

            // When/Then - should not throw
            assertThatCode(() -> permissionAspect.checkPermission(joinPoint, annotation))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when permission missing")
        void checkPermission_NoPermission_ThrowsAccessDenied() {
            // Given
            RequiresPermission annotation = createAnnotation(Permission.WRITE, false, "", "");
            setupJoinPoint("testMethod", new String[]{}, new Object[]{});
            when(permissionService.hasPermission(USER_ID, DRIVE_ID, Permission.WRITE)).thenReturn(false);

            // When/Then
            assertThatThrownBy(() -> permissionAspect.checkPermission(joinPoint, annotation))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Access denied")
                    .hasMessageContaining("WRITE");
        }

        @Test
        @DisplayName("Should proceed when soft check and no permission")
        void checkPermission_SoftCheck_WarnsButProceeds() {
            // Given
            RequiresPermission annotation = createAnnotation(Permission.DELETE, true, "", "");
            setupJoinPoint("testMethod", new String[]{}, new Object[]{});
            when(permissionService.hasPermission(USER_ID, DRIVE_ID, Permission.DELETE)).thenReturn(false);

            // When/Then - should not throw due to soft check
            assertThatCode(() -> permissionAspect.checkPermission(joinPoint, annotation))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should check permission for all Permission types")
        void checkPermission_AllPermissionTypes() {
            for (Permission permission : Permission.values()) {
                // Given
                RequiresPermission annotation = createAnnotation(permission, false, "", "");
                setupJoinPoint("testMethod", new String[]{}, new Object[]{});
                when(permissionService.hasPermission(USER_ID, DRIVE_ID, permission)).thenReturn(true);

                // When/Then
                assertThatCode(() -> permissionAspect.checkPermission(joinPoint, annotation))
                        .doesNotThrowAnyException();

                verify(permissionService).hasPermission(USER_ID, DRIVE_ID, permission);
                reset(permissionService);
            }
        }
    }

    @Nested
    @DisplayName("Parameter Extraction Tests")
    class ParameterExtractionTests {

        @Test
        @DisplayName("Should extract userId from method parameter")
        void extractUserId_FromParam_ReturnsValue() {
            // Given
            String paramUserId = "param-user-123";
            RequiresPermission annotation = createAnnotation(Permission.READ, false, "userId", "");
            setupJoinPoint("testMethod", new String[]{"userId", "data"}, new Object[]{paramUserId, "someData"});
            when(permissionService.hasPermission(paramUserId, DRIVE_ID, Permission.READ)).thenReturn(true);

            // When/Then
            assertThatCode(() -> permissionAspect.checkPermission(joinPoint, annotation))
                    .doesNotThrowAnyException();

            verify(permissionService).hasPermission(paramUserId, DRIVE_ID, Permission.READ);
        }

        @Test
        @DisplayName("Should extract driveId from method parameter")
        void extractDriveId_FromParam_ReturnsValue() {
            // Given
            String paramDriveId = "param-drive-123";
            RequiresPermission annotation = createAnnotation(Permission.READ, false, "", "driveId");
            setupJoinPoint("testMethod", new String[]{"driveId", "data"}, new Object[]{paramDriveId, "someData"});
            when(permissionService.hasPermission(USER_ID, paramDriveId, Permission.READ)).thenReturn(true);

            // When/Then
            assertThatCode(() -> permissionAspect.checkPermission(joinPoint, annotation))
                    .doesNotThrowAnyException();

            verify(permissionService).hasPermission(USER_ID, paramDriveId, Permission.READ);
        }

        @Test
        @DisplayName("Should extract both userId and driveId from parameters")
        void extractBoth_FromParams_ReturnsValues() {
            // Given
            String paramUserId = "param-user-123";
            String paramDriveId = "param-drive-456";
            RequiresPermission annotation = createAnnotation(Permission.WRITE, false, "userId", "driveId");
            setupJoinPoint("testMethod",
                    new String[]{"userId", "driveId", "data"},
                    new Object[]{paramUserId, paramDriveId, "someData"});
            when(permissionService.hasPermission(paramUserId, paramDriveId, Permission.WRITE)).thenReturn(true);

            // When/Then
            assertThatCode(() -> permissionAspect.checkPermission(joinPoint, annotation))
                    .doesNotThrowAnyException();

            verify(permissionService).hasPermission(paramUserId, paramDriveId, Permission.WRITE);
        }

        @Test
        @DisplayName("Should throw when specified param not found")
        void extractUserId_ParamNotFound_ThrowsException() {
            // Given - param name is specified but doesn't exist in method signature
            RequiresPermission annotation = createAnnotation(Permission.READ, false, "nonExistentParam", "");
            setupJoinPoint("testMethod", new String[]{"otherParam"}, new Object[]{"otherValue"});

            // When/Then - extractParameterValue returns null when param not found
            // This causes userId to be null which triggers AccessDeniedException
            assertThatThrownBy(() -> permissionAspect.checkPermission(joinPoint, annotation))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Missing user or drive context");
        }

        @Test
        @DisplayName("Should use TenantContext when driveIdParam is empty")
        void extractDriveId_EmptyParamName_UsesTenantContext() {
            // Given
            RequiresPermission annotation = createAnnotation(Permission.READ, false, "", "");
            setupJoinPoint("testMethod", new String[]{}, new Object[]{});
            when(permissionService.hasPermission(USER_ID, DRIVE_ID, Permission.READ)).thenReturn(true);

            // When/Then
            assertThatCode(() -> permissionAspect.checkPermission(joinPoint, annotation))
                    .doesNotThrowAnyException();

            verify(permissionService).hasPermission(USER_ID, DRIVE_ID, Permission.READ);
        }

        @Test
        @DisplayName("Should handle null parameter value")
        void extractParam_NullValue_ReturnsNull() {
            // Given
            RequiresPermission annotation = createAnnotation(Permission.READ, false, "userId", "");
            setupJoinPoint("testMethod", new String[]{"userId"}, new Object[]{null});

            // When - null userId + null from context should throw (softCheck=false)
            tenantContextMock.when(TenantContext::getUserId).thenReturn(null);

            // Then - should throw because userId is null
            assertThatThrownBy(() -> permissionAspect.checkPermission(joinPoint, annotation))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Missing user or drive context");
        }

        @Test
        @DisplayName("Should throw when parameter names array is null")
        void extractParam_NullParamNames_ThrowsException() {
            // Given - param name specified but getParameterNames() returns null
            RequiresPermission annotation = createAnnotation(Permission.READ, false, "userId", "");
            lenient().when(joinPoint.getSignature()).thenReturn(methodSignature);
            lenient().when(methodSignature.getParameterNames()).thenReturn(null);
            lenient().when(methodSignature.getName()).thenReturn("testMethod");
            lenient().when(joinPoint.getArgs()).thenReturn(new Object[]{"someValue"});

            // When/Then - extractParameterValue returns null when parameterNames is null
            // This causes userId to be null which triggers AccessDeniedException
            assertThatThrownBy(() -> permissionAspect.checkPermission(joinPoint, annotation))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Missing user or drive context");
        }

        @Test
        @DisplayName("Should convert non-string parameter to string")
        void extractParam_NonStringValue_ConvertsToString() {
            // Given
            Long numericId = 12345L;
            RequiresPermission annotation = createAnnotation(Permission.READ, false, "userId", "");
            setupJoinPoint("testMethod", new String[]{"userId"}, new Object[]{numericId});
            when(permissionService.hasPermission("12345", DRIVE_ID, Permission.READ)).thenReturn(true);

            // When/Then
            assertThatCode(() -> permissionAspect.checkPermission(joinPoint, annotation))
                    .doesNotThrowAnyException();

            verify(permissionService).hasPermission("12345", DRIVE_ID, Permission.READ);
        }
    }

    @Nested
    @DisplayName("Missing Context Tests")
    class MissingContextTests {

        @Test
        @DisplayName("Should throw when userId is null and hard check")
        void missingUserId_HardCheck_Throws() {
            // Given
            RequiresPermission annotation = createAnnotation(Permission.READ, false, "", "");
            setupJoinPoint("testMethod", new String[]{}, new Object[]{});
            tenantContextMock.when(TenantContext::getUserId).thenReturn(null);

            // When/Then
            assertThatThrownBy(() -> permissionAspect.checkPermission(joinPoint, annotation))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Missing user or drive context");
        }

        @Test
        @DisplayName("Should throw when driveId is null and hard check")
        void missingDriveId_HardCheck_Throws() {
            // Given
            RequiresPermission annotation = createAnnotation(Permission.READ, false, "", "");
            setupJoinPoint("testMethod", new String[]{}, new Object[]{});
            tenantContextMock.when(TenantContext::getDriveId).thenReturn(null);

            // When/Then
            assertThatThrownBy(() -> permissionAspect.checkPermission(joinPoint, annotation))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Missing user or drive context");
        }

        @Test
        @DisplayName("Should return early when userId is null and soft check")
        void missingUserId_SoftCheck_ReturnsEarly() {
            // Given
            RequiresPermission annotation = createAnnotation(Permission.READ, true, "", "");
            setupJoinPoint("testMethod", new String[]{}, new Object[]{});
            tenantContextMock.when(TenantContext::getUserId).thenReturn(null);

            // When/Then - should not throw due to soft check
            assertThatCode(() -> permissionAspect.checkPermission(joinPoint, annotation))
                    .doesNotThrowAnyException();

            // Permission service should NOT be called
            verifyNoInteractions(permissionService);
        }

        @Test
        @DisplayName("Should return early when driveId is null and soft check")
        void missingDriveId_SoftCheck_ReturnsEarly() {
            // Given
            RequiresPermission annotation = createAnnotation(Permission.READ, true, "", "");
            setupJoinPoint("testMethod", new String[]{}, new Object[]{});
            tenantContextMock.when(TenantContext::getDriveId).thenReturn(null);

            // When/Then - should not throw due to soft check
            assertThatCode(() -> permissionAspect.checkPermission(joinPoint, annotation))
                    .doesNotThrowAnyException();

            verifyNoInteractions(permissionService);
        }
    }

    @Nested
    @DisplayName("Class Level Annotation Tests")
    class ClassLevelAnnotationTests {

        @Test
        @DisplayName("Should apply class-level permission to public methods")
        void classAnnotation_AppliesToPublicMethods() throws NoSuchMethodException {
            // Given
            RequiresPermission annotation = createAnnotation(Permission.READ, false, "", "");
            Method method = TestServiceWithoutMethodAnnotation.class.getMethod("publicMethod");
            setupJoinPointWithMethod(method, new String[]{}, new Object[]{});
            when(permissionService.hasPermission(USER_ID, DRIVE_ID, Permission.READ)).thenReturn(true);

            // When/Then
            assertThatCode(() -> permissionAspect.checkClassPermission(joinPoint, annotation))
                    .doesNotThrowAnyException();

            verify(permissionService).hasPermission(USER_ID, DRIVE_ID, Permission.READ);
        }

        @Test
        @DisplayName("Method annotation should override class annotation")
        void methodAnnotation_OverridesClassAnnotation() throws NoSuchMethodException {
            // Given
            RequiresPermission classAnnotation = createAnnotation(Permission.READ, false, "", "");
            Method method = TestServiceWithMethodAnnotation.class.getMethod("annotatedMethod");
            setupJoinPointWithMethod(method, new String[]{}, new Object[]{});

            // When - method has its own @RequiresPermission, class check should skip
            permissionAspect.checkClassPermission(joinPoint, classAnnotation);

            // Then - permission service should NOT be called (method annotation takes precedence)
            verifyNoInteractions(permissionService);
        }
    }

    @Nested
    @DisplayName("Exception Message Tests")
    class ExceptionMessageTests {

        @Test
        @DisplayName("Exception should include permission type")
        void exception_IncludesPermissionType() {
            // Given
            RequiresPermission annotation = createAnnotation(Permission.MANAGE_ROLES, false, "", "");
            setupJoinPoint("testMethod", new String[]{}, new Object[]{});
            when(permissionService.hasPermission(USER_ID, DRIVE_ID, Permission.MANAGE_ROLES)).thenReturn(false);

            // When/Then
            assertThatThrownBy(() -> permissionAspect.checkPermission(joinPoint, annotation))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("MANAGE_ROLES");
        }

        @Test
        @DisplayName("Exception should include drive ID")
        void exception_IncludesDriveId() {
            // Given
            RequiresPermission annotation = createAnnotation(Permission.WRITE, false, "", "");
            setupJoinPoint("testMethod", new String[]{}, new Object[]{});
            when(permissionService.hasPermission(USER_ID, DRIVE_ID, Permission.WRITE)).thenReturn(false);

            // When/Then
            assertThatThrownBy(() -> permissionAspect.checkPermission(joinPoint, annotation))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining(DRIVE_ID);
        }
    }

    // ============================================
    // Helper Methods
    // ============================================

    private RequiresPermission createAnnotation(Permission permission, boolean softCheck,
                                                 String userIdParam, String driveIdParam) {
        return new RequiresPermission() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return RequiresPermission.class;
            }

            @Override
            public Permission value() {
                return permission;
            }

            @Override
            public boolean softCheck() {
                return softCheck;
            }

            @Override
            public String userIdParam() {
                return userIdParam;
            }

            @Override
            public String driveIdParam() {
                return driveIdParam;
            }
        };
    }

    private void setupJoinPoint(String methodName, String[] paramNames, Object[] args) {
        lenient().when(joinPoint.getSignature()).thenReturn(methodSignature);
        lenient().when(methodSignature.getParameterNames()).thenReturn(paramNames);
        lenient().when(methodSignature.getName()).thenReturn(methodName);
        lenient().when(joinPoint.getArgs()).thenReturn(args);
    }

    private void setupJoinPointWithMethod(Method method, String[] paramNames, Object[] args) {
        lenient().when(joinPoint.getSignature()).thenReturn(methodSignature);
        lenient().when(methodSignature.getMethod()).thenReturn(method);
        lenient().when(methodSignature.getParameterNames()).thenReturn(paramNames);
        lenient().when(methodSignature.getName()).thenReturn(method.getName());
        lenient().when(joinPoint.getArgs()).thenReturn(args);
    }

    // Test classes for class-level annotation tests
    public static class TestServiceWithoutMethodAnnotation {
        public void publicMethod() {}
    }

    public static class TestServiceWithMethodAnnotation {
        @RequiresPermission(Permission.WRITE)
        public void annotatedMethod() {}
    }
}
