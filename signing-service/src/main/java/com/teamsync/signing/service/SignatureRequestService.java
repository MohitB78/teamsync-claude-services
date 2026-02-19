package com.teamsync.signing.service;

import com.teamsync.common.exception.BadRequestException;
import com.teamsync.common.exception.ResourceNotFoundException;
import com.teamsync.signing.dto.CreateSignatureRequestRequest;
import com.teamsync.signing.dto.SendRequestRequest;
import com.teamsync.signing.dto.SignatureRequestDTO;
import com.teamsync.signing.model.SignatureEvent.SignatureEventType;
import com.teamsync.signing.model.SignatureRequest;
import com.teamsync.signing.model.SignatureRequest.SignatureRequestStatus;
import com.teamsync.signing.model.SignatureRequest.Signer;
import com.teamsync.signing.model.SignatureRequest.SignerStatus;
import com.teamsync.signing.model.SigningTemplate;
import com.teamsync.signing.model.SigningTemplate.SigningOrder;
import com.teamsync.signing.model.SigningTemplate.TemplateStatus;
import com.teamsync.signing.repository.SignatureRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for managing signature requests.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SignatureRequestService {

    private final SignatureRequestRepository requestRepository;
    private final SigningTemplateService templateService;
    private final SigningTokenService tokenService;
    private final SignatureEventService eventService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Create a new signature request.
     */
    @Transactional
    public SignatureRequestDTO createRequest(String tenantId, String userId, String userName,
                                             String userEmail, CreateSignatureRequestRequest request) {
        // Get and validate template
        SigningTemplate template = templateService.getTemplateEntity(tenantId, request.getTemplateId());

        if (template.getStatus() != TemplateStatus.ACTIVE) {
            throw new BadRequestException("Template is not active");
        }

        // Determine signing order
        SigningOrder signingOrder = request.getSigningOrder() != null
                ? request.getSigningOrder()
                : template.getWorkflowConfig() != null
                        ? template.getWorkflowConfig().getSigningOrder()
                        : SigningOrder.PARALLEL;

        // Determine expiration days
        int expirationDays = request.getExpirationDays() != null
                ? request.getExpirationDays()
                : template.getExpirationDays() != null
                        ? template.getExpirationDays()
                        : 7;

        // Create signers with tokens
        List<Signer> signers = createSigners(request.getSigners(), signingOrder);

        SignatureRequest sigRequest = SignatureRequest.builder()
                .tenantId(tenantId)
                .driveId(request.getDriveId())
                .templateId(template.getId())
                .templateName(template.getName())
                // Document will be set when copying from template or using existing
                .documentStorageKey(template.getBaseDocumentStorageKey())
                .documentBucket(template.getBaseDocumentBucket())
                .documentName(template.getBaseDocumentName())
                .documentSize(template.getBaseDocumentSize())
                .pageCount(template.getBaseDocumentPageCount())
                .senderId(userId)
                .senderName(userName)
                .senderEmail(userEmail)
                .subject(request.getSubject())
                .message(request.getMessage())
                .signers(signers)
                .fieldDefinitions(template.getFieldDefinitions())
                .signingOrder(signingOrder)
                .requireAllSignatures(template.getRequireAllSignatures() != null
                        ? template.getRequireAllSignatures() : true)
                .status(SignatureRequestStatus.DRAFT)
                .expiresAt(Instant.now().plus(expirationDays, ChronoUnit.DAYS))
                .createdAt(Instant.now())
                .build();

        SignatureRequest saved = requestRepository.save(sigRequest);

        // Log event
        eventService.logRequestEvent(
                tenantId, saved.getId(),
                SignatureEventType.REQUEST_CREATED,
                userId, userEmail, userName,
                "Signature request created",
                null, null);

        log.info("Created signature request: {} for tenant: {}", saved.getId(), tenantId);

        return SignatureRequestDTO.fromEntity(saved);
    }

    /**
     * Get a signature request by ID.
     */
    public SignatureRequestDTO getRequest(String tenantId, String requestId) {
        SignatureRequest request = requestRepository.findByIdAndTenantId(requestId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Signature request not found"));
        return SignatureRequestDTO.fromEntity(request);
    }

    /**
     * Get request entity (for internal use).
     */
    public SignatureRequest getRequestEntity(String tenantId, String requestId) {
        return requestRepository.findByIdAndTenantId(requestId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Signature request not found"));
    }

    /**
     * List requests for a sender.
     */
    public Page<SignatureRequestDTO> listRequestsBySender(String tenantId, String senderId,
                                                           Pageable pageable) {
        return requestRepository.findByTenantIdAndSenderId(tenantId, senderId, pageable)
                .map(SignatureRequestDTO::fromEntity);
    }

    /**
     * List requests by status.
     */
    public Page<SignatureRequestDTO> listRequestsByStatus(String tenantId,
                                                           SignatureRequestStatus status,
                                                           Pageable pageable) {
        return requestRepository.findByTenantIdAndStatus(tenantId, status, pageable)
                .map(SignatureRequestDTO::fromEntity);
    }

    /**
     * Send a signature request to signers.
     */
    @Transactional
    public SignatureRequestDTO sendRequest(String tenantId, String requestId, String userId,
                                           String userEmail, String userName,
                                           SendRequestRequest sendRequest, String ipAddress) {
        SignatureRequest request = requestRepository.findByIdAndTenantId(requestId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Signature request not found"));

        if (request.getStatus() != SignatureRequestStatus.DRAFT) {
            throw new BadRequestException("Request has already been sent");
        }

        // Update request status
        request.setStatus(SignatureRequestStatus.PENDING);
        request.setSentAt(Instant.now());
        request.setUpdatedAt(Instant.now());

        // For sequential signing, only first signer is notified initially
        List<Signer> signersToNotify = request.getSigningOrder() == SigningOrder.SEQUENTIAL
                ? List.of(request.getSigners().get(0))
                : request.getSigners();

        // Generate signing URLs and send notifications
        for (Signer signer : signersToNotify) {
            sendSigningNotification(request, signer, sendRequest);
        }

        SignatureRequest saved = requestRepository.save(request);

        // Log event
        eventService.logRequestEvent(
                tenantId, saved.getId(),
                SignatureEventType.REQUEST_SENT,
                userId, userEmail, userName,
                "Signature request sent to " + signersToNotify.size() + " signer(s)",
                ipAddress, null);

        log.info("Sent signature request: {} to {} signers", requestId, signersToNotify.size());

        return SignatureRequestDTO.fromEntity(saved);
    }

    /**
     * Void (cancel) a signature request.
     */
    @Transactional
    public SignatureRequestDTO voidRequest(String tenantId, String requestId, String userId,
                                           String userEmail, String userName,
                                           String reason, String ipAddress) {
        SignatureRequest request = requestRepository.findByIdAndTenantId(requestId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Signature request not found"));

        if (request.getStatus() == SignatureRequestStatus.COMPLETED ||
                request.getStatus() == SignatureRequestStatus.VOIDED) {
            throw new BadRequestException("Cannot void a completed or already voided request");
        }

        request.setStatus(SignatureRequestStatus.VOIDED);
        request.setVoidedAt(Instant.now());
        request.setVoidReason(reason);
        request.setUpdatedAt(Instant.now());

        SignatureRequest saved = requestRepository.save(request);

        // Log event
        eventService.logRequestEvent(
                tenantId, saved.getId(),
                SignatureEventType.REQUEST_VOIDED,
                userId, userEmail, userName,
                "Signature request voided: " + reason,
                ipAddress, null);

        // Notify signers that request was voided
        publishVoidNotification(saved, reason);

        log.info("Voided signature request: {}", requestId);

        return SignatureRequestDTO.fromEntity(saved);
    }

    /**
     * Send reminder to pending signers.
     */
    @Transactional
    public void sendReminder(String tenantId, String requestId, String userId,
                             String userEmail, String userName, String ipAddress) {
        SignatureRequest request = requestRepository.findByIdAndTenantId(requestId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Signature request not found"));

        if (request.getStatus() != SignatureRequestStatus.PENDING &&
                request.getStatus() != SignatureRequestStatus.IN_PROGRESS) {
            throw new BadRequestException("Cannot send reminder for this request status");
        }

        // Find signers who haven't signed yet
        List<Signer> pendingSigners = request.getSigners().stream()
                .filter(s -> s.getStatus() == SignerStatus.PENDING ||
                        s.getStatus() == SignerStatus.VIEWED)
                .toList();

        if (pendingSigners.isEmpty()) {
            throw new BadRequestException("No pending signers to remind");
        }

        // For sequential signing, only remind current signer
        List<Signer> signersToRemind = request.getSigningOrder() == SigningOrder.SEQUENTIAL
                ? List.of(pendingSigners.get(0))
                : pendingSigners;

        for (Signer signer : signersToRemind) {
            publishReminderNotification(request, signer);
        }

        // Log event
        eventService.logRequestEvent(
                tenantId, requestId,
                SignatureEventType.REMINDER_SENT,
                userId, userEmail, userName,
                "Reminder sent to " + signersToRemind.size() + " signer(s)",
                ipAddress, null);

        log.info("Sent reminder for request: {} to {} signers", requestId, signersToRemind.size());
    }

    // ========================================
    // Private Helper Methods
    // ========================================

    private List<Signer> createSigners(List<CreateSignatureRequestRequest.SignerRequest> signerRequests,
                                        SigningOrder signingOrder) {
        List<Signer> signers = new ArrayList<>();

        for (int i = 0; i < signerRequests.size(); i++) {
            CreateSignatureRequestRequest.SignerRequest sr = signerRequests.get(i);

            String signerId = UUID.randomUUID().toString();
            String accessToken = tokenService.generateSigningToken();
            String accessTokenHash = tokenService.hashToken(accessToken);

            int order = sr.getOrder() != null ? sr.getOrder() : (i + 1);

            Signer signer = Signer.builder()
                    .id(signerId)
                    .email(sr.getEmail().toLowerCase().trim())
                    .name(sr.getName())
                    .order(order)
                    .accessTokenHash(accessTokenHash)
                    .status(SignerStatus.PENDING)
                    .build();

            // Store plain token temporarily for email sending
            // It will be retrieved when sending notifications
            signer.setAccessTokenHash(accessTokenHash);

            signers.add(signer);
        }

        // Sort by order for sequential signing
        if (signingOrder == SigningOrder.SEQUENTIAL) {
            signers.sort((a, b) -> a.getOrder().compareTo(b.getOrder()));
        }

        return signers;
    }

    private void sendSigningNotification(SignatureRequest request, Signer signer,
                                         SendRequestRequest sendRequest) {
        // Generate a fresh signing token for the email
        String signingToken = tokenService.generateSigningToken();
        String tokenHash = tokenService.hashToken(signingToken);

        // Update signer with new token hash
        signer.setAccessTokenHash(tokenHash);

        Map<String, Object> notification = Map.ofEntries(
                Map.entry("type", "SIGNING_REQUEST"),
                Map.entry("tenantId", request.getTenantId()),
                Map.entry("requestId", request.getId()),
                Map.entry("signerId", signer.getId()),
                Map.entry("signerEmail", signer.getEmail()),
                Map.entry("signerName", signer.getName() != null ? signer.getName() : signer.getEmail()),
                Map.entry("senderName", request.getSenderName()),
                Map.entry("senderEmail", request.getSenderEmail()),
                Map.entry("documentName", request.getDocumentName()),
                Map.entry("subject", request.getSubject() != null ? request.getSubject() : ""),
                Map.entry("message", sendRequest != null && sendRequest.getCustomMessage() != null
                        ? sendRequest.getCustomMessage()
                        : (request.getMessage() != null ? request.getMessage() : "")),
                Map.entry("signingToken", signingToken),
                Map.entry("expiresAt", request.getExpiresAt().toString())
        );

        kafkaTemplate.send("teamsync.signing.request.sent", notification);
    }

    private void publishVoidNotification(SignatureRequest request, String reason) {
        for (Signer signer : request.getSigners()) {
            Map<String, Object> notification = Map.of(
                    "type", "SIGNING_VOIDED",
                    "tenantId", request.getTenantId(),
                    "requestId", request.getId(),
                    "signerEmail", signer.getEmail(),
                    "signerName", signer.getName() != null ? signer.getName() : signer.getEmail(),
                    "senderName", request.getSenderName(),
                    "documentName", request.getDocumentName(),
                    "reason", reason != null ? reason : "Request cancelled by sender"
            );

            kafkaTemplate.send("teamsync.signing.voided", notification);
        }
    }

    private void publishReminderNotification(SignatureRequest request, Signer signer) {
        // Generate a fresh token for the reminder
        String signingToken = tokenService.generateSigningToken();
        String tokenHash = tokenService.hashToken(signingToken);

        // Update signer with new token hash
        signer.setAccessTokenHash(tokenHash);
        requestRepository.save(request);

        Map<String, Object> notification = Map.of(
                "type", "SIGNING_REMINDER",
                "tenantId", request.getTenantId(),
                "requestId", request.getId(),
                "signerId", signer.getId(),
                "signerEmail", signer.getEmail(),
                "signerName", signer.getName() != null ? signer.getName() : signer.getEmail(),
                "senderName", request.getSenderName(),
                "documentName", request.getDocumentName(),
                "signingToken", signingToken,
                "expiresAt", request.getExpiresAt().toString()
        );

        kafkaTemplate.send("teamsync.signing.reminder.due", notification);
    }
}
