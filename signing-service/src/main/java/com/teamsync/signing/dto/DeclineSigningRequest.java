package com.teamsync.signing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for declining to sign.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeclineSigningRequest {

    @NotBlank(message = "Reason is required")
    @Size(max = 1000, message = "Reason cannot exceed 1000 characters")
    private String reason;
}
