package com.teamsync.content.controller;

import com.teamsync.common.dto.ApiResponse;
import com.teamsync.common.dto.CursorPage;
import com.teamsync.content.dto.ContentItemDTO;
import com.teamsync.content.service.ContentOrchestrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for unified content operations in the TeamSync content service.
 *
 * <h2>Overview</h2>
 * <p>This controller provides a unified API that returns both folders and documents
 * in a single response. This improves performance by eliminating the need for
 * separate API calls to list folders and documents.
 *
 * <h2>Use Cases</h2>
 * <ul>
 *   <li><b>File Browser UI</b>: Single call to populate folder view with mixed content</li>
 *   <li><b>Content Statistics</b>: Get counts of folders vs documents in a location</li>
 *   <li><b>Efficient Navigation</b>: Reduce round-trips for folder exploration</li>
 * </ul>
 *
 * <h2>Response Format</h2>
 * <p>Returns {@link ContentItemDTO} which provides a polymorphic wrapper:
 * <ul>
 *   <li>{@code type}: "FOLDER" or "DOCUMENT"</li>
 *   <li>{@code folder}: Folder data (when type is FOLDER)</li>
 *   <li>{@code document}: Document data (when type is DOCUMENT)</li>
 * </ul>
 *
 * <h2>Sorting</h2>
 * <p>Results are sorted with folders first, then documents. Within each group,
 * items are sorted alphabetically by name.
 *
 * <h2>Filtering</h2>
 * <p>Use the {@code type} parameter to filter:
 * <ul>
 *   <li>{@code type=ALL} (default): Both folders and documents</li>
 *   <li>{@code type=FOLDER}: Folders only</li>
 *   <li>{@code type=DOCUMENT}: Documents only</li>
 * </ul>
 *
 * @author TeamSync Platform Team
 * @version 1.0.0
 * @since Spring Boot 4.0.0
 * @see ContentOrchestrationService
 * @see ContentItemDTO
 *
 * SECURITY FIX (Round 15 #H6): Added @PreAuthorize("isAuthenticated()") for defense-in-depth.
 */
@RestController
@RequestMapping("/api/content")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("isAuthenticated()")
public class ContentController {

    private final ContentOrchestrationService contentOrchestrationService;

    /**
     * Lists unified content (folders and documents) in a parent folder.
     *
     * <p>Returns a mixed list of folders and documents in a single response,
     * optimized for file browser UIs. Results are sorted with folders first,
     * then documents, both alphabetically by name.
     *
     * <p><b>Cursor Pagination:</b> Pass the {@code nextCursor} from a previous
     * response to fetch the next page. The cursor tracks position across both
     * folders and documents collections.
     *
     * <p><b>Example requests:</b>
     * <pre>
     * GET /api/content                           - Root level, all types
     * GET /api/content?parentId=abc123           - Specific folder contents
     * GET /api/content?type=FOLDER               - Folders only
     * GET /api/content?parentId=abc&amp;type=DOCUMENT - Documents in folder
     * GET /api/content?cursor=abc123             - Next page using cursor
     * </pre>
     *
     * @param parentId the parent folder ID (null or omitted for root level)
     * @param type     content type filter: ALL, FOLDER, or DOCUMENT (default ALL)
     * @param cursor   pagination cursor from previous response (null for first page)
     * @param limit    maximum items to return (default 100, max 500)
     * @return 200 OK with paginated content items, or 400 if limit invalid
     */
    @GetMapping
    public ResponseEntity<ApiResponse<CursorPage<ContentItemDTO>>> listContent(
            @RequestParam(required = false) String parentId,
            @RequestParam(required = false) ContentItemDTO.ContentType type,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "100") int limit) {

        log.info("Listing content - parentId: {}, type: {}, cursor: {}, limit: {}",
                parentId, type, cursor != null ? "present" : "null", limit);

        // Validate limit
        if (limit <= 0 || limit > 500) {
            return ResponseEntity.badRequest().body(
                ApiResponse.<CursorPage<ContentItemDTO>>builder()
                    .success(false)
                    .error("Limit must be between 1 and 500")
                    .build()
            );
        }

        CursorPage<ContentItemDTO> content = contentOrchestrationService.listUnifiedContent(
                parentId, type, cursor, limit);

        return ResponseEntity.ok(
            ApiResponse.<CursorPage<ContentItemDTO>>builder()
                .success(true)
                .data(content)
                .build()
        );
    }

    /**
     * Retrieves statistics about content in a folder.
     *
     * <p>Returns counts of folders vs documents for UI display (e.g., showing
     * "12 folders, 45 documents" in a folder header).
     *
     * <p><b>Note:</b> Statistics are computed from actual content listing,
     * limited by the {@code limit} parameter. For folders with more items
     * than the limit, {@code hasMore} will be true and counts may be incomplete.
     *
     * @param parentId the folder to get statistics for (null for root)
     * @param limit    maximum items to analyze (default 100)
     * @return 200 OK with content statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<ContentStats>> getContentStats(
            @RequestParam(required = false) String parentId,
            @RequestParam(defaultValue = "100") int limit) {

        log.info("Getting content stats - parentId: {}", parentId);

        // SECURITY FIX (Round 9): Validate limit to prevent negative values and enforce max
        int validatedLimit = Math.max(1, Math.min(limit, 500));
        CursorPage<ContentItemDTO> content = contentOrchestrationService.listUnifiedContent(
                parentId, null, null, validatedLimit);

        long folderCount = content.getItems().stream()
                .filter(ContentItemDTO::isFolder)
                .count();
        long documentCount = content.getItems().stream()
                .filter(ContentItemDTO::isDocument)
                .count();

        ContentStats stats = ContentStats.builder()
                .totalItems(content.getItems().size())
                .folderCount(folderCount)
                .documentCount(documentCount)
                .hasMore(content.isHasMore())
                .build();

        return ResponseEntity.ok(
            ApiResponse.<ContentStats>builder()
                .success(true)
                .data(stats)
                .build()
        );
    }

    /**
     * Lists all trashed content (folders and documents) for the current user.
     *
     * <p>Returns a unified list of trashed items owned by the current user across
     * all their drives. Used by the trash page UI to display items that can
     * be restored or permanently deleted.</p>
     *
     * <p><b>Note:</b> This endpoint does not require a drive ID header since it
     * returns items from all user's drives. Access is controlled by ownership.</p>
     *
     * <p><b>Sorting:</b> Items are sorted by deletion date (most recent first).</p>
     *
     * @param cursor pagination cursor from previous response (null for first page)
     * @param limit  maximum items to return (default 50, max 100)
     * @return 200 OK with paginated trashed content items
     */
    @GetMapping("/trashed")
    public ResponseEntity<ApiResponse<CursorPage<ContentItemDTO>>> listTrashedContent(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit) {

        log.info("Listing trashed content - cursor: {}, limit: {}", cursor != null ? "present" : "null", limit);

        // Validate limit
        int validatedLimit = Math.max(1, Math.min(limit, 100));

        CursorPage<ContentItemDTO> content = contentOrchestrationService.listTrashedContent(cursor, validatedLimit);

        return ResponseEntity.ok(
            ApiResponse.<CursorPage<ContentItemDTO>>builder()
                .success(true)
                .data(content)
                .message("Trashed content retrieved successfully")
                .build()
        );
    }

    /**
     * Data transfer object for content statistics.
     *
     * <p>Provides summary counts of content items in a folder location.
     */
    @lombok.Data
    @lombok.Builder
    public static class ContentStats {
        private int totalItems;
        private long folderCount;
        private long documentCount;
        private boolean hasMore;
    }
}
