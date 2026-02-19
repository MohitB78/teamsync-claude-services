package com.teamsync.storage.service;

import com.teamsync.common.context.TenantContext;
import com.teamsync.common.exception.StorageQuotaExceededException;
import com.teamsync.common.storage.CloudStorageProvider;
import com.teamsync.storage.dto.*;
import com.teamsync.storage.model.StorageQuota;
import com.teamsync.storage.model.UploadSession;
import com.teamsync.storage.model.UploadSession.UploadStatus;
import com.teamsync.storage.repository.StorageQuotaRepository;
import com.teamsync.storage.repository.UploadSessionRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for Upload functionality.
 * Covers:
 * - DIRECT upload strategy (<10MB backend streaming)
 * - PRESIGNED upload strategy (≥10MB presigned URLs)
 * - Multipart upload for large files
 * - Quota validation and reservation
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Upload Strategy Tests")
class UploadStrategyTest {

    @Mock
    private CloudStorageProvider storageProvider;

    @Mock
    private StorageQuotaRepository quotaRepository;

    @Mock
    private UploadSessionRepository sessionRepository;

    @InjectMocks
    private StorageService storageService;

    @Captor
    private ArgumentCaptor<UploadSession> sessionCaptor;

    private MockedStatic<TenantContext> tenantContextMock;

    private static final String TENANT_ID = "tenant-123";
    private static final String DRIVE_ID = "drive-456";
    private static final String USER_ID = "user-789";
    private static final String DEFAULT_BUCKET = "teamsync-documents";

    @BeforeEach
    void setUp() {
        tenantContextMock = mockStatic(TenantContext.class);
        tenantContextMock.when(TenantContext::getTenantId).thenReturn(TENANT_ID);
        tenantContextMock.when(TenantContext::getDriveId).thenReturn(DRIVE_ID);
        tenantContextMock.when(TenantContext::getUserId).thenReturn(USER_ID);

        // Set default values via reflection
        ReflectionTestUtils.setField(storageService, "defaultBucket", DEFAULT_BUCKET);
        ReflectionTestUtils.setField(storageService, "multipartThreshold", 104857600L); // 100MB
        ReflectionTestUtils.setField(storageService, "defaultChunkSize", 10485760); // 10MB
        ReflectionTestUtils.setField(storageService, "urlExpirySeconds", 3600);
        ReflectionTestUtils.setField(storageService, "publicUrlPrefix", "");
        ReflectionTestUtils.setField(storageService, "minioEndpoint", "http://localhost:9000");
    }

    @AfterEach
    void tearDown() {
        tenantContextMock.close();
    }

    private StorageQuota createDefaultQuota() {
        return StorageQuota.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(TENANT_ID)
                .driveId(DRIVE_ID)
                .quotaLimit(10L * 1024 * 1024 * 1024) // 10 GB
                .usedStorage(0L)
                .reservedStorage(0L)
                .maxFileCount(10000L)
                .currentFileCount(0L)
                .maxFileSizeBytes(1L * 1024 * 1024 * 1024) // 1 GB max file
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("Direct Upload Tests (<10MB)")
    class DirectUploadTests {

        @Test
        @DisplayName("Should upload small file directly through backend")
        void directUpload_SmallFile_Success() {
            // Given
            byte[] content = "test file content".getBytes();
            MultipartFile file = new MockMultipartFile(
                    "file",
                    "test-document.pdf",
                    "application/pdf",
                    content
            );

            StorageQuota quota = createDefaultQuota();
            when(quotaRepository.findByTenantIdAndDriveId(TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(quota));

            // When
            DirectUploadResponse response = storageService.uploadFileDirect(file, "application/pdf");

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getBucket()).isEqualTo(DEFAULT_BUCKET);
            assertThat(response.getStorageKey()).contains(TENANT_ID).contains(DRIVE_ID);
            assertThat(response.getFileSize()).isEqualTo(content.length);
            assertThat(response.getContentType()).isEqualTo("application/pdf");

            // Verify storage upload was called
            verify(storageProvider).upload(
                    eq(DEFAULT_BUCKET),
                    anyString(),
                    any(),
                    eq((long) content.length),
                    eq("application/pdf")
            );

            // Verify quota was incremented
            verify(quotaRepository).incrementUsage(TENANT_ID, DRIVE_ID, content.length, 1);
        }

        @Test
        @DisplayName("Should detect content type from filename when not provided")
        void directUpload_DetectsContentType() {
            // Given
            byte[] content = "test content".getBytes();
            MultipartFile file = new MockMultipartFile(
                    "file",
                    "document.docx",
                    null, // No content type
                    content
            );

            StorageQuota quota = createDefaultQuota();
            when(quotaRepository.findByTenantIdAndDriveId(TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(quota));

            // When
            DirectUploadResponse response = storageService.uploadFileDirect(file, null);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getContentType()).isNotNull();

            verify(storageProvider).upload(
                    eq(DEFAULT_BUCKET),
                    anyString(),
                    any(),
                    anyLong(),
                    anyString()
            );
        }

        @Test
        @DisplayName("Should fail when quota is exceeded")
        void directUpload_QuotaExceeded_ThrowsException() {
            // Given
            byte[] content = new byte[1024];
            MultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", content);

            StorageQuota quota = createDefaultQuota();
            quota.setUsedStorage(quota.getQuotaLimit()); // Quota full
            when(quotaRepository.findByTenantIdAndDriveId(TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(quota));

            // When/Then
            assertThatThrownBy(() -> storageService.uploadFileDirect(file, "application/pdf"))
                    .isInstanceOf(StorageQuotaExceededException.class)
                    .hasMessageContaining("Insufficient storage");
        }

        @Test
        @DisplayName("Should fail when file exceeds max file size")
        void directUpload_ExceedsMaxFileSize_ThrowsException() {
            // Given
            byte[] content = new byte[1024];
            MultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", content);

            StorageQuota quota = createDefaultQuota();
            quota.setMaxFileSizeBytes(100L); // Max 100 bytes
            when(quotaRepository.findByTenantIdAndDriveId(TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(quota));

            // When/Then
            assertThatThrownBy(() -> storageService.uploadFileDirect(file, "application/pdf"))
                    .isInstanceOf(StorageQuotaExceededException.class)
                    .hasMessageContaining("File size exceeds maximum allowed");
        }
    }

    @Nested
    @DisplayName("Presigned Upload Tests (≥10MB)")
    class PresignedUploadTests {

        @Test
        @DisplayName("Should initialize simple upload for small files")
        void initializeUpload_SmallFile_ReturnsSimpleUpload() {
            // Given
            UploadInitRequest request = UploadInitRequest.builder()
                    .filename("document.pdf")
                    .fileSize(5 * 1024 * 1024L) // 5MB
                    .contentType("application/pdf")
                    .build();

            StorageQuota quota = createDefaultQuota();
            when(quotaRepository.findByTenantIdAndDriveId(TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(quota));
            when(storageProvider.generatePresignedUploadUrl(anyString(), anyString(), any(Duration.class), anyString()))
                    .thenReturn("http://minio:9000/bucket/key?presigned");
            when(sessionRepository.save(any(UploadSession.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            UploadInitResponse response = storageService.initializeUpload(request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getUploadType()).isEqualTo("SIMPLE");
            assertThat(response.getUploadUrl()).isNotNull();
            assertThat(response.getBucket()).isEqualTo(DEFAULT_BUCKET);
            assertThat(response.getStorageKey()).contains(TENANT_ID);
            assertThat(response.getPartUrls()).isNull();

            // Verify session was saved
            verify(sessionRepository).save(sessionCaptor.capture());
            UploadSession savedSession = sessionCaptor.getValue();
            assertThat(savedSession.getStatus()).isEqualTo(UploadStatus.IN_PROGRESS);
            assertThat(savedSession.getTenantId()).isEqualTo(TENANT_ID);
            assertThat(savedSession.getDriveId()).isEqualTo(DRIVE_ID);
            assertThat(savedSession.getUserId()).isEqualTo(USER_ID);

            // Verify quota reservation
            verify(quotaRepository).updateReservedStorage(TENANT_ID, DRIVE_ID, request.getFileSize());
        }

        @Test
        @DisplayName("Should initialize multipart upload for large files (>100MB)")
        void initializeUpload_LargeFile_ReturnsMultipartUpload() {
            // Given
            long fileSize = 150 * 1024 * 1024L; // 150MB
            UploadInitRequest request = UploadInitRequest.builder()
                    .filename("large-video.mp4")
                    .fileSize(fileSize)
                    .contentType("video/mp4")
                    .build();

            StorageQuota quota = createDefaultQuota();
            when(quotaRepository.findByTenantIdAndDriveId(TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(quota));
            when(storageProvider.initiateMultipartUpload(anyString(), anyString(), anyString()))
                    .thenReturn("upload-id-123");
            when(storageProvider.generatePresignedPartUploadUrl(anyString(), anyString(), anyString(), anyInt(), any(Duration.class)))
                    .thenReturn("http://minio:9000/bucket/key?partNumber=1");
            when(sessionRepository.save(any(UploadSession.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            UploadInitResponse response = storageService.initializeUpload(request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getUploadType()).isEqualTo("MULTIPART");
            assertThat(response.getUploadUrl()).isNull(); // No single URL for multipart
            assertThat(response.getPartUrls()).isNotNull();
            assertThat(response.getTotalParts()).isGreaterThan(1);
            assertThat(response.getChunkSize()).isEqualTo(10485760); // 10MB chunks

            // Expected parts: 150MB / 10MB = 15 parts
            int expectedParts = (int) Math.ceil((double) fileSize / 10485760);
            assertThat(response.getPartUrls()).hasSize(expectedParts);

            // Verify multipart was initiated
            verify(storageProvider).initiateMultipartUpload(eq(DEFAULT_BUCKET), anyString(), eq("video/mp4"));
        }

        @Test
        @DisplayName("Should allow forcing multipart upload via request flag")
        void initializeUpload_ForceMultipart_ReturnsMultipartUpload() {
            // Given
            UploadInitRequest request = UploadInitRequest.builder()
                    .filename("document.pdf")
                    .fileSize(20 * 1024 * 1024L) // 20MB (under threshold)
                    .contentType("application/pdf")
                    .useMultipart(true) // Force multipart
                    .build();

            StorageQuota quota = createDefaultQuota();
            when(quotaRepository.findByTenantIdAndDriveId(TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(quota));
            when(storageProvider.initiateMultipartUpload(anyString(), anyString(), anyString()))
                    .thenReturn("upload-id-456");
            when(storageProvider.generatePresignedPartUploadUrl(anyString(), anyString(), anyString(), anyInt(), any(Duration.class)))
                    .thenReturn("http://minio:9000/bucket/key?partNumber=1");
            when(sessionRepository.save(any(UploadSession.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            UploadInitResponse response = storageService.initializeUpload(request);

            // Then
            assertThat(response.getUploadType()).isEqualTo("MULTIPART");
            verify(storageProvider).initiateMultipartUpload(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Should allow custom chunk size")
        void initializeUpload_CustomChunkSize() {
            // Given
            int customChunkSize = 5 * 1024 * 1024; // 5MB
            long fileSize = 25 * 1024 * 1024L; // 25MB
            UploadInitRequest request = UploadInitRequest.builder()
                    .filename("document.pdf")
                    .fileSize(fileSize)
                    .contentType("application/pdf")
                    .useMultipart(true)
                    .chunkSize(customChunkSize)
                    .build();

            StorageQuota quota = createDefaultQuota();
            when(quotaRepository.findByTenantIdAndDriveId(TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(quota));
            when(storageProvider.initiateMultipartUpload(anyString(), anyString(), anyString()))
                    .thenReturn("upload-id-789");
            when(storageProvider.generatePresignedPartUploadUrl(anyString(), anyString(), anyString(), anyInt(), any(Duration.class)))
                    .thenReturn("http://minio:9000/bucket/key?partNumber=1");
            when(sessionRepository.save(any(UploadSession.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            UploadInitResponse response = storageService.initializeUpload(request);

            // Then
            assertThat(response.getChunkSize()).isEqualTo(customChunkSize);
            // 25MB / 5MB = 5 parts
            assertThat(response.getPartUrls()).hasSize(5);
        }
    }

    @Nested
    @DisplayName("Upload Completion Tests")
    class UploadCompletionTests {

        @Test
        @DisplayName("Should complete simple upload successfully")
        void completeUpload_SimpleUpload_Success() {
            // Given
            String sessionId = "session-123";
            UploadSession session = UploadSession.builder()
                    .id(sessionId)
                    .tenantId(TENANT_ID)
                    .driveId(DRIVE_ID)
                    .userId(USER_ID)
                    .filename("document.pdf")
                    .contentType("application/pdf")
                    .totalSize(5 * 1024 * 1024L)
                    .uploadedSize(0L)
                    .bucket(DEFAULT_BUCKET)
                    .storageKey("tenant/drive/12345/document.pdf")
                    .status(UploadStatus.IN_PROGRESS)
                    .createdAt(Instant.now())
                    .build();

            StorageQuota quota = createDefaultQuota();
            quota.setReservedStorage(session.getTotalSize());

            UploadCompleteRequest request = UploadCompleteRequest.builder()
                    .sessionId(sessionId)
                    .build();

            when(sessionRepository.findByIdAndTenantIdAndDriveId(sessionId, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(session));
            when(storageProvider.exists(DEFAULT_BUCKET, session.getStorageKey())).thenReturn(true);
            when(quotaRepository.findByTenantIdAndDriveId(TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(quota));
            when(sessionRepository.save(any(UploadSession.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            UploadCompleteResponse response = storageService.completeUpload(request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getSuccess()).isTrue();
            assertThat(response.getStorageKey()).isEqualTo(session.getStorageKey());
            assertThat(response.getBucket()).isEqualTo(DEFAULT_BUCKET);
            assertThat(response.getFilename()).isEqualTo("document.pdf");
            assertThat(response.getFileSize()).isEqualTo(session.getTotalSize());

            // Verify session status was updated
            verify(sessionRepository, times(2)).save(sessionCaptor.capture());
            List<UploadSession> savedSessions = sessionCaptor.getAllValues();
            assertThat(savedSessions.get(0).getStatus()).isEqualTo(UploadStatus.COMPLETING);
            assertThat(savedSessions.get(1).getStatus()).isEqualTo(UploadStatus.COMPLETED);
        }

        @Test
        @DisplayName("Should complete multipart upload with ETags")
        void completeUpload_MultipartUpload_Success() {
            // Given
            String sessionId = "session-456";
            String uploadId = "upload-id-456";
            UploadSession session = UploadSession.builder()
                    .id(sessionId)
                    .tenantId(TENANT_ID)
                    .driveId(DRIVE_ID)
                    .userId(USER_ID)
                    .filename("large-video.mp4")
                    .contentType("video/mp4")
                    .totalSize(150 * 1024 * 1024L)
                    .uploadedSize(0L)
                    .bucket(DEFAULT_BUCKET)
                    .storageKey("tenant/drive/12345/large-video.mp4")
                    .uploadId(uploadId)
                    .totalParts(15)
                    .completedParts(new ArrayList<>())
                    .chunkETags(new HashMap<>())
                    .status(UploadStatus.IN_PROGRESS)
                    .createdAt(Instant.now())
                    .build();

            StorageQuota quota = createDefaultQuota();
            quota.setReservedStorage(session.getTotalSize());

            List<UploadCompleteRequest.PartETag> parts = new ArrayList<>();
            for (int i = 1; i <= 15; i++) {
                parts.add(UploadCompleteRequest.PartETag.builder()
                        .partNumber(i)
                        .etag("etag-" + i)
                        .build());
            }

            UploadCompleteRequest request = UploadCompleteRequest.builder()
                    .sessionId(sessionId)
                    .parts(parts)
                    .build();

            when(sessionRepository.findByIdAndTenantIdAndDriveId(sessionId, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(session));
            when(storageProvider.exists(DEFAULT_BUCKET, session.getStorageKey())).thenReturn(true);
            when(quotaRepository.findByTenantIdAndDriveId(TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(quota));
            when(sessionRepository.save(any(UploadSession.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            UploadCompleteResponse response = storageService.completeUpload(request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getSuccess()).isTrue();

            // Verify multipart completion was called
            verify(storageProvider).completeMultipartUpload(
                    eq(DEFAULT_BUCKET),
                    eq(session.getStorageKey()),
                    eq(uploadId),
                    anyMap()
            );
        }

        @Test
        @DisplayName("Should fail if file not found after upload")
        void completeUpload_FileNotFound_ThrowsException() {
            // Given
            String sessionId = "session-789";
            UploadSession session = UploadSession.builder()
                    .id(sessionId)
                    .tenantId(TENANT_ID)
                    .driveId(DRIVE_ID)
                    .userId(USER_ID)
                    .filename("document.pdf")
                    .totalSize(5 * 1024 * 1024L)
                    .bucket(DEFAULT_BUCKET)
                    .storageKey("tenant/drive/12345/document.pdf")
                    .status(UploadStatus.IN_PROGRESS)
                    .createdAt(Instant.now())
                    .build();

            UploadCompleteRequest request = UploadCompleteRequest.builder()
                    .sessionId(sessionId)
                    .build();

            when(sessionRepository.findByIdAndTenantIdAndDriveId(sessionId, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(session));
            when(storageProvider.exists(DEFAULT_BUCKET, session.getStorageKey())).thenReturn(false);
            when(sessionRepository.save(any(UploadSession.class))).thenAnswer(inv -> inv.getArgument(0));

            // When/Then
            assertThatThrownBy(() -> storageService.completeUpload(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not found in storage");

            // Verify storage was released
            verify(quotaRepository).updateReservedStorage(TENANT_ID, DRIVE_ID, -session.getTotalSize());
        }

        @Test
        @DisplayName("Should fail when completing already completed upload")
        void completeUpload_AlreadyCompleted_ThrowsException() {
            // Given
            String sessionId = "session-completed";
            UploadSession session = UploadSession.builder()
                    .id(sessionId)
                    .tenantId(TENANT_ID)
                    .driveId(DRIVE_ID)
                    .status(UploadStatus.COMPLETED)
                    .build();

            UploadCompleteRequest request = UploadCompleteRequest.builder()
                    .sessionId(sessionId)
                    .build();

            when(sessionRepository.findByIdAndTenantIdAndDriveId(sessionId, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(session));

            // When/Then
            assertThatThrownBy(() -> storageService.completeUpload(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already completed");
        }
    }

    @Nested
    @DisplayName("Upload Cancellation Tests")
    class UploadCancellationTests {

        @Test
        @DisplayName("Should cancel simple upload and release reserved storage")
        void cancelUpload_SimpleUpload_Success() {
            // Given
            String sessionId = "session-cancel";
            UploadSession session = UploadSession.builder()
                    .id(sessionId)
                    .tenantId(TENANT_ID)
                    .driveId(DRIVE_ID)
                    .userId(USER_ID)
                    .totalSize(5 * 1024 * 1024L)
                    .status(UploadStatus.IN_PROGRESS)
                    .createdAt(Instant.now())
                    .build();

            when(sessionRepository.findByIdAndTenantIdAndDriveId(sessionId, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(session));
            when(sessionRepository.save(any(UploadSession.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            storageService.cancelUpload(sessionId);

            // Then
            verify(quotaRepository).updateReservedStorage(TENANT_ID, DRIVE_ID, -session.getTotalSize());
            verify(sessionRepository).save(sessionCaptor.capture());
            assertThat(sessionCaptor.getValue().getStatus()).isEqualTo(UploadStatus.CANCELLED);
        }

        @Test
        @DisplayName("Should abort multipart upload when cancelling")
        void cancelUpload_MultipartUpload_AbortsMultipart() {
            // Given
            String sessionId = "session-multipart-cancel";
            String uploadId = "upload-id-abort";
            UploadSession session = UploadSession.builder()
                    .id(sessionId)
                    .tenantId(TENANT_ID)
                    .driveId(DRIVE_ID)
                    .userId(USER_ID)
                    .totalSize(150 * 1024 * 1024L)
                    .bucket(DEFAULT_BUCKET)
                    .storageKey("tenant/drive/12345/video.mp4")
                    .uploadId(uploadId)
                    .status(UploadStatus.IN_PROGRESS)
                    .createdAt(Instant.now())
                    .build();

            when(sessionRepository.findByIdAndTenantIdAndDriveId(sessionId, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(session));
            when(sessionRepository.save(any(UploadSession.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            storageService.cancelUpload(sessionId);

            // Then
            verify(storageProvider).abortMultipartUpload(DEFAULT_BUCKET, session.getStorageKey(), uploadId);
            verify(quotaRepository).updateReservedStorage(TENANT_ID, DRIVE_ID, -session.getTotalSize());
        }

        @Test
        @DisplayName("Should fail when cancelling completed upload")
        void cancelUpload_CompletedUpload_ThrowsException() {
            // Given
            String sessionId = "session-completed-cancel";
            UploadSession session = UploadSession.builder()
                    .id(sessionId)
                    .tenantId(TENANT_ID)
                    .driveId(DRIVE_ID)
                    .status(UploadStatus.COMPLETED)
                    .build();

            when(sessionRepository.findByIdAndTenantIdAndDriveId(sessionId, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(session));

            // When/Then
            assertThatThrownBy(() -> storageService.cancelUpload(sessionId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot cancel completed upload");
        }
    }

    @Nested
    @DisplayName("Storage Key Generation Tests")
    class StorageKeyGenerationTests {

        @Test
        @DisplayName("Should generate unique storage keys with tenant/drive isolation")
        void generateStorageKey_ContainsTenantAndDrive() {
            // Given
            UploadInitRequest request = UploadInitRequest.builder()
                    .filename("test.pdf")
                    .fileSize(1024L)
                    .contentType("application/pdf")
                    .build();

            StorageQuota quota = createDefaultQuota();
            when(quotaRepository.findByTenantIdAndDriveId(TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(quota));
            when(storageProvider.generatePresignedUploadUrl(anyString(), anyString(), any(Duration.class), anyString()))
                    .thenReturn("http://minio/url");
            when(sessionRepository.save(any(UploadSession.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            UploadInitResponse response = storageService.initializeUpload(request);

            // Then
            assertThat(response.getStorageKey())
                    .contains(TENANT_ID)
                    .contains(DRIVE_ID);
        }

        @Test
        @DisplayName("Should sanitize filename in storage key")
        void generateStorageKey_SanitizesFilename() {
            // Given
            UploadInitRequest request = UploadInitRequest.builder()
                    .filename("file with spaces & special<chars>.pdf")
                    .fileSize(1024L)
                    .contentType("application/pdf")
                    .build();

            StorageQuota quota = createDefaultQuota();
            when(quotaRepository.findByTenantIdAndDriveId(TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(quota));
            when(storageProvider.generatePresignedUploadUrl(anyString(), anyString(), any(Duration.class), anyString()))
                    .thenReturn("http://minio/url");
            when(sessionRepository.save(any(UploadSession.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            UploadInitResponse response = storageService.initializeUpload(request);

            // Then
            assertThat(response.getStorageKey())
                    .doesNotContain(" ")
                    .doesNotContain("&")
                    .doesNotContain("<")
                    .doesNotContain(">");
        }
    }
}
