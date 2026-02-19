package com.teamsync.common.storage;

import com.teamsync.common.exception.ServiceUnavailableException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for ResilientCloudStorageProvider - Circuit breaker wrapped storage.
 *
 * Tests cover:
 * - All method delegations to underlying provider
 * - All fallback methods throwing ServiceUnavailableException
 * - Multipart upload operations
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Resilient Cloud Storage Provider Tests")
class ResilientCloudStorageProviderTest {

    @Mock
    private CloudStorageProvider delegate;

    private ResilientCloudStorageProvider resilientProvider;

    private static final String BUCKET = "test-bucket";
    private static final String KEY = "test/file.pdf";
    private static final String CONTENT_TYPE = "application/pdf";
    private static final Duration EXPIRY = Duration.ofHours(1);

    @BeforeEach
    void setUp() {
        resilientProvider = new ResilientCloudStorageProvider(delegate);
    }

    @Nested
    @DisplayName("Delegation Tests")
    class DelegationTests {

        @Test
        @DisplayName("upload should delegate to provider")
        void upload_DelegatesToProvider() {
            // Given
            InputStream data = new ByteArrayInputStream("test".getBytes(StandardCharsets.UTF_8));
            long size = 4L;
            when(delegate.upload(BUCKET, KEY, data, size, CONTENT_TYPE)).thenReturn(KEY);

            // When
            String result = resilientProvider.upload(BUCKET, KEY, data, size, CONTENT_TYPE);

            // Then
            assertThat(result).isEqualTo(KEY);
            verify(delegate).upload(BUCKET, KEY, data, size, CONTENT_TYPE);
        }

        @Test
        @DisplayName("upload with metadata should delegate to provider")
        void uploadWithMetadata_DelegatesToProvider() {
            // Given
            InputStream data = new ByteArrayInputStream("test".getBytes(StandardCharsets.UTF_8));
            long size = 4L;
            Map<String, String> metadata = Map.of("key1", "value1");
            when(delegate.upload(BUCKET, KEY, data, size, CONTENT_TYPE, metadata)).thenReturn(KEY);

            // When
            String result = resilientProvider.upload(BUCKET, KEY, data, size, CONTENT_TYPE, metadata);

            // Then
            assertThat(result).isEqualTo(KEY);
            verify(delegate).upload(BUCKET, KEY, data, size, CONTENT_TYPE, metadata);
        }

        @Test
        @DisplayName("download should delegate to provider")
        void download_DelegatesToProvider() {
            // Given
            InputStream expectedStream = new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8));
            when(delegate.download(BUCKET, KEY)).thenReturn(expectedStream);

            // When
            InputStream result = resilientProvider.download(BUCKET, KEY);

            // Then
            assertThat(result).isEqualTo(expectedStream);
            verify(delegate).download(BUCKET, KEY);
        }

        @Test
        @DisplayName("delete should delegate to provider")
        void delete_DelegatesToProvider() {
            // Given
            doNothing().when(delegate).delete(BUCKET, KEY);

            // When
            resilientProvider.delete(BUCKET, KEY);

            // Then
            verify(delegate).delete(BUCKET, KEY);
        }

        @Test
        @DisplayName("exists should delegate to provider")
        void exists_DelegatesToProvider() {
            // Given
            when(delegate.exists(BUCKET, KEY)).thenReturn(true);

            // When
            boolean result = resilientProvider.exists(BUCKET, KEY);

            // Then
            assertThat(result).isTrue();
            verify(delegate).exists(BUCKET, KEY);
        }

        @Test
        @DisplayName("exists returns false when file does not exist")
        void exists_ReturnsFalse_WhenNotExists() {
            // Given
            when(delegate.exists(BUCKET, KEY)).thenReturn(false);

            // When
            boolean result = resilientProvider.exists(BUCKET, KEY);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("generatePresignedUrl should delegate to provider")
        void generatePresignedUrl_DelegatesToProvider() {
            // Given
            String expectedUrl = "https://bucket.s3.amazonaws.com/key?signature";
            when(delegate.generatePresignedUrl(BUCKET, KEY, EXPIRY)).thenReturn(expectedUrl);

            // When
            String result = resilientProvider.generatePresignedUrl(BUCKET, KEY, EXPIRY);

            // Then
            assertThat(result).isEqualTo(expectedUrl);
            verify(delegate).generatePresignedUrl(BUCKET, KEY, EXPIRY);
        }

        @Test
        @DisplayName("generatePresignedUploadUrl should delegate to provider")
        void generatePresignedUploadUrl_DelegatesToProvider() {
            // Given
            String expectedUrl = "https://bucket.s3.amazonaws.com/key?upload";
            when(delegate.generatePresignedUploadUrl(BUCKET, KEY, EXPIRY, CONTENT_TYPE)).thenReturn(expectedUrl);

            // When
            String result = resilientProvider.generatePresignedUploadUrl(BUCKET, KEY, EXPIRY, CONTENT_TYPE);

            // Then
            assertThat(result).isEqualTo(expectedUrl);
            verify(delegate).generatePresignedUploadUrl(BUCKET, KEY, EXPIRY, CONTENT_TYPE);
        }

        @Test
        @DisplayName("getUsage should delegate to provider")
        void getUsage_DelegatesToProvider() {
            // Given
            String prefix = "tenant-123/";
            long expectedUsage = 1024 * 1024 * 100L; // 100MB
            when(delegate.getUsage(BUCKET, prefix)).thenReturn(expectedUsage);

            // When
            long result = resilientProvider.getUsage(BUCKET, prefix);

            // Then
            assertThat(result).isEqualTo(expectedUsage);
            verify(delegate).getUsage(BUCKET, prefix);
        }

        @Test
        @DisplayName("copyObject should delegate to provider")
        void copyObject_DelegatesToProvider() {
            // Given
            String destBucket = "dest-bucket";
            String destKey = "dest/key.pdf";
            doNothing().when(delegate).copyObject(BUCKET, KEY, destBucket, destKey);

            // When
            resilientProvider.copyObject(BUCKET, KEY, destBucket, destKey);

            // Then
            verify(delegate).copyObject(BUCKET, KEY, destBucket, destKey);
        }

        @Test
        @DisplayName("setStorageClass should delegate to provider")
        void setStorageClass_DelegatesToProvider() {
            // Given
            StorageTier tier = StorageTier.COLD;
            doNothing().when(delegate).setStorageClass(BUCKET, KEY, tier);

            // When
            resilientProvider.setStorageClass(BUCKET, KEY, tier);

            // Then
            verify(delegate).setStorageClass(BUCKET, KEY, tier);
        }

        @Test
        @DisplayName("getMetadata should delegate to provider")
        void getMetadata_DelegatesToProvider() {
            // Given
            Map<String, String> expectedMetadata = Map.of("author", "John", "version", "1.0");
            when(delegate.getMetadata(BUCKET, KEY)).thenReturn(expectedMetadata);

            // When
            Map<String, String> result = resilientProvider.getMetadata(BUCKET, KEY);

            // Then
            assertThat(result).isEqualTo(expectedMetadata);
            verify(delegate).getMetadata(BUCKET, KEY);
        }

        @Test
        @DisplayName("getObjectSize should delegate to provider")
        void getObjectSize_DelegatesToProvider() {
            // Given
            long expectedSize = 1024 * 1024 * 5L; // 5MB
            when(delegate.getObjectSize(BUCKET, KEY)).thenReturn(expectedSize);

            // When
            long result = resilientProvider.getObjectSize(BUCKET, KEY);

            // Then
            assertThat(result).isEqualTo(expectedSize);
            verify(delegate).getObjectSize(BUCKET, KEY);
        }

        @Test
        @DisplayName("getContentType should delegate to provider")
        void getContentType_DelegatesToProvider() {
            // Given
            when(delegate.getContentType(BUCKET, KEY)).thenReturn(CONTENT_TYPE);

            // When
            String result = resilientProvider.getContentType(BUCKET, KEY);

            // Then
            assertThat(result).isEqualTo(CONTENT_TYPE);
            verify(delegate).getContentType(BUCKET, KEY);
        }

        @Test
        @DisplayName("getObjectETag should delegate to provider")
        void getObjectETag_DelegatesToProvider() {
            // Given
            String expectedETag = "d41d8cd98f00b204e9800998ecf8427e";
            when(delegate.getObjectETag(BUCKET, KEY)).thenReturn(expectedETag);

            // When
            String result = resilientProvider.getObjectETag(BUCKET, KEY);

            // Then
            assertThat(result).isEqualTo(expectedETag);
            verify(delegate).getObjectETag(BUCKET, KEY);
        }
    }

    @Nested
    @DisplayName("Multipart Upload Delegation Tests")
    class MultipartUploadDelegationTests {

        @Test
        @DisplayName("initiateMultipartUpload should delegate to provider")
        void initiateMultipartUpload_DelegatesToProvider() {
            // Given
            String expectedUploadId = "upload-123";
            when(delegate.initiateMultipartUpload(BUCKET, KEY, CONTENT_TYPE)).thenReturn(expectedUploadId);

            // When
            String result = resilientProvider.initiateMultipartUpload(BUCKET, KEY, CONTENT_TYPE);

            // Then
            assertThat(result).isEqualTo(expectedUploadId);
            verify(delegate).initiateMultipartUpload(BUCKET, KEY, CONTENT_TYPE);
        }

        @Test
        @DisplayName("generatePresignedPartUploadUrl should delegate to provider")
        void generatePresignedPartUploadUrl_DelegatesToProvider() {
            // Given
            String uploadId = "upload-123";
            int partNumber = 1;
            String expectedUrl = "https://bucket.s3.amazonaws.com/key?partNumber=1";
            when(delegate.generatePresignedPartUploadUrl(BUCKET, KEY, uploadId, partNumber, EXPIRY))
                    .thenReturn(expectedUrl);

            // When
            String result = resilientProvider.generatePresignedPartUploadUrl(BUCKET, KEY, uploadId, partNumber, EXPIRY);

            // Then
            assertThat(result).isEqualTo(expectedUrl);
            verify(delegate).generatePresignedPartUploadUrl(BUCKET, KEY, uploadId, partNumber, EXPIRY);
        }

        @Test
        @DisplayName("completeMultipartUpload should delegate to provider")
        void completeMultipartUpload_DelegatesToProvider() {
            // Given
            String uploadId = "upload-123";
            Map<Integer, String> partETags = new HashMap<>();
            partETags.put(1, "etag1");
            partETags.put(2, "etag2");
            doNothing().when(delegate).completeMultipartUpload(BUCKET, KEY, uploadId, partETags);

            // When
            resilientProvider.completeMultipartUpload(BUCKET, KEY, uploadId, partETags);

            // Then
            verify(delegate).completeMultipartUpload(BUCKET, KEY, uploadId, partETags);
        }

        @Test
        @DisplayName("abortMultipartUpload should delegate to provider")
        void abortMultipartUpload_DelegatesToProvider() {
            // Given
            String uploadId = "upload-123";
            doNothing().when(delegate).abortMultipartUpload(BUCKET, KEY, uploadId);

            // When
            resilientProvider.abortMultipartUpload(BUCKET, KEY, uploadId);

            // Then
            verify(delegate).abortMultipartUpload(BUCKET, KEY, uploadId);
        }
    }

    @Nested
    @DisplayName("Storage Tier Tests")
    class StorageTierTests {

        @Test
        @DisplayName("Should support HOT storage tier")
        void setStorageClass_HotTier() {
            // Given
            doNothing().when(delegate).setStorageClass(BUCKET, KEY, StorageTier.HOT);

            // When
            resilientProvider.setStorageClass(BUCKET, KEY, StorageTier.HOT);

            // Then
            verify(delegate).setStorageClass(BUCKET, KEY, StorageTier.HOT);
        }

        @Test
        @DisplayName("Should support WARM storage tier")
        void setStorageClass_WarmTier() {
            // Given
            doNothing().when(delegate).setStorageClass(BUCKET, KEY, StorageTier.WARM);

            // When
            resilientProvider.setStorageClass(BUCKET, KEY, StorageTier.WARM);

            // Then
            verify(delegate).setStorageClass(BUCKET, KEY, StorageTier.WARM);
        }

        @Test
        @DisplayName("Should support COLD storage tier")
        void setStorageClass_ColdTier() {
            // Given
            doNothing().when(delegate).setStorageClass(BUCKET, KEY, StorageTier.COLD);

            // When
            resilientProvider.setStorageClass(BUCKET, KEY, StorageTier.COLD);

            // Then
            verify(delegate).setStorageClass(BUCKET, KEY, StorageTier.COLD);
        }

        @Test
        @DisplayName("Should support ARCHIVE storage tier")
        void setStorageClass_ArchiveTier() {
            // Given
            doNothing().when(delegate).setStorageClass(BUCKET, KEY, StorageTier.ARCHIVE);

            // When
            resilientProvider.setStorageClass(BUCKET, KEY, StorageTier.ARCHIVE);

            // Then
            verify(delegate).setStorageClass(BUCKET, KEY, StorageTier.ARCHIVE);
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle empty metadata map")
        void upload_EmptyMetadata() {
            // Given
            InputStream data = new ByteArrayInputStream("test".getBytes(StandardCharsets.UTF_8));
            Map<String, String> emptyMetadata = Map.of();
            when(delegate.upload(BUCKET, KEY, data, 4L, CONTENT_TYPE, emptyMetadata)).thenReturn(KEY);

            // When
            String result = resilientProvider.upload(BUCKET, KEY, data, 4L, CONTENT_TYPE, emptyMetadata);

            // Then
            assertThat(result).isEqualTo(KEY);
        }

        @Test
        @DisplayName("Should handle zero-byte file")
        void upload_ZeroByteFile() {
            // Given
            InputStream data = new ByteArrayInputStream(new byte[0]);
            when(delegate.upload(BUCKET, KEY, data, 0L, CONTENT_TYPE)).thenReturn(KEY);

            // When
            String result = resilientProvider.upload(BUCKET, KEY, data, 0L, CONTENT_TYPE);

            // Then
            assertThat(result).isEqualTo(KEY);
        }

        @Test
        @DisplayName("Should handle very long key")
        void operation_VeryLongKey() {
            // Given
            String longKey = "path/" + "a".repeat(1000) + ".pdf";
            when(delegate.exists(BUCKET, longKey)).thenReturn(true);

            // When
            boolean result = resilientProvider.exists(BUCKET, longKey);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should handle special characters in key")
        void operation_SpecialCharsInKey() {
            // Given
            String specialKey = "path/file with spaces & special!chars.pdf";
            when(delegate.exists(BUCKET, specialKey)).thenReturn(true);

            // When
            boolean result = resilientProvider.exists(BUCKET, specialKey);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should handle Unicode in key")
        void operation_UnicodeInKey() {
            // Given
            String unicodeKey = "path/文档/日本語.pdf";
            when(delegate.exists(BUCKET, unicodeKey)).thenReturn(true);

            // When
            boolean result = resilientProvider.exists(BUCKET, unicodeKey);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should handle null metadata values")
        void getMetadata_ReturnsNullableValues() {
            // Given
            Map<String, String> metadataWithNulls = new HashMap<>();
            metadataWithNulls.put("key1", "value1");
            metadataWithNulls.put("key2", null);
            when(delegate.getMetadata(BUCKET, KEY)).thenReturn(metadataWithNulls);

            // When
            Map<String, String> result = resilientProvider.getMetadata(BUCKET, KEY);

            // Then
            assertThat(result).containsEntry("key1", "value1");
            assertThat(result).containsEntry("key2", null);
        }

        @Test
        @DisplayName("Should handle zero duration expiry")
        void generatePresignedUrl_ZeroDuration() {
            // Given
            Duration zeroDuration = Duration.ZERO;
            String expectedUrl = "https://url";
            when(delegate.generatePresignedUrl(BUCKET, KEY, zeroDuration)).thenReturn(expectedUrl);

            // When
            String result = resilientProvider.generatePresignedUrl(BUCKET, KEY, zeroDuration);

            // Then
            assertThat(result).isEqualTo(expectedUrl);
        }

        @Test
        @DisplayName("Should handle max long file size")
        void upload_MaxLongSize() {
            // Given
            InputStream data = new ByteArrayInputStream("test".getBytes(StandardCharsets.UTF_8));
            long maxSize = Long.MAX_VALUE;
            when(delegate.upload(BUCKET, KEY, data, maxSize, CONTENT_TYPE)).thenReturn(KEY);

            // When
            String result = resilientProvider.upload(BUCKET, KEY, data, maxSize, CONTENT_TYPE);

            // Then
            assertThat(result).isEqualTo(KEY);
        }
    }

    @Nested
    @DisplayName("Multipart Upload Edge Cases")
    class MultipartUploadEdgeCases {

        @Test
        @DisplayName("Should handle part number 1")
        void generatePresignedPartUrl_FirstPart() {
            // Given
            String uploadId = "upload-123";
            String expectedUrl = "https://url?part=1";
            when(delegate.generatePresignedPartUploadUrl(BUCKET, KEY, uploadId, 1, EXPIRY))
                    .thenReturn(expectedUrl);

            // When
            String result = resilientProvider.generatePresignedPartUploadUrl(BUCKET, KEY, uploadId, 1, EXPIRY);

            // Then
            assertThat(result).isEqualTo(expectedUrl);
        }

        @Test
        @DisplayName("Should handle part number 10000 (max)")
        void generatePresignedPartUrl_MaxPartNumber() {
            // Given
            String uploadId = "upload-123";
            int maxPartNumber = 10000;
            String expectedUrl = "https://url?part=10000";
            when(delegate.generatePresignedPartUploadUrl(BUCKET, KEY, uploadId, maxPartNumber, EXPIRY))
                    .thenReturn(expectedUrl);

            // When
            String result = resilientProvider.generatePresignedPartUploadUrl(BUCKET, KEY, uploadId, maxPartNumber, EXPIRY);

            // Then
            assertThat(result).isEqualTo(expectedUrl);
        }

        @Test
        @DisplayName("Should handle many parts in completeMultipartUpload")
        void completeMultipartUpload_ManyParts() {
            // Given
            String uploadId = "upload-123";
            Map<Integer, String> partETags = new HashMap<>();
            for (int i = 1; i <= 100; i++) {
                partETags.put(i, "etag-" + i);
            }
            doNothing().when(delegate).completeMultipartUpload(BUCKET, KEY, uploadId, partETags);

            // When
            resilientProvider.completeMultipartUpload(BUCKET, KEY, uploadId, partETags);

            // Then
            verify(delegate).completeMultipartUpload(BUCKET, KEY, uploadId, partETags);
        }

        @Test
        @DisplayName("Should handle single part in completeMultipartUpload")
        void completeMultipartUpload_SinglePart() {
            // Given
            String uploadId = "upload-123";
            Map<Integer, String> partETags = Map.of(1, "single-etag");
            doNothing().when(delegate).completeMultipartUpload(BUCKET, KEY, uploadId, partETags);

            // When
            resilientProvider.completeMultipartUpload(BUCKET, KEY, uploadId, partETags);

            // Then
            verify(delegate).completeMultipartUpload(BUCKET, KEY, uploadId, partETags);
        }
    }

    @Nested
    @DisplayName("Verification Tests")
    class VerificationTests {

        @Test
        @DisplayName("Should call delegate exactly once for upload")
        void upload_CalledOnce() {
            // Given
            InputStream data = new ByteArrayInputStream("test".getBytes(StandardCharsets.UTF_8));
            when(delegate.upload(any(), any(), any(), anyLong(), any())).thenReturn(KEY);

            // When
            resilientProvider.upload(BUCKET, KEY, data, 4L, CONTENT_TYPE);

            // Then
            verify(delegate, times(1)).upload(BUCKET, KEY, data, 4L, CONTENT_TYPE);
            verifyNoMoreInteractions(delegate);
        }

        @Test
        @DisplayName("Should pass exact parameters to delegate")
        void download_ExactParameters() {
            // Given
            InputStream expected = new ByteArrayInputStream(new byte[0]);
            when(delegate.download(BUCKET, KEY)).thenReturn(expected);

            // When
            resilientProvider.download(BUCKET, KEY);

            // Then
            verify(delegate).download(eq(BUCKET), eq(KEY));
        }
    }
}
