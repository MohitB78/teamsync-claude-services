package com.teamsync.presence.controller;

import com.teamsync.presence.config.WebSocketConfig;
import com.teamsync.presence.dto.*;
import com.teamsync.presence.service.DocumentAccessService;
import com.teamsync.presence.service.DocumentPresenceService;
import com.teamsync.presence.service.UserPresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
@Slf4j
public class WebSocketPresenceController {

    private final UserPresenceService userPresenceService;
    private final DocumentPresenceService documentPresenceService;
    private final DocumentAccessService documentAccessService;

    @MessageMapping("/presence/heartbeat")
    @SendToUser("/queue/presence/heartbeat")
    public HeartbeatResponse handleHeartbeat(
            @Payload HeartbeatRequest request,
            Principal principal,
            SimpMessageHeaderAccessor headerAccessor) {

        if (!(principal instanceof WebSocketConfig.WebSocketPrincipal wsPrincipal)) {
            log.warn("Heartbeat received without valid principal");
            return HeartbeatResponse.builder()
                    .acknowledged(false)
                    .build();
        }

        String userName = headerAccessor.getFirstNativeHeader("X-User-Name");
        String email = headerAccessor.getFirstNativeHeader("X-User-Email");
        String avatarUrl = headerAccessor.getFirstNativeHeader("X-User-Avatar");

        return userPresenceService.processHeartbeat(
                request,
                wsPrincipal.getTenantId(),
                wsPrincipal.getUserId(),
                userName != null ? userName : wsPrincipal.getUserName(),
                email,
                avatarUrl
        );
    }

    @MessageMapping("/presence/status")
    public void handleStatusUpdate(
            @Payload StatusUpdateRequest request,
            Principal principal) {

        if (!(principal instanceof WebSocketConfig.WebSocketPrincipal wsPrincipal)) {
            log.warn("Status update received without valid principal");
            return;
        }

        userPresenceService.updateStatus(
                wsPrincipal.getTenantId(),
                wsPrincipal.getUserId(),
                request.getStatus(),
                request.getStatusMessage()
        );
    }

    @MessageMapping("/presence/document/{documentId}/join")
    @SendToUser("/queue/presence/document/join")
    public JoinDocumentResponse handleJoinDocument(
            @DestinationVariable String documentId,
            @Payload JoinDocumentRequest request,
            Principal principal,
            SimpMessageHeaderAccessor headerAccessor) {

        if (!(principal instanceof WebSocketConfig.WebSocketPrincipal wsPrincipal)) {
            log.warn("Join document received without valid principal");
            return JoinDocumentResponse.builder()
                    .joined(false)
                    .build();
        }

        // Ensure document ID from path matches request
        if (request.getDocumentId() == null) {
            request.setDocumentId(documentId);
        }

        // SECURITY FIX: Verify user has access to the document's drive
        String driveId = request.getDriveId();
        if (driveId == null || driveId.isBlank()) {
            log.warn("SECURITY: Join document request missing driveId for document: {}", documentId);
            return JoinDocumentResponse.builder()
                    .joined(false)
                    .errorMessage("Drive ID is required")
                    .build();
        }

        if (!documentAccessService.hasDocumentAccess(wsPrincipal.getTenantId(), wsPrincipal.getUserId(), driveId)) {
            log.warn("SECURITY: User {} denied access to document {} in drive {}",
                    wsPrincipal.getUserId(), documentId, driveId);
            return JoinDocumentResponse.builder()
                    .joined(false)
                    .errorMessage("Access denied")
                    .build();
        }

        String userName = wsPrincipal.getUserName();
        String email = wsPrincipal.getEmail();
        String avatarUrl = headerAccessor.getFirstNativeHeader("X-User-Avatar");

        return documentPresenceService.joinDocument(
                request,
                wsPrincipal.getTenantId(),
                wsPrincipal.getUserId(),
                userName,
                email,
                avatarUrl
        );
    }

    @MessageMapping("/presence/document/{documentId}/leave")
    public void handleLeaveDocument(
            @DestinationVariable String documentId,
            Principal principal) {

        if (!(principal instanceof WebSocketConfig.WebSocketPrincipal wsPrincipal)) {
            log.warn("Leave document received without valid principal");
            return;
        }

        documentPresenceService.leaveDocument(
                wsPrincipal.getTenantId(),
                documentId,
                wsPrincipal.getUserId()
        );
    }

    @MessageMapping("/presence/document/{documentId}/cursor")
    public void handleCursorUpdate(
            @DestinationVariable String documentId,
            @Payload UpdateCursorRequest request,
            Principal principal) {

        if (!(principal instanceof WebSocketConfig.WebSocketPrincipal wsPrincipal)) {
            log.warn("Cursor update received without valid principal");
            return;
        }

        // Ensure document ID from path matches request
        if (request.getDocumentId() == null) {
            request.setDocumentId(documentId);
        }

        documentPresenceService.updateCursor(
                request,
                wsPrincipal.getTenantId(),
                wsPrincipal.getUserId()
        );
    }

    @MessageMapping("/presence/document/{documentId}/typing")
    public void handleTypingIndicator(
            @DestinationVariable String documentId,
            @Payload TypingRequest request,
            Principal principal) {

        if (!(principal instanceof WebSocketConfig.WebSocketPrincipal wsPrincipal)) {
            log.warn("Typing indicator received without valid principal");
            return;
        }

        DocumentPresenceDTO.EditorState state = request.isTyping() ?
                DocumentPresenceDTO.EditorState.TYPING :
                DocumentPresenceDTO.EditorState.EDITING;

        documentPresenceService.updateEditorState(
                wsPrincipal.getTenantId(),
                documentId,
                wsPrincipal.getUserId(),
                state
        );
    }

    // Simple DTO for status updates via WebSocket
    @lombok.Data
    public static class StatusUpdateRequest {
        private UserPresenceDTO.PresenceStatus status;
        private String statusMessage;
    }

    // Simple DTO for typing indicators
    @lombok.Data
    public static class TypingRequest {
        private boolean typing;
    }
}
