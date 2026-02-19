package com.teamsync.storage.service;

import com.teamsync.common.context.TenantContext;
import com.teamsync.common.exception.StorageQuotaExceededException;
import com.teamsync.common.storage.CloudStorageProvider;
import com.teamsync.common.storage.StorageTier;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for Storage Quota functionality.
 * Covers:
 * - Reserved storage tracking during uploads
 * - Used storage tracking after completion
 * - Atomic quota updates via MongoDB
 * - Quota validation before uploads
 * - Storage tier distribution
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Storage Quota Tests")
class StorageQuotaTest {

    @Mock
    private CloudStorageProvider storageProvider;

    @Mock
    private StorageQuotaRepository quotaRepository;

    @Mock
    private UploadSessionRepository sessionRepository;

    @InjectMocks
    private StorageService storageService;

    @Captor
    private ArgumentCaptor<StorageQuota> quotaCaptor;

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

        ReflectionTestUtils.setField(storageService, "defaultBucket", DEFAULT_BUCKET);
        ReflectionTestUtils.setField(storageService, "multipartThreshold", 104857600L);
        ReflectionTestUtils.setField(storageService, "defaultChunkSize", 10485760);
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
                .hotStorageUsed(0L)
                .warmStorageUsed(0L)
                .coldStorageUsed(0L)
                .archiveStorageUsed(0L)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private StorageQuota createQuotaWithUsage(long used, long reserved) {
        StorageQuota quota = createDefaultQuota();
        quota.setUsedStorage(used);
        quota.setReservedStorage(reserved);
        return quota;
    }

    @Nested
    @DisplayName("Reserved Storage Tests")
    class ReservedStorageTests {

        @Test
        @DisplayName("Should reserve storage during upload initialization")
        void initializeUpload_ReservesStorage() {
            // Given
            long fileSize = 50 * 1024 * 1024L; // 50MB
            UploadInitRequest request = UploadInitRequest.builder()
                    .filename("document.pdf")
                    .fileSize(fileSize)
                    .contentType("application/pdf")
                    .build();

            StorageQuota quota = createDefaultQuota();
            when(quotaRepository.findByTenantIdAndDriveId(TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(quota));
            when(storageProvider.generatePresignedUploadUrl(anyString(), anyString(), any(Duration.class), anyString()))
                    .thenReturn("http://minio/url");
            when(sessionRepository.save(any(UploadSession.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            storageService.initializeUpload(request);

            // Then
            verify(quotaRepository).updateReservedStorage(TENANT_ID, DRIVE_ID, fileSize);
        }

        @Test
        @DisplayName("Should release reserved storage on upload cancellation")
        void cancelUpload_ReleasesReservedStorage() {
            // Given
            String sessionId = "session-123";
            long fileSize = 50 * 1024 * 1024L;
            UploadSession session = UploadSession.builder()
                    .id(sessionId)
                    .tenantId(TENANT_ID)
                    .driveId(DRIVE_ID)
                    .userId(USER_ID)
                    .totalSize(fileSize)
                    .status(UploadStatus.IN_PROGRESS)
                    .createdAt(Instant.now())
                    .build();

            when(sessionRepository.findByIdAndTenantIdAndDriveId(sessionId, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(session));
            when(sessionRepository.save(any(UploadSession.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            storageService.cancelUpload(sessionId);

            // Then - reserved storage should be released (negative increment)
            verify(quotaRepository).updateReservedStorage(TENANT_ID, DRIVE_ID, -fileSize);
        }

        @Test
        @DisplayName("Should release reserved storage on upload failure")
        void completeUpload_Failure_ReleasesReservedStorage() {
            // Given
            String sessionId = "session-failed";
            long fileSize = 50 * 1024 * 1024L;
            UploadSession session = UploadSession.builder()
                    .id(sessionId)
                    .tenantId(TENANT_ID)
                    .driveId(DRIVE_ID)
                    .userId(USER_ID)
                    .totalSize(fileSize)
                    .bucket(DEFAULT_BUCKET)
                    .storageKey("tenant/drive/file.pdf")
                    .status(UploadStatus.IN_PROGRESS)
                    .createdAt(Instant.now())
                    .build();

            UploadCompleteRequest request = UploadCompleteRequest.builder()
                    .sessionId(sessionId)
                    .build();

            when(sessionRepository.findByIdAndTenantIdAndDriveId(sessionId, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(session));
            when(storageProvider.exists(anyString(), anyString())).thenReturn(false); // File not found
            when(sessionRepository.save(any(UploadSession.class))).thenAnswer(inv -> inv.getArgument(0));

            // When/Then
            assertThatThrownBy(() -> storageService.completeUpload(request))
                    .isInstanceOf(IllegalStateException.class);

            // Reserved storage should be released
            verify(quotaRepository).updateReservedStorage(TENANT_ID, DRIVE_ID, -fileSize);
        }

        @Test
        @DisplayName("Should consider reserved storage in available space calculation")
        void validateQuota_ConsidersReservedStorage() {
            // Given
            long quotaLimit = 10L * 1024 * 1024 * 1024; // 10 GB
            long usedStorage = 8L * 1024 * 1024 * 1024; // 8 GB used
            long reservedStorage = 1L * 1024 * 1024 * 1024; // 1 GB reserved
            // Available: 10 - 8 - 1 = 1 GB

            StorageQuota quota = createDefaultQuota();
            quota.setQuotaLimit(quotaLimit);
            quota.setUsedStorage(usedStorage);
            quota.setReservedStorage(reservedStorage);

            when(quotaRepository.findByTenantIdAndDriveId(TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(quota));

            // When trying to upload 2 GB file
            UploadInitRequest request = UploadInitRequest.builder()
                    .filename("large-file.zip")
                    .fileSize(2L * 1024 * 1024 * 1024) // 2 GB
                    .contentType("application/zip")
                    .build();

            // Then - should fail because only 1 GB available
            assertThatThrownBy(() -> storageService.initializeUpload(request))
                    .isInstanceOf(StorageQuotaExceededException.class)
                    .hasMessageContaining("Insufficient storage");
        }
    }

    @Nested
    @DisplayName("Used Storage Tests")
    class UsedStorageTests {

        @Test
        @DisplayName("Should convert reserved to used on upload completion")
        void completeUpload_ConvertsReservedToUsed() {
            // Given
            String sessionId = "session-complete";
            long fileSize = 50 * 1024 * 1024L;
            UploadSession session = UploadSession.builder()
                    .id(sessionId)
                    .tenantId(TENANT_ID)
                    .driveId(DRIVE_ID)
                    .userId(USER_ID)
                    .filename("document.pdf")
                    .contentType("application/pdf")
                    .totalSize(fileSize)
                    .bucket(DEFAULT_BUCKET)
                    .storageKey("tenant/drive/file.pdf")
                    .status(UploadStatus.IN_PROGRESS)
                    .createdAt(Instant.now())
                    .build();

            StorageQuota quota = createDefaultQuota();
            quota.setReservedStorage(fileSize);

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
            storageService.completeUpload(request);

            // Then - quota should be updated (reserved -> used)
            verify(quotaRepository).save(quotaCaptor.capture());
            StorageQuota savedQuota = quotaCaptor.getValue();

            assertThat(savedQuota.getReservedStorage()).isEqualTo(0L); // Reserved cleared
            assertThat(savedQuota.getUsedStorage()).isEqualTo(fileSize); // Used increased
            assertThat(savedQuota.getCurrentFileCount()).isEqualTo(1L); // File count increased
        }

        @Test
        @DisplayName("Should increment used storage for direct uploads")
        void directUpload_IncrementsUsedStorage() {
            // Given
            byte[] content = new byte[1024 * 1024]; // 1MB
            MultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", content);

            StorageQuota quota = createDefaultQuota();
            when(quotaRepository.findByTenantIdAndDriveId(TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(quota));

            // When
            storageService.uploadFileDirect(file, "application/pdf");

            // Then
            verify(quotaRepository).incrementUsage(TENANT_ID, DRIVE_ID, content.length, 1);
        }

        @Test
        @DisplayName("Should decrement used storage on file deletion")
        void deleteFile_DecrementsUsedStorage() {
            // Given
            String bucket = "teamsync-documents";
            String storageKey = "tenant/drive/file.pdf";
            long fileSize = 5 * 1024 * 1024L;

            when(storageProvider.getObjectSize(bucket, storageKey)).thenReturn(fileSize);

            // When
            storageService.deleteFile(bucket, storageKey);

            // Then
            verify(quotaRepository).incrementUsage(TENANT_ID, DRIVE_ID, -fileSize, -1);
            verify(storageProvider).delete(bucket, storageKey);
        }

        @Test
        @DisplayName("Should increment used storage on file copy")
        void copyFile_IncrementsUsedStorage() {
            // Given
            String sourceBucket = "teamsync-documents";
            String sourceKey = "tenant/drive/original.pdf";
            String destBucket = "teamsync-documents";
            String destKey = "tenant/drive/copy.pdf";
            long fileSize = 10 * 1024 * 1024L;

            StorageQuota quota = createDefaultQuota();
            when(quotaRepository.findByTenantIdAndDriveId(TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(quota));
            when(storageProvider.getObjectSize(sourceBucket, sourceKey)).thenReturn(fileSize);

            // When
            storageService.copyFile(sourceBucket, sourceKey, destBucket, destKey);

            // Then
            verify(quotaRepository).incrementUsage(TENANT_ID, DRIVE_ID, fileSize, 1);
            verify(storageProvider).copy(sourceBucket, sourceKey, destBucket, destKey);
        }
    }

    @Nested
    @DisplayName("Quota Validation Tests")
    class QuotaValidationTests {

        @Test
        @DisplayName("Should reject upload when quota is exceeded")
        void validateQuota_QuotaExceeded_RejectsUpload() {
            // Given
            StorageQuota quota = createDefaultQuota();
            quota.setUsedStorage(quota.getQuotaLimit()); // Full

            when(quotaRepository.findByTenantIdAndDriveId(TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(quota));

            UploadInitRequest request = UploadInitRequest.builder()
                    .filename("document.pdf")
                    .fileSize(1024L)
                    .contentType("application/pdf")
                    .build();

            // When/Then
            assertThatThrownBy(() -> storageService.initializeUpload(request))
                    .isInstanceOf(StorageQuotaExceededException.class)
                    .hasMessageContaining("Insufficient storage");
        }

        @Test
        @DisplayName("Should reject file exceeding max file size")
        void validateQuota_MaxFileSize_RejectsUpload() {
            // Given
            StorageQuota quota = createDefaultQuota();
            quota.setMaxFileSizeBytes(100 * 1024 * 1024L); // 100MB max

            when(quotaRepository.findByTenantIdAndDriveId(TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(quota));

            UploadInitRequest request = UploadInitRequest.builder()
                    .filename("huge-file.zip")
                    .fileSize(200 * 1024 * 1024L) // 200MB
                    .contentType("application/zip")
                    .build();

            // When/Then
            assertThatThrownBy(() -> storageService.initializeUpload(request))
                    .isInstanceOf(StorageQuotaExceededException.class)
                    .hasMessageContaining("File size exceeds maximum allowed");
        }

        @Test
        @DisplayName("Should create default quota if none exists")
        void validateQuota_NoQuota_CreatesDefault() {
            // Given
            when(quotaRepository.findByTenantIdAndDriveId(TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.empty());
            when(quotaRepository.save(any(StorageQuota.class))).thenAnswer(inv -> inv.getArgument(0));
            when(storageProvider.generatePresignedUploadUrl(anyString(), anyString(), any(Duration.class), anyString()))
                    .thenReturn("http://minio/url");
            when(sessionRepository.save(any(UploadSession.class))).thenAnswer(inv -> inv.getArgument(0));

            UploadInitRequest request = UploadInitRequest.builder()
                    .filename("document.pdf")
                    .fileSize(1024L)
                    .contentType("application/pdf")
                    .build();

            // When
            storageService.initializeUpload(request);

            // Then - default quota should be created
            verify(quotaRepository).save(quotaCaptor.capture());
            StorageQuota createdQuota = quotaCaptor.getValue();

            assertThat(createdQuota.getTenantId()).isEqualTo(TENANT_ID);
            assertThat(createdQuota.getDriveId()).isEqualTo(DRIVE_ID);
            assertThat(createdQuota.getQuotaLimit()).isEqualTo(10L * 1024 * 1024 * 1024); // 10 GB default
        }

        @Test
        @DisplayName("Should validate quota on file copy")
        void copyFile_QuotaExceeded_RejectsCopy() {
            // Given
            StorageQuota quota = createDefaultQuota();
            quota.setUsedStorage(quota.getQuotaLimit() - 1024L); // Almost full

            when(quotaRepository.findByTenantIdAndDriveId(TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(quota));
            when(storageProvider.getObjectSize(anyString(), anyString()))
                    .thenReturn(10 * 1024 * 1024L); // 10MB file

            // When/Then
            assertThatThrownBy(() -> storageService.copyFile(
                    "bucket", "source", "bucket", "dest"))
                    .isInstanceOf(StorageQuotaExceededException.class);
        }
    }

    @Nested
    @DisplayName("Get Storage Quota Tests")
    class GetStorageQuotaTests {

        @Test
        @DisplayName("Should return formatted quota information")
        void getStorageQuota_ReturnsFormattedInfo() {
            // Given
            StorageQuota quota = createDefaultQuota();
            quota.setUsedStorage(5L * 1024 * 1024 * 1024); // 5 GB used
            quota.setCurrentFileCount(100L);
            quota.setHotStorageUsed(4L * 1024 * 1024 * 1024);
            quota.setWarmStorageUsed(1L * 1024 * 1024 * 1024);

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
            assertThat(result.getAvailableStorage()).isEqualTo(5L * 1024 * 1024 * 1024);
            assertThat(result.getUsagePercentage()).isEqualTo(50.0);
            assertThat(result.getCurrentFileCount()).isEqualTo(100L);
            assertThat(result.getHotStorageUsed()).isEqualTo(4L * 1024 * 1024 * 1024);
            assertThat(result.getWarmStorageUsed()).isEqualTo(1L * 1024 * 1024 * 1024);

            // Formatted values
            assertThat(result.getQuotaLimitFormatted()).contains("GB");
            assertThat(result.getUsedStorageFormatted()).contains("GB");
        }

        @Test
        @DisplayName("Should indicate near quota limit")
        void getStorageQuota_NearLimit_SetsWarningFlag() {
            // Given
            StorageQuota quota = createDefaultQuota();
            quota.setUsedStorage((long) (quota.getQuotaLimit() * 0.95)); // 95% used

            when(quotaRepository.findByTenantIdAndDriveId(TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(quota));

            // When
            StorageQuotaDTO result = storageService.getStorageQuota();

            // Then
            assertThat(result.getIsNearQuotaLimit()).isTrue();
            assertThat(result.getIsQuotaExceeded()).isFalse();
        }

        @Test
        @DisplayName("Should indicate quota exceeded")
        void getStorageQuota_Exceeded_SetsExceededFlag() {
            // Given
            StorageQuota quota = createDefaultQuota();
            quota.setUsedStorage(quota.getQuotaLimit()); // 100% used

            when(quotaRepository.findByTenantIdAndDriveId(TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(quota));

            // When
            StorageQuotaDTO result = storageService.getStorageQuota();

            // Then
            assertThat(result.getIsQuotaExceeded()).isTrue();
        }
    }

    @Nested
    @DisplayName("Update Storage Quota Tests")
    class UpdateStorageQuotaTests {

        @Test
        @DisplayName("Should update quota limit for drive")
        void updateStorageQuota_Success() {
            // Given
            StorageQuota quota = createDefaultQuota();
            Long newLimit = 20L * 1024 * 1024 * 1024; // 20 GB

            when(quotaRepository.findByTenantIdAndDriveId(TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(quota));
            when(quotaRepository.save(any(StorageQuota.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            StorageQuotaDTO result = storageService.updateStorageQuota(DRIVE_ID, newLimit);

            // Then
            verify(quotaRepository).save(quotaCaptor.capture());
            StorageQuota savedQuota = quotaCaptor.getValue();

            assertThat(savedQuota.getQuotaLimit()).isEqualTo(newLimit);
            assertThat(savedQuota.getLastUpdatedBy()).isEqualTo(USER_ID);
            assertThat(savedQuota.getUpdatedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Storage Tier Tests")
    class StorageTierTests {

        @Test
        @DisplayName("Should change storage tier")
        void changeStorageTier_Success() {
            // Given
            String bucket = "teamsync-documents";
            String storageKey = "tenant/drive/archive-file.pdf";
            StorageTier newTier = StorageTier.COLD;

            // When
            storageService.changeStorageTier(bucket, storageKey, newTier);

            // Then
            verify(storageProvider).setStorageClass(bucket, storageKey, newTier);
        }
    }

    @Nested
    @DisplayName("Byte Formatting Tests")
    class ByteFormattingTests {

        @Test
        @DisplayName("Should format bytes correctly")
        void formatBytes_VariousSizes() {
            // Given
            StorageQuota quota = createDefaultQuota();

            // Test various sizes
            quota.setQuotaLimit(1024L); // 1 KB
            when(quotaRepository.findByTenantIdAndDriveId(TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(quota));

            StorageQuotaDTO result = storageService.getStorageQuota();
            assertThat(result.getQuotaLimitFormatted()).isEqualTo("1.00 KB");

            // 1 MB
            quota.setQuotaLimit(1024L * 1024);
            result = storageService.getStorageQuota();
            assertThat(result.getQuotaLimitFormatted()).isEqualTo("1.00 MB");

            // 1 GB
            quota.setQuotaLimit(1024L * 1024 * 1024);
            result = storageService.getStorageQuota();
            assertThat(result.getQuotaLimitFormatted()).isEqualTo("1.00 GB");

            // 1 TB
            quota.setQuotaLimit(1024L * 1024 * 1024 * 1024);
            result = storageService.getStorageQuota();
            assertThat(result.getQuotaLimitFormatted()).isEqualTo("1.00 TB");
        }

        @Test
        @DisplayName("Should handle zero bytes")
        void formatBytes_Zero() {
            // Given
            StorageQuota quota = createDefaultQuota();
            quota.setUsedStorage(0L);

            when(quotaRepository.findByTenantIdAndDriveId(TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(quota));

            // When
            StorageQuotaDTO result = storageService.getStorageQuota();

            // Then
            assertThat(result.getUsedStorageFormatted()).isEqualTo("0 B");
        }
    }
}
