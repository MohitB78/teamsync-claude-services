package com.teamsync.presence.model;

import com.teamsync.presence.dto.DocumentPresenceDTO;
import com.teamsync.presence.dto.JoinDocumentRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentParticipant implements Serializable {

    private static final long serialVersionUID = 1L;

    private String participantId;  // unique id for this session
    private String documentId;
    private String tenantId;
    private String userId;
    private String userName;
    private String email;
    private String avatarUrl;
    private String assignedColor;
    private JoinDocumentRequest.JoinMode mode;
    private DocumentPresenceDTO.EditorState editorState;
    private CursorInfo cursorInfo;
    private Instant joinedAt;
    private Instant lastActivity;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CursorInfo implements Serializable {
        private static final long serialVersionUID = 1L;

        private int line;
        private int column;
        private int offset;
        private Integer selectionStartLine;
        private Integer selectionStartColumn;
        private Integer selectionStartOffset;
        private Integer selectionEndLine;
        private Integer selectionEndColumn;
        private Integer selectionEndOffset;
        private boolean hasSelection;
    }

    public static String buildRedisKey(String tenantId, String documentId, String userId) {
        return String.format("presence:participant:%s:%s:%s", tenantId, documentId, userId);
    }

    public static String buildUserDocumentsSetKey(String tenantId, String userId) {
        return String.format("presence:user-documents:%s:%s", tenantId, userId);
    }

    public boolean isViewer() {
        return mode == JoinDocumentRequest.JoinMode.VIEW;
    }

    public boolean isEditor() {
        return mode == JoinDocumentRequest.JoinMode.EDIT;
    }

    public boolean isTyping() {
        return editorState == DocumentPresenceDTO.EditorState.TYPING;
    }

    public boolean isIdle(int idleThresholdSeconds) {
        if (lastActivity == null) {
            return true;
        }
        return Instant.now().isAfter(lastActivity.plusSeconds(idleThresholdSeconds));
    }
}
