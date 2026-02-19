package com.teamsync.signing.service;

import com.teamsync.common.exception.BadRequestException;
import com.teamsync.common.exception.ResourceNotFoundException;
import com.teamsync.common.exception.UnauthorizedException;
import com.teamsync.signing.dto.DeclineSigningRequest;
import com.teamsync.signing.dto.PortalSigningSessionDTO;
import com.teamsync.signing.dto.PortalSigningSessionDTO.SignatureFieldDTO;
import com.teamsync.signing.dto.SubmitSignaturesRequest;
import com.teamsync.signing.model.SignatureEvent.SignatureEventType;
import com.teamsync.signing.model.SignatureRequest;
import com.teamsync.signing.model.SignatureRequest.SignatureFieldValue;
import com.teamsync.signing.model.SignatureRequest.SignatureRequestStatus;
import com.teamsync.signing.model.SignatureRequest.Signer;
import com.teamsync.signing.model.SignatureRequest.SignerStatus;
import com.teamsync.signing.model.SigningTemplate.SignatureFieldDefinition;
import com.teamsync.signing.model.SigningTemplate.SigningOrder;
import com.teamsync.signing.repository.SignatureRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for portal signing operations (external users).
 * All operations are token-authenticated, no Zitadel JWT required.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PortalSigningService {

    private final SignatureRequestRepository requestRepository;
    private final SigningTokenService tokenService;
    private final SignatureEventService eventService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Validate signing token and return session info.
     */
    public PortalSigningSessionDTO validateToken(String token) {
        String tokenHash = tokenService.hashToken(token);

        // Find request and signer by token hash
        SignatureRequest request = requestRepository.findBySignerAccessTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired signing link"));

        Signer signer = findSignerByTokenHash(request, tokenHash);

        // Validate request status
        validateRequestForSigning(request, signer);

        return buildSessionDTO(request, signer, false);
    }

    /**
     * Record that document was viewed.
     */
    @Transactional
    public void recordDocumentView(String token, String ipAddress, String userAgent) {
        String tokenHash = tokenService.hashToken(token);

        SignatureRequest request = requestRepository.findBySignerAccessTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired signing link"));

        Signer signer = findSignerByTokenHash(request, tokenHash);

        // Only record first view
        if (signer.getViewedAt() == null) {
            signer.setViewedAt(Instant.now());
            signer.setStatus(SignerStatus.VIEWED);
            request.setUpdatedAt(Instant.now());

            // Update request status if first activity
            if (request.getStatus() == SignatureRequestStatus.PENDING) {
                request.setStatus(SignatureRequestStatus.IN_PROGRESS);
            }

            requestRepository.save(request);

            // Log event
            eventService.logSignerEvent(
                    request.getTenantId(), request.getId(),
                    SignatureEventType.DOCUMENT_VIEWED,
                    signer.getId(), signer.getEmail(), signer.getName(),
                    "Document viewed by signer",
                    ipAddress, userAgent, null);

            log.info("Document viewed: request={}, signer={}", request.getId(), signer.getEmail());
        }
    }

    /**
     * Get document for viewing (returns presigned URL).
     */
    public String getDocumentUrl(String token) {
        String tokenHash = tokenService.hashToken(token);

        SignatureRequest request = requestRepository.findBySignerAccessTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired signing link"));

        Signer signer = findSignerByTokenHash(request, tokenHash);
        validateRequestForSigning(request, signer);

        // Generate download token for document viewing
        String downloadToken = tokenService.generateDownloadToken(
                request.getId(), signer.getId(),
                request.getDocumentBucket(), request.getDocumentStorageKey());

        return downloadToken;
    }

    /**
     * Submit signatures for the document.
     */
    @Transactional
    public PortalSigningSessionDTO submitSignatures(String token, SubmitSignaturesRequest submitRequest,
                                                     String ipAddress, String userAgent) {
        String tokenHash = tokenService.hashToken(token);

        SignatureRequest request = requestRepository.findBySignerAccessTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired signing link"));

        Signer signer = findSignerByTokenHash(request, tokenHash);
        validateRequestForSigning(request, signer);

        if (signer.getStatus() == SignerStatus.SIGNED) {
            throw new BadRequestException("You have already signed this document");
        }

        // Validate all required fields are provided
        validateRequiredFields(request, signer, submitRequest);

        // Store field values
        List<SignatureFieldValue> fieldValues = request.getFieldValues();
        if (fieldValues == null) {
            fieldValues = new ArrayList<>();
        }

        for (SubmitSignaturesRequest.FieldValueRequest fv : submitRequest.getFieldValues()) {
            SignatureFieldValue fieldValue = SignatureFieldValue.builder()
                    .fieldId(fv.getFieldId())
                    .signerId(signer.getId())
                    .signerEmail(signer.getEmail())
                    .value(fv.getValue())
                    .type(fv.getType())
                    .signatureMethod(fv.getSignatureMethod())
                    .appliedAt(Instant.now())
                    .build();

            fieldValues.add(fieldValue);
        }

        request.setFieldValues(fieldValues);

        // Update signer status
        signer.setStatus(SignerStatus.SIGNED);
        signer.setSignedAt(Instant.now());

        // Log event for each signature
        for (SubmitSignaturesRequest.FieldValueRequest fv : submitRequest.getFieldValues()) {
            eventService.logSignerEvent(
                    request.getTenantId(), request.getId(),
                    SignatureEventType.SIGNATURE_APPLIED,
                    signer.getId(), signer.getEmail(), signer.getName(),
                    "Signature applied to field: " + fv.getFieldId(),
                    ipAddress, userAgent,
                    Map.of("fieldId", fv.getFieldId(), "fieldType", fv.getType().name()));
        }

        // Check if all signers have completed
        boolean allSigned = request.getSigners().stream()
                .allMatch(s -> s.getStatus() == SignerStatus.SIGNED);

        if (allSigned) {
            request.setStatus(SignatureRequestStatus.COMPLETED);
            request.setCompletedAt(Instant.now());

            // Log completion event
            eventService.logSystemEvent(
                    request.getTenantId(), request.getId(),
                    SignatureEventType.ALL_SIGNATURES_COLLECTED,
                    "All signatures collected",
                    null);

            // Publish completion event
            publishCompletionEvent(request);

            log.info("All signatures collected for request: {}", request.getId());
        } else if (request.getSigningOrder() == SigningOrder.SEQUENTIAL) {
            // Notify next signer in sequence
            notifyNextSigner(request, signer);
        }

        request.setUpdatedAt(Instant.now());
        SignatureRequest saved = requestRepository.save(request);

        log.info("Signatures submitted: request={}, signer={}", request.getId(), signer.getEmail());

        return buildSessionDTO(saved, signer, true);
    }

    /**
     * Decline to sign the document.
     */
    @Transactional
    public void declineSigning(String token, DeclineSigningRequest declineRequest,
                               String ipAddress, String userAgent) {
        String tokenHash = tokenService.hashToken(token);

        SignatureRequest request = requestRepository.findBySignerAccessTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired signing link"));

        Signer signer = findSignerByTokenHash(request, tokenHash);

        if (signer.getStatus() == SignerStatus.SIGNED) {
            throw new BadRequestException("You have already signed this document");
        }

        if (signer.getStatus() == SignerStatus.DECLINED) {
            throw new BadRequestException("You have already declined this document");
        }

        // Update signer status
        signer.setStatus(SignerStatus.DECLINED);
        signer.setDeclinedAt(Instant.now());
        signer.setDeclineReason(declineRequest.getReason());

        // Update request status
        request.setStatus(SignatureRequestStatus.DECLINED);
        request.setUpdatedAt(Instant.now());

        requestRepository.save(request);

        // Log event
        eventService.logSignerEvent(
                request.getTenantId(), request.getId(),
                SignatureEventType.SIGNER_DECLINED,
                signer.getId(), signer.getEmail(), signer.getName(),
                "Signer declined to sign: " + declineRequest.getReason(),
                ipAddress, userAgent,
                Map.of("reason", declineRequest.getReason()));

        // Publish decline event
        publishDeclineEvent(request, signer, declineRequest.getReason());

        log.info("Signing declined: request={}, signer={}, reason={}",
                request.getId(), signer.getEmail(), declineRequest.getReason());
    }

    /**
     * Get download URL for signed document (post-completion only).
     */
    public String getSignedDocumentUrl(String token) {
        String tokenHash = tokenService.hashToken(token);

        SignatureRequest request = requestRepository.findBySignerAccessTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired signing link"));

        Signer signer = findSignerByTokenHash(request, tokenHash);

        if (request.getStatus() != SignatureRequestStatus.COMPLETED) {
            throw new BadRequestException("Document is not yet fully signed");
        }

        // Use signed document if available, otherwise original
        String storageKey = request.getSignedDocumentStorageKey() != null
                ? request.getSignedDocumentStorageKey()
                : request.getDocumentStorageKey();

        // Generate download token
        String downloadToken = tokenService.generateDownloadToken(
                request.getId(), signer.getId(),
                request.getDocumentBucket(), storageKey);

        return downloadToken;
    }

    // ========================================
    // Private Helper Methods
    // ========================================

    private Signer findSignerByTokenHash(SignatureRequest request, String tokenHash) {
        return request.getSigners().stream()
                .filter(s -> tokenHash.equals(s.getAccessTokenHash()))
                .findFirst()
                .orElseThrow(() -> new UnauthorizedException("Invalid signing link"));
    }

    private void validateRequestForSigning(SignatureRequest request, Signer signer) {
        // Check request status
        if (request.getStatus() == SignatureRequestStatus.VOIDED) {
            throw new BadRequestException("This signing request has been cancelled");
        }

        if (request.getStatus() == SignatureRequestStatus.EXPIRED) {
            throw new BadRequestException("This signing request has expired");
        }

        if (request.getStatus() == SignatureRequestStatus.DECLINED) {
            throw new BadRequestException("This signing request has been declined");
        }

        // Check expiration
        if (request.getExpiresAt() != null && request.getExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("This signing request has expired");
        }

        // For sequential signing, check if it's this signer's turn
        if (request.getSigningOrder() == SigningOrder.SEQUENTIAL &&
                request.getStatus() != SignatureRequestStatus.DRAFT) {

            // Find signers who haven't signed yet, ordered by signing order
            List<Signer> pendingSigners = request.getSigners().stream()
                    .filter(s -> s.getStatus() != SignerStatus.SIGNED)
                    .sorted((a, b) -> a.getOrder().compareTo(b.getOrder()))
                    .toList();

            if (!pendingSigners.isEmpty() && !pendingSigners.get(0).getId().equals(signer.getId())) {
                throw new BadRequestException("It's not your turn to sign yet. Please wait for previous signers.");
            }
        }
    }

    private void validateRequiredFields(SignatureRequest request, Signer signer,
                                         SubmitSignaturesRequest submitRequest) {
        // Get required fields for this signer
        Set<String> requiredFieldIds = getFieldsForSigner(request, signer).stream()
                .filter(f -> Boolean.TRUE.equals(f.getRequired()))
                .map(SignatureFieldDefinition::getId)
                .collect(Collectors.toSet());

        // Get submitted field IDs
        Set<String> submittedFieldIds = submitRequest.getFieldValues().stream()
                .map(SubmitSignaturesRequest.FieldValueRequest::getFieldId)
                .collect(Collectors.toSet());

        // Check all required fields are submitted
        Set<String> missingFields = requiredFieldIds.stream()
                .filter(id -> !submittedFieldIds.contains(id))
                .collect(Collectors.toSet());

        if (!missingFields.isEmpty()) {
            throw new BadRequestException("Missing required fields: " + String.join(", ", missingFields));
        }
    }

    private List<SignatureFieldDefinition> getFieldsForSigner(SignatureRequest request, Signer signer) {
        if (request.getFieldDefinitions() == null) {
            return List.of();
        }

        int signerIndex = request.getSigners().indexOf(signer);

        return request.getFieldDefinitions().stream()
                .filter(f -> f.getSignerIndex() == null || f.getSignerIndex() == signerIndex)
                .toList();
    }

    private PortalSigningSessionDTO buildSessionDTO(SignatureRequest request, Signer signer,
                                                     boolean includeDownload) {
        List<SignatureFieldDefinition> signerFields = getFieldsForSigner(request, signer);

        // Get completed field IDs for this signer
        Set<String> completedFieldIds = request.getFieldValues() != null
                ? request.getFieldValues().stream()
                        .filter(fv -> fv.getSignerId().equals(signer.getId()))
                        .map(SignatureFieldValue::getFieldId)
                        .collect(Collectors.toSet())
                : Set.of();

        List<SignatureFieldDTO> fieldDTOs = signerFields.stream()
                .map(f -> SignatureFieldDTO.builder()
                        .id(f.getId())
                        .name(f.getName())
                        .type(f.getType())
                        .pageNumber(f.getPageNumber())
                        .xPosition(f.getXPosition())
                        .yPosition(f.getYPosition())
                        .width(f.getWidth())
                        .height(f.getHeight())
                        .required(f.getRequired())
                        .placeholder(f.getPlaceholder())
                        .completed(completedFieldIds.contains(f.getId()))
                        .build())
                .toList();

        boolean canDownload = includeDownload &&
                request.getStatus() == SignatureRequestStatus.COMPLETED &&
                signer.getStatus() == SignerStatus.SIGNED;

        String downloadToken = null;
        if (canDownload) {
            String storageKey = request.getSignedDocumentStorageKey() != null
                    ? request.getSignedDocumentStorageKey()
                    : request.getDocumentStorageKey();

            downloadToken = tokenService.generateDownloadToken(
                    request.getId(), signer.getId(),
                    request.getDocumentBucket(), storageKey);
        }

        return PortalSigningSessionDTO.builder()
                .requestId(request.getId())
                .signerId(signer.getId())
                .documentName(request.getDocumentName())
                .pageCount(request.getPageCount())
                .documentSize(request.getDocumentSize())
                .senderName(request.getSenderName())
                .senderEmail(request.getSenderEmail())
                .subject(request.getSubject())
                .message(request.getMessage())
                .expiresAt(request.getExpiresAt())
                .signerEmail(signer.getEmail())
                .signerName(signer.getName())
                .alreadySigned(signer.getStatus() == SignerStatus.SIGNED)
                .fields(fieldDTOs)
                .totalFields(fieldDTOs.size())
                .completedFields((int) completedFieldIds.size())
                .canDownload(canDownload)
                .downloadToken(downloadToken)
                .build();
    }

    private void notifyNextSigner(SignatureRequest request, Signer completedSigner) {
        // Find next signer in sequence
        request.getSigners().stream()
                .filter(s -> s.getStatus() == SignerStatus.PENDING)
                .filter(s -> s.getOrder() > completedSigner.getOrder())
                .min((a, b) -> a.getOrder().compareTo(b.getOrder()))
                .ifPresent(nextSigner -> {
                    // Generate new token for next signer
                    String signingToken = tokenService.generateSigningToken();
                    String tokenHash = tokenService.hashToken(signingToken);
                    nextSigner.setAccessTokenHash(tokenHash);

                    Map<String, Object> notification = Map.of(
                            "type", "SIGNING_YOUR_TURN",
                            "tenantId", request.getTenantId(),
                            "requestId", request.getId(),
                            "signerId", nextSigner.getId(),
                            "signerEmail", nextSigner.getEmail(),
                            "signerName", nextSigner.getName() != null ? nextSigner.getName() : nextSigner.getEmail(),
                            "senderName", request.getSenderName(),
                            "documentName", request.getDocumentName(),
                            "signingToken", signingToken,
                            "expiresAt", request.getExpiresAt().toString()
                    );

                    kafkaTemplate.send("teamsync.signing.request.sent", notification);

                    log.info("Notified next signer: {} for request: {}",
                            nextSigner.getEmail(), request.getId());
                });
    }

    private void publishCompletionEvent(SignatureRequest request) {
        Map<String, Object> event = Map.of(
                "type", "SIGNING_COMPLETED",
                "tenantId", request.getTenantId(),
                "requestId", request.getId(),
                "documentName", request.getDocumentName(),
                "senderEmail", request.getSenderEmail(),
                "senderName", request.getSenderName(),
                "completedAt", request.getCompletedAt().toString(),
                "signerEmails", request.getSigners().stream()
                        .map(Signer::getEmail)
                        .toList()
        );

        kafkaTemplate.send("teamsync.signing.completed", event);
    }

    private void publishDeclineEvent(SignatureRequest request, Signer signer, String reason) {
        Map<String, Object> event = Map.of(
                "type", "SIGNING_DECLINED",
                "tenantId", request.getTenantId(),
                "requestId", request.getId(),
                "documentName", request.getDocumentName(),
                "senderEmail", request.getSenderEmail(),
                "senderName", request.getSenderName(),
                "declinedBy", signer.getEmail(),
                "declinedByName", signer.getName() != null ? signer.getName() : signer.getEmail(),
                "reason", reason
        );

        kafkaTemplate.send("teamsync.signing.declined", event);
    }
}
