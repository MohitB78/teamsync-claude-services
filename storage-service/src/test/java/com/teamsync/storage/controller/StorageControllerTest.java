package com.teamsync.storage.controller;

import com.teamsync.common.dto.ApiResponse;
import com.teamsync.common.storage.StorageTier;
import com.teamsync.storage.dto.*;
import com.teamsync.storage.service.StorageService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for StorageController.
 * Tests all REST endpoints and edge cases.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Storage Controller Tests")
class StorageControllerTest {

    @InjectMocks
    private StorageController controller;

    @Mock
    private StorageService storageService;

    @Nested
    @DisplayName("Initialize Upload Tests")
    class InitializeUploadTests {

        @Test
        @DisplayName("Should initialize simple upload successfully")
        void initializeUpload_Simple_ReturnsCreated() {
            // Given
            UploadInitRequest request = UploadInitRequest.builder()
                    .filename("test.pdf")
                    .fileSize(5_000_000L)
                    .contentType("application/pdf")
                    .useMultipart(false)
                    .build();

            UploadInitResponse response = UploadInitResponse.builder()
                    .sessionId("session-123")
                    .uploadUrl("http://storage/presigned-url")
                    .uploadType("SIMPLE")
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();

            when(storageService.initializeUpload(request)).thenReturn(response);

            // When
            ResponseEntity<ApiResponse<UploadInitResponse>> result = controller.initializeUpload(request);

            // Then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(result.getBody()).isNotNull();
            assertThat(result.getBody().isSuccess()).isTrue();
            assertThat(result.getBody().getData().getSessionId()).isEqualTo("session-123");
        }

        @Test
        @DisplayName("Should initialize multipart upload successfully")
        void initializeUpload_Multipart_ReturnsCreated() {
            // Given
            UploadInitRequest request = UploadInitRequest.builder()
                    .filename("large-file.zip")
                    .fileSize(500_000_000L) // 500MB
                    .contentType("application/zip")
                    .useMultipart(true)
                    .chunkSize(10_000_000) // 10MB chunks
                    .build();

            UploadInitResponse response = UploadInitResponse.builder()
                    .sessionId("session-multipart")
                    .uploadType("MULTIPART")
                    .totalParts(50)
                    .chunkSize(10_000_000)
                    .partUrls(List.of(
                            UploadInitResponse.PartUploadUrl.builder().partNumber(1).uploadUrl("url1").build(),
                            UploadInitResponse.PartUploadUrl.builder().partNumber(2).uploadUrl("url2").build(),
                            UploadInitResponse.PartUploadUrl.builder().partNumber(3).uploadUrl("url3").build()
                    ))
                    .build();

            when(storageService.initializeUpload(request)).thenReturn(response);

            // When
            ResponseEntity<ApiResponse<UploadInitResponse>> result = controller.initializeUpload(request);

            // Then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(result.getBody().getData().getUploadType()).isEqualTo("MULTIPART");
            assertThat(result.getBody().getData().getTotalParts()).isEqualTo(50);
        }
    }

    @Nested
    @DisplayName("Complete Upload Tests")
    class CompleteUploadTests {

        @Test
        @DisplayName("Should complete simple upload")
        void completeUpload_Simple_ReturnsOk() {
            // Given
            UploadCompleteRequest request = UploadCompleteRequest.builder()
                    .sessionId("session-123")
                    .build();

            UploadCompleteResponse response = UploadCompleteResponse.builder()
                    .storageKey("tenant/drive/doc.pdf")
                    .bucket("teamsync-documents")
                    .filename("test.pdf")
                    .fileSize(5_000_000L)
                    .success(true)
                    .build();

            when(storageService.completeUpload(request)).thenReturn(response);

            // When
            ResponseEntity<ApiResponse<UploadCompleteResponse>> result = controller.completeUpload(request);

            // Then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result.getBody().isSuccess()).isTrue();
            assertThat(result.getBody().getData().getStorageKey()).isEqualTo("tenant/drive/doc.pdf");
        }

        @Test
        @DisplayName("Should complete multipart upload with ETags")
        void completeUpload_Multipart_ReturnsOk() {
            // Given
            UploadCompleteRequest request = UploadCompleteRequest.builder()
                    .sessionId("session-multipart")
                    .parts(List.of(
                            UploadCompleteRequest.PartETag.builder()
                                    .partNumber(1)
                                    .etag("etag-1")
                                    .build(),
                            UploadCompleteRequest.PartETag.builder()
                                    .partNumber(2)
                                    .etag("etag-2")
                                    .build()
                    ))
                    .build();

            UploadCompleteResponse response = UploadCompleteResponse.builder()
                    .storageKey("tenant/drive/large.zip")
                    .bucket("teamsync-documents")
                    .success(true)
                    .build();

            when(storageService.completeUpload(request)).thenReturn(response);

            // When
            ResponseEntity<ApiResponse<UploadCompleteResponse>> result = controller.completeUpload(request);

            // Then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result.getBody().isSuccess()).isTrue();
        }
    }

    @Nested
    @DisplayName("Cancel Upload Tests")
    class CancelUploadTests {

        @Test
        @DisplayName("Should cancel upload successfully")
        void cancelUpload_ReturnsOk() {
            // Given
            String sessionId = "session-to-cancel";
            doNothing().when(storageService).cancelUpload(sessionId);

            // When
            ResponseEntity<ApiResponse<Void>> result = controller.cancelUpload(sessionId);

            // Then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result.getBody().isSuccess()).isTrue();
            assertThat(result.getBody().getMessage()).contains("cancelled");
            verify(storageService).cancelUpload(sessionId);
        }
    }

    @Nested
    @DisplayName("Get Upload Status Tests")
    class GetUploadStatusTests {

        @Test
        @DisplayName("Should return upload status")
        void getUploadStatus_ReturnsStatus() {
            // Given
            String sessionId = "session-123";
            UploadStatusResponse response = UploadStatusResponse.builder()
                    .status("IN_PROGRESS")
                    .totalParts(10)
                    .completedParts(List.of(1, 2, 3, 4, 5))
                    .uploadedSize(50_000_000L)
                    .totalSize(100_000_000L)
                    .progressPercent(50.0)
                    .canResume(true)
                    .build();

            when(storageService.getUploadStatus(sessionId)).thenReturn(response);

            // When
            ResponseEntity<ApiResponse<UploadStatusResponse>> result = controller.getUploadStatus(sessionId);

            // Then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result.getBody().getData().getProgressPercent()).isEqualTo(50.0);
            assertThat(result.getBody().getData().getCanResume()).isTrue();
        }
    }

    @Nested
    @DisplayName("Direct Upload Tests")
    class DirectUploadTests {

        @Test
        @DisplayName("Should upload file directly")
        void uploadDirect_ReturnsCreated() {
            // Given
            byte[] fileContent = "test file content".getBytes();
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "test.txt",
                    "text/plain",
                    fileContent
            );

            DirectUploadResponse response = DirectUploadResponse.builder()
                    .bucket("teamsync-documents")
                    .storageKey("tenant/drive/test.txt")
                    .fileSize((long) fileContent.length)
                    .contentType("text/plain")
                    .build();

            when(storageService.uploadFileDirect(file, "text/plain")).thenReturn(response);

            // When
            ResponseEntity<ApiResponse<DirectUploadResponse>> result =
                    controller.uploadDirect(file, "text/plain");

            // Then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(result.getBody().isSuccess()).isTrue();
            assertThat(result.getBody().getData().getStorageKey()).contains("tenant/drive");
        }
    }

    @Nested
    @DisplayName("Download URL Tests")
    class DownloadUrlTests {

        @Test
        @DisplayName("Should generate download URL")
        void getDownloadUrl_ReturnsUrl() {
            // Given
            String bucket = "teamsync-documents";
            String storageKey = "tenant/drive/doc.pdf";
            String filename = "document.pdf";
            int expirySeconds = 3600;

            DownloadUrlResponse response = DownloadUrlResponse.builder()
                    .url("http://storage/presigned-download-url")
                    .filename(filename)
                    .contentType("application/pdf")
                    .fileSize(1024000L)
                    .expiresInSeconds(expirySeconds)
                    .build();

            when(storageService.generateDownloadUrl(bucket, storageKey, filename, expirySeconds))
                    .thenReturn(response);

            // When
            ResponseEntity<ApiResponse<DownloadUrlResponse>> result =
                    controller.getDownloadUrl(bucket, storageKey, filename, expirySeconds);

            // Then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result.getBody().getData().getUrl()).contains("presigned-download-url");
        }

        @Test
        @DisplayName("Should limit expiry to 24 hours")
        void getDownloadUrl_LimitsExpiry() {
            // Given
            String bucket = "bucket";
            String storageKey = "key";
            int requestedExpiry = 100000; // More than 24 hours
            int maxExpiry = 86400;

            DownloadUrlResponse response = DownloadUrlResponse.builder()
                    .url("http://storage/url")
                    .expiresInSeconds(maxExpiry)
                    .build();

            // Should be called with capped expiry
            when(storageService.generateDownloadUrl(bucket, storageKey, null, maxExpiry))
                    .thenReturn(response);

            // When
            controller.getDownloadUrl(bucket, storageKey, null, requestedExpiry);

            // Then
            verify(storageService).generateDownloadUrl(bucket, storageKey, null, maxExpiry);
        }
    }

    @Nested
    @DisplayName("Download File Tests")
    class DownloadFileTests {

        private HttpServletRequest createInternalServiceRequest() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Internal-Service", "content-service");
            return request;
        }

        @Test
        @DisplayName("Should stream file download")
        void downloadFile_ReturnsStream() {
            // Given
            String bucket = "teamsync-documents";
            String storageKey = "tenant/drive/doc.pdf";
            byte[] fileContent = "PDF content".getBytes();
            InputStream inputStream = new ByteArrayInputStream(fileContent);
            HttpServletRequest request = createInternalServiceRequest();

            StorageService.FileDownload download = new StorageService.FileDownload(
                    inputStream,
                    "application/pdf",
                    fileContent.length
            );

            when(storageService.downloadFile(bucket, storageKey)).thenReturn(download);

            // When
            ResponseEntity<InputStreamResource> result =
                    controller.downloadFile(bucket, storageKey, "document.pdf", request);

            // Then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
            assertThat(result.getHeaders().getContentLength()).isEqualTo(fileContent.length);
            assertThat(result.getHeaders().getContentDisposition().getFilename()).isEqualTo("document.pdf");
        }

        @Test
        @DisplayName("Should extract filename from storage key when not provided")
        void downloadFile_NoFilename_ExtractsFromKey() {
            // Given
            String bucket = "teamsync-documents";
            String storageKey = "tenant/drive/extracted-name.docx";
            byte[] content = "content".getBytes();
            HttpServletRequest request = createInternalServiceRequest();

            StorageService.FileDownload download = new StorageService.FileDownload(
                    new ByteArrayInputStream(content),
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    content.length
            );

            when(storageService.downloadFile(bucket, storageKey)).thenReturn(download);

            // When
            ResponseEntity<InputStreamResource> result =
                    controller.downloadFile(bucket, storageKey, null, request);

            // Then
            assertThat(result.getHeaders().getContentDisposition().getFilename())
                    .isEqualTo("extracted-name.docx");
        }

        @Test
        @DisplayName("Should handle storage key without path separator")
        void downloadFile_SimpleKey_UsesAsFilename() {
            // Given
            String bucket = "teamsync-documents";
            String storageKey = "simple-file.txt";
            byte[] content = "content".getBytes();
            HttpServletRequest request = createInternalServiceRequest();

            StorageService.FileDownload download = new StorageService.FileDownload(
                    new ByteArrayInputStream(content),
                    "text/plain",
                    content.length
            );

            when(storageService.downloadFile(bucket, storageKey)).thenReturn(download);

            // When
            ResponseEntity<InputStreamResource> result =
                    controller.downloadFile(bucket, storageKey, "", request);

            // Then
            assertThat(result.getHeaders().getContentDisposition().getFilename())
                    .isEqualTo("simple-file.txt");
        }
    }

    @Nested
    @DisplayName("Delete File Tests")
    class DeleteFileTests {

        @Test
        @DisplayName("Should delete file successfully")
        void deleteFile_ReturnsOk() {
            // Given
            String bucket = "teamsync-documents";
            String storageKey = "tenant/drive/to-delete.pdf";

            doNothing().when(storageService).deleteFile(bucket, storageKey);

            // When
            ResponseEntity<ApiResponse<Void>> result = controller.deleteFile(bucket, storageKey);

            // Then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result.getBody().isSuccess()).isTrue();
            assertThat(result.getBody().getMessage()).contains("deleted");
            verify(storageService).deleteFile(bucket, storageKey);
        }
    }

    @Nested
    @DisplayName("Copy File Tests")
    class CopyFileTests {

        @Test
        @DisplayName("Should copy file successfully")
        void copyFile_ReturnsNewKey() {
            // Given
            FileCopyRequest request = FileCopyRequest.builder()
                    .sourceBucket("source-bucket")
                    .sourceKey("source/key.pdf")
                    .destBucket("dest-bucket")
                    .destKey("dest/key.pdf")
                    .build();

            when(storageService.copyFile("source-bucket", "source/key.pdf", "dest-bucket", "dest/key.pdf"))
                    .thenReturn("dest/key.pdf");

            // When
            ResponseEntity<ApiResponse<Map<String, String>>> result = controller.copyFile(request);

            // Then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result.getBody().isSuccess()).isTrue();
            assertThat(result.getBody().getData()).containsEntry("storageKey", "dest/key.pdf");
            assertThat(result.getBody().getData()).containsEntry("bucket", "dest-bucket");
        }
    }

    @Nested
    @DisplayName("Change Storage Tier Tests")
    class ChangeStorageTierTests {

        @Test
        @DisplayName("Should change to HOT tier")
        void changeStorageTier_Hot_ReturnsOk() {
            // Given
            String bucket = "bucket";
            String storageKey = "key";
            String tier = "HOT";

            doNothing().when(storageService).changeStorageTier(bucket, storageKey, StorageTier.HOT);

            // When
            ResponseEntity<ApiResponse<Void>> result =
                    controller.changeStorageTier(bucket, storageKey, tier);

            // Then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result.getBody().getMessage()).contains("HOT");
            verify(storageService).changeStorageTier(bucket, storageKey, StorageTier.HOT);
        }

        @Test
        @DisplayName("Should change to WARM tier")
        void changeStorageTier_Warm_ReturnsOk() {
            // Given
            doNothing().when(storageService).changeStorageTier(any(), any(), eq(StorageTier.WARM));

            // When
            ResponseEntity<ApiResponse<Void>> result =
                    controller.changeStorageTier("bucket", "key", "warm"); // lowercase

            // Then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(storageService).changeStorageTier("bucket", "key", StorageTier.WARM);
        }

        @Test
        @DisplayName("Should change to COLD tier")
        void changeStorageTier_Cold_ReturnsOk() {
            // Given
            doNothing().when(storageService).changeStorageTier(any(), any(), eq(StorageTier.COLD));

            // When
            ResponseEntity<ApiResponse<Void>> result =
                    controller.changeStorageTier("bucket", "key", "COLD");

            // Then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(storageService).changeStorageTier("bucket", "key", StorageTier.COLD);
        }

        @Test
        @DisplayName("Should change to ARCHIVE tier")
        void changeStorageTier_Archive_ReturnsOk() {
            // Given
            doNothing().when(storageService).changeStorageTier(any(), any(), eq(StorageTier.ARCHIVE));

            // When
            ResponseEntity<ApiResponse<Void>> result =
                    controller.changeStorageTier("bucket", "key", "ARCHIVE");

            // Then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(storageService).changeStorageTier("bucket", "key", StorageTier.ARCHIVE);
        }

        @Test
        @DisplayName("Should throw exception for invalid tier")
        void changeStorageTier_Invalid_ThrowsException() {
            // Given/When/Then
            assertThatThrownBy(() ->
                    controller.changeStorageTier("bucket", "key", "INVALID_TIER"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Get Storage Quota Tests")
    class GetStorageQuotaTests {

        @Test
        @DisplayName("Should return storage quota")
        void getStorageQuota_ReturnsQuota() {
            // Given
            StorageQuotaDTO quota = StorageQuotaDTO.builder()
                    .quotaLimit(10_000_000_000L)
                    .usedStorage(5_000_000_000L)
                    .availableStorage(5_000_000_000L)
                    .usagePercentage(50.0)
                    .currentFileCount(100L)
                    .maxFileCount(10000L)
                    .isNearQuotaLimit(false)
                    .isQuotaExceeded(false)
                    .build();

            when(storageService.getStorageQuota()).thenReturn(quota);

            // When
            ResponseEntity<ApiResponse<StorageQuotaDTO>> result = controller.getStorageQuota();

            // Then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result.getBody().getData().getUsagePercentage()).isEqualTo(50.0);
            assertThat(result.getBody().getData().getIsNearQuotaLimit()).isFalse();
        }
    }

    @Nested
    @DisplayName("Update Storage Quota Tests")
    class UpdateStorageQuotaTests {

        @Test
        @DisplayName("Should update storage quota")
        void updateStorageQuota_ReturnsUpdatedQuota() {
            // Given
            String driveId = "drive-123";
            Long newQuotaLimit = 20_000_000_000L;

            UpdateQuotaRequest request = UpdateQuotaRequest.builder()
                    .quotaLimit(newQuotaLimit)
                    .build();

            StorageQuotaDTO updatedQuota = StorageQuotaDTO.builder()
                    .quotaLimit(newQuotaLimit)
                    .usedStorage(5_000_000_000L)
                    .availableStorage(15_000_000_000L)
                    .usagePercentage(25.0)
                    .build();

            when(storageService.updateStorageQuota(driveId, newQuotaLimit)).thenReturn(updatedQuota);

            // When
            ResponseEntity<ApiResponse<StorageQuotaDTO>> result =
                    controller.updateStorageQuota(driveId, request);

            // Then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result.getBody().getData().getQuotaLimit()).isEqualTo(newQuotaLimit);
            assertThat(result.getBody().getMessage()).contains("updated");
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should handle very large file size")
        void initializeUpload_VeryLargeFile_Handles() {
            // Given
            UploadInitRequest request = UploadInitRequest.builder()
                    .filename("huge.zip")
                    .fileSize(1_000_000_000_000L) // 1TB
                    .contentType("application/zip")
                    .useMultipart(true)
                    .build();

            UploadInitResponse response = UploadInitResponse.builder()
                    .sessionId("session-large")
                    .uploadType("MULTIPART")
                    .build();

            when(storageService.initializeUpload(request)).thenReturn(response);

            // When
            ResponseEntity<ApiResponse<UploadInitResponse>> result = controller.initializeUpload(request);

            // Then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        @Test
        @DisplayName("Should handle special characters in filename")
        void downloadFile_SpecialCharsFilename_Handles() {
            // Given
            String bucket = "teamsync-documents";
            String storageKey = "tenant/drive/file with spaces & special.pdf";
            byte[] content = "content".getBytes();

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Internal-Service", "content-service");

            StorageService.FileDownload download = new StorageService.FileDownload(
                    new ByteArrayInputStream(content),
                    "application/pdf",
                    content.length
            );

            when(storageService.downloadFile(bucket, storageKey)).thenReturn(download);

            // When
            ResponseEntity<InputStreamResource> result =
                    controller.downloadFile(bucket, storageKey, "my file (1).pdf", request);

            // Then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("Should handle empty bucket name")
        void deleteFile_EmptyBucket_CallsService() {
            // Given - edge case that should be handled by validation, but test controller behavior
            String bucket = "";
            String storageKey = "key";

            doNothing().when(storageService).deleteFile(bucket, storageKey);

            // When
            ResponseEntity<ApiResponse<Void>> result = controller.deleteFile(bucket, storageKey);

            // Then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(storageService).deleteFile(bucket, storageKey);
        }

        @Test
        @DisplayName("Should handle zero expiry seconds")
        void getDownloadUrl_ZeroExpiry_UsesDefault() {
            // Given
            String bucket = "bucket";
            String storageKey = "key";

            DownloadUrlResponse response = DownloadUrlResponse.builder()
                    .url("http://url")
                    .build();

            when(storageService.generateDownloadUrl(bucket, storageKey, null, 0))
                    .thenReturn(response);

            // When
            ResponseEntity<ApiResponse<DownloadUrlResponse>> result =
                    controller.getDownloadUrl(bucket, storageKey, null, 0);

            // Then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Nested
    @DisplayName("Response Format Tests")
    class ResponseFormatTests {

        @Test
        @DisplayName("All successful responses should have success=true")
        void allResponses_SuccessfulOperations_HaveSuccessTrue() {
            // Initialize
            when(storageService.initializeUpload(any())).thenReturn(UploadInitResponse.builder().build());
            ResponseEntity<ApiResponse<UploadInitResponse>> initResult =
                    controller.initializeUpload(UploadInitRequest.builder().build());
            assertThat(initResult.getBody().isSuccess()).isTrue();

            // Complete
            when(storageService.completeUpload(any())).thenReturn(UploadCompleteResponse.builder().build());
            ResponseEntity<ApiResponse<UploadCompleteResponse>> completeResult =
                    controller.completeUpload(UploadCompleteRequest.builder().build());
            assertThat(completeResult.getBody().isSuccess()).isTrue();

            // Status
            when(storageService.getUploadStatus(any())).thenReturn(UploadStatusResponse.builder().build());
            ResponseEntity<ApiResponse<UploadStatusResponse>> statusResult =
                    controller.getUploadStatus("session");
            assertThat(statusResult.getBody().isSuccess()).isTrue();

            // Delete
            doNothing().when(storageService).deleteFile(any(), any());
            ResponseEntity<ApiResponse<Void>> deleteResult =
                    controller.deleteFile("bucket", "key");
            assertThat(deleteResult.getBody().isSuccess()).isTrue();
        }

        @Test
        @DisplayName("All responses should include message")
        void allResponses_IncludeMessage() {
            // Cancel
            doNothing().when(storageService).cancelUpload(any());
            ResponseEntity<ApiResponse<Void>> cancelResult = controller.cancelUpload("session");
            assertThat(cancelResult.getBody().getMessage()).isNotBlank();

            // Delete
            doNothing().when(storageService).deleteFile(any(), any());
            ResponseEntity<ApiResponse<Void>> deleteResult = controller.deleteFile("bucket", "key");
            assertThat(deleteResult.getBody().getMessage()).isNotBlank();
        }
    }
}
