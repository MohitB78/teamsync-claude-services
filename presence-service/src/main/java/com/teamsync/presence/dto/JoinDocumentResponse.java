package com.teamsync.presence.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
public class JoinDocumentResponse {

    private boolean joined;
    private String documentId;
    private String sessionId;
    private String assignedColor;
    private Instant joinedAt;
    private List<DocumentPresenceDTO.DocumentViewer> currentViewers;
    private List<DocumentPresenceDTO.DocumentEditor> currentEditors;
    private int totalParticipants;

    // Error message when join fails (e.g., access denied)
    private String errorMessage;
}
