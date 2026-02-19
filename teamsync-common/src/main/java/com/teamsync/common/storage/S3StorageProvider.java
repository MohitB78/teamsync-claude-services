package com.teamsync.common.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

import java.io.InputStream;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "teamsync.storage.provider", havingValue = "s3")
@ConditionalOnBean(S3Client.class)
@RequiredArgsConstructor
@Slf4j
public class S3StorageProvider implements CloudStorageProvider {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Override
    public String upload(String bucket, String key, InputStream data, long size, String contentType) {
        return upload(bucket, key, data, size, contentType, Map.of());
    }

    @Override
    public String upload(String bucket, String key, InputStream data, long size, String contentType, Map<String, String> metadata) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .contentLength(size)
                    .metadata(metadata)
                    .build();

            s3Client.putObject(request, RequestBody.fromInputStream(data, size));
            log.info("Uploaded file to S3: {}/{}", bucket, key);
            return key;
        } catch (Exception e) {
            log.error("Failed to upload file to S3: {}/{}", bucket, key, e);
            throw new RuntimeException("Failed to upload file", e);
        }
    }

    @Override
    public InputStream download(String bucket, String key) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            return s3Client.getObject(request);
        } catch (Exception e) {
            log.error("Failed to download file from S3: {}/{}", bucket, key, e);
            throw new RuntimeException("Failed to download file", e);
        }
    }

    @Override
    public InputStream downloadRange(String bucket, String key, long offset, long length) {
        try {
            // S3 Range header format: bytes=start-end (inclusive)
            long endByte = offset + length - 1;
            String range = String.format("bytes=%d-%d", offset, endByte);

            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .range(range)
                    .build();

            log.debug("Downloading range from S3: {}/{} range={}", bucket, key, range);
            return s3Client.getObject(request);
        } catch (Exception e) {
            log.error("Failed to download range from S3: {}/{} offset={} length={}", bucket, key, offset, length, e);
            throw new RuntimeException("Failed to download file range", e);
        }
    }

    @Override
    public void delete(String bucket, String key) {
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            s3Client.deleteObject(request);
            log.info("Deleted file from S3: {}/{}", bucket, key);
        } catch (Exception e) {
            log.error("Failed to delete file from S3: {}/{}", bucket, key, e);
            throw new RuntimeException("Failed to delete file", e);
        }
    }

    @Override
    public boolean exists(String bucket, String key) {
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            s3Client.headObject(request);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    @Override
    public String generatePresignedUrl(String bucket, String key, Duration expiry) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(expiry)
                    .getObjectRequest(getObjectRequest)
                    .build();

            return s3Presigner.presignGetObject(presignRequest).url().toString();
        } catch (Exception e) {
            log.error("Failed to generate presigned URL for S3: {}/{}", bucket, key, e);
            throw new RuntimeException("Failed to generate presigned URL", e);
        }
    }

    @Override
    public String generatePresignedUploadUrl(String bucket, String key, Duration expiry, String contentType) {
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(expiry)
                    .putObjectRequest(putObjectRequest)
                    .build();

            return s3Presigner.presignPutObject(presignRequest).url().toString();
        } catch (Exception e) {
            log.error("Failed to generate presigned upload URL for S3: {}/{}", bucket, key, e);
            throw new RuntimeException("Failed to generate presigned upload URL", e);
        }
    }

    @Override
    public long getUsage(String bucket, String prefix) {
        try {
            long totalSize = 0;
            ListObjectsV2Request request = ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(prefix)
                    .build();

            ListObjectsV2Response response;
            do {
                response = s3Client.listObjectsV2(request);
                for (S3Object s3Object : response.contents()) {
                    totalSize += s3Object.size();
                }
                request = request.toBuilder()
                        .continuationToken(response.nextContinuationToken())
                        .build();
            } while (response.isTruncated());

            return totalSize;
        } catch (Exception e) {
            log.error("Failed to calculate usage for S3: {}/{}", bucket, prefix, e);
            throw new RuntimeException("Failed to calculate storage usage", e);
        }
    }

    @Override
    public void copyObject(String sourceBucket, String sourceKey, String destBucket, String destKey) {
        try {
            CopyObjectRequest request = CopyObjectRequest.builder()
                    .sourceBucket(sourceBucket)
                    .sourceKey(sourceKey)
                    .destinationBucket(destBucket)
                    .destinationKey(destKey)
                    .build();
            s3Client.copyObject(request);
            log.info("Copied file in S3: {}/{} -> {}/{}", sourceBucket, sourceKey, destBucket, destKey);
        } catch (Exception e) {
            log.error("Failed to copy file in S3", e);
            throw new RuntimeException("Failed to copy file", e);
        }
    }

    @Override
    public void setStorageClass(String bucket, String key, StorageTier storageTier) {
        try {
            StorageClass s3StorageClass = mapToS3StorageClass(storageTier);

            CopyObjectRequest request = CopyObjectRequest.builder()
                    .sourceBucket(bucket)
                    .sourceKey(key)
                    .destinationBucket(bucket)
                    .destinationKey(key)
                    .storageClass(s3StorageClass)
                    .metadataDirective(MetadataDirective.COPY)
                    .build();

            s3Client.copyObject(request);
            log.info("Changed storage class for S3: {}/{} -> {}", bucket, key, s3StorageClass);
        } catch (Exception e) {
            log.error("Failed to change storage class in S3", e);
            throw new RuntimeException("Failed to change storage class", e);
        }
    }

    @Override
    public Map<String, String> getMetadata(String bucket, String key) {
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            HeadObjectResponse response = s3Client.headObject(request);
            return new HashMap<>(response.metadata());
        } catch (Exception e) {
            log.error("Failed to get metadata from S3: {}/{}", bucket, key, e);
            throw new RuntimeException("Failed to get metadata", e);
        }
    }

    @Override
    public long getObjectSize(String bucket, String key) {
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            HeadObjectResponse response = s3Client.headObject(request);
            return response.contentLength();
        } catch (Exception e) {
            log.error("Failed to get object size from S3: {}/{}", bucket, key, e);
            throw new RuntimeException("Failed to get object size", e);
        }
    }

    @Override
    public String getContentType(String bucket, String key) {
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            HeadObjectResponse response = s3Client.headObject(request);
            return response.contentType();
        } catch (Exception e) {
            log.error("Failed to get content type from S3: {}/{}", bucket, key, e);
            throw new RuntimeException("Failed to get content type", e);
        }
    }

    @Override
    public String getObjectETag(String bucket, String key) {
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            HeadObjectResponse response = s3Client.headObject(request);
            // Remove quotes from ETag if present
            String etag = response.eTag();
            if (etag != null && etag.startsWith("\"") && etag.endsWith("\"")) {
                etag = etag.substring(1, etag.length() - 1);
            }
            return etag;
        } catch (Exception e) {
            log.error("Failed to get ETag from S3: {}/{}", bucket, key, e);
            throw new RuntimeException("Failed to get ETag", e);
        }
    }

    /**
     * Get combined object metadata in a single S3 API call.
     * This is more efficient than calling getObjectSize(), getContentType(), and getObjectETag() separately,
     * as it reduces network round trips from 4 to 1.
     */
    @Override
    public ObjectMetadata getObjectMetadata(String bucket, String key) {
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            HeadObjectResponse response = s3Client.headObject(request);

            // Remove quotes from ETag if present
            String etag = response.eTag();
            if (etag != null && etag.startsWith("\"") && etag.endsWith("\"")) {
                etag = etag.substring(1, etag.length() - 1);
            }

            return new ObjectMetadata(
                    response.contentLength(),
                    response.contentType(),
                    etag,
                    new HashMap<>(response.metadata())
            );
        } catch (Exception e) {
            log.error("Failed to get object metadata from S3: {}/{}", bucket, key, e);
            throw new RuntimeException("Failed to get object metadata", e);
        }
    }

    @Override
    public String initiateMultipartUpload(String bucket, String key, String contentType) {
        try {
            CreateMultipartUploadRequest request = CreateMultipartUploadRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .build();
            CreateMultipartUploadResponse response = s3Client.createMultipartUpload(request);
            log.info("Initiated multipart upload for S3: {}/{} with uploadId: {}", bucket, key, response.uploadId());
            return response.uploadId();
        } catch (Exception e) {
            log.error("Failed to initiate multipart upload for S3: {}/{}", bucket, key, e);
            throw new RuntimeException("Failed to initiate multipart upload", e);
        }
    }

    @Override
    public String generatePresignedPartUploadUrl(String bucket, String key, String uploadId, int partNumber, Duration expiry) {
        try {
            UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .uploadId(uploadId)
                    .partNumber(partNumber)
                    .build();

            UploadPartPresignRequest presignRequest = UploadPartPresignRequest.builder()
                    .signatureDuration(expiry)
                    .uploadPartRequest(uploadPartRequest)
                    .build();

            return s3Presigner.presignUploadPart(presignRequest).url().toString();
        } catch (Exception e) {
            log.error("Failed to generate presigned part upload URL for S3", e);
            throw new RuntimeException("Failed to generate presigned part upload URL", e);
        }
    }

    @Override
    public void completeMultipartUpload(String bucket, String key, String uploadId, Map<Integer, String> partETags) {
        try {
            List<CompletedPart> completedParts = partETags.entrySet().stream()
                    .map(entry -> CompletedPart.builder()
                            .partNumber(entry.getKey())
                            .eTag(entry.getValue())
                            .build())
                    .collect(Collectors.toList());

            CompleteMultipartUploadRequest request = CompleteMultipartUploadRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .uploadId(uploadId)
                    .multipartUpload(CompletedMultipartUpload.builder().parts(completedParts).build())
                    .build();

            s3Client.completeMultipartUpload(request);
            log.info("Completed multipart upload for S3: {}/{} with {} parts", bucket, key, partETags.size());
        } catch (Exception e) {
            log.error("Failed to complete multipart upload for S3: {}/{}", bucket, key, e);
            throw new RuntimeException("Failed to complete multipart upload", e);
        }
    }

    @Override
    public void abortMultipartUpload(String bucket, String key, String uploadId) {
        try {
            AbortMultipartUploadRequest request = AbortMultipartUploadRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .uploadId(uploadId)
                    .build();
            s3Client.abortMultipartUpload(request);
            log.info("Aborted multipart upload for S3: {}/{}", bucket, key);
        } catch (Exception e) {
            log.error("Failed to abort multipart upload for S3: {}/{}", bucket, key, e);
            throw new RuntimeException("Failed to abort multipart upload", e);
        }
    }

    private StorageClass mapToS3StorageClass(StorageTier tier) {
        return switch (tier) {
            case HOT -> StorageClass.STANDARD;
            case WARM -> StorageClass.STANDARD_IA;
            case COLD -> StorageClass.GLACIER_IR;
            case ARCHIVE -> StorageClass.DEEP_ARCHIVE;
        };
    }
}
