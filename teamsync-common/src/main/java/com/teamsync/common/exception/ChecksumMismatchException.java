package com.teamsync.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when file checksum verification fails.
 * This indicates data corruption during upload or a mismatch between
 * the expected and actual file content.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class ChecksumMismatchException extends RuntimeException {

    private final String expectedChecksum;
    private final String actualChecksum;
    private final String storageKey;

    public ChecksumMismatchException(String message) {
        super(message);
        this.expectedChecksum = null;
        this.actualChecksum = null;
        this.storageKey = null;
    }

    public ChecksumMismatchException(String storageKey, String expectedChecksum, String actualChecksum) {
        super(String.format("Checksum mismatch for file %s. Expected: %s, Actual: %s",
                storageKey, expectedChecksum, actualChecksum));
        this.storageKey = storageKey;
        this.expectedChecksum = expectedChecksum;
        this.actualChecksum = actualChecksum;
    }

    public String getExpectedChecksum() {
        return expectedChecksum;
    }

    public String getActualChecksum() {
        return actualChecksum;
    }

    public String getStorageKey() {
        return storageKey;
    }
}
