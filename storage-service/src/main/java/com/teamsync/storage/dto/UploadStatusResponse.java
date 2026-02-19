package com.teamsync.storage.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Response for upload session status.
 * Used for upload resumption - tracks which parts have been completed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UploadStatusResponse {

    /**
     * The upload session ID.
     */
    private String sessionId;

    /**
     * Current status of the upload (INITIATED, IN_PROGRESS, COMPLETING, COMPLETED, CANCELLED, FAILED, EXPIRED).
     */
    private String status;

    /**
     * Total number of parts for multipart upload.
     */
    private Integer totalParts;

    /**
     * List of part numbers that have been successfully uploaded.
     */
    private List<Integer> completedParts;

    /**
     * Total bytes already uploaded.
     */
    private Long uploadedSize;

    /**
     * Total expected file size.
     */
    private Long totalSize;

    /**
     * When the upload session expires.
     */
    private Instant expiresAt;

    /**
     * The storage bucket.
     */
    private String bucket;

    /**
     * The storage key/path.
     */
    private String storageKey;

    /**
     * Whether the upload can be resumed.
     */
    private Boolean canResume;

    /**
     * Human-readable progress percentage.
     */
    private Double progressPercent;
}
