package com.teamsync.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.INSUFFICIENT_STORAGE)
public class StorageQuotaExceededException extends RuntimeException {

    private final String driveId;
    private final long currentUsage;
    private final long quota;

    public StorageQuotaExceededException(String message) {
        super(message);
        this.driveId = null;
        this.currentUsage = 0;
        this.quota = 0;
    }

    public StorageQuotaExceededException(String driveId, long currentUsage, long quota) {
        super(String.format("Storage quota exceeded for drive %s. Current: %d bytes, Quota: %d bytes",
                driveId, currentUsage, quota));
        this.driveId = driveId;
        this.currentUsage = currentUsage;
        this.quota = quota;
    }

    public String getDriveId() {
        return driveId;
    }

    public long getCurrentUsage() {
        return currentUsage;
    }

    public long getQuota() {
        return quota;
    }
}
