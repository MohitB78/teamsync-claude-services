package com.teamsync.common.storage;

import io.minio.*;
import io.minio.http.Method;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnProperty(name = "teamsync.storage.provider", havingValue = "minio", matchIfMissing = false)
@ConditionalOnBean(MinioClient.class)
@RequiredArgsConstructor
@Slf4j
public class MinIOStorageProvider implements CloudStorageProvider {

    private final MinioClient minioClient;

    @Override
    public String upload(String bucket, String key, InputStream data, long size, String contentType) {
        return upload(bucket, key, data, size, contentType, Map.of());
    }

    @Override
    public String upload(String bucket, String key, InputStream data, long size, String contentType, Map<String, String> metadata) {
        try {
            ensureBucketExists(bucket);

            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(data, size, -1)
                    .contentType(contentType)
                    .userMetadata(metadata)
                    .build());

            log.info("Uploaded file to MinIO: {}/{}", bucket, key);
            return key;
        } catch (Exception e) {
            log.error("Failed to upload file to MinIO: {}/{}", bucket, key, e);
            throw new RuntimeException("Failed to upload file", e);
        }
    }

    @Override
    public InputStream download(String bucket, String key) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build());
        } catch (Exception e) {
            log.error("Failed to download file from MinIO: {}/{}", bucket, key, e);
            throw new RuntimeException("Failed to download file", e);
        }
    }

    @Override
    public InputStream downloadRange(String bucket, String key, long offset, long length) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .offset(offset)
                    .length(length)
                    .build());
        } catch (Exception e) {
            log.error("Failed to download range from MinIO: {}/{} (offset={}, length={})", bucket, key, offset, length, e);
            throw new RuntimeException("Failed to download file range", e);
        }
    }

    @Override
    public void delete(String bucket, String key) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build());
            log.info("Deleted file from MinIO: {}/{}", bucket, key);
        } catch (Exception e) {
            log.error("Failed to delete file from MinIO: {}/{}", bucket, key, e);
            throw new RuntimeException("Failed to delete file", e);
        }
    }

    @Override
    public boolean exists(String bucket, String key) {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String generatePresignedUrl(String bucket, String key, Duration expiry) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .method(Method.GET)
                    .expiry((int) expiry.toSeconds(), TimeUnit.SECONDS)
                    .build());
        } catch (Exception e) {
            log.error("Failed to generate presigned URL for MinIO: {}/{}", bucket, key, e);
            throw new RuntimeException("Failed to generate presigned URL", e);
        }
    }

    @Override
    public String generatePresignedUploadUrl(String bucket, String key, Duration expiry, String contentType) {
        try {
            ensureBucketExists(bucket);
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .method(Method.PUT)
                    .expiry((int) expiry.toSeconds(), TimeUnit.SECONDS)
                    .build());
        } catch (Exception e) {
            log.error("Failed to generate presigned upload URL for MinIO: {}/{}", bucket, key, e);
            throw new RuntimeException("Failed to generate presigned upload URL", e);
        }
    }

    @Override
    public long getUsage(String bucket, String prefix) {
        try {
            long totalSize = 0;
            Iterable<Result<Item>> results = minioClient.listObjects(ListObjectsArgs.builder()
                    .bucket(bucket)
                    .prefix(prefix)
                    .recursive(true)
                    .build());

            for (Result<Item> result : results) {
                totalSize += result.get().size();
            }
            return totalSize;
        } catch (Exception e) {
            log.error("Failed to calculate usage for MinIO: {}/{}", bucket, prefix, e);
            throw new RuntimeException("Failed to calculate storage usage", e);
        }
    }

    @Override
    public void copyObject(String sourceBucket, String sourceKey, String destBucket, String destKey) {
        try {
            ensureBucketExists(destBucket);
            minioClient.copyObject(CopyObjectArgs.builder()
                    .bucket(destBucket)
                    .object(destKey)
                    .source(CopySource.builder()
                            .bucket(sourceBucket)
                            .object(sourceKey)
                            .build())
                    .build());
            log.info("Copied file in MinIO: {}/{} -> {}/{}", sourceBucket, sourceKey, destBucket, destKey);
        } catch (Exception e) {
            log.error("Failed to copy file in MinIO", e);
            throw new RuntimeException("Failed to copy file", e);
        }
    }

    @Override
    public void setStorageClass(String bucket, String key, StorageTier storageClass) {
        // MinIO doesn't support storage classes - this is a no-op
        log.debug("Storage class change ignored for MinIO (not supported): {}/{} -> {}", bucket, key, storageClass);
    }

    @Override
    public Map<String, String> getMetadata(String bucket, String key) {
        try {
            StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build());
            return new HashMap<>(stat.userMetadata());
        } catch (Exception e) {
            log.error("Failed to get metadata from MinIO: {}/{}", bucket, key, e);
            throw new RuntimeException("Failed to get metadata", e);
        }
    }

    @Override
    public long getObjectSize(String bucket, String key) {
        try {
            StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build());
            return stat.size();
        } catch (Exception e) {
            log.error("Failed to get object size from MinIO: {}/{}", bucket, key, e);
            throw new RuntimeException("Failed to get object size", e);
        }
    }

    @Override
    public String getContentType(String bucket, String key) {
        try {
            StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build());
            return stat.contentType();
        } catch (Exception e) {
            log.error("Failed to get content type from MinIO: {}/{}", bucket, key, e);
            throw new RuntimeException("Failed to get content type", e);
        }
    }

    @Override
    public String getObjectETag(String bucket, String key) {
        try {
            StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build());
            // Remove quotes from ETag if present
            String etag = stat.etag();
            if (etag != null && etag.startsWith("\"") && etag.endsWith("\"")) {
                etag = etag.substring(1, etag.length() - 1);
            }
            return etag;
        } catch (Exception e) {
            log.error("Failed to get ETag from MinIO: {}/{}", bucket, key, e);
            throw new RuntimeException("Failed to get ETag", e);
        }
    }

    /**
     * Get combined object metadata in a single MinIO API call.
     * This is more efficient than calling getObjectSize(), getContentType(), and getObjectETag() separately,
     * as it reduces network round trips from 4 to 1.
     */
    @Override
    public ObjectMetadata getObjectMetadata(String bucket, String key) {
        try {
            StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build());

            // Remove quotes from ETag if present
            String etag = stat.etag();
            if (etag != null && etag.startsWith("\"") && etag.endsWith("\"")) {
                etag = etag.substring(1, etag.length() - 1);
            }

            return new ObjectMetadata(
                    stat.size(),
                    stat.contentType(),
                    etag,
                    new HashMap<>(stat.userMetadata())
            );
        } catch (Exception e) {
            log.error("Failed to get object metadata from MinIO: {}/{}", bucket, key, e);
            throw new RuntimeException("Failed to get object metadata", e);
        }
    }

    @Override
    public String initiateMultipartUpload(String bucket, String key, String contentType) {
        // MinIO handles multipart uploads internally through the SDK
        // For presigned URL approach, we return a placeholder upload ID
        log.info("Initiating multipart upload for MinIO: {}/{}", bucket, key);
        return java.util.UUID.randomUUID().toString();
    }

    @Override
    public String generatePresignedPartUploadUrl(String bucket, String key, String uploadId, int partNumber, Duration expiry) {
        try {
            ensureBucketExists(bucket);
            // MinIO uses standard presigned PUT URLs for multipart parts
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .bucket(bucket)
                    .object(key + ".part" + partNumber)
                    .method(Method.PUT)
                    .expiry((int) expiry.toSeconds(), TimeUnit.SECONDS)
                    .build());
        } catch (Exception e) {
            log.error("Failed to generate presigned part upload URL for MinIO", e);
            throw new RuntimeException("Failed to generate presigned part upload URL", e);
        }
    }

    @Override
    public void completeMultipartUpload(String bucket, String key, String uploadId, Map<Integer, String> partETags) {
        log.info("Completing multipart upload for MinIO: {}/{} with {} parts", bucket, key, partETags.size());
        // MinIO SDK handles this internally when using putObject with large files
        // For manual multipart, would need to compose objects
    }

    @Override
    public void abortMultipartUpload(String bucket, String key, String uploadId) {
        log.info("Aborting multipart upload for MinIO: {}/{}", bucket, key);
        // Clean up any partial uploads
        try {
            // Remove any part files if they exist
            for (int i = 1; i <= 10000; i++) {
                String partKey = key + ".part" + i;
                if (exists(bucket, partKey)) {
                    delete(bucket, partKey);
                } else {
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("Error cleaning up partial uploads: {}", e.getMessage());
        }
    }

    private void ensureBucketExists(String bucket) throws Exception {
        boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!found) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            log.info("Created MinIO bucket: {}", bucket);
        }
    }
}
