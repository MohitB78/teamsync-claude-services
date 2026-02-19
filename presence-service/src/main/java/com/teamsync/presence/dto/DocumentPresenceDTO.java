package com.teamsync.presence.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DocumentPresenceDTO {

    private String documentId;
    private String documentName;
    private String tenantId;
    private String driveId;
    private List<DocumentViewer> viewers;
    private List<DocumentEditor> editors;
    private int totalViewers;
    private int totalEditors;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentViewer {
        private String userId;
        private String userName;
        private String email;
        private String avatarUrl;
        private String color;  // Assigned color for cursor/selection display
        private Instant joinedAt;
        private Instant lastActivity;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentEditor {
        private String userId;
        private String userName;
        private String email;
        private String avatarUrl;
        private String color;
        private EditorState state;
        private CursorPosition cursorPosition;
        private SelectionRange selectionRange;
        private Instant joinedAt;
        private Instant lastActivity;
    }

    public enum EditorState {
        VIEWING,
        EDITING,
        TYPING,
        IDLE
    }

    /**
     * SECURITY FIX (Round 14 #H13): Added bounds validation to prevent
     * resource exhaustion via extreme cursor position values.
     * Maximum values are set to reasonable document limits:
     * - line: up to 10 million lines
     * - column: up to 10000 columns per line
     * - offset: up to 1 billion characters total
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CursorPosition {
        @Min(value = 0, message = "Line must be non-negative")
        @Max(value = 10_000_000, message = "Line exceeds maximum allowed value")
        private int line;

        @Min(value = 0, message = "Column must be non-negative")
        @Max(value = 10_000, message = "Column exceeds maximum allowed value")
        private int column;

        @Min(value = 0, message = "Offset must be non-negative")
        @Max(value = 1_000_000_000, message = "Offset exceeds maximum allowed value")
        private int offset;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SelectionRange {
        private CursorPosition start;
        private CursorPosition end;
    }
}
