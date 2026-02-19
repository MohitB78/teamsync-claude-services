package com.teamsync.presence.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for bulk presence queries.
 *
 * SECURITY FIX (Round 15 #M25): Added @Size constraints to prevent DoS attacks.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BulkPresenceRequest {

    @NotEmpty(message = "User IDs list cannot be empty")
    @Size(max = 100, message = "Cannot query more than 100 users at once")
    private List<String> userIds;

    private boolean includeDocumentInfo;
}
