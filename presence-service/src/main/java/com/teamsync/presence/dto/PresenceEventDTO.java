package com.teamsync.presence.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PresenceEventDTO {

    private EventType eventType;
    private String tenantId;
    private String userId;
    private String userName;
    private String avatarUrl;
    private String documentId;
    private String documentName;
    private String driveId;
    private String color;
    private UserPresenceDTO.PresenceStatus status;
    private DocumentPresenceDTO.EditorState editorState;
    private DocumentPresenceDTO.CursorPosition cursorPosition;
    private DocumentPresenceDTO.SelectionRange selectionRange;
    private Instant timestamp;

    public enum EventType {
        // User presence events
        USER_ONLINE,
        USER_OFFLINE,
        USER_STATUS_CHANGED,
        USER_AWAY,

        // Document presence events
        USER_JOINED_DOCUMENT,
        USER_LEFT_DOCUMENT,
        USER_CURSOR_MOVED,
        USER_SELECTION_CHANGED,
        USER_STARTED_EDITING,
        USER_STOPPED_EDITING,
        USER_TYPING,
        USER_IDLE
    }
}
