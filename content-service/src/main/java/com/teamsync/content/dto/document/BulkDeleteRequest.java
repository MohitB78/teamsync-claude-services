package com.teamsync.content.dto.document;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for bulk document deletion.
 *
 * SECURITY (Round 6): Uses typed DTO with validation instead of Map to:
 * - Enforce maximum batch size to prevent DoS attacks
 * - Validate document ID format
 * - Prevent mass assignment vulnerabilities
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkDeleteRequest {

    /**
     * Maximum number of documents that can be deleted in a single bulk operation.
     * This limit prevents DoS attacks via extremely large bulk requests.
     */
    public static final int MAX_BULK_SIZE = 100;

    /**
     * List of document IDs to delete (move to trash).
     * Limited to 100 documents per request to prevent resource exhaustion.
     */
    @NotEmpty(message = "documentIds is required and cannot be empty")
    @Size(max = MAX_BULK_SIZE, message = "Cannot delete more than " + MAX_BULK_SIZE + " documents at once")
    private List<@Size(max = 64, message = "Document ID must not exceed 64 characters") String> documentIds;
}
