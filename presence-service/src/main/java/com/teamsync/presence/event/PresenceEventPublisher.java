package com.teamsync.presence.event;

import com.teamsync.presence.config.PresenceProperties;
import com.teamsync.presence.dto.DocumentPresenceDTO;
import com.teamsync.presence.dto.PresenceEventDTO;
import com.teamsync.presence.dto.UserPresenceDTO;
import com.teamsync.presence.model.DocumentParticipant;
import com.teamsync.presence.model.DocumentPresence;
import com.teamsync.presence.model.UserPresence;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class PresenceEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final PresenceProperties presenceProperties;

    private static final String KAFKA_TOPIC_USER_PRESENCE = "teamsync.presence.user";
    private static final String KAFKA_TOPIC_DOCUMENT_PRESENCE = "teamsync.presence.document";

    public void publishUserOnline(UserPresence presence) {
        PresenceEventDTO event = PresenceEventDTO.builder()
                .eventType(PresenceEventDTO.EventType.USER_ONLINE)
                .tenantId(presence.getTenantId())
                .userId(presence.getUserId())
                .userName(presence.getUserName())
                .avatarUrl(presence.getAvatarUrl())
                .status(presence.getStatus())
                .timestamp(Instant.now())
                .build();

        publishUserEvent(event);
    }

    public void publishUserOffline(UserPresence presence) {
        PresenceEventDTO event = PresenceEventDTO.builder()
                .eventType(PresenceEventDTO.EventType.USER_OFFLINE)
                .tenantId(presence.getTenantId())
                .userId(presence.getUserId())
                .userName(presence.getUserName())
                .avatarUrl(presence.getAvatarUrl())
                .status(UserPresenceDTO.PresenceStatus.OFFLINE)
                .timestamp(Instant.now())
                .build();

        publishUserEvent(event);
    }

    public void publishStatusChanged(UserPresence presence, UserPresenceDTO.PresenceStatus oldStatus) {
        PresenceEventDTO.EventType eventType = switch (presence.getStatus()) {
            case AWAY -> PresenceEventDTO.EventType.USER_AWAY;
            case OFFLINE -> PresenceEventDTO.EventType.USER_OFFLINE;
            default -> PresenceEventDTO.EventType.USER_STATUS_CHANGED;
        };

        PresenceEventDTO event = PresenceEventDTO.builder()
                .eventType(eventType)
                .tenantId(presence.getTenantId())
                .userId(presence.getUserId())
                .userName(presence.getUserName())
                .avatarUrl(presence.getAvatarUrl())
                .status(presence.getStatus())
                .timestamp(Instant.now())
                .build();

        publishUserEvent(event);
    }

    public void publishUserJoinedDocument(DocumentParticipant participant, DocumentPresence document) {
        PresenceEventDTO event = PresenceEventDTO.builder()
                .eventType(PresenceEventDTO.EventType.USER_JOINED_DOCUMENT)
                .tenantId(participant.getTenantId())
                .userId(participant.getUserId())
                .userName(participant.getUserName())
                .avatarUrl(participant.getAvatarUrl())
                .documentId(document.getDocumentId())
                .documentName(document.getDocumentName())
                .driveId(document.getDriveId())
                .color(participant.getAssignedColor())
                .timestamp(Instant.now())
                .build();

        publishDocumentEvent(event);
    }

    public void publishUserLeftDocument(DocumentParticipant participant, DocumentPresence document) {
        PresenceEventDTO event = PresenceEventDTO.builder()
                .eventType(PresenceEventDTO.EventType.USER_LEFT_DOCUMENT)
                .tenantId(participant.getTenantId())
                .userId(participant.getUserId())
                .userName(participant.getUserName())
                .avatarUrl(participant.getAvatarUrl())
                .documentId(document.getDocumentId())
                .documentName(document.getDocumentName())
                .driveId(document.getDriveId())
                .color(participant.getAssignedColor())
                .timestamp(Instant.now())
                .build();

        publishDocumentEvent(event);
    }

    public void publishCursorMoved(DocumentParticipant participant, DocumentPresence document) {
        DocumentPresenceDTO.CursorPosition cursorPosition = null;
        DocumentPresenceDTO.SelectionRange selectionRange = null;

        if (participant.getCursorInfo() != null) {
            cursorPosition = DocumentPresenceDTO.CursorPosition.builder()
                    .line(participant.getCursorInfo().getLine())
                    .column(participant.getCursorInfo().getColumn())
                    .offset(participant.getCursorInfo().getOffset())
                    .build();

            if (participant.getCursorInfo().isHasSelection()) {
                selectionRange = DocumentPresenceDTO.SelectionRange.builder()
                        .start(DocumentPresenceDTO.CursorPosition.builder()
                                .line(participant.getCursorInfo().getSelectionStartLine())
                                .column(participant.getCursorInfo().getSelectionStartColumn())
                                .offset(participant.getCursorInfo().getSelectionStartOffset())
                                .build())
                        .end(DocumentPresenceDTO.CursorPosition.builder()
                                .line(participant.getCursorInfo().getSelectionEndLine())
                                .column(participant.getCursorInfo().getSelectionEndColumn())
                                .offset(participant.getCursorInfo().getSelectionEndOffset())
                                .build())
                        .build();
            }
        }

        PresenceEventDTO event = PresenceEventDTO.builder()
                .eventType(selectionRange != null ?
                        PresenceEventDTO.EventType.USER_SELECTION_CHANGED :
                        PresenceEventDTO.EventType.USER_CURSOR_MOVED)
                .tenantId(participant.getTenantId())
                .userId(participant.getUserId())
                .userName(participant.getUserName())
                .avatarUrl(participant.getAvatarUrl())
                .documentId(document.getDocumentId())
                .documentName(document.getDocumentName())
                .driveId(document.getDriveId())
                .color(participant.getAssignedColor())
                .editorState(participant.getEditorState())
                .cursorPosition(cursorPosition)
                .selectionRange(selectionRange)
                .timestamp(Instant.now())
                .build();

        publishDocumentEvent(event);
    }

    public void publishUserStartedEditing(DocumentParticipant participant, DocumentPresence document) {
        PresenceEventDTO event = PresenceEventDTO.builder()
                .eventType(PresenceEventDTO.EventType.USER_STARTED_EDITING)
                .tenantId(participant.getTenantId())
                .userId(participant.getUserId())
                .userName(participant.getUserName())
                .avatarUrl(participant.getAvatarUrl())
                .documentId(document.getDocumentId())
                .documentName(document.getDocumentName())
                .driveId(document.getDriveId())
                .color(participant.getAssignedColor())
                .editorState(participant.getEditorState())
                .timestamp(Instant.now())
                .build();

        publishDocumentEvent(event);
    }

    public void publishUserStoppedEditing(DocumentParticipant participant, DocumentPresence document) {
        PresenceEventDTO event = PresenceEventDTO.builder()
                .eventType(PresenceEventDTO.EventType.USER_STOPPED_EDITING)
                .tenantId(participant.getTenantId())
                .userId(participant.getUserId())
                .userName(participant.getUserName())
                .avatarUrl(participant.getAvatarUrl())
                .documentId(document.getDocumentId())
                .documentName(document.getDocumentName())
                .driveId(document.getDriveId())
                .color(participant.getAssignedColor())
                .editorState(participant.getEditorState())
                .timestamp(Instant.now())
                .build();

        publishDocumentEvent(event);
    }

    public void publishUserTyping(DocumentParticipant participant, DocumentPresence document) {
        PresenceEventDTO event = PresenceEventDTO.builder()
                .eventType(PresenceEventDTO.EventType.USER_TYPING)
                .tenantId(participant.getTenantId())
                .userId(participant.getUserId())
                .userName(participant.getUserName())
                .avatarUrl(participant.getAvatarUrl())
                .documentId(document.getDocumentId())
                .documentName(document.getDocumentName())
                .driveId(document.getDriveId())
                .color(participant.getAssignedColor())
                .editorState(DocumentPresenceDTO.EditorState.TYPING)
                .timestamp(Instant.now())
                .build();

        publishDocumentEvent(event);
    }

    private void publishUserEvent(PresenceEventDTO event) {
        // Publish to WebSocket
        if (presenceProperties.isEnableWebSocket()) {
            String destination = String.format("/topic/presence/tenant/%s", event.getTenantId());
            try {
                messagingTemplate.convertAndSend(destination, event);
                log.debug("Published user event to WebSocket: {} for user {}", event.getEventType(), event.getUserId());
            } catch (Exception e) {
                log.warn("Failed to publish user event to WebSocket: {}", e.getMessage());
            }
        }

        // Publish to Kafka
        if (presenceProperties.isPublishToKafka()) {
            try {
                kafkaTemplate.send(KAFKA_TOPIC_USER_PRESENCE, event.getUserId(), event);
                log.debug("Published user event to Kafka: {} for user {}", event.getEventType(), event.getUserId());
            } catch (Exception e) {
                log.warn("Failed to publish user event to Kafka: {}", e.getMessage());
            }
        }
    }

    private void publishDocumentEvent(PresenceEventDTO event) {
        // Publish to WebSocket - document-specific topic
        if (presenceProperties.isEnableWebSocket()) {
            String documentDestination = String.format("/topic/presence/document/%s/%s",
                    event.getTenantId(), event.getDocumentId());
            try {
                messagingTemplate.convertAndSend(documentDestination, event);
                log.debug("Published document event to WebSocket: {} for document {}",
                        event.getEventType(), event.getDocumentId());
            } catch (Exception e) {
                log.warn("Failed to publish document event to WebSocket: {}", e.getMessage());
            }
        }

        // Publish to Kafka
        if (presenceProperties.isPublishToKafka()) {
            try {
                kafkaTemplate.send(KAFKA_TOPIC_DOCUMENT_PRESENCE, event.getDocumentId(), event);
                log.debug("Published document event to Kafka: {} for document {}",
                        event.getEventType(), event.getDocumentId());
            } catch (Exception e) {
                log.warn("Failed to publish document event to Kafka: {}", e.getMessage());
            }
        }
    }
}
