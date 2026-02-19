package com.teamsync.storage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for copying files between storage locations.
 *
 * SECURITY (Round 6): Uses typed DTO with validation instead of Map<String, String>
 * to prevent mass assignment and ensure input validation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileCopyRequest {

    @NotBlank(message = "Source bucket is required")
    @Size(max = 63, message = "Source bucket name must not exceed 63 characters")
    @Pattern(regexp = "^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$",
             message = "Source bucket name must be valid S3 bucket format")
    private String sourceBucket;

    @NotBlank(message = "Source key is required")
    @Size(max = 1024, message = "Source key must not exceed 1024 characters")
    @Pattern(regexp = "^[^\\x00-\\x1f\\x7f]*$",
             message = "Source key contains invalid characters")
    private String sourceKey;

    @NotBlank(message = "Destination bucket is required")
    @Size(max = 63, message = "Destination bucket name must not exceed 63 characters")
    @Pattern(regexp = "^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$",
             message = "Destination bucket name must be valid S3 bucket format")
    private String destBucket;

    @NotBlank(message = "Destination key is required")
    @Size(max = 1024, message = "Destination key must not exceed 1024 characters")
    @Pattern(regexp = "^[^\\x00-\\x1f\\x7f]*$",
             message = "Destination key contains invalid characters")
    private String destKey;
}
