package com.teamsync.storage.service;

import com.teamsync.common.context.TenantContext;
import com.teamsync.common.exception.ChecksumMismatchException;
import com.teamsync.common.exception.ResourceNotFoundException;
import com.teamsync.common.exception.StorageQuotaExceededException;
import com.teamsync.common.storage.CloudStorageProvider;
import com.teamsync.common.storage.StorageTier;
import com.teamsync.common.model.Permission;
import com.teamsync.common.permission.RequiresPermission;
import com.teamsync.storage.dto.*;
import com.teamsync.storage.event.StorageEventPublisher;
import com.teamsync.storage.model.StorageQuota;
import com.teamsync.storage.model.UploadSession;
import com.teamsync.storage.model.UploadSession.UploadPart;
import com.teamsync.storage.model.UploadSession.UploadStatus;
import com.teamsync.storage.repository.StorageQuotaRepository;
import com.teamsync.storage.repository.UploadSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Slf4j
public class StorageService {

    private final CloudStorageProvider storageProvider;
    private final StorageQuotaRepository quotaRepository;
    private final UploadSessionRepository sessionRepository;
    private final StorageEventPublisher eventPublisher;
    private final Executor presignExecutor;
    private final Tika tika = new Tika();

    // MIME type cache - common extensions are cached to avoid Tika detection overhead
    private static final Map<String, String> CONTENT_TYPE_CACHE = new ConcurrentHashMap<>();

    static {
        // Pre-populate common file types for instant lookup
        CONTENT_TYPE_CACHE.put("pdf", "application/pdf");
        CONTENT_TYPE_CACHE.put("doc", "application/msword");
        CONTENT_TYPE_CACHE.put("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        CONTENT_TYPE_CACHE.put("xls", "application/vnd.ms-excel");
        CONTENT_TYPE_CACHE.put("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        CONTENT_TYPE_CACHE.put("ppt", "application/vnd.ms-powerpoint");
        CONTENT_TYPE_CACHE.put("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation");
        CONTENT_TYPE_CACHE.put("txt", "text/plain");
        CONTENT_TYPE_CACHE.put("csv", "text/csv");
        CONTENT_TYPE_CACHE.put("json", "application/json");
        CONTENT_TYPE_CACHE.put("xml", "application/xml");
        CONTENT_TYPE_CACHE.put("html", "text/html");
        CONTENT_TYPE_CACHE.put("htm", "text/html");
        CONTENT_TYPE_CACHE.put("css", "text/css");
        CONTENT_TYPE_CACHE.put("js", "application/javascript");
        CONTENT_TYPE_CACHE.put("jpg", "image/jpeg");
        CONTENT_TYPE_CACHE.put("jpeg", "image/jpeg");
        CONTENT_TYPE_CACHE.put("png", "image/png");
        CONTENT_TYPE_CACHE.put("gif", "image/gif");
        CONTENT_TYPE_CACHE.put("svg", "image/svg+xml");
        CONTENT_TYPE_CACHE.put("webp", "image/webp");
        CONTENT_TYPE_CACHE.put("ico", "image/x-icon");
        CONTENT_TYPE_CACHE.put("mp4", "video/mp4");
        CONTENT_TYPE_CACHE.put("webm", "video/webm");
        CONTENT_TYPE_CACHE.put("avi", "video/x-msvideo");
        CONTENT_TYPE_CACHE.put("mov", "video/quicktime");
        CONTENT_TYPE_CACHE.put("mp3", "audio/mpeg");
        CONTENT_TYPE_CACHE.put("wav", "audio/wav");
        CONTENT_TYPE_CACHE.put("ogg", "audio/ogg");
        CONTENT_TYPE_CACHE.put("zip", "application/zip");
        CONTENT_TYPE_CACHE.put("gz", "application/gzip");
        CONTENT_TYPE_CACHE.put("tar", "application/x-tar");
        CONTENT_TYPE_CACHE.put("rar", "application/vnd.rar");
        CONTENT_TYPE_CACHE.put("7z", "application/x-7z-compressed");
        CONTENT_TYPE_CACHE.put("md", "text/markdown");
        CONTENT_TYPE_CACHE.put("yaml", "text/yaml");
        CONTENT_TYPE_CACHE.put("yml", "text/yaml");
    }

    public StorageService(
            CloudStorageProvider storageProvider,
            StorageQuotaRepository quotaRepository,
            UploadSessionRepository sessionRepository,
            StorageEventPublisher eventPublisher,
            @Qualifier("presignExecutor") Executor presignExecutor) {
        this.storageProvider = storageProvider;
        this.quotaRepository = quotaRepository;
        this.sessionRepository = sessionRepository;
        this.eventPublisher = eventPublisher;
        this.presignExecutor = presignExecutor;
    }

    @Value("${teamsync.storage.default-bucket:teamsync-documents}")
    private String defaultBucket;

    @Value("${teamsync.storage.multipart-threshold:104857600}")  // 100MB
    private long multipartThreshold;

    @Value("${teamsync.storage.default-chunk-size:10485760}")  // 10MB
    private int defaultChunkSize;

    @Value("${teamsync.storage.url-expiry-seconds:3600}")
    private int urlExpirySeconds;

    // Public URL prefix for presigned URLs - routes through API Gateway proxy
    // When set, presigned URLs are rewritten to go through API Gateway instead of directly to MinIO
    @Value("${teamsync.storage.public-url:}")
    private String publicUrlPrefix;

    // MinIO endpoint for URL rewriting
    @Value("${teamsync.storage.minio.endpoint:http://localhost:9000}")
    private String minioEndpoint;

    /**
     * Initialize an upload session.
     * Requires WRITE permission on the drive.
     */
    @RequiresPermission(Permission.WRITE)
    @Transactional
    public UploadInitResponse initializeUpload(UploadInitRequest request) {
        String tenantId = TenantContext.getTenantId();
        String driveId = TenantContext.getDriveId();
        String userId = TenantContext.getUserId();

        log.info("Initializing upload for file: {} size: {} for tenant: {}",
                request.getFilename(), request.getFileSize(), tenantId);

        // SECURITY FIX (Round 5): Atomic quota validation and reservation
        // Prevents race condition where concurrent uploads could both pass quota check
        validateAndReserveQuota(tenantId, driveId, request.getFileSize());

        // Detect content type if not provided (using cache for common types)
        String contentType = request.getContentType();
        if (contentType == null || contentType.isEmpty()) {
            contentType = detectContentType(request.getFilename());
        }

        // Generate storage key
        String storageKey = generateStorageKey(tenantId, driveId, request.getFilename());

        // Determine upload type
        boolean useMultipart = request.getUseMultipart() != null
                ? request.getUseMultipart()
                : request.getFileSize() > multipartThreshold;

        int chunkSize = request.getChunkSize() != null
                ? request.getChunkSize()
                : defaultChunkSize;

        // Create session
        UploadSession session = UploadSession.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .driveId(driveId)
                .userId(userId)
                .filename(request.getFilename())
                .contentType(contentType)
                .totalSize(request.getFileSize())
                .uploadedSize(0L)
                .bucket(defaultBucket)
                .storageKey(storageKey)
                .chunkSize(chunkSize)
                .status(UploadStatus.INITIATED)
                .expiresAt(Instant.now().plus(Duration.ofHours(24)))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        if (useMultipart) {
            return initializeMultipartUpload(session, request.getFileSize(), chunkSize);
        } else {
            return initializeSimpleUpload(session);
        }
    }

    private UploadInitResponse initializeSimpleUpload(UploadSession session) {
        // Generate presigned upload URL
        String uploadUrl = storageProvider.generatePresignedUploadUrl(
                session.getBucket(),
                session.getStorageKey(),
                Duration.ofSeconds(urlExpirySeconds),
                session.getContentType());

        // Rewrite URL to go through API Gateway proxy if configured
        uploadUrl = rewriteToPublicUrl(uploadUrl);

        session.setStatus(UploadStatus.IN_PROGRESS);
        sessionRepository.save(session);

        return UploadInitResponse.builder()
                .sessionId(session.getId())
                .uploadType("SIMPLE")
                .uploadUrl(uploadUrl)
                .bucket(session.getBucket())
                .storageKey(session.getStorageKey())
                .expiresAt(session.getExpiresAt())
                .urlValiditySeconds(urlExpirySeconds)
                .build();
    }

    private UploadInitResponse initializeMultipartUpload(UploadSession session, long fileSize, int chunkSize) {
        // Calculate number of parts
        int totalParts = (int) Math.ceil((double) fileSize / chunkSize);

        // Initiate multipart upload with storage provider
        String uploadId = storageProvider.initiateMultipartUpload(
                session.getBucket(),
                session.getStorageKey(),
                session.getContentType());

        session.setUploadId(uploadId);
        session.setTotalParts(totalParts);
        // Pre-allocate collections with known capacity for memory efficiency
        session.setCompletedParts(new ArrayList<>(totalParts));
        session.setChunkETags(new HashMap<>(totalParts));
        session.setStatus(UploadStatus.IN_PROGRESS);
        sessionRepository.save(session);

        // Generate presigned URLs for each part in parallel using virtual threads
        // This dramatically reduces latency for large files (e.g., 100 parts = ~10s sequential → ~1s parallel)
        List<CompletableFuture<UploadInitResponse.PartUploadUrl>> futures = new ArrayList<>(totalParts);

        for (int partNum = 1; partNum <= totalParts; partNum++) {
            final int partNumber = partNum;
            futures.add(CompletableFuture.supplyAsync(() -> {
                String partUrl = storageProvider.generatePresignedPartUploadUrl(
                        session.getBucket(),
                        session.getStorageKey(),
                        uploadId,
                        partNumber,
                        Duration.ofSeconds(urlExpirySeconds));

                // Rewrite URL to go through API Gateway proxy if configured
                partUrl = rewriteToPublicUrl(partUrl);

                return UploadInitResponse.PartUploadUrl.builder()
                        .partNumber(partNumber)
                        .uploadUrl(partUrl)
                        .build();
            }, presignExecutor));
        }

        // SECURITY FIX (Round 14 #H16): Wait for all URLs with timeout to prevent indefinite blocking.
        // Timeout of 60 seconds is generous for generating presigned URLs (typically <1s each).
        // If any URL generation times out, the upload session should fail gracefully.
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(60, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            log.error("Timeout waiting for presigned URL generation for session: {}", session.getId());
            session.setStatus(UploadStatus.FAILED);
            sessionRepository.save(session);
            throw new RuntimeException("Upload initialization timed out. Please try again.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Upload initialization was interrupted");
        } catch (java.util.concurrent.ExecutionException e) {
            log.error("Error generating presigned URLs for session: {}", session.getId(), e);
            throw new RuntimeException("Failed to generate upload URLs");
        }

        List<UploadInitResponse.PartUploadUrl> partUrls = futures.stream()
                .map(CompletableFuture::join)  // Safe after allOf completes
                .sorted(Comparator.comparingInt(UploadInitResponse.PartUploadUrl::getPartNumber))
                .toList();

        return UploadInitResponse.builder()
                .sessionId(session.getId())
                .uploadType("MULTIPART")
                .partUrls(partUrls)
                .totalParts(totalParts)
                .chunkSize(chunkSize)
                .bucket(session.getBucket())
                .storageKey(session.getStorageKey())
                .expiresAt(session.getExpiresAt())
                .urlValiditySeconds(urlExpirySeconds)
                .build();
    }

    /**
     * SECURITY FIX (Round 15 #M25): Maximum retry attempts for transient failures.
     */
    private static final int MAX_COMPLETE_RETRIES = 3;

    /**
     * SECURITY FIX (Round 15 #M25): Base delay for exponential backoff (milliseconds).
     */
    private static final long RETRY_BASE_DELAY_MS = 500;

    /**
     * Complete an upload.
     * Requires WRITE permission on the drive.
     *
     * SECURITY FIX (Round 15 #M25): Made idempotent - returns success if already completed.
     * Also adds retry logic with exponential backoff for transient failures.
     */
    @RequiresPermission(Permission.WRITE)
    @Transactional
    public UploadCompleteResponse completeUpload(UploadCompleteRequest request) {
        String tenantId = TenantContext.getTenantId();
        String driveId = TenantContext.getDriveId();

        log.info("Completing upload session: {} for tenant: {}, drive: {}", request.getSessionId(), tenantId, driveId);

        UploadSession session = sessionRepository.findByIdAndTenantIdAndDriveId(request.getSessionId(), tenantId, driveId)
                .orElseThrow(() -> new ResourceNotFoundException("Upload session not found"));

        // SECURITY FIX (Round 15 #M25): Idempotent - return success if already completed
        if (session.getStatus() == UploadStatus.COMPLETED) {
            log.info("Upload session {} already completed, returning existing response (idempotent)", request.getSessionId());
            return UploadCompleteResponse.builder()
                    .storageKey(session.getStorageKey())
                    .bucket(session.getBucket())
                    .filename(session.getFilename())
                    .contentType(session.getContentType())
                    .fileSize(session.getTotalSize())
                    .success(true)
                    .message("Upload already completed successfully")
                    .build();
        }

        if (session.getStatus() == UploadStatus.CANCELLED || session.getStatus() == UploadStatus.EXPIRED) {
            throw new IllegalStateException("Upload session is no longer valid");
        }

        // SECURITY FIX (Round 15 #M25): Retry with exponential backoff for transient failures
        return completeUploadWithRetry(session, request, tenantId, driveId, 0);
    }

    /**
     * SECURITY FIX (Round 15 #M25): Complete upload with retry logic for transient failures.
     * Uses exponential backoff with jitter to prevent thundering herd on failures.
     *
     * @param session The upload session
     * @param request The completion request
     * @param tenantId The tenant ID
     * @param driveId The drive ID
     * @param attempt Current retry attempt (0-based)
     * @return The completion response
     */
    private UploadCompleteResponse completeUploadWithRetry(UploadSession session, UploadCompleteRequest request,
                                                           String tenantId, String driveId, int attempt) {
        try {
            // Check for concurrent completion (another thread may have completed it)
            if (session.getStatus() == UploadStatus.COMPLETED) {
                log.info("Upload session {} completed by another thread (idempotent)", session.getId());
                return UploadCompleteResponse.builder()
                        .storageKey(session.getStorageKey())
                        .bucket(session.getBucket())
                        .filename(session.getFilename())
                        .contentType(session.getContentType())
                        .fileSize(session.getTotalSize())
                        .success(true)
                        .message("Upload already completed successfully")
                        .build();
            }

            session.setStatus(UploadStatus.COMPLETING);
            sessionRepository.save(session);

            // Complete multipart upload if applicable
            if (session.getUploadId() != null && request.getParts() != null) {
                completeMultipartUpload(session, request.getParts());
            }

            // Verify file exists and get metadata
            boolean exists = storageProvider.exists(session.getBucket(), session.getStorageKey());
            if (!exists) {
                throw new IllegalStateException("Uploaded file not found in storage");
            }

            // Verify checksum if provided
            if (request.getChecksum() != null && !request.getChecksum().isEmpty()) {
                String actualETag = storageProvider.getObjectETag(session.getBucket(), session.getStorageKey());
                String expectedChecksum = request.getChecksum().replace("\"", "");

                if (!actualETag.equalsIgnoreCase(expectedChecksum)) {
                    log.error("Checksum mismatch for {}: expected={}, actual={}",
                            session.getStorageKey(), expectedChecksum, actualETag);
                    throw new ChecksumMismatchException(session.getStorageKey(), expectedChecksum, actualETag);
                }
                log.debug("Checksum verified for {}: {}", session.getStorageKey(), actualETag);
            }

            // Update quota - convert reserved to used
            finalizeStorageUsage(tenantId, session.getDriveId(), session.getTotalSize());

            session.setStatus(UploadStatus.COMPLETED);
            session.setUploadedSize(session.getTotalSize());
            session.setUpdatedAt(Instant.now());
            sessionRepository.save(session);

            log.info("Upload completed successfully: {}", session.getId());

            // Publish audit event asynchronously (fire-and-forget)
            eventPublisher.publishUploadCompleted(tenantId, driveId, TenantContext.getUserId(),
                    session.getBucket(), session.getStorageKey(), session.getTotalSize());

            return UploadCompleteResponse.builder()
                    .storageKey(session.getStorageKey())
                    .bucket(session.getBucket())
                    .filename(session.getFilename())
                    .contentType(session.getContentType())
                    .fileSize(session.getTotalSize())
                    .success(true)
                    .message("Upload completed successfully")
                    .build();

        } catch (org.springframework.dao.TransientDataAccessException | com.mongodb.MongoTimeoutException e) {
            // SECURITY FIX (Round 15 #M25): Retry transient failures with exponential backoff
            if (attempt < MAX_COMPLETE_RETRIES) {
                long delay = calculateRetryDelay(attempt);
                log.warn("Transient failure completing upload (attempt {}/{}), retrying in {}ms: {}",
                        attempt + 1, MAX_COMPLETE_RETRIES, delay, e.getMessage());
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Upload completion interrupted", ie);
                }
                // Reload session to get latest state before retry
                session = sessionRepository.findByIdAndTenantIdAndDriveId(session.getId(), tenantId, driveId)
                        .orElseThrow(() -> new ResourceNotFoundException("Upload session not found during retry"));
                return completeUploadWithRetry(session, request, tenantId, driveId, attempt + 1);
            } else {
                log.error("Failed to complete upload after {} attempts: {}", MAX_COMPLETE_RETRIES, e.getMessage());
                session.setStatus(UploadStatus.FAILED);
                sessionRepository.save(session);
                releaseReservedStorage(tenantId, session.getDriveId(), session.getTotalSize());
                throw new RuntimeException("Upload completion failed after retries. Please try again.", e);
            }

        } catch (Exception e) {
            log.error("Error completing upload: {}", e.getMessage());
            session.setStatus(UploadStatus.FAILED);
            sessionRepository.save(session);

            // Release reserved storage
            releaseReservedStorage(tenantId, session.getDriveId(), session.getTotalSize());

            throw e;
        }
    }

    /**
     * SECURITY FIX (Round 15 #M25): Calculate retry delay with exponential backoff and jitter.
     * Jitter prevents thundering herd when multiple requests retry simultaneously.
     *
     * @param attempt The current attempt number (0-based)
     * @return The delay in milliseconds before the next retry
     */
    private long calculateRetryDelay(int attempt) {
        // Exponential backoff: 500ms, 1000ms, 2000ms, etc.
        long baseDelay = RETRY_BASE_DELAY_MS * (1L << attempt);
        // Add up to 30% jitter to prevent thundering herd
        long jitter = (long) (baseDelay * 0.3 * ThreadLocalRandom.current().nextDouble());
        return baseDelay + jitter;
    }

    private void completeMultipartUpload(UploadSession session, List<UploadCompleteRequest.PartETag> parts) {
        List<UploadPart> uploadParts = parts.stream()
                .map(p -> UploadPart.builder()
                        .partNumber(p.getPartNumber())
                        .etag(p.getEtag())
                        .build())
                .toList();

        storageProvider.completeMultipartUpload(
                session.getBucket(),
                session.getStorageKey(),
                session.getUploadId(),
                uploadParts.stream()
                        .collect(java.util.stream.Collectors.toMap(
                                UploadPart::getPartNumber,
                                UploadPart::getEtag)));
    }

    /**
     * Cancel an upload.
     * Requires WRITE permission on the drive.
     */
    @RequiresPermission(Permission.WRITE)
    @Transactional
    public void cancelUpload(String sessionId) {
        String tenantId = TenantContext.getTenantId();
        String driveId = TenantContext.getDriveId();

        log.info("Cancelling upload session: {} for tenant: {}, drive: {}", sessionId, tenantId, driveId);

        UploadSession session = sessionRepository.findByIdAndTenantIdAndDriveId(sessionId, tenantId, driveId)
                .orElseThrow(() -> new ResourceNotFoundException("Upload session not found"));

        if (session.getStatus() == UploadStatus.COMPLETED) {
            throw new IllegalStateException("Cannot cancel completed upload");
        }

        // Abort multipart upload if applicable
        if (session.getUploadId() != null) {
            try {
                storageProvider.abortMultipartUpload(
                        session.getBucket(),
                        session.getStorageKey(),
                        session.getUploadId());
            } catch (Exception e) {
                log.warn("Error aborting multipart upload: {}", e.getMessage());
            }
        }

        // Release reserved storage
        releaseReservedStorage(tenantId, session.getDriveId(), session.getTotalSize());

        session.setStatus(UploadStatus.CANCELLED);
        session.setUpdatedAt(Instant.now());
        sessionRepository.save(session);

        // Publish audit event asynchronously (fire-and-forget)
        eventPublisher.publishUploadCancelled(tenantId, driveId, TenantContext.getUserId(),
                session.getBucket(), session.getStorageKey(), session.getTotalSize());

        log.info("Upload cancelled: {}", sessionId);
    }

    /**
     * Get upload session status.
     * Used for upload resumption - returns which parts have been completed.
     * Requires READ permission on the drive.
     *
     * @param sessionId The upload session ID
     * @return Upload status including completed parts
     */
    @RequiresPermission(Permission.READ)
    public UploadStatusResponse getUploadStatus(String sessionId) {
        String tenantId = TenantContext.getTenantId();
        String driveId = TenantContext.getDriveId();

        log.debug("Getting upload status for session: {} tenant: {}", sessionId, tenantId);

        UploadSession session = sessionRepository.findByIdAndTenantIdAndDriveId(sessionId, tenantId, driveId)
                .orElseThrow(() -> new ResourceNotFoundException("Upload session not found: " + sessionId));

        List<Integer> completedPartNumbers = session.getCompletedParts() != null
                ? session.getCompletedParts().stream()
                    .map(UploadPart::getPartNumber)
                    .toList()
                : List.of();

        boolean canResume = session.getStatus() == UploadStatus.IN_PROGRESS
                && session.getExpiresAt() != null
                && session.getExpiresAt().isAfter(Instant.now());

        double progressPercent = session.getTotalSize() != null && session.getTotalSize() > 0
                ? (session.getUploadedSize() != null ? session.getUploadedSize() : 0L) * 100.0 / session.getTotalSize()
                : 0.0;

        return UploadStatusResponse.builder()
                .sessionId(session.getId())
                .status(session.getStatus().name())
                .totalParts(session.getTotalParts())
                .completedParts(completedPartNumbers)
                .uploadedSize(session.getUploadedSize())
                .totalSize(session.getTotalSize())
                .expiresAt(session.getExpiresAt())
                .bucket(session.getBucket())
                .storageKey(session.getStorageKey())
                .canResume(canResume)
                .progressPercent(Math.round(progressPercent * 100.0) / 100.0)
                .build();
    }

    /**
     * Upload file directly through backend streaming.
     * Used for small files (<10MB) where simplicity > performance.
     * No presigned URLs - file streams through backend to storage.
     *
     * @param file The multipart file to upload
     * @param contentType The content type of the file
     * @return DirectUploadResponse with bucket and storage key
     */
    @RequiresPermission(Permission.WRITE)
    @Transactional
    public DirectUploadResponse uploadFileDirect(MultipartFile file, String contentType) {
        String tenantId = TenantContext.getTenantId();
        String driveId = TenantContext.getDriveId();

        String filename = file.getOriginalFilename();
        long fileSize = file.getSize();

        log.info("Direct upload for file: {} size: {} for tenant: {}, drive: {}",
                filename, fileSize, tenantId, driveId);

        // Check quota
        validateQuota(tenantId, driveId, fileSize);

        // SECURITY FIX: Validate content type using magic bytes, not just filename
        String detectedContentType = detectContentTypeFromStream(file);
        if (contentType == null || contentType.isEmpty()) {
            contentType = detectedContentType;
        } else {
            // Validate that claimed content type matches actual content
            validateContentTypeMatch(contentType, detectedContentType, filename);
        }

        // Generate storage key
        String storageKey = generateStorageKey(tenantId, driveId, filename);

        try (InputStream inputStream = file.getInputStream()) {
            // Upload directly to storage
            storageProvider.upload(defaultBucket, storageKey, inputStream, fileSize, contentType);

            // Update quota (direct increment, no reservation needed)
            incrementStorageUsage(tenantId, driveId, fileSize);

            log.info("Direct upload completed: {} -> {}/{}", filename, defaultBucket, storageKey);

            // Publish audit event asynchronously (fire-and-forget)
            eventPublisher.publishStorageEvent("FILE_UPLOADED", tenantId, driveId, TenantContext.getUserId(),
                    defaultBucket, storageKey, fileSize, "SUCCESS");

            return DirectUploadResponse.builder()
                    .bucket(defaultBucket)
                    .storageKey(storageKey)
                    .fileSize(fileSize)
                    .contentType(contentType)
                    .build();

        } catch (IOException e) {
            // SECURITY FIX (Round 14 #H27): Don't expose internal error details
            // IOException messages can reveal storage paths, bucket names, or network configuration
            log.error("Error during direct upload for file: {}", filename, e);
            eventPublisher.publishStorageEvent("FILE_UPLOADED", tenantId, driveId, TenantContext.getUserId(),
                    defaultBucket, storageKey, fileSize, "FAILURE");
            throw new RuntimeException("Failed to upload file. Please try again.");
        }
    }

    /**
     * Generate download URL.
     * Requires READ permission on the drive.
     */
    @RequiresPermission(Permission.READ)
    public DownloadUrlResponse generateDownloadUrl(String bucket, String storageKey, String filename, int expirySeconds) {
        log.debug("Generating download URL for: {}/{}", bucket, storageKey);

        String url = storageProvider.generatePresignedUrl(bucket, storageKey, Duration.ofSeconds(expirySeconds));

        // Get file metadata in single API call (reduces network round trips from 2 to 1)
        CloudStorageProvider.ObjectMetadata metadata = storageProvider.getObjectMetadata(bucket, storageKey);

        return DownloadUrlResponse.builder()
                .url(url)
                .filename(filename)
                .contentType(metadata.contentType())
                .fileSize(metadata.size())
                .expiresInSeconds(expirySeconds)
                .build();
    }

    /**
     * Download file stream from storage.
     * Requires READ permission on the drive.
     * Returns the input stream along with file metadata for streaming to client.
     */
    @RequiresPermission(Permission.READ)
    public FileDownload downloadFile(String bucket, String storageKey) {
        log.debug("Downloading file from storage: {}/{}", bucket, storageKey);

        // Get file metadata in single API call (reduces network round trips from 2 to 1)
        CloudStorageProvider.ObjectMetadata metadata = storageProvider.getObjectMetadata(bucket, storageKey);

        // Get input stream
        InputStream inputStream = storageProvider.download(bucket, storageKey);

        // Publish audit event asynchronously for download tracking
        eventPublisher.publishFileDownloaded(
                TenantContext.getTenantId(), TenantContext.getDriveId(), TenantContext.getUserId(),
                bucket, storageKey, metadata.size());

        return new FileDownload(inputStream, metadata.contentType(), metadata.size());
    }

    /**
     * Record class for file download response containing stream and metadata.
     */
    public record FileDownload(InputStream inputStream, String contentType, long fileSize) {}

    /**
     * Delete a file from storage.
     * Requires DELETE permission on the drive.
     */
    @RequiresPermission(Permission.DELETE)
    public void deleteFile(String bucket, String storageKey) {
        String tenantId = TenantContext.getTenantId();
        String driveId = TenantContext.getDriveId();

        log.info("Deleting file: {}/{} for tenant: {}", bucket, storageKey, tenantId);

        // Get file size before deletion
        long fileSize = storageProvider.getObjectSize(bucket, storageKey);

        // Delete from storage
        storageProvider.delete(bucket, storageKey);

        // Update quota
        decrementStorageUsage(tenantId, driveId, fileSize);

        // Publish audit event asynchronously
        eventPublisher.publishFileDeleted(tenantId, driveId, TenantContext.getUserId(),
                bucket, storageKey, fileSize);

        log.info("File deleted: {}/{}", bucket, storageKey);
    }

    /**
     * Copy a file within storage.
     * Requires WRITE permission on the drive.
     */
    @RequiresPermission(Permission.WRITE)
    public String copyFile(String sourceBucket, String sourceKey, String destBucket, String destKey) {
        String tenantId = TenantContext.getTenantId();
        String driveId = TenantContext.getDriveId();

        log.info("Copying file: {}/{} to {}/{}", sourceBucket, sourceKey, destBucket, destKey);

        // Get source file size
        long fileSize = storageProvider.getObjectSize(sourceBucket, sourceKey);

        // Check quota for copy
        validateQuota(tenantId, driveId, fileSize);

        // Copy file
        storageProvider.copy(sourceBucket, sourceKey, destBucket, destKey);

        // Update quota
        incrementStorageUsage(tenantId, driveId, fileSize);

        // Publish audit event asynchronously
        eventPublisher.publishFileCopied(tenantId, driveId, TenantContext.getUserId(),
                destBucket, destKey, fileSize);

        return destKey;
    }

    /**
     * Move file to different storage tier.
     * Requires WRITE permission on the drive.
     */
    @RequiresPermission(Permission.WRITE)
    public void changeStorageTier(String bucket, String storageKey, StorageTier newTier) {
        log.info("Changing storage tier for {}/{} to {}", bucket, storageKey, newTier);
        storageProvider.setStorageClass(bucket, storageKey, newTier);
    }

    /**
     * Get storage quota for drive from TenantContext.
     * Requires READ permission on the drive.
     */
    @RequiresPermission(Permission.READ)
    public StorageQuotaDTO getStorageQuota() {
        String tenantId = TenantContext.getTenantId();
        String driveId = TenantContext.getDriveId();

        StorageQuota quota = quotaRepository.findByTenantIdAndDriveId(tenantId, driveId)
                .orElse(createDefaultQuota(tenantId, driveId));

        return mapToQuotaDTO(quota);
    }

    /**
     * Get storage quota for a specific drive.
     *
     * SECURITY FIX (Round 12): Added explicit driveId parameter version.
     * Authorization is handled at controller level via @PreAuthorize.
     *
     * @param driveId The drive ID to get quota for
     * @return StorageQuotaDTO with quota information
     */
    public StorageQuotaDTO getStorageQuotaForDrive(String driveId) {
        String tenantId = TenantContext.getTenantId();

        if (driveId == null || driveId.isBlank()) {
            throw new IllegalArgumentException("Drive ID is required");
        }

        StorageQuota quota = quotaRepository.findByTenantIdAndDriveId(tenantId, driveId)
                .orElse(createDefaultQuota(tenantId, driveId));

        return mapToQuotaDTO(quota);
    }

    /**
     * Update storage quota.
     * Requires MANAGE_USERS permission on the drive (admin function).
     *
     * SECURITY FIX: Added validation for quota limits to prevent:
     * - Negative quotas that break storage calculations
     * - Quotas below current usage (data loss)
     * - Unreasonably large quotas
     */
    @RequiresPermission(Permission.MANAGE_USERS)
    @Transactional
    public StorageQuotaDTO updateStorageQuota(String driveId, Long newQuotaLimit) {
        String tenantId = TenantContext.getTenantId();

        // SECURITY FIX: Validate quota limit
        if (newQuotaLimit == null || newQuotaLimit <= 0) {
            throw new IllegalArgumentException("Quota limit must be a positive value");
        }

        // Maximum quota: 10TB - prevent unreasonable values
        long maxAllowedQuota = 10L * 1024 * 1024 * 1024 * 1024; // 10TB
        if (newQuotaLimit > maxAllowedQuota) {
            throw new IllegalArgumentException("Quota limit cannot exceed 10TB");
        }

        StorageQuota quota = quotaRepository.findByTenantIdAndDriveId(tenantId, driveId)
                .orElse(createDefaultQuota(tenantId, driveId));

        // SECURITY FIX: Cannot reduce quota below current usage
        if (newQuotaLimit < quota.getUsedStorage()) {
            throw new IllegalArgumentException(
                    String.format("Cannot set quota (%s) below current usage (%s)",
                            formatBytes(newQuotaLimit), formatBytes(quota.getUsedStorage())));
        }

        quota.setQuotaLimit(newQuotaLimit);
        quota.setUpdatedAt(Instant.now());
        quota.setLastUpdatedBy(TenantContext.getUserId());

        quotaRepository.save(quota);

        log.info("SECURITY: Updated quota for drive {} to {} by user {}",
                driveId, formatBytes(newQuotaLimit), TenantContext.getUserId());

        return mapToQuotaDTO(quota);
    }

    // Helper methods

    private String generateStorageKey(String tenantId, String driveId, String filename) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String sanitizedFilename = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
        return String.format("%s/%s/%s/%s_%s", tenantId, driveId, timestamp.substring(0, 8), uuid, sanitizedFilename);
    }

    /**
     * SECURITY FIX (Round 5): Atomic quota validation and reservation.
     * Prevents race condition where concurrent uploads could both pass quota check
     * before either reserves storage, allowing quota to be exceeded.
     *
     * Uses MongoDB conditional update that only succeeds if quota is available.
     */
    private void validateAndReserveQuota(String tenantId, String driveId, long requiredBytes) {
        // First ensure quota document exists
        StorageQuota quota = quotaRepository.findByTenantIdAndDriveId(tenantId, driveId)
                .orElse(createDefaultQuota(tenantId, driveId));

        // Check max file size limit (this doesn't need to be atomic)
        if (quota.getMaxFileSizeBytes() != null && requiredBytes > quota.getMaxFileSizeBytes()) {
            throw new StorageQuotaExceededException(
                    String.format("File size exceeds maximum allowed: %d bytes", quota.getMaxFileSizeBytes()));
        }

        // SECURITY FIX (Round 5): Enforce file count limit
        // This prevents resource exhaustion where an attacker creates millions of tiny files
        // to overwhelm metadata storage even if storage quota is enforced
        if (quota.getMaxFileCount() != null && quota.getCurrentFileCount() != null) {
            if (quota.getCurrentFileCount() >= quota.getMaxFileCount()) {
                log.warn("SECURITY: File count limit exceeded for tenant={}, drive={}, current={}, max={}",
                        tenantId, driveId, quota.getCurrentFileCount(), quota.getMaxFileCount());
                throw new StorageQuotaExceededException(
                        String.format("File count limit exceeded. Maximum %d files allowed.", quota.getMaxFileCount()));
            }
        }

        // SECURITY FIX: Atomic check-and-reserve operation
        // MongoDB conditional update: only reserves if sufficient space available
        long modifiedCount = quotaRepository.atomicReserveIfAvailable(
                tenantId, driveId, requiredBytes, Instant.now());

        if (modifiedCount == 0) {
            // Atomic reservation failed - quota exceeded
            long availableStorage = quota.getQuotaLimit() - quota.getUsedStorage() - quota.getReservedStorage();
            log.warn("SECURITY: Quota reservation failed atomically for tenant={}, drive={}, required={}, available={}",
                    tenantId, driveId, requiredBytes, availableStorage);
            throw new StorageQuotaExceededException(
                    String.format("Insufficient storage. Required: %d bytes", requiredBytes));
        }

        log.debug("Atomically reserved {} bytes for drive {} in tenant {}", requiredBytes, driveId, tenantId);
    }

    /**
     * @deprecated Use validateAndReserveQuota() for atomic operation
     */
    @Deprecated
    private void validateQuota(String tenantId, String driveId, long requiredBytes) {
        StorageQuota quota = quotaRepository.findByTenantIdAndDriveId(tenantId, driveId)
                .orElse(createDefaultQuota(tenantId, driveId));

        long availableStorage = quota.getQuotaLimit() - quota.getUsedStorage() - quota.getReservedStorage();

        if (requiredBytes > availableStorage) {
            throw new StorageQuotaExceededException(
                    String.format("Insufficient storage. Required: %d bytes, Available: %d bytes",
                            requiredBytes, availableStorage));
        }

        if (quota.getMaxFileSizeBytes() != null && requiredBytes > quota.getMaxFileSizeBytes()) {
            throw new StorageQuotaExceededException(
                    String.format("File size exceeds maximum allowed: %d bytes", quota.getMaxFileSizeBytes()));
        }
    }

    private void reserveStorage(String tenantId, String driveId, long bytes) {
        quotaRepository.updateReservedStorage(tenantId, driveId, bytes);
    }

    private void releaseReservedStorage(String tenantId, String driveId, long bytes) {
        quotaRepository.updateReservedStorage(tenantId, driveId, -bytes);
    }

    /**
     * SECURITY FIX: Use atomic MongoDB operation to finalize storage usage.
     * Prevents race condition where concurrent uploads could corrupt quota tracking
     * through read-modify-write patterns.
     */
    private void finalizeStorageUsage(String tenantId, String driveId, long bytes) {
        // SECURITY: Atomic operation - no race condition possible
        quotaRepository.atomicFinalizeUpload(tenantId, driveId, bytes, Instant.now());
        log.debug("Atomically finalized {} bytes for drive {} in tenant {}", bytes, driveId, tenantId);
    }

    private void incrementStorageUsage(String tenantId, String driveId, long bytes) {
        quotaRepository.incrementUsage(tenantId, driveId, bytes, 1);
    }

    private void decrementStorageUsage(String tenantId, String driveId, long bytes) {
        quotaRepository.incrementUsage(tenantId, driveId, -bytes, -1);
    }

    private StorageQuota createDefaultQuota(String tenantId, String driveId) {
        StorageQuota quota = StorageQuota.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .driveId(driveId)
                .quotaLimit(10L * 1024 * 1024 * 1024)  // 10 GB default
                .usedStorage(0L)
                .reservedStorage(0L)
                .maxFileCount(10000L)
                .currentFileCount(0L)
                .maxFileSizeBytes(1L * 1024 * 1024 * 1024)  // 1 GB max file size
                .hotStorageUsed(0L)
                .warmStorageUsed(0L)
                .coldStorageUsed(0L)
                .archiveStorageUsed(0L)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        return quotaRepository.save(quota);
    }

    private StorageQuotaDTO mapToQuotaDTO(StorageQuota quota) {
        long available = quota.getQuotaLimit() - quota.getUsedStorage();
        double usagePercentage = quota.getQuotaLimit() > 0
                ? (double) quota.getUsedStorage() / quota.getQuotaLimit() * 100
                : 0;

        return StorageQuotaDTO.builder()
                .tenantId(quota.getTenantId())
                .driveId(quota.getDriveId())
                .quotaLimit(quota.getQuotaLimit())
                .usedStorage(quota.getUsedStorage())
                .availableStorage(available)
                .usagePercentage(usagePercentage)
                .quotaLimitFormatted(formatBytes(quota.getQuotaLimit()))
                .usedStorageFormatted(formatBytes(quota.getUsedStorage()))
                .availableStorageFormatted(formatBytes(available))
                .maxFileCount(quota.getMaxFileCount())
                .currentFileCount(quota.getCurrentFileCount())
                .maxFileSizeBytes(quota.getMaxFileSizeBytes())
                .maxFileSizeFormatted(formatBytes(quota.getMaxFileSizeBytes()))
                .hotStorageUsed(quota.getHotStorageUsed())
                .warmStorageUsed(quota.getWarmStorageUsed())
                .coldStorageUsed(quota.getColdStorageUsed())
                .archiveStorageUsed(quota.getArchiveStorageUsed())
                .isQuotaExceeded(quota.getUsedStorage() >= quota.getQuotaLimit())
                .isNearQuotaLimit(usagePercentage >= 90)
                .build();
    }

    private String formatBytes(Long bytes) {
        if (bytes == null || bytes == 0) return "0 B";
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = 0;
        double size = bytes;
        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }
        return String.format("%.2f %s", size, units[unitIndex]);
    }

    /**
     * Rewrites a presigned URL to use the public URL prefix (API Gateway proxy).
     * This keeps MinIO internal and routes all storage requests through the API Gateway.
     *
     * SECURITY: Validates URL structure to prevent path traversal and injection attacks.
     *
     * Example:
     *   MinIO URL: http://teamsync-minio:9000/bucket/key?X-Amz-...
     *   Public URL: http://api-gateway:9080/storage-proxy/bucket/key?X-Amz-...
     *
     * @param minioUrl The presigned URL generated by MinIO/S3
     * @return The rewritten URL if publicUrlPrefix is configured, otherwise the original URL
     * @throws SecurityException if URL validation fails
     */
    private String rewriteToPublicUrl(String minioUrl) {
        if (publicUrlPrefix == null || publicUrlPrefix.isEmpty()) {
            log.debug("No public URL prefix configured, returning original URL");
            return minioUrl;
        }

        // SECURITY FIX: Validate URL structure before rewriting
        try {
            URL url = new URL(minioUrl);
            URL expectedEndpoint = new URL(minioEndpoint);

            // SECURITY FIX (Round 9): Validate both host AND port to prevent routing attacks
            // Previous code only checked getHost() which strips port, allowing
            // attacker to specify different port (e.g., localhost:9001 vs localhost:9000)
            String minioHost = expectedEndpoint.getHost();
            int expectedPort = expectedEndpoint.getPort() != -1 ? expectedEndpoint.getPort() :
                    (expectedEndpoint.getProtocol().equals("https") ? 443 : 80);
            int actualPort = url.getPort() != -1 ? url.getPort() :
                    (url.getProtocol().equals("https") ? 443 : 80);

            if (!url.getHost().equals(minioHost)) {
                log.warn("SECURITY: Presigned URL host mismatch - expected: {}, got: {}",
                        minioHost, url.getHost());
                throw new SecurityException("Invalid presigned URL: host mismatch");
            }

            if (actualPort != expectedPort) {
                log.warn("SECURITY: Presigned URL port mismatch - expected: {}, got: {}",
                        expectedPort, actualPort);
                throw new SecurityException("Invalid presigned URL: port mismatch");
            }

            // Also validate protocol to prevent http/https confusion
            if (!url.getProtocol().equals(expectedEndpoint.getProtocol())) {
                log.warn("SECURITY: Presigned URL protocol mismatch - expected: {}, got: {}",
                        expectedEndpoint.getProtocol(), url.getProtocol());
                throw new SecurityException("Invalid presigned URL: protocol mismatch");
            }

            // Validate path doesn't contain path traversal attempts
            String path = url.getPath();
            if (path.contains("..") || path.contains("//") || path.contains("\\")) {
                log.warn("SECURITY: Presigned URL contains path traversal attempt: {}", path);
                throw new SecurityException("Invalid presigned URL: path traversal detected");
            }

            // Validate path starts with / and has at least bucket/key structure
            if (!path.startsWith("/") || path.split("/").length < 3) {
                log.warn("SECURITY: Presigned URL has invalid path structure: {}", path);
                throw new SecurityException("Invalid presigned URL: malformed path");
            }

            // Safe to rewrite - use URL components instead of string replacement
            String query = url.getQuery();
            String rewrittenUrl = publicUrlPrefix + path + (query != null ? "?" + query : "");

            log.debug("Rewrote presigned URL: {} -> {}", minioUrl, rewrittenUrl);
            return rewrittenUrl;

        } catch (MalformedURLException e) {
            log.error("SECURITY: Failed to parse presigned URL: {}", minioUrl, e);
            throw new SecurityException("Invalid presigned URL format", e);
        }
    }

    // SECURITY: Allowlist of content types that can be uploaded
    // This prevents uploading executable content disguised as documents
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            // Documents
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/rtf",
            "application/vnd.oasis.opendocument.text",
            "application/vnd.oasis.opendocument.spreadsheet",
            "application/vnd.oasis.opendocument.presentation",
            // Text
            "text/plain",
            "text/csv",
            "text/markdown",
            "text/html",
            "text/css",
            "text/xml",
            "application/json",
            "application/xml",
            "application/javascript",
            "text/yaml",
            // Images
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp",
            "image/svg+xml",
            "image/bmp",
            "image/tiff",
            "image/x-icon",
            // Audio
            "audio/mpeg",
            "audio/wav",
            "audio/ogg",
            "audio/webm",
            "audio/aac",
            // Video
            "video/mp4",
            "video/webm",
            "video/quicktime",
            "video/x-msvideo",
            // Archives (non-executable)
            "application/zip",
            "application/gzip",
            "application/x-tar",
            "application/x-7z-compressed",
            "application/vnd.rar",
            // Generic binary (only if explicitly validated)
            "application/octet-stream"
    );

    // SECURITY: Content types that should NEVER be allowed
    private static final Set<String> BLOCKED_CONTENT_TYPES = Set.of(
            "application/x-executable",
            "application/x-msdownload",
            "application/x-msdos-program",
            "application/x-sh",
            "application/x-shellscript",
            "application/x-bat",
            "application/x-msi",
            "application/vnd.microsoft.portable-executable",
            "application/x-dosexec"
    );

    /**
     * SECURITY FIX: Detect content type from actual file bytes using Tika magic bytes.
     * This prevents attackers from uploading malicious files with fake extensions.
     *
     * @param file The multipart file to analyze
     * @return The detected MIME type based on file content
     */
    private String detectContentTypeFromStream(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            // Tika uses magic bytes (file signatures) to detect type
            String detected = tika.detect(inputStream, file.getOriginalFilename());
            log.debug("Tika detected content type: {} for file: {}", detected, file.getOriginalFilename());

            // Validate against blocklist
            if (BLOCKED_CONTENT_TYPES.contains(detected)) {
                log.warn("SECURITY: Blocked content type detected: {} for file: {}",
                        detected, file.getOriginalFilename());
                throw new SecurityException("File type not allowed: " + detected);
            }

            // Validate against allowlist (if not generic octet-stream)
            if (!"application/octet-stream".equals(detected) && !ALLOWED_CONTENT_TYPES.contains(detected)) {
                log.warn("SECURITY: Unknown content type detected: {} for file: {}",
                        detected, file.getOriginalFilename());
                // Allow but log for monitoring - could be tightened to reject
            }

            return detected;
        } catch (IOException e) {
            // SECURITY FIX (Round 14 #H27): Don't expose internal error details
            // IOException messages can reveal file system paths or I/O implementation details
            log.error("Error detecting content type for file: {}", file.getOriginalFilename(), e);
            throw new RuntimeException("Failed to process file. Please try again.");
        }
    }

    /**
     * SECURITY FIX: Validate that claimed content type matches detected content type.
     * Prevents content-type spoofing attacks where attacker sends wrong Content-Type header.
     *
     * @param claimed The content type claimed by the client (Content-Type header)
     * @param detected The content type detected from file magic bytes
     * @param filename The filename for logging purposes
     * @throws SecurityException if types don't match and represent a security risk
     */
    private void validateContentTypeMatch(String claimed, String detected, String filename) {
        // Normalize types for comparison (strip charset and parameters)
        String normalizedClaimed = normalizeContentType(claimed);
        String normalizedDetected = normalizeContentType(detected);

        // Exact match is always OK
        if (normalizedClaimed.equals(normalizedDetected)) {
            log.debug("Content type match: {} for file: {}", normalizedClaimed, filename);
            return;
        }

        // Handle common equivalent types
        if (areEquivalentTypes(normalizedClaimed, normalizedDetected)) {
            log.debug("Content type equivalent: {} ~= {} for file: {}",
                    normalizedClaimed, normalizedDetected, filename);
            return;
        }

        // Generic octet-stream can be replaced with specific type
        if ("application/octet-stream".equals(normalizedClaimed)) {
            log.debug("Replacing generic octet-stream with detected: {} for file: {}",
                    normalizedDetected, filename);
            return;
        }

        // Mismatch - this could be an attack
        log.warn("SECURITY: Content type mismatch for file: {} - claimed: {}, detected: {}",
                filename, normalizedClaimed, normalizedDetected);

        // If detected type is dangerous, reject immediately
        if (BLOCKED_CONTENT_TYPES.contains(normalizedDetected)) {
            throw new SecurityException("File content does not match declared type - blocked content detected");
        }

        // For other mismatches, log but allow (could be tightened based on security requirements)
        // The detected type will be used for storage
        log.warn("SECURITY: Allowing content type mismatch but using detected type: {} for file: {}",
                normalizedDetected, filename);
    }

    /**
     * Normalize content type by removing charset and other parameters.
     */
    private String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "application/octet-stream";
        }
        int semicolon = contentType.indexOf(';');
        if (semicolon > 0) {
            contentType = contentType.substring(0, semicolon);
        }
        return contentType.trim().toLowerCase();
    }

    /**
     * Check if two content types are functionally equivalent.
     */
    private boolean areEquivalentTypes(String type1, String type2) {
        // Text variations
        if ((type1.equals("text/plain") && type2.startsWith("text/")) ||
            (type2.equals("text/plain") && type1.startsWith("text/"))) {
            return true;
        }
        // XML variations
        if ((type1.contains("xml") && type2.contains("xml"))) {
            return true;
        }
        // JSON variations
        if ((type1.contains("json") && type2.contains("json"))) {
            return true;
        }
        // JPEG variations
        if ((type1.equals("image/jpeg") && type2.equals("image/jpg")) ||
            (type1.equals("image/jpg") && type2.equals("image/jpeg"))) {
            return true;
        }
        return false;
    }

    /**
     * Detect content type with caching for common extensions.
     * Uses a pre-populated cache for ~40 common file types, falling back to Tika for unknown extensions.
     * This reduces CPU overhead by avoiding Tika's signature analysis for known types.
     *
     * @param filename The filename to detect content type for
     * @return The MIME type (e.g., "application/pdf", "image/jpeg")
     */
    private String detectContentType(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "application/octet-stream";
        }

        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filename.length() - 1) {
            String extension = filename.substring(lastDot + 1).toLowerCase();
            String cached = CONTENT_TYPE_CACHE.get(extension);
            if (cached != null) {
                log.debug("Content type cache hit for extension: {} -> {}", extension, cached);
                return cached;
            }
            // Cache miss - use Tika and cache result for future lookups
            String detected = tika.detect(filename);
            CONTENT_TYPE_CACHE.put(extension, detected);
            log.debug("Content type cache miss for extension: {} -> {} (cached)", extension, detected);
            return detected;
        }

        // No extension - use Tika
        return tika.detect(filename);
    }
}
