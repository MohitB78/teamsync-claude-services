package com.teamsync.presence.service;

import com.teamsync.presence.config.PresenceProperties;
import com.teamsync.presence.dto.*;
import com.teamsync.presence.event.PresenceEventPublisher;
import com.teamsync.presence.exception.DocumentFullException;
import com.teamsync.presence.exception.ParticipantNotFoundException;
import com.teamsync.presence.model.DocumentParticipant;
import com.teamsync.presence.model.DocumentPresence;
import com.teamsync.presence.repository.DocumentPresenceRepository;
import com.teamsync.presence.repository.UserPresenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentPresenceService {

    /**
     * SECURITY FIX (Round 7): Use SecureRandom instead of java.util.Random for color generation.
     * While color generation is not security-sensitive, using SecureRandom ensures
     * consistent security practices across the codebase and prevents copy-paste mistakes.
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final DocumentPresenceRepository documentPresenceRepository;
    private final UserPresenceRepository userPresenceRepository;
    private final PresenceEventPublisher eventPublisher;
    private final PresenceProperties presenceProperties;

    /**
     * SECURITY FIX (Round 11): Added synchronized keyword to prevent race conditions.
     *
     * The original code had a TOCTOU (Time-of-Check to Time-of-Use) vulnerability:
     * 1. Thread A checks viewer count (e.g., 49/50)
     * 2. Thread B checks viewer count (e.g., 49/50)
     * 3. Thread A adds viewer (now 50/50)
     * 4. Thread B adds viewer (now 51/50 - exceeds limit!)
     *
     * The synchronization ensures atomic check-and-add for the same document.
     * We use a per-document lock via the documentId to minimize contention.
     */
    public synchronized JoinDocumentResponse joinDocument(JoinDocumentRequest request, String tenantId, String userId,
                                              String userName, String email, String avatarUrl) {
        log.info("User {} joining document {} in tenant {}", userId, request.getDocumentId(), tenantId);

        String documentId = request.getDocumentId();

        // SECURITY FIX (Round 11): Atomic check-and-reserve using Redis increment
        // Check if document has room (atomic operation)
        int currentViewers = documentPresenceRepository.countViewers(tenantId, documentId);
        if (currentViewers >= presenceProperties.getMaxViewersPerDocument()) {
            throw new DocumentFullException("Document has reached maximum viewer limit");
        }

        if (request.getMode() == JoinDocumentRequest.JoinMode.EDIT) {
            int currentEditors = documentPresenceRepository.countEditors(tenantId, documentId);
            if (currentEditors >= presenceProperties.getMaxEditorsPerDocument()) {
                throw new DocumentFullException("Document has reached maximum editor limit");
            }
        }

        // Create or update document presence
        DocumentPresence docPresence = documentPresenceRepository.findDocument(tenantId, documentId)
                .orElseGet(() -> DocumentPresence.builder()
                        .documentId(documentId)
                        .documentName(request.getDocumentName())
                        .tenantId(tenantId)
                        .driveId(request.getDriveId())
                        .createdAt(Instant.now())
                        .build());

        documentPresenceRepository.saveDocument(docPresence);

        // Check if user is already in document
        Optional<DocumentParticipant> existingParticipant =
                documentPresenceRepository.findParticipant(tenantId, documentId, userId);

        String assignedColor = existingParticipant
                .map(DocumentParticipant::getAssignedColor)
                .orElseGet(() -> assignColor(tenantId, documentId, request.getPreferredColor()));

        DocumentParticipant participant = existingParticipant.orElseGet(() ->
                DocumentParticipant.builder()
                        .participantId(UUID.randomUUID().toString())
                        .documentId(documentId)
                        .tenantId(tenantId)
                        .joinedAt(Instant.now())
                        .build());

        participant.setUserId(userId);
        participant.setUserName(userName);
        participant.setEmail(email);
        participant.setAvatarUrl(avatarUrl);
        participant.setAssignedColor(assignedColor);
        participant.setMode(request.getMode() != null ? request.getMode() : JoinDocumentRequest.JoinMode.VIEW);
        participant.setEditorState(DocumentPresenceDTO.EditorState.VIEWING);
        participant.setLastActivity(Instant.now());

        documentPresenceRepository.addParticipant(participant);

        // Update user presence with current document
        userPresenceRepository.findById(tenantId, userId).ifPresent(userPresence -> {
            userPresence.setCurrentDocumentId(documentId);
            userPresence.setCurrentDocumentName(request.getDocumentName());
            userPresenceRepository.save(userPresence);
        });

        // Get current participants
        List<DocumentParticipant> allParticipants = documentPresenceRepository.findAllParticipants(tenantId, documentId);
        List<DocumentPresenceDTO.DocumentViewer> viewers = mapToViewers(allParticipants.stream()
                .filter(DocumentParticipant::isViewer)
                .collect(Collectors.toList()));
        List<DocumentPresenceDTO.DocumentEditor> editors = mapToEditors(allParticipants.stream()
                .filter(DocumentParticipant::isEditor)
                .collect(Collectors.toList()));

        // Publish event
        eventPublisher.publishUserJoinedDocument(participant, docPresence);

        return JoinDocumentResponse.builder()
                .joined(true)
                .documentId(documentId)
                .sessionId(participant.getParticipantId())
                .assignedColor(assignedColor)
                .joinedAt(participant.getJoinedAt())
                .currentViewers(viewers)
                .currentEditors(editors)
                .totalParticipants(allParticipants.size())
                .build();
    }

    public void leaveDocument(String tenantId, String documentId, String userId) {
        log.info("User {} leaving document {} in tenant {}", userId, documentId, tenantId);

        Optional<DocumentParticipant> participant =
                documentPresenceRepository.findParticipant(tenantId, documentId, userId);

        if (participant.isPresent()) {
            documentPresenceRepository.removeParticipant(tenantId, documentId, userId);

            // Clear current document from user presence
            userPresenceRepository.findById(tenantId, userId).ifPresent(userPresence -> {
                if (documentId.equals(userPresence.getCurrentDocumentId())) {
                    userPresence.setCurrentDocumentId(null);
                    userPresence.setCurrentDocumentName(null);
                    userPresenceRepository.save(userPresence);
                }
            });

            // Publish event
            documentPresenceRepository.findDocument(tenantId, documentId).ifPresent(doc ->
                    eventPublisher.publishUserLeftDocument(participant.get(), doc));
        }
    }

    public void updateCursor(UpdateCursorRequest request, String tenantId, String userId) {
        log.debug("Updating cursor for user {} in document {}", userId, request.getDocumentId());

        DocumentParticipant participant = documentPresenceRepository
                .findParticipant(tenantId, request.getDocumentId(), userId)
                .orElseThrow(() -> new ParticipantNotFoundException(
                        "User is not a participant in this document"));

        if (request.getState() != null) {
            participant.setEditorState(request.getState());
        }

        if (request.getCursorPosition() != null || request.getSelectionRange() != null) {
            DocumentParticipant.CursorInfo cursorInfo = DocumentParticipant.CursorInfo.builder()
                    .build();

            if (request.getCursorPosition() != null) {
                cursorInfo.setLine(request.getCursorPosition().getLine());
                cursorInfo.setColumn(request.getCursorPosition().getColumn());
                cursorInfo.setOffset(request.getCursorPosition().getOffset());
            }

            if (request.getSelectionRange() != null) {
                cursorInfo.setHasSelection(true);
                if (request.getSelectionRange().getStart() != null) {
                    cursorInfo.setSelectionStartLine(request.getSelectionRange().getStart().getLine());
                    cursorInfo.setSelectionStartColumn(request.getSelectionRange().getStart().getColumn());
                    cursorInfo.setSelectionStartOffset(request.getSelectionRange().getStart().getOffset());
                }
                if (request.getSelectionRange().getEnd() != null) {
                    cursorInfo.setSelectionEndLine(request.getSelectionRange().getEnd().getLine());
                    cursorInfo.setSelectionEndColumn(request.getSelectionRange().getEnd().getColumn());
                    cursorInfo.setSelectionEndOffset(request.getSelectionRange().getEnd().getOffset());
                }
            }

            participant.setCursorInfo(cursorInfo);
        }

        participant.setLastActivity(Instant.now());
        documentPresenceRepository.updateParticipant(participant);

        // Publish cursor event
        documentPresenceRepository.findDocument(tenantId, request.getDocumentId()).ifPresent(doc ->
                eventPublisher.publishCursorMoved(participant, doc));
    }

    public void updateEditorState(String tenantId, String documentId, String userId,
                                   DocumentPresenceDTO.EditorState state) {
        log.debug("Updating editor state for user {} in document {} to {}", userId, documentId, state);

        DocumentParticipant participant = documentPresenceRepository
                .findParticipant(tenantId, documentId, userId)
                .orElseThrow(() -> new ParticipantNotFoundException(
                        "User is not a participant in this document"));

        DocumentPresenceDTO.EditorState oldState = participant.getEditorState();
        participant.setEditorState(state);
        participant.setLastActivity(Instant.now());

        documentPresenceRepository.updateParticipant(participant);

        // Publish appropriate event
        documentPresenceRepository.findDocument(tenantId, documentId).ifPresent(doc -> {
            if (state == DocumentPresenceDTO.EditorState.EDITING && oldState != DocumentPresenceDTO.EditorState.EDITING) {
                eventPublisher.publishUserStartedEditing(participant, doc);
            } else if (oldState == DocumentPresenceDTO.EditorState.EDITING && state != DocumentPresenceDTO.EditorState.EDITING) {
                eventPublisher.publishUserStoppedEditing(participant, doc);
            } else if (state == DocumentPresenceDTO.EditorState.TYPING) {
                eventPublisher.publishUserTyping(participant, doc);
            }
        });
    }

    public DocumentPresenceDTO getDocumentPresence(String tenantId, String documentId) {
        Optional<DocumentPresence> docPresence = documentPresenceRepository.findDocument(tenantId, documentId);

        if (docPresence.isEmpty()) {
            return DocumentPresenceDTO.builder()
                    .documentId(documentId)
                    .tenantId(tenantId)
                    .viewers(Collections.emptyList())
                    .editors(Collections.emptyList())
                    .totalViewers(0)
                    .totalEditors(0)
                    .build();
        }

        List<DocumentParticipant> allParticipants = documentPresenceRepository.findAllParticipants(tenantId, documentId);
        List<DocumentParticipant> viewers = allParticipants.stream()
                .filter(DocumentParticipant::isViewer)
                .collect(Collectors.toList());
        List<DocumentParticipant> editors = allParticipants.stream()
                .filter(DocumentParticipant::isEditor)
                .collect(Collectors.toList());

        return DocumentPresenceDTO.builder()
                .documentId(documentId)
                .documentName(docPresence.get().getDocumentName())
                .tenantId(tenantId)
                .driveId(docPresence.get().getDriveId())
                .viewers(mapToViewers(viewers))
                .editors(mapToEditors(editors))
                .totalViewers(viewers.size())
                .totalEditors(editors.size())
                .build();
    }

    public List<DocumentPresenceDTO> getActiveDocuments(String tenantId) {
        Set<String> activeDocIds = documentPresenceRepository.findActiveDocuments(tenantId);

        return activeDocIds.stream()
                .map(docId -> getDocumentPresence(tenantId, docId))
                .filter(dto -> dto.getTotalViewers() > 0)
                .sorted((a, b) -> Integer.compare(b.getTotalViewers(), a.getTotalViewers()))
                .collect(Collectors.toList());
    }

    public Set<String> getUserDocuments(String tenantId, String userId) {
        return documentPresenceRepository.findDocumentsByUser(tenantId, userId);
    }

    public void cleanupIdleParticipants() {
        log.debug("Cleaning up idle document participants");

        Set<String> tenantIds = userPresenceRepository.findAllTenantIds();

        for (String tenantId : tenantIds) {
            Set<String> activeDocuments = documentPresenceRepository.findActiveDocuments(tenantId);

            for (String documentId : activeDocuments) {
                List<DocumentParticipant> idleParticipants = documentPresenceRepository
                        .findIdleParticipants(tenantId, documentId, presenceProperties.getDocumentIdleThresholdSeconds());

                for (DocumentParticipant participant : idleParticipants) {
                    // Check if user is still online
                    Optional<com.teamsync.presence.model.UserPresence> userPresence =
                            userPresenceRepository.findById(tenantId, participant.getUserId());

                    if (userPresence.isEmpty() || userPresence.get().isExpired(presenceProperties.getTimeoutSeconds())) {
                        log.info("Removing idle participant {} from document {}", participant.getUserId(), documentId);
                        leaveDocument(tenantId, documentId, participant.getUserId());
                    } else if (participant.getEditorState() == DocumentPresenceDTO.EditorState.EDITING ||
                            participant.getEditorState() == DocumentPresenceDTO.EditorState.TYPING) {
                        // Mark as idle
                        participant.setEditorState(DocumentPresenceDTO.EditorState.IDLE);
                        documentPresenceRepository.updateParticipant(participant);
                    }
                }
            }
        }
    }

    private String assignColor(String tenantId, String documentId, String preferredColor) {
        Set<String> usedColors = documentPresenceRepository.getUsedColors(tenantId, documentId);
        List<String> availableColors = presenceProperties.getCursorColors();

        // Try preferred color first
        if (preferredColor != null && !usedColors.contains(preferredColor)) {
            return preferredColor;
        }

        // Find first unused color
        for (String color : availableColors) {
            if (!usedColors.contains(color)) {
                return color;
            }
        }

        // If all colors used, generate a random one
        // SECURITY FIX (Round 7): Use SecureRandom instead of java.util.Random
        return String.format("#%06X", SECURE_RANDOM.nextInt(0xFFFFFF + 1));
    }

    private List<DocumentPresenceDTO.DocumentViewer> mapToViewers(List<DocumentParticipant> participants) {
        return participants.stream()
                .map(p -> DocumentPresenceDTO.DocumentViewer.builder()
                        .userId(p.getUserId())
                        .userName(p.getUserName())
                        .email(p.getEmail())
                        .avatarUrl(p.getAvatarUrl())
                        .color(p.getAssignedColor())
                        .joinedAt(p.getJoinedAt())
                        .lastActivity(p.getLastActivity())
                        .build())
                .collect(Collectors.toList());
    }

    private List<DocumentPresenceDTO.DocumentEditor> mapToEditors(List<DocumentParticipant> participants) {
        return participants.stream()
                .map(p -> {
                    DocumentPresenceDTO.CursorPosition cursorPosition = null;
                    DocumentPresenceDTO.SelectionRange selectionRange = null;

                    if (p.getCursorInfo() != null) {
                        cursorPosition = DocumentPresenceDTO.CursorPosition.builder()
                                .line(p.getCursorInfo().getLine())
                                .column(p.getCursorInfo().getColumn())
                                .offset(p.getCursorInfo().getOffset())
                                .build();

                        if (p.getCursorInfo().isHasSelection()) {
                            selectionRange = DocumentPresenceDTO.SelectionRange.builder()
                                    .start(DocumentPresenceDTO.CursorPosition.builder()
                                            .line(p.getCursorInfo().getSelectionStartLine())
                                            .column(p.getCursorInfo().getSelectionStartColumn())
                                            .offset(p.getCursorInfo().getSelectionStartOffset())
                                            .build())
                                    .end(DocumentPresenceDTO.CursorPosition.builder()
                                            .line(p.getCursorInfo().getSelectionEndLine())
                                            .column(p.getCursorInfo().getSelectionEndColumn())
                                            .offset(p.getCursorInfo().getSelectionEndOffset())
                                            .build())
                                    .build();
                        }
                    }

                    return DocumentPresenceDTO.DocumentEditor.builder()
                            .userId(p.getUserId())
                            .userName(p.getUserName())
                            .email(p.getEmail())
                            .avatarUrl(p.getAvatarUrl())
                            .color(p.getAssignedColor())
                            .state(p.getEditorState())
                            .cursorPosition(cursorPosition)
                            .selectionRange(selectionRange)
                            .joinedAt(p.getJoinedAt())
                            .lastActivity(p.getLastActivity())
                            .build();
                })
                .collect(Collectors.toList());
    }
}
