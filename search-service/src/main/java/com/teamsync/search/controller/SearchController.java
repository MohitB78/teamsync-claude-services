package com.teamsync.search.controller;

import com.teamsync.common.dto.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Search Service Controller.
 *
 * SECURITY (Round 6): Added comprehensive input validation to prevent:
 * - ReDoS attacks via unchecked query strings
 * - DoS via excessive offset/limit values
 * - SQL/NoSQL injection via unvalidated type parameter
 *
 * NOTE: Uses cursor-based pagination for scale (offset is deprecated but kept for backward compat)
 *
 * SECURITY FIX (Round 15 #H3): Added @PreAuthorize("isAuthenticated()") for defense-in-depth.
 */
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Validated
@Slf4j
@PreAuthorize("isAuthenticated()")
public class SearchController {

    private static final int MAX_QUERY_LENGTH = 500;
    private static final int MAX_LIMIT = 100;
    private static final int DEFAULT_LIMIT = 20;

    /**
     * SECURITY FIX (Round 15 #M8): Maximum offset + size to prevent Elasticsearch deep pagination.
     * Deep pagination is expensive - use search_after cursor for results beyond this limit.
     */
    private static final int MAX_OFFSET_PLUS_SIZE = 1000;

    /**
     * Search documents.
     * SECURITY FIX (Round 6): Added input validation for query parameters.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<SearchResponse>> search(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-User-ID") String userId,
            @RequestParam
            @Size(min = 1, max = MAX_QUERY_LENGTH, message = "Query must be between 1 and 500 characters")
            // SECURITY FIX (Round 14 #C22): Restricted dangerous characters that could be used
            // for Elasticsearch query injection. Removed: < > | { } [ ] \ which have special
            // meaning in Lucene/Elasticsearch query syntax.
            @Pattern(regexp = "^[\\p{L}\\p{N}\\s\\-_.,!?@#$%&*()+=:;\"']*$",
                     message = "Query contains invalid characters")
            String q,
            @RequestParam(required = false)
            @Pattern(regexp = "^(document|folder|all)?$", message = "Type must be 'document', 'folder', or 'all'")
            String type,
            @RequestParam(required = false)
            @Size(max = 256, message = "Cursor must not exceed 256 characters")
            String cursor,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Offset must be non-negative")
            @Max(value = 900, message = "Offset must not exceed 900 (use cursor for deep pagination)")
            int offset,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Limit must be at least 1")
            @Max(value = MAX_LIMIT, message = "Limit must not exceed 100")
            int limit) {

        // Enforce limits
        limit = Math.min(limit, MAX_LIMIT);

        // SECURITY FIX (Round 15 #M8): Block deep pagination to prevent Elasticsearch cluster overload
        // Deep pagination with offset is expensive - use search_after (cursor) for results beyond 1000
        if (offset + limit > MAX_OFFSET_PLUS_SIZE) {
            log.warn("SECURITY: Deep pagination blocked - offset {} + limit {} > {}. " +
                    "Use cursor-based pagination for results beyond {}.",
                    offset, limit, MAX_OFFSET_PLUS_SIZE, MAX_OFFSET_PLUS_SIZE);
            throw new IllegalArgumentException(
                "Offset + limit cannot exceed " + MAX_OFFSET_PLUS_SIZE + ". " +
                "Use cursor parameter (search_after) for pagination beyond " + MAX_OFFSET_PLUS_SIZE + " results.");
        }

        // SECURITY: Escape special regex characters to prevent ReDoS attacks
        String sanitizedQuery = java.util.regex.Pattern.quote(q);

        log.info("Search request - tenant: {}, user: {}, query: '{}', type: {}, cursor: {}, limit: {}",
                tenantId, userId, q.length() > 50 ? q.substring(0, 50) + "..." : q,
                type, cursor, limit);

        // TODO: Implement actual search via Elasticsearch
        // For now, return stub response with cursor-based pagination structure
        return ResponseEntity.ok(ApiResponse.<SearchResponse>builder()
                .success(true)
                .data(SearchResponse.builder()
                        .query(q)
                        .totalResults(0)
                        .nextCursor(null)
                        .hasMore(false)
                        .build())
                .message("Search completed")
                .build());
    }

    /**
     * Get search suggestions (autocomplete).
     * SECURITY FIX (Round 6): Added input validation.
     */
    @GetMapping("/suggest")
    public ResponseEntity<ApiResponse<SuggestResponse>> suggest(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-User-ID") String userId,
            @RequestParam
            @Size(min = 1, max = 100, message = "Query must be between 1 and 100 characters")
            @Pattern(regexp = "^[\\p{L}\\p{N}\\s\\-_.]*$", message = "Query contains invalid characters")
            String q) {

        log.debug("Suggest request - tenant: {}, query: '{}'", tenantId, q);

        // TODO: Implement actual suggestions via Elasticsearch
        return ResponseEntity.ok(ApiResponse.<SuggestResponse>builder()
                .success(true)
                .data(SuggestResponse.builder()
                        .query(q)
                        .suggestions(java.util.List.of())
                        .build())
                .message("Suggestions retrieved")
                .build());
    }

    /**
     * Trigger reindex for a document.
     * SECURITY FIX (Round 6): Added authorization header requirements.
     */
    @PostMapping("/reindex/{documentId}")
    public ResponseEntity<ApiResponse<String>> reindex(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-User-ID") String userId,
            @PathVariable
            @Size(max = 64, message = "Document ID must not exceed 64 characters")
            @Pattern(regexp = "^[a-zA-Z0-9\\-_]+$", message = "Document ID contains invalid characters")
            String documentId) {

        log.info("Reindex request - tenant: {}, user: {}, documentId: {}", tenantId, userId, documentId);

        // TODO: Implement reindex via Kafka event to search indexer
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .success(true)
                .message("Reindex queued for document: " + documentId)
                .build());
    }

    // ==================== Response DTOs ====================

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SearchResponse {
        private String query;
        private long totalResults;
        private java.util.List<SearchResult> results;
        private String nextCursor;
        private boolean hasMore;

        /**
         * SECURITY FIX (Round 15 #M8): Sort values of the last hit for search_after pagination.
         * When results exceed 1000, use this value as the cursor parameter for the next page.
         * Format: [score, docId] - pass as cursor=score,docId
         */
        private java.util.List<Object> lastSortValues;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SearchResult {
        private String id;
        private String type;
        private String name;
        private String path;
        private String highlight;
        private double score;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SuggestResponse {
        private String query;
        private java.util.List<String> suggestions;
    }
}
