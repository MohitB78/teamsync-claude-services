package com.teamsync.common.exception;

import com.teamsync.common.model.Permission;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class AccessDeniedException extends RuntimeException {

    private final String driveId;
    private final Permission requiredPermission;

    public AccessDeniedException(String message) {
        super(message);
        this.driveId = null;
        this.requiredPermission = null;
    }

    public AccessDeniedException(String driveId, Permission requiredPermission) {
        super(String.format("Access denied to drive %s. Required permission: %s", driveId, requiredPermission));
        this.driveId = driveId;
        this.requiredPermission = requiredPermission;
    }

    public String getDriveId() {
        return driveId;
    }

    public Permission getRequiredPermission() {
        return requiredPermission;
    }
}
