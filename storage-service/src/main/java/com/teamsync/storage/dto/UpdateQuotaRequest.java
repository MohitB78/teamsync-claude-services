package com.teamsync.storage.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating storage quota.
 *
 * SECURITY FIX (Round 12): Replaces raw Map<String, Long> with typed DTO
 * to ensure proper input validation and prevent mass assignment vulnerabilities.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateQuotaRequest {

    /**
     * New quota limit in bytes.
     *
     * Constraints:
     * - Minimum: 1 GB (1073741824 bytes)
     * - Maximum: 10 PB (10 * 1024^5 bytes) - reasonable upper bound
     */
    @NotNull(message = "Quota limit is required")
    @Min(value = 1073741824L, message = "Quota must be at least 1 GB")
    @Max(value = 11258999068426240L, message = "Quota cannot exceed 10 PB")
    private Long quotaLimit;
}
