package com.teamsync.common.permission;

import com.teamsync.common.context.TenantContext;
import com.teamsync.common.exception.AccessDeniedException;
import com.teamsync.common.model.Permission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * AOP Aspect that enforces permission checks before method execution.
 *
 * This aspect intercepts methods annotated with @RequiresPermission
 * and validates that the current user has the required permission.
 *
 * Order is set high to ensure it runs after authentication/context setup
 * but before actual business logic.
 */
@Aspect
@Component
@Order(100)
@Slf4j
@RequiredArgsConstructor
public class PermissionAspect {

    private final PermissionService permissionService;

    /**
     * Before advice for methods annotated with @RequiresPermission.
     */
    @Before("@annotation(requiresPermission)")
    public void checkPermission(JoinPoint joinPoint, RequiresPermission requiresPermission) {
        Permission requiredPermission = requiresPermission.value();
        boolean softCheck = requiresPermission.softCheck();

        // Extract userId and driveId
        String userId = extractUserId(joinPoint, requiresPermission);
        String driveId = extractDriveId(joinPoint, requiresPermission);

        if (userId == null || driveId == null) {
            log.warn("Cannot check permission: userId={}, driveId={}", userId, driveId);
            if (!softCheck) {
                throw new AccessDeniedException("Missing user or drive context for permission check");
            }
            return;
        }

        log.debug("Checking {} permission for user {} on drive {}",
                requiredPermission, userId, driveId);

        boolean hasPermission = permissionService.hasPermission(userId, driveId, requiredPermission);

        if (!hasPermission) {
            String methodName = joinPoint.getSignature().getName();
            log.warn("Permission denied: {} requires {} on drive {} for user {}",
                    methodName, requiredPermission, driveId, userId);

            if (!softCheck) {
                throw new AccessDeniedException(
                        String.format("Access denied: %s permission required on drive %s",
                                requiredPermission, driveId));
            }
        }

        log.debug("Permission {} granted for user {} on drive {}",
                requiredPermission, userId, driveId);
    }

    /**
     * Before advice for classes annotated with @RequiresPermission.
     * Applies the permission check to all public methods in the class.
     */
    @Before("@within(requiresPermission) && execution(public * *(..))")
    public void checkClassPermission(JoinPoint joinPoint, RequiresPermission requiresPermission) {
        // Check if method has its own annotation (which takes precedence)
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        if (method.isAnnotationPresent(RequiresPermission.class)) {
            // Method-level annotation takes precedence, skip class-level check
            return;
        }

        // Apply class-level permission check
        checkPermission(joinPoint, requiresPermission);
    }

    /**
     * Extract userId from parameter or TenantContext.
     */
    private String extractUserId(JoinPoint joinPoint, RequiresPermission annotation) {
        String paramName = annotation.userIdParam();

        if (!paramName.isEmpty()) {
            return extractParameterValue(joinPoint, paramName);
        }

        return TenantContext.getUserId();
    }

    /**
     * Extract driveId from parameter or TenantContext.
     */
    private String extractDriveId(JoinPoint joinPoint, RequiresPermission annotation) {
        String paramName = annotation.driveIdParam();

        if (!paramName.isEmpty()) {
            return extractParameterValue(joinPoint, paramName);
        }

        return TenantContext.getDriveId();
    }

    /**
     * Extract a parameter value by name from the method arguments.
     */
    private String extractParameterValue(JoinPoint joinPoint, String paramName) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        if (parameterNames == null) {
            log.warn("Cannot extract parameter names. Compile with -parameters flag.");
            return null;
        }

        for (int i = 0; i < parameterNames.length; i++) {
            if (parameterNames[i].equals(paramName)) {
                Object value = args[i];
                if (value != null) {
                    return value.toString();
                }
            }
        }

        log.warn("Parameter '{}' not found in method arguments", paramName);
        return null;
    }
}
