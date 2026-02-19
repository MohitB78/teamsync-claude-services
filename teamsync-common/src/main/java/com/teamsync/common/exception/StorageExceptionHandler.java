package com.teamsync.common.exception;

import com.teamsync.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Exception handler for storage-specific exceptions.
 * Only loaded when storage classes are present on classpath.
 */
@ControllerAdvice
@Slf4j
@ConditionalOnClass(name = "io.minio.MinioClient")
public class StorageExceptionHandler {

    /**
     * SECURITY FIX (Round 14 #H25): No longer exposes internal quota details.
     * Previously, the raw exception message could reveal internal storage limits,
     * bucket names, or path information. Now returns a generic message while
     * logging full details server-side.
     */
    @ExceptionHandler(StorageQuotaExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleQuotaExceeded(StorageQuotaExceededException ex) {
        log.warn("Storage quota exceeded: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INSUFFICIENT_STORAGE)
                .body(ApiResponse.error("Storage quota exceeded. Please free up space or contact your administrator.", "QUOTA_EXCEEDED"));
    }

    @ExceptionHandler(ChecksumMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleChecksumMismatch(ChecksumMismatchException ex) {
        log.error("Checksum mismatch: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("File integrity check failed. Please re-upload the file.", "CHECKSUM_MISMATCH"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        log.warn("File size exceeded: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error("File size exceeds maximum allowed size", "FILE_TOO_LARGE"));
    }
}
