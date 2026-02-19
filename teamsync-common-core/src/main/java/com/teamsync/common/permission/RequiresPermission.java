package com.teamsync.common.permission;

import com.teamsync.common.model.Permission;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to declaratively require a permission before method execution.
 *
 * Usage:
 * <pre>
 * @RequiresPermission(Permission.WRITE)
 * public DocumentDTO createDocument(CreateDocumentRequest request) { ... }
 *
 * @RequiresPermission(value = Permission.DELETE, driveIdParam = "driveId")
 * public void deleteDocument(String driveId, String documentId) { ... }
 * </pre>
 *
 * The aspect will:
 * 1. Extract userId from TenantContext
 * 2. Extract driveId from TenantContext (or specified parameter)
 * 3. Call PermissionService to check permission
 * 4. Throw AccessDeniedException if permission is denied
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {

    /**
     * The permission required to execute the method.
     */
    Permission value();

    /**
     * Optional: Name of the method parameter containing the driveId.
     * If not specified, driveId is taken from TenantContext.
     */
    String driveIdParam() default "";

    /**
     * Optional: Name of the method parameter containing the userId.
     * If not specified, userId is taken from TenantContext.
     */
    String userIdParam() default "";

    /**
     * If true, allows method execution even if permission check fails,
     * but sets a flag in TenantContext. Useful for conditional logic.
     */
    boolean softCheck() default false;
}
