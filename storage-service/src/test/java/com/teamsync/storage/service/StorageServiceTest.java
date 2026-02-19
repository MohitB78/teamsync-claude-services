package com.teamsync.storage.service;

import com.teamsync.common.context.TenantContext;
import com.teamsync.common.exception.ResourceNotFoundException;
import com.teamsync.common.exception.StorageQuotaExceededException;
import com.teamsync.common.storage.CloudStorageProvider;
import com.teamsync.common.storage.StorageTier;
import com.teamsync.storage.dto.*;
import com.teamsync.storage.model.StorageQuota;
import com.teamsync.storage.model.UploadSession;
import com.teamsync.storage.model.UploadSession.UploadStatus;
import com.teamsync.storage.repository.StorageQuotaRepository;
import com.teamsync.storage.repository.UploadSessionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("StorageService Tests")
class StorageServiceTest {

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

    @Captor
    private ArgumentCaptor<StorageQuota> quotaCaptor;

    private MockedStatic<TenantContext> tenantContextMock;

    private static final String TENANT_ID = "tenant-123";
    private static final String DRIVE_ID = "drive-456";
    private static final String USER_ID = "user-789";
    private static final String SESSION_ID = "session-001";
    private static final String BUCKET = "teamsync-documents";
    private static final String STORAGE_KEY = "tenant-123/drive-456/12345678/abc12345_test-file.pdf";

    @BeforeEach
    void setUp() {
        tenantContextMock = mockStatic(TenantContext.class);
        tenantContextMock.when(TenantContext::getTenantId).thenReturn(TENANT_ID);
        tenantContextMock.when(TenantContext::getDriveId).thenReturn(DRIVE_ID);
        tenantContextMock.when(TenantContext::getUserId).thenReturn(USER_ID);

        // Set @Value fields via reflection
        ReflectionTestUtils.setField(storageService, "defaultBucket", BUCKET);
        ReflectionTestUtils.setField(storageService, "multipartThreshold", 104857600L); // 100MB
        ReflectionTestUtils.setField(storageService, "defaultChunkSize", 10485760);     // 10MB
        ReflectionTestUtils.setField(storageService, "urlExpirySeconds", 3600);
    }

    @AfterEach
    void tearDown() {
        tenantContextMock.close();
    }

    private StorageQuota createTestQuota() {
        return StorageQuota.builder()
                .id("quota-001")
                .tenantId(TENANT_ID)
                .driveId(DRIVE_ID)
                .quotaLimit(10L * 1024 * 1024 * 1024)  // 10 GB
                .usedStorage(1L * 1024 * 1024 * 1024)   // 1 GB used
                .reservedStorage(0L)
                .maxFileCount(10000L)
                .currentFileCount(100L)
                .maxFileSizeBytes(1L * 1024 * 1024 * 1024)  // 1 GB max file
                .hotStorageUsed(0L)
                .warmStorageUsed(0L)
                .coldStorageUsed(0L)
                .archiveStorageUsed(0L)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private UploadSession createTestUploadSession() {
        return UploadSession.builder()
                .id(SESSION_ID)
                .tenantId(TENANT_ID)
                .driveId(DRIVE_ID)
                .userId(USER_ID)
                .filename("test-file.pdf")
                .contentType("application/pdf")
                .totalSize(1024L)
                .uploadedSize(0L)
                .bucket(BUCKET)
                .storageKey(STORAGE_KEY)
                .chunkSize(10485760)
                .status(UploadStatus.IN_PROGRESS)
                .expiresAt(Instant.now().plus(Duration.ofHours(24)))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("initializeUpload Tests")
    class InitializeUploadTests {

        @Test
        @DisplayName("Should initialize simple upload for small files")
        void initializeUpload_SmallFile_ReturnsSimpleUpload() {
            // Given
            UploadInitRequest request = UploadInitRequest.builder()
                    .filename("test-file.pdf")
                    .fileSize(1024L)
                    .contentType("application/pdf")
                    .build();

            StorageQuota quota = createTestQuota();
            String presignedUrl = "https://storage.example.com/upload-url";

            when(quotaRepository.findByTenantIdAndDriveId(TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(quota));
            when(storageProvider.generatePresignedUploadUrl(anyString(), anyString(), any(Duration.class), anyString()))
                    .thenReturn(presignedUrl);
            when(sessionRepository.save(any(UploadSession.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            UploadInitResponse response = storageService.initializeUpload(request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getUploadType()).isEqualTo("SIMPLE");
            assertThat(response.getUploadUrl()).isEqualTo(presignedUrl);
            assertThat(response.getSessionId()).isNotNull();
            assertThat(response.getBucket()).isEqualTo(BUCKET);
            assertThat(response.getPartUrls()).isNull();

            verify(quotaRepository).updateReservedStorage(TENANT_ID, DRIVE_ID, 1024L);
            verify(sessionRepository).save(sessionCaptor.capture());

            UploadSession savedSession = sessionCaptor.getValue();
            assertThat(savedSession.getFilename()).isEqualTo("test-file.pdf");
            assertThat(savedSession.getStatus()).isEqualTo(UploadStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("Should initialize multipart upload for large files")
        void initializeUpload_LargeFile_ReturnsMultipartUpload() {
            // Given
            long largeFileSize = 200L * 1024 * 1024; // 200 MB
            UploadInitRequest request = UploadInitRequest.builder()
                    .filename("large-file.zip")
                    .fileSize(largeFileSize)
                    .contentType("application/zip")
                    .build();

            StorageQuota quota = createTestQuota();
            String uploadId = "multipart-upload-123";

            when(quotaRepository.findByTenantIdAndDriveId(TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(quota));
            when(storageProvider.initiateMultipartUpload(anyString(), anyString(), anyString()))
                    .thenReturn(uploadId);
            when(storageProvider.generatePresignedPartUploadUrl(anyString(), anyString(), anyString(), anyInt(), any(Duration.class)))
                    .thenReturn("https://storage.example.com/part-url");
            when(sessionRepository.save(any(UploadSession.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            UploadInitResponse response = storageService.initializeUpload(request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getUploadType()).isEqualTo("MULTIPART");
            assertThat(response.getUploadUrl()).isNull();
            assertThat(response.getPartUrls()).isNotEmpty();
            assertThat(response.getTotalParts()).isEqualTo(20); // 200MB / 10MB = 20 parts

            verify(storageProvider).initiateMultipartUpload(eq(BUCKET), anyString(), eq("application/zip"));
        }

        @Test
        @DisplayName("Should force multipart upload when requested")
        void initializeUpload_ForceMultipart_ReturnsMultipartUpload() {
            // Given
            UploadInitRequest request = UploadInitRequest.builder()
                    .filename("test-file.pdf")
                    .fileSize(1024L)
                    .useMultipart(true)
                    .build();

            StorageQuota quota = createTestQuota();

            when(quotaRepository.findByTenantIdAndDriveId(TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(quota));
            when(storageProvider.initiateMultipartUpload(anyString(), anyString(), anyString()))
                    .thenReturn("upload-id");
            when(storageProvider.generatePresignedPartUploadUrl(anyString(), anyString(), anyString(), anyInt(), any(Duration.class)))
                    .thenReturn("https://storage.example.com/part-url");
            when(sessionRepository.save(any(UploadSession.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            UploadInitResponse response = storageService.initializeUpload(request);

            // Then
            assertThat(response.getUploadType()).isEqualTo("MULTIPART");
        }

        @Test
        @DisplayName("Should throw exception when quota exceeded")
        void initializeUpload_QuotaExceeded_ThrowsException() {
            // Given
            UploadInitRequest request = UploadInitRequest.builder()
                    .filename("huge-file.zip")
                    .fileSize(20L * 1024 * 1024 * 1024) // 20 GB - exceeds 10 GB quota
                    .build();

            StorageQuota quota = createTestQuota();

            when(quotaRepository.findByTenantIdAndDriveId(TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(quota));

            // When/Then
            assertThatThrownBy(() -> storageService.initializeUpload(request))
                    .isInstanceOf(StorageQuotaExceededException.class)
                    .hasMessageContaining("Insufficient storage");

            verify(sessionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw exception when file size exceeds max")
        void initializeUpload_FileTooLarge_ThrowsException() {
            // Given
            UploadInitRequest request = UploadInitRequest.builder()
                    .filename("huge-file.zip")
                    .fileSize(2L * 1024 * 1024 * 1024) // 2 GB - exceeds 1 GB max file size
                    .build();

            StorageQuota quota = createTestQuota();
            quota.setUsedStorage(0L); // Ensure there's quota space

            when(quotaRepository.findByTenantIdAndDriveId(TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(quota));

            // When/Then
            assertThatThrownBy(() -> storageService.initializeUpload(request))
                    .isInstanceOf(StorageQuotaExceededException.class)
                    .hasMessageContaining("exceeds maximum allowed");
        }

        @Test
        @DisplayName("Should detect content type from filename when not provided")
        void initializeUpload_NoContentType_DetectsFromFilename() {
            // Given
            UploadInitRequest request = UploadInitRequest.builder()
                    .filename("document.docx")
                    .fileSize(1024L)
                    .build();

            StorageQuota quota = createTestQuota();

            when(quotaRepository.findByTenantIdAndDriveId(TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(quota));
            when(storageProvider.generatePresignedUploadUrl(anyString(), anyString(), any(Duration.class), anyString()))
                    .thenReturn("https://storage.example.com/upload-url");
            when(sessionRepository.save(any(UploadSession.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            storageService.initializeUpload(request);

            // Then
            verify(sessionRepository).save(sessionCaptor.capture());
            UploadSession savedSession = sessionCaptor.getValue();
            // Apache Tika should detect .docx as Word document
            assertThat(savedSession.getContentType()).isNotNull();
        }
    }

    @Nested
    @DisplayName("completeUpload Tests")
    class CompleteUploadTests {

        @Test
        @DisplayName("Should complete simple upload successfully")
        void completeUpload_SimpleUpload_Success() {
            // Given
            UploadSession session = createTestUploadSession();

            UploadCompleteRequest request = UploadCompleteRequest.builder()
                    .sessionId(SESSION_ID)
                    .build();

            StorageQuota quota = createTestQuota();
            quota.setReservedStorage(1024L);

            when(sessionRepository.findByIdAndTenantId(SESSION_ID, TENANT_ID))
                    .thenReturn(Optional.of(session));
            when(storageProvider.exists(BUCKET, STORAGE_KEY)).thenReturn(true);
            when(quotaRepository.findByTenantIdAndDriveId(TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(quota));
            when(quotaRepository.save(any(StorageQuota.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(sessionRepository.save(any(UploadSession.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            UploadCompleteResponse response = storageService.completeUpload(request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getSuccess()).isTrue();
            assertThat(response.getStorageKey()).isEqualTo(STORAGE_KEY);
            assertThat(response.getBucket()).isEqualTo(BUCKET);
            assertThat(response.getFilename()).isEqualTo("test-file.pdf");

            verify(sessionRepository).save(sessionCaptor.capture());
            UploadSession completedSession = sessionCaptor.getValue();
            assertThat(completedSession.getStatus()).isEqualTo(UploadStatus.COMPLETED);
        }

        @Test
        @DisplayName("Should complete multipart upload successfully")
        void completeUpload_MultipartUpload_Success() {
            // Given
            UploadSession session = createTestUploadSession();
            session.setUploadId("multipart-upload-123");
            session.setTotalParts(3);

            List<UploadCompleteRequest.PartETag> parts = List.of(
                    UploadCompleteRequest.PartETag.builder().partNumber(1).etag("etag1").build(),
                    UploadCompleteRequest.PartETag.builder().partNumber(2).etag("etag2").build(),
                    UploadCompleteRequest.PartETag.builder().partNumber(3).etag("etag3").build()
            );

            UploadCompleteRequest request = UploadCompleteRequest.builder()
                    .sessionId(SESSION_ID)
                    .parts(parts)
                    .build();

            StorageQuota quota = createTestQuota();
            quota.setReservedStorage(1024L);

            when(sessionRepository.findByIdAndTenantId(SESSION_ID, TENANT_ID))
                    .thenReturn(Optional.of(session));
            when(storageProvider.exists(BUCKET, STORAGE_KEY)).thenReturn(true);
            when(quotaRepository.findByTenantIdAndDriveId(TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(quota));
            when(quotaRepository.save(any(StorageQuota.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(sessionRepository.save(any(UploadSession.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            UploadCompleteResponse response = storageService.completeUpload(request);

            // Then
            assertThat(response.getSuccess()).isTrue();
            verify(storageProvider).completeMultipartUpload(
                    eq(BUCKET),
                    eq(STORAGE_KEY),
                    eq("multipart-upload-123"),
                    any(Map.class)
            );
        }

        @Test
        @DisplayName("Should throw exception when session not found")
        void completeUpload_SessionNotFound_ThrowsException() {
            // Given
            UploadCompleteRequest request = UploadCompleteRequest.builder()
                    .sessionId("non-existent-session")
                    .build();

            when(sessionRepository.findByIdAndTenantId("non-existent-session", TENANT_ID))
                    .thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> storageService.completeUpload(request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Upload session not found");
        }

        @Test
        @DisplayName("Should throw exception when upload already completed")
        void completeUpload_AlreadyCompleted_ThrowsException() {
            // Given
            UploadSession session = createTestUploadSession();
            session.setStatus(UploadStatus.COMPLETED);

            UploadCompleteRequest request = UploadCompleteRequest.builder()
                    .sessionId(SESSION_ID)
                    .build();

            when(sessionRepository.findByIdAndTenantId(SESSION_ID, TENANT_ID))
                    .thenReturn(Optional.of(session));

            // When/Then
            assertThatThrownBy(() -> storageService.completeUpload(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already completed");
        }

        @Test
        @DisplayName("Should throw exception when session is cancelled")
        void completeUpload_SessionCancelled_ThrowsException() {
            // Given
            UploadSession session = createTestUploadSession();
            session.setStatus(UploadStatus.CANCELLED);

            UploadCompleteRequest request = UploadCompleteRequest.builder()
                    .sessionId(SESSION_ID)
                    .build();

            when(sessionRepository.findByIdAndTenantId(SESSION_ID, TENANT_ID))
                    .thenReturn(Optional.of(session));

            // When/Then
            assertThatThrownBy(() -> storageService.completeUpload(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no longer valid");
        }

        @Test
        @DisplayName("Should throw exception when file not found in storage")
        void completeUpload_FileNotInStorage_ThrowsException() {
            // Given
            UploadSession session = createTestUploadSession();

            UploadCompleteRequest request = UploadCompleteRequest.builder()
                    .sessionId(SESSION_ID)
                    .build();

            when(sessionRepository.findByIdAndTenantId(SESSION_ID, TENANT_ID))
                    .thenReturn(Optional.of(session));
            when(storageProvider.exists(BUCKET, STORAGE_KEY)).thenReturn(false);
            when(sessionRepository.save(any(UploadSession.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When/Then
            assertThatThrownBy(() -> storageService.completeUpload(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not found in storage");

            // Should release reserved storage on failure
            verify(quotaRepository).updateReservedStorage(TENANT_ID, DRIVE_ID, -1024L);
        }
    }

    @Nested
    @DisplayName("cancelUpload Tests")
    class CancelUploadTests {

        @Test
        @DisplayName("Should cancel simple upload successfully")
        void cancelUpload_SimpleUpload_Success() {
            // Given
            UploadSession session = createTestUploadSession();

            when(sessionRepository.findByIdAndTenantId(SESSION_ID, TENANT_ID))
                    .thenReturn(Optional.of(session));
            when(sessionRepository.save(any(UploadSession.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            storageService.cancelUpload(SESSION_ID);

            // Then
            verify(quotaRepository).updateReservedStorage(TENANT_ID, DRIVE_ID, -1024L);
            verify(sessionRepository).save(sessionCaptor.capture());

            UploadSession cancelledSession = sessionCaptor.getValue();
            assertThat(cancelledSession.getStatus()).isEqualTo(UploadStatus.CANCELLED);
        }

        @Test
        @DisplayName("Should abort multipart upload when cancelling")
        void cancelUpload_MultipartUpload_AbortsMultipart() {
            // Given
            UploadSession session = createTestUploadSession();
            session.setUploadId("multipart-upload-123");

            when(sessionRepository.findByIdAndTenantId(SESSION_ID, TENANT_ID))
                    .thenReturn(Optional.of(session));
            when(sessionRepository.save(any(UploadSession.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            storageService.cancelUpload(SESSION_ID);

            // Then
            verify(storageProvider).abortMultipartUpload(BUCKET, STORAGE_KEY, "multipart-upload-123");
            verify(quotaRepository).updateReservedStorage(TENANT_ID, DRIVE_ID, -1024L);
        }

        @Test
        @DisplayName("Should throw exception when cancelling completed upload")
        void cancelUpload_AlreadyCompleted_ThrowsException() {
            // Given
            UploadSession session = createTestUploadSession();
            session.setStatus(UploadStatus.COMPLETED);

            when(sessionRepository.findByIdAndTenantId(SESSION_ID, TENANT_ID))
                    .thenReturn(Optional.of(session));

            // When/Then
            assertThatThrownBy(() -> storageService.cancelUpload(SESSION_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot cancel completed upload");
        }

        @Test
        @DisplayName("Should throw exception when session not found")
        void cancelUpload_SessionNotFound_ThrowsException() {
            // Given
            when(sessionRepository.findByIdAndTenantId("non-existent", TENANT_ID))
                    .thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> storageService.cancelUpload("non-existent"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Upload session not found");
        }
    }

    @Nested
    @DisplayName("generateDownloadUrl Tests")
    class GenerateDownloadUrlTests {

        @Test
        @DisplayName("Should generate presigned download URL")
        void generateDownloadUrl_Success() {
            // Given
            String expectedUrl = "https://storage.example.com/download-url";

            when(storageProvider.generatePresignedUrl(eq(BUCKET), eq(STORAGE_KEY), any(Duration.class)))
                    .thenReturn(expectedUrl);
            when(storageProvider.getObjectSize(BUCKET, STORAGE_KEY)).thenReturn(1024L);
            when(storageProvider.getContentType(BUCKET, STORAGE_KEY)).thenReturn("application/pdf");

            // When
            DownloadUrlResponse response = storageService.generateDownloadUrl(BUCKET, STORAGE_KEY, "test-file.pdf", 3600);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getUrl()).isEqualTo(expectedUrl);
            assertThat(response.getFilename()).isEqualTo("test-file.pdf");
            assertThat(response.getContentType()).isEqualTo("application/pdf");
            assertThat(response.getFileSize()).isEqualTo(1024L);
            assertThat(response.getExpiresInSeconds()).isEqualTo(3600);
        }
    }

    @Nested
    @DisplayName("deleteFile Tests")
    class DeleteFileTests {

        @Test
        @DisplayName("Should delete file and update quota")
        void deleteFile_Success() {
            // Given
            when(storageProvider.getObjectSize(BUCKET, STORAGE_KEY)).thenReturn(2048L);

            // When
            storageService.deleteFile(BUCKET, STORAGE_KEY);

            // Then
            verify(storageProvider).delete(BUCKET, STORAGE_KEY);
            verify(quotaRepository).incrementUsage(TENANT_ID, DRIVE_ID, -2048L, -1);
        }
    }

    @Nested
    @DisplayName("copyFile Tests")
    class CopyFileTests {

        @Test
        @DisplayName("Should copy file and update quota")
        void copyFile_Success() {
            // Given
            String destKey = "tenant-123/drive-456/new-path/copied-file.pdf";
            StorageQuota quota = createTestQuota();

            when(storageProvider.getObjectSize(BUCKET, STORAGE_KEY)).thenReturn(1024L);
            when(quotaRepository.findByTenantIdAndDriveId(TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(quota));

            // When
            String result = storageService.copyFile(BUCKET, STORAGE_KEY, BUCKET, destKey);

            // Then
            assertThat(result).isEqualTo(destKey);
            verify(storageProvider).copy(BUCKET, STORAGE_KEY, BUCKET, destKey);
            verify(quotaRepository).incrementUsage(TENANT_ID, DRIVE_ID, 1024L, 1);
        }

        @Test
        @DisplayName("Should throw exception when quota insufficient for copy")
        void copyFile_QuotaInsufficient_ThrowsException() {
            // Given
            StorageQuota quota = createTestQuota();
            quota.setUsedStorage(9L * 1024 * 1024 * 1024); // 9 GB used of 10 GB
            long fileToCopySize = 2L * 1024 * 1024 * 1024; // 2 GB file

            when(storageProvider.getObjectSize(BUCKET, STORAGE_KEY)).thenReturn(fileToCopySize);
            when(quotaRepository.findByTenantIdAndDriveId(TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(quota));

            // When/Then
            assertThatThrownBy(() -> storageService.copyFile(BUCKET, STORAGE_KEY, BUCKET, "dest-key"))
                    .isInstanceOf(StorageQuotaExceededException.class);

            verify(storageProvider, never()).copy(anyString(), anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("changeStorageTier Tests")
    class ChangeStorageTierTests {

        @Test
        @DisplayName("Should change storage tier successfully")
        void changeStorageTier_Success() {
            // When
            storageService.changeStorageTier(BUCKET, STORAGE_KEY, StorageTier.WARM);

            // Then
            verify(storageProvider).setStorageClass(BUCKET, STORAGE_KEY, StorageTier.WARM);
        }
    }

    @Nested
    @DisplayName("getStorageQuota Tests")
    class GetStorageQuotaTests {

        @Test
        @DisplayName("Should return existing quota")
        void getStorageQuota_ExistingQuota_ReturnsDTO() {
            // Given
            StorageQuota quota = createTestQuota();
            quota.setUsedStorage(5L * 1024 * 1024 * 1024); // 5 GB used

            when(quotaRepository.findByTenantIdAndDriveId(TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(quota));

            // When
            StorageQuotaDTO result = storageService.getStorageQuota();

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getTenantId()).isEqualTo(TENANT_ID);
            assertThat(result.getDriveId()).isEqualTo(DRIVE_ID);
            assertThat(result.getQuotaLimit()).isEqualTo(10L * 1024 * 1024 * 1024);
            assertThat(result.getUsedStorage()).isEqualTo(5L * 1024 * 1024 * 1024);
            assertThat(result.getUsagePercentage()).isEqualTo(50.0);
            assertThat(result.getIsQuotaExceeded()).isFalse();
            assertThat(result.getIsNearQuotaLimit()).isFalse();
        }

        @Test
        @DisplayName("Should create default quota when none exists")
        void getStorageQuota_NoExistingQuota_CreatesDefault() {
            // Given
            when(quotaRepository.findByTenantIdAndDriveId(TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.empty());
            when(quotaRepository.save(any(StorageQuota.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            StorageQuotaDTO result = storageService.getStorageQuota();

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getQuotaLimit()).isEqualTo(10L * 1024 * 1024 * 1024); // 10 GB default
            assertThat(result.getUsedStorage()).isEqualTo(0L);
            assertThat(result.getUsagePercentage()).isEqualTo(0.0);

            verify(quotaRepository).save(quotaCaptor.capture());
            StorageQuota savedQuota = quotaCaptor.getValue();
            assertThat(savedQuota.getTenantId()).isEqualTo(TENANT_ID);
            assertThat(savedQuota.getDriveId()).isEqualTo(DRIVE_ID);
        }

        @Test
        @DisplayName("Should indicate near quota limit when over 90%")
        void getStorageQuota_NearLimit_IndicatesNearLimit() {
            // Given
            StorageQuota quota = createTestQuota();
            quota.setUsedStorage((long) (9.5 * 1024 * 1024 * 1024)); // 9.5 GB of 10 GB (95%)

            when(quotaRepository.findByTenantIdAndDriveId(TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(quota));

            // When
            StorageQuotaDTO result = storageService.getStorageQuota();

            // Then
            assertThat(result.getIsNearQuotaLimit()).isTrue();
            assertThat(result.getUsagePercentage()).isGreaterThan(90.0);
        }

        @Test
        @DisplayName("Should indicate quota exceeded when at or over limit")
        void getStorageQuota_Exceeded_IndicatesExceeded() {
            // Given
            StorageQuota quota = createTestQuota();
            quota.setUsedStorage(10L * 1024 * 1024 * 1024); // Full 10 GB used

            when(quotaRepository.findByTenantIdAndDriveId(TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(quota));

            // When
            StorageQuotaDTO result = storageService.getStorageQuota();

            // Then
            assertThat(result.getIsQuotaExceeded()).isTrue();
        }
    }

    @Nested
    @DisplayName("updateStorageQuota Tests")
    class UpdateStorageQuotaTests {

        @Test
        @DisplayName("Should update quota limit successfully")
        void updateStorageQuota_Success() {
            // Given
            StorageQuota quota = createTestQuota();
            Long newLimit = 20L * 1024 * 1024 * 1024; // 20 GB

            when(quotaRepository.findByTenantIdAndDriveId(TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(quota));
            when(quotaRepository.save(any(StorageQuota.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            StorageQuotaDTO result = storageService.updateStorageQuota(DRIVE_ID, newLimit);

            // Then
            assertThat(result).isNotNull();

            verify(quotaRepository).save(quotaCaptor.capture());
            StorageQuota savedQuota = quotaCaptor.getValue();
            assertThat(savedQuota.getQuotaLimit()).isEqualTo(newLimit);
            assertThat(savedQuota.getLastUpdatedBy()).isEqualTo(USER_ID);
        }

        @Test
        @DisplayName("Should create quota if none exists when updating")
        void updateStorageQuota_CreatesIfNotExists() {
            // Given
            Long newLimit = 15L * 1024 * 1024 * 1024; // 15 GB

            when(quotaRepository.findByTenantIdAndDriveId(TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.empty());
            when(quotaRepository.save(any(StorageQuota.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            StorageQuotaDTO result = storageService.updateStorageQuota(DRIVE_ID, newLimit);

            // Then
            assertThat(result).isNotNull();

            // Called twice: once for createDefaultQuota, once for the update
            verify(quotaRepository).save(quotaCaptor.capture());
        }
    }

    @Nested
    @DisplayName("Quota Calculation Tests")
    class QuotaCalculationTests {

        @Test
        @DisplayName("Should format bytes correctly")
        void formatBytes_VariousSizes() {
            // We test this indirectly through getStorageQuota
            StorageQuota quota = createTestQuota();
            quota.setQuotaLimit(1L * 1024 * 1024 * 1024); // 1 GB
            quota.setUsedStorage(512L * 1024 * 1024);      // 512 MB

            when(quotaRepository.findByTenantIdAndDriveId(TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(quota));

            StorageQuotaDTO result = storageService.getStorageQuota();

            assertThat(result.getQuotaLimitFormatted()).isEqualTo("1.00 GB");
            assertThat(result.getUsedStorageFormatted()).isEqualTo("512.00 MB");
        }

        @Test
        @DisplayName("Should calculate available storage correctly with reservations")
        void calculateAvailableStorage_WithReservations() {
            // Given
            StorageQuota quota = createTestQuota();
            quota.setQuotaLimit(10L * 1024 * 1024 * 1024);   // 10 GB limit
            quota.setUsedStorage(5L * 1024 * 1024 * 1024);    // 5 GB used
            quota.setReservedStorage(1L * 1024 * 1024 * 1024); // 1 GB reserved

            when(quotaRepository.findByTenantIdAndDriveId(TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(quota));

            // The available calculation in mapToQuotaDTO doesn't include reserved
            // But the validation does: available = limit - used - reserved
            StorageQuotaDTO result = storageService.getStorageQuota();

            // DTO shows limit - used (doesn't expose reserved directly in available)
            assertThat(result.getAvailableStorage()).isEqualTo(5L * 1024 * 1024 * 1024);
        }
    }
}
