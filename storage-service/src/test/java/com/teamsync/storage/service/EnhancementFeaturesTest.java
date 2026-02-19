package com.teamsync.storage.service;

import com.teamsync.common.context.TenantContext;
import com.teamsync.common.exception.ChecksumMismatchException;
import com.teamsync.common.exception.ResourceNotFoundException;
import com.teamsync.common.storage.CloudStorageProvider;
import com.teamsync.storage.dto.*;
import com.teamsync.storage.model.StorageQuota;
import com.teamsync.storage.model.UploadSession;
import com.teamsync.storage.model.UploadSession.UploadPart;
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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for the 5 enhancements implemented in Phase 2:
 *
 * Enhancement 1: Permission Caching (Request-Scoped) - Already implemented, tested separately
 * Enhancement 2: Checksum Verification
 * Enhancement 3: Audit Logging (Kafka Events)
 * Enhancement 4: Concurrent Edit Detection (Optimistic Locking) - Tested in content-service
 * Enhancement 5: Multipart Upload Resumption
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Storage Service Enhancement Features Tests")
class EnhancementFeaturesTest {

    @Mock
    private CloudStorageProvider storageProvider;

    @Mock
    private StorageQuotaRepository quotaRepository;

    @Mock
    private UploadSessionRepository sessionRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private StorageService storageService;

    @Captor
    private ArgumentCaptor<UploadSession> sessionCaptor;

    @Captor
    private ArgumentCaptor<Map<String, Object>> eventCaptor;

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
                .reservedStorage(1024L)
                .maxFileCount(10000L)
                .currentFileCount(100L)
                .maxFileSizeBytes(1L * 1024 * 1024 * 1024)  // 1 GB max file
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

    // ==========================================================================
    // Enhancement 2: Checksum Verification Tests
    // ==========================================================================
    @Nested
    @DisplayName("Enhancement 2: Checksum Verification Tests")
    class ChecksumVerificationTests {

        @Test
        @DisplayName("Should verify checksum successfully when matching")
        void completeUpload_WithMatchingChecksum_Success() {
            // Given
            UploadSession session = createTestUploadSession();
            String expectedChecksum = "abc123def456";

            UploadCompleteRequest request = UploadCompleteRequest.builder()
                    .sessionId(SESSION_ID)
                    .checksum(expectedChecksum)
                    .checksumAlgorithm("MD5")
                    .build();

            StorageQuota quota = createTestQuota();

            when(sessionRepository.findByIdAndTenantIdAndDriveId(SESSION_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(session));
            when(storageProvider.exists(BUCKET, STORAGE_KEY)).thenReturn(true);
            when(storageProvider.getObjectETag(BUCKET, STORAGE_KEY)).thenReturn(expectedChecksum);
            when(quotaRepository.findByTenantIdAndDriveId(TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(quota));
            when(quotaRepository.save(any(StorageQuota.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(sessionRepository.save(any(UploadSession.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(kafkaTemplate.send(anyString(), anyString(), any()))
                    .thenReturn(CompletableFuture.completedFuture(null));

            // When
            UploadCompleteResponse response = storageService.completeUpload(request);

            // Then
            assertThat(response.getSuccess()).isTrue();
            verify(storageProvider).getObjectETag(BUCKET, STORAGE_KEY);
        }

        @Test
        @DisplayName("Should verify checksum with quoted ETag")
        void completeUpload_WithQuotedChecksum_Success() {
            // Given
            UploadSession session = createTestUploadSession();
            String checksumWithQuotes = "\"abc123def456\"";
            String checksumWithoutQuotes = "abc123def456";

            UploadCompleteRequest request = UploadCompleteRequest.builder()
                    .sessionId(SESSION_ID)
                    .checksum(checksumWithQuotes)
                    .build();

            StorageQuota quota = createTestQuota();

            when(sessionRepository.findByIdAndTenantIdAndDriveId(SESSION_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(session));
            when(storageProvider.exists(BUCKET, STORAGE_KEY)).thenReturn(true);
            when(storageProvider.getObjectETag(BUCKET, STORAGE_KEY)).thenReturn(checksumWithoutQuotes);
            when(quotaRepository.findByTenantIdAndDriveId(TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(quota));
            when(quotaRepository.save(any(StorageQuota.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(sessionRepository.save(any(UploadSession.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(kafkaTemplate.send(anyString(), anyString(), any()))
                    .thenReturn(CompletableFuture.completedFuture(null));

            // When
            UploadCompleteResponse response = storageService.completeUpload(request);

            // Then
            assertThat(response.getSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should throw ChecksumMismatchException when checksum doesn't match")
        void completeUpload_WithMismatchedChecksum_ThrowsException() {
            // Given
            UploadSession session = createTestUploadSession();
            String expectedChecksum = "expected123";
            String actualChecksum = "actual456";

            UploadCompleteRequest request = UploadCompleteRequest.builder()
                    .sessionId(SESSION_ID)
                    .checksum(expectedChecksum)
                    .build();

            when(sessionRepository.findByIdAndTenantIdAndDriveId(SESSION_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(session));
            when(storageProvider.exists(BUCKET, STORAGE_KEY)).thenReturn(true);
            when(storageProvider.getObjectETag(BUCKET, STORAGE_KEY)).thenReturn(actualChecksum);
            when(sessionRepository.save(any(UploadSession.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When/Then
            assertThatThrownBy(() -> storageService.completeUpload(request))
                    .isInstanceOf(ChecksumMismatchException.class)
                    .hasMessageContaining(STORAGE_KEY)
                    .hasMessageContaining(expectedChecksum)
                    .hasMessageContaining(actualChecksum);

            // Verify reserved storage is released on failure
            verify(quotaRepository).updateReservedStorage(TENANT_ID, DRIVE_ID, -1024L);
        }

        @Test
        @DisplayName("Should skip checksum verification when not provided")
        void completeUpload_WithoutChecksum_SkipsVerification() {
            // Given
            UploadSession session = createTestUploadSession();

            UploadCompleteRequest request = UploadCompleteRequest.builder()
                    .sessionId(SESSION_ID)
                    .build();

            StorageQuota quota = createTestQuota();

            when(sessionRepository.findByIdAndTenantIdAndDriveId(SESSION_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(session));
            when(storageProvider.exists(BUCKET, STORAGE_KEY)).thenReturn(true);
            when(quotaRepository.findByTenantIdAndDriveId(TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(quota));
            when(quotaRepository.save(any(StorageQuota.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(sessionRepository.save(any(UploadSession.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(kafkaTemplate.send(anyString(), anyString(), any()))
                    .thenReturn(CompletableFuture.completedFuture(null));

            // When
            UploadCompleteResponse response = storageService.completeUpload(request);

            // Then
            assertThat(response.getSuccess()).isTrue();
            verify(storageProvider, never()).getObjectETag(anyString(), anyString());
        }

        @Test
        @DisplayName("Should verify checksum case-insensitively")
        void completeUpload_WithDifferentCaseChecksum_Success() {
            // Given
            UploadSession session = createTestUploadSession();
            String expectedChecksum = "ABC123DEF456";
            String actualChecksum = "abc123def456";

            UploadCompleteRequest request = UploadCompleteRequest.builder()
                    .sessionId(SESSION_ID)
                    .checksum(expectedChecksum)
                    .build();

            StorageQuota quota = createTestQuota();

            when(sessionRepository.findByIdAndTenantIdAndDriveId(SESSION_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(session));
            when(storageProvider.exists(BUCKET, STORAGE_KEY)).thenReturn(true);
            when(storageProvider.getObjectETag(BUCKET, STORAGE_KEY)).thenReturn(actualChecksum);
            when(quotaRepository.findByTenantIdAndDriveId(TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(quota));
            when(quotaRepository.save(any(StorageQuota.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(sessionRepository.save(any(UploadSession.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(kafkaTemplate.send(anyString(), anyString(), any()))
                    .thenReturn(CompletableFuture.completedFuture(null));

            // When
            UploadCompleteResponse response = storageService.completeUpload(request);

            // Then
            assertThat(response.getSuccess()).isTrue();
        }
    }

    // ==========================================================================
    // Enhancement 3: Audit Logging (Kafka Events) Tests
    // ==========================================================================
    @Nested
    @DisplayName("Enhancement 3: Audit Logging (Kafka Events) Tests")
    class AuditLoggingTests {

        @Test
        @DisplayName("Should publish UPLOAD_COMPLETED event on successful upload")
        void completeUpload_Success_PublishesEvent() {
            // Given
            UploadSession session = createTestUploadSession();
            StorageQuota quota = createTestQuota();

            UploadCompleteRequest request = UploadCompleteRequest.builder()
                    .sessionId(SESSION_ID)
                    .build();

            when(sessionRepository.findByIdAndTenantIdAndDriveId(SESSION_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(session));
            when(storageProvider.exists(BUCKET, STORAGE_KEY)).thenReturn(true);
            when(quotaRepository.findByTenantIdAndDriveId(TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(quota));
            when(quotaRepository.save(any(StorageQuota.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(sessionRepository.save(any(UploadSession.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(kafkaTemplate.send(anyString(), anyString(), any()))
                    .thenReturn(CompletableFuture.completedFuture(null));

            // When
            storageService.completeUpload(request);

            // Then
            verify(kafkaTemplate).send(
                    eq("teamsync.storage.events"),
                    eq(STORAGE_KEY),
                    eventCaptor.capture()
            );

            Map<String, Object> event = eventCaptor.getValue();
            assertThat(event.get("eventType")).isEqualTo("UPLOAD_COMPLETED");
            assertThat(event.get("tenantId")).isEqualTo(TENANT_ID);
            assertThat(event.get("driveId")).isEqualTo(DRIVE_ID);
            assertThat(event.get("userId")).isEqualTo(USER_ID);
            assertThat(event.get("bucket")).isEqualTo(BUCKET);
            assertThat(event.get("storageKey")).isEqualTo(STORAGE_KEY);
            assertThat(event.get("outcome")).isEqualTo("SUCCESS");
            assertThat(event.get("timestamp")).isNotNull();
        }

        @Test
        @DisplayName("Should publish UPLOAD_CANCELLED event on cancel")
        void cancelUpload_PublishesEvent() {
            // Given
            UploadSession session = createTestUploadSession();

            when(sessionRepository.findByIdAndTenantIdAndDriveId(SESSION_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(session));
            when(sessionRepository.save(any(UploadSession.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(kafkaTemplate.send(anyString(), anyString(), any()))
                    .thenReturn(CompletableFuture.completedFuture(null));

            // When
            storageService.cancelUpload(SESSION_ID);

            // Then
            verify(kafkaTemplate).send(
                    eq("teamsync.storage.events"),
                    eq(STORAGE_KEY),
                    eventCaptor.capture()
            );

            Map<String, Object> event = eventCaptor.getValue();
            assertThat(event.get("eventType")).isEqualTo("UPLOAD_CANCELLED");
            assertThat(event.get("outcome")).isEqualTo("SUCCESS");
        }

        @Test
        @DisplayName("Should publish FILE_DELETED event on delete")
        void deleteFile_PublishesEvent() {
            // Given
            long fileSize = 2048L;
            when(storageProvider.getObjectSize(BUCKET, STORAGE_KEY)).thenReturn(fileSize);
            when(kafkaTemplate.send(anyString(), anyString(), any()))
                    .thenReturn(CompletableFuture.completedFuture(null));

            // When
            storageService.deleteFile(BUCKET, STORAGE_KEY);

            // Then
            verify(kafkaTemplate).send(
                    eq("teamsync.storage.events"),
                    eq(STORAGE_KEY),
                    eventCaptor.capture()
            );

            Map<String, Object> event = eventCaptor.getValue();
            assertThat(event.get("eventType")).isEqualTo("FILE_DELETED");
            assertThat(event.get("fileSize")).isEqualTo(fileSize);
        }

        @Test
        @DisplayName("Should publish FILE_DOWNLOADED event on download")
        void downloadFile_PublishesEvent() {
            // Given
            long fileSize = 1024L;
            String contentType = "application/pdf";
            java.io.InputStream mockStream = new java.io.ByteArrayInputStream("test".getBytes());

            when(storageProvider.getObjectSize(BUCKET, STORAGE_KEY)).thenReturn(fileSize);
            when(storageProvider.getContentType(BUCKET, STORAGE_KEY)).thenReturn(contentType);
            when(storageProvider.download(BUCKET, STORAGE_KEY)).thenReturn(mockStream);
            when(kafkaTemplate.send(anyString(), anyString(), any()))
                    .thenReturn(CompletableFuture.completedFuture(null));

            // When
            StorageService.FileDownload download = storageService.downloadFile(BUCKET, STORAGE_KEY);

            // Then
            assertThat(download).isNotNull();
            verify(kafkaTemplate).send(
                    eq("teamsync.storage.events"),
                    eq(STORAGE_KEY),
                    eventCaptor.capture()
            );

            Map<String, Object> event = eventCaptor.getValue();
            assertThat(event.get("eventType")).isEqualTo("FILE_DOWNLOADED");
            assertThat(event.get("fileSize")).isEqualTo(fileSize);
        }

        @Test
        @DisplayName("Should publish FILE_COPIED event on copy")
        void copyFile_PublishesEvent() {
            // Given
            String destKey = "tenant-123/drive-456/dest/file.pdf";
            long fileSize = 1024L;
            StorageQuota quota = createTestQuota();

            when(storageProvider.getObjectSize(BUCKET, STORAGE_KEY)).thenReturn(fileSize);
            when(quotaRepository.findByTenantIdAndDriveId(TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(quota));
            when(kafkaTemplate.send(anyString(), anyString(), any()))
                    .thenReturn(CompletableFuture.completedFuture(null));

            // When
            storageService.copyFile(BUCKET, STORAGE_KEY, BUCKET, destKey);

            // Then
            verify(kafkaTemplate).send(
                    eq("teamsync.storage.events"),
                    eq(destKey),
                    eventCaptor.capture()
            );

            Map<String, Object> event = eventCaptor.getValue();
            assertThat(event.get("eventType")).isEqualTo("FILE_COPIED");
        }

        @Test
        @DisplayName("Should handle Kafka publishing failure gracefully")
        void completeUpload_KafkaFailure_DoesNotFailOperation() {
            // Given
            UploadSession session = createTestUploadSession();
            StorageQuota quota = createTestQuota();

            UploadCompleteRequest request = UploadCompleteRequest.builder()
                    .sessionId(SESSION_ID)
                    .build();

            when(sessionRepository.findByIdAndTenantIdAndDriveId(SESSION_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(session));
            when(storageProvider.exists(BUCKET, STORAGE_KEY)).thenReturn(true);
            when(quotaRepository.findByTenantIdAndDriveId(TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(quota));
            when(quotaRepository.save(any(StorageQuota.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(sessionRepository.save(any(UploadSession.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            // Simulate Kafka failure
            when(kafkaTemplate.send(anyString(), anyString(), any()))
                    .thenThrow(new RuntimeException("Kafka unavailable"));

            // When
            UploadCompleteResponse response = storageService.completeUpload(request);

            // Then - operation should still succeed even if Kafka fails
            assertThat(response.getSuccess()).isTrue();
        }
    }

    // ==========================================================================
    // Enhancement 5: Multipart Upload Resumption Tests
    // ==========================================================================
    @Nested
    @DisplayName("Enhancement 5: Multipart Upload Resumption Tests")
    class MultipartResumptionTests {

        @Test
        @DisplayName("Should return upload status for in-progress session")
        void getUploadStatus_InProgress_ReturnsStatus() {
            // Given
            UploadSession session = createTestUploadSession();
            session.setTotalParts(10);
            session.setUploadedSize(512L);
            session.setCompletedParts(List.of(
                    UploadPart.builder().partNumber(1).etag("etag1").build(),
                    UploadPart.builder().partNumber(2).etag("etag2").build(),
                    UploadPart.builder().partNumber(3).etag("etag3").build()
            ));

            when(sessionRepository.findByIdAndTenantIdAndDriveId(SESSION_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(session));

            // When
            UploadStatusResponse response = storageService.getUploadStatus(SESSION_ID);

            // Then
            assertThat(response.getSessionId()).isEqualTo(SESSION_ID);
            assertThat(response.getStatus()).isEqualTo("IN_PROGRESS");
            assertThat(response.getTotalParts()).isEqualTo(10);
            assertThat(response.getCompletedParts()).containsExactly(1, 2, 3);
            assertThat(response.getUploadedSize()).isEqualTo(512L);
            assertThat(response.getTotalSize()).isEqualTo(1024L);
            assertThat(response.getCanResume()).isTrue();
            assertThat(response.getProgressPercent()).isEqualTo(50.0);
        }

        @Test
        @DisplayName("Should indicate cannot resume when session expired")
        void getUploadStatus_Expired_CannotResume() {
            // Given
            UploadSession session = createTestUploadSession();
            session.setExpiresAt(Instant.now().minus(Duration.ofHours(1))); // Expired

            when(sessionRepository.findByIdAndTenantIdAndDriveId(SESSION_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(session));

            // When
            UploadStatusResponse response = storageService.getUploadStatus(SESSION_ID);

            // Then
            assertThat(response.getCanResume()).isFalse();
        }

        @Test
        @DisplayName("Should indicate cannot resume when session completed")
        void getUploadStatus_Completed_CannotResume() {
            // Given
            UploadSession session = createTestUploadSession();
            session.setStatus(UploadStatus.COMPLETED);

            when(sessionRepository.findByIdAndTenantIdAndDriveId(SESSION_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(session));

            // When
            UploadStatusResponse response = storageService.getUploadStatus(SESSION_ID);

            // Then
            assertThat(response.getStatus()).isEqualTo("COMPLETED");
            assertThat(response.getCanResume()).isFalse();
        }

        @Test
        @DisplayName("Should indicate cannot resume when session cancelled")
        void getUploadStatus_Cancelled_CannotResume() {
            // Given
            UploadSession session = createTestUploadSession();
            session.setStatus(UploadStatus.CANCELLED);

            when(sessionRepository.findByIdAndTenantIdAndDriveId(SESSION_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(session));

            // When
            UploadStatusResponse response = storageService.getUploadStatus(SESSION_ID);

            // Then
            assertThat(response.getStatus()).isEqualTo("CANCELLED");
            assertThat(response.getCanResume()).isFalse();
        }

        @Test
        @DisplayName("Should throw exception when session not found")
        void getUploadStatus_SessionNotFound_ThrowsException() {
            // Given
            when(sessionRepository.findByIdAndTenantIdAndDriveId(SESSION_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> storageService.getUploadStatus(SESSION_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Upload session not found");
        }

        @Test
        @DisplayName("Should return empty completed parts when none uploaded")
        void getUploadStatus_NoPartsUploaded_ReturnsEmptyList() {
            // Given
            UploadSession session = createTestUploadSession();
            session.setTotalParts(10);
            session.setCompletedParts(null);

            when(sessionRepository.findByIdAndTenantIdAndDriveId(SESSION_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(session));

            // When
            UploadStatusResponse response = storageService.getUploadStatus(SESSION_ID);

            // Then
            assertThat(response.getCompletedParts()).isEmpty();
            assertThat(response.getProgressPercent()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Should calculate progress percentage correctly")
        void getUploadStatus_CalculatesProgressCorrectly() {
            // Given
            UploadSession session = createTestUploadSession();
            session.setTotalSize(1000L);
            session.setUploadedSize(333L);

            when(sessionRepository.findByIdAndTenantIdAndDriveId(SESSION_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(session));

            // When
            UploadStatusResponse response = storageService.getUploadStatus(SESSION_ID);

            // Then
            assertThat(response.getProgressPercent()).isEqualTo(33.3);
        }

        @Test
        @DisplayName("Should return storage location details")
        void getUploadStatus_ReturnsStorageLocation() {
            // Given
            UploadSession session = createTestUploadSession();

            when(sessionRepository.findByIdAndTenantIdAndDriveId(SESSION_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(session));

            // When
            UploadStatusResponse response = storageService.getUploadStatus(SESSION_ID);

            // Then
            assertThat(response.getBucket()).isEqualTo(BUCKET);
            assertThat(response.getStorageKey()).isEqualTo(STORAGE_KEY);
            assertThat(response.getExpiresAt()).isNotNull();
        }

        @Test
        @DisplayName("Should handle zero total size gracefully")
        void getUploadStatus_ZeroTotalSize_ReturnsZeroProgress() {
            // Given
            UploadSession session = createTestUploadSession();
            session.setTotalSize(0L);

            when(sessionRepository.findByIdAndTenantIdAndDriveId(SESSION_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(session));

            // When
            UploadStatusResponse response = storageService.getUploadStatus(SESSION_ID);

            // Then
            assertThat(response.getProgressPercent()).isEqualTo(0.0);
        }
    }
}
