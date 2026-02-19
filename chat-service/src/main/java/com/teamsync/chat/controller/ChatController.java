package com.teamsync.chat.controller;

import com.teamsync.common.dto.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for chat operations.
 *
 * SECURITY FIX (Round 12): Added rate limiting via RateLimitAspect.
 * All endpoints are now protected by dual-layer rate limiting:
 * - Global rate limiter (Resilience4j)
 * - Per-user rate limiter (Redis-based)
 *
 * Also added input validation for path variables to prevent injection.
 */
/**
 * SECURITY FIX (Round 15 #H9): Added class-level @PreAuthorize for defense-in-depth.
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Validated
@Slf4j
@PreAuthorize("isAuthenticated()")
public class ChatController {

    /**
     * SECURITY FIX (Round 12): Valid ID pattern for path variables.
     * Prevents NoSQL injection and path traversal attacks.
     */
    private static final String VALID_ID_PATTERN = "^[a-zA-Z0-9-]{1,64}$";

    /**
     * Get messages for a channel.
     * Rate limited by RateLimitAspect (messageReadOperations).
     */
    @GetMapping("/channels/{channelId}/messages")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<String>> getMessages(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid channel ID format")
            String channelId) {

        log.debug("GET /channels/{}/messages", channelId);

        return ResponseEntity.ok(ApiResponse.<String>builder()
                .success(true)
                .message("Chat Service - Messages for channel: " + channelId)
                .build());
    }

    /**
     * Send a message to a channel.
     * Rate limited by RateLimitAspect (messageSendOperations).
     */
    @PostMapping("/channels/{channelId}/messages")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<String>> sendMessage(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid channel ID format")
            String channelId) {

        log.info("POST /channels/{}/messages", channelId);

        return ResponseEntity.ok(ApiResponse.<String>builder()
                .success(true)
                .message("Chat Service - Send to channel: " + channelId)
                .build());
    }

    /**
     * Get comments for a document.
     * Rate limited by RateLimitAspect (messageReadOperations).
     */
    @GetMapping("/documents/{documentId}/comments")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<String>> getComments(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid document ID format")
            String documentId) {

        log.debug("GET /documents/{}/comments", documentId);

        return ResponseEntity.ok(ApiResponse.<String>builder()
                .success(true)
                .message("Chat Service - Comments for document: " + documentId)
                .build());
    }

    /**
     * Add a comment to a document.
     * Rate limited by RateLimitAspect (commentOperations).
     */
    @PostMapping("/documents/{documentId}/comments")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<String>> addComment(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid document ID format")
            String documentId) {

        log.info("POST /documents/{}/comments", documentId);

        return ResponseEntity.ok(ApiResponse.<String>builder()
                .success(true)
                .message("Chat Service - Add comment to: " + documentId)
                .build());
    }

    /**
     * AI chat endpoint (DocuTalk).
     * Rate limited by RateLimitAspect (aiChatOperations) - stricter limits.
     *
     * SECURITY NOTE: This endpoint is expensive (AI inference) and must be
     * heavily rate limited to prevent resource exhaustion.
     */
    @PostMapping("/documents/{documentId}/ai/chat")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<String>> chat(
            @PathVariable
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid document ID format")
            String documentId,
            @RequestBody
            @NotBlank
            @Size(min = 1, max = 4000, message = "Query must be between 1 and 4000 characters")
            String query) {

        log.info("POST /documents/{}/ai/chat - query length: {}", documentId, query.length());

        return ResponseEntity.ok(ApiResponse.<String>builder()
                .success(true)
                .message("Chat Service - AI response for document: " + documentId)
                .build());
    }

    /**
     * Ask AI a question about a document.
     * Rate limited by RateLimitAspect (aiChatOperations) - stricter limits.
     */
    @PostMapping("/ai/ask")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<String>> askAI(
            @RequestParam
            @NotBlank
            @Pattern(regexp = VALID_ID_PATTERN, message = "Invalid document ID format")
            String documentId,
            @RequestBody
            @NotBlank
            @Size(min = 1, max = 4000, message = "Question must be between 1 and 4000 characters")
            String question) {

        log.info("POST /ai/ask - documentId: {}, question length: {}", documentId, question.length());

        return ResponseEntity.ok(ApiResponse.<String>builder()
                .success(true)
                .message("Chat Service - AI answer for question")
                .build());
    }
}
