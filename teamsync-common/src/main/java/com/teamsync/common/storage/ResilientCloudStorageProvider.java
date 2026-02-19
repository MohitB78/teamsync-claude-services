package com.teamsync.common.storage;

import com.teamsync.common.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.Duration;
import java.util.Map;

/**
 * Resilient wrapper around CloudStorageProvider that adds circuit breaker
 * and retry patterns for improved fault tolerance.
 *
 * This is marked as @Primary so it will be injected by default when
 * CloudStorageProvider is autowired.
 *
 * Only created when a delegate CloudStorageProvider (MinIO or S3) is available.
 */
@Component
@Primary
@ConditionalOnBean(MinIOStorageProvider.class)
@Slf4j
public class ResilientCloudStorageProvider implements CloudStorageProvider {

    private static final String CIRCUIT_BREAKER_NAME = "storage";

    private final CloudStorageProvider delegate;

    public ResilientCloudStorageProvider(@Qualifier("minIOStorageProvider") CloudStorageProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "uploadFallback")
    @Retry(name = CIRCUIT_BREAKER_NAME)
    public String upload(String bucket, String key, InputStream data, long size, String contentType) {
        return delegate.upload(bucket, key, data, size, contentType);
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "uploadWithMetadataFallback")
    @Retry(name = CIRCUIT_BREAKER_NAME)
    public String upload(String bucket, String key, InputStream data, long size, String contentType, Map<String, String> metadata) {
        return delegate.upload(bucket, key, data, size, contentType, metadata);
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "downloadFallback")
    @Retry(name = CIRCUIT_BREAKER_NAME)
    public InputStream download(String bucket, String key) {
        return delegate.download(bucket, key);
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "downloadRangeFallback")
    @Retry(name = CIRCUIT_BREAKER_NAME)
    public InputStream downloadRange(String bucket, String key, long offset, long length) {
        return delegate.downloadRange(bucket, key, offset, length);
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "deleteFallback")
    @Retry(name = CIRCUIT_BREAKER_NAME)
    public void delete(String bucket, String key) {
        delegate.delete(bucket, key);
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "existsFallback")
    @Retry(name = CIRCUIT_BREAKER_NAME)
    public boolean exists(String bucket, String key) {
        return delegate.exists(bucket, key);
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "generatePresignedUrlFallback")
    @Retry(name = CIRCUIT_BREAKER_NAME)
    public String generatePresignedUrl(String bucket, String key, Duration expiry) {
        return delegate.generatePresignedUrl(bucket, key, expiry);
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "generatePresignedUploadUrlFallback")
    @Retry(name = CIRCUIT_BREAKER_NAME)
    public String generatePresignedUploadUrl(String bucket, String key, Duration expiry, String contentType) {
        return delegate.generatePresignedUploadUrl(bucket, key, expiry, contentType);
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "getUsageFallback")
    @Retry(name = CIRCUIT_BREAKER_NAME)
    public long getUsage(String bucket, String prefix) {
        return delegate.getUsage(bucket, prefix);
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "copyObjectFallback")
    @Retry(name = CIRCUIT_BREAKER_NAME)
    public void copyObject(String sourceBucket, String sourceKey, String destBucket, String destKey) {
        delegate.copyObject(sourceBucket, sourceKey, destBucket, destKey);
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "setStorageClassFallback")
    @Retry(name = CIRCUIT_BREAKER_NAME)
    public void setStorageClass(String bucket, String key, StorageTier storageClass) {
        delegate.setStorageClass(bucket, key, storageClass);
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "getMetadataFallback")
    @Retry(name = CIRCUIT_BREAKER_NAME)
    public Map<String, String> getMetadata(String bucket, String key) {
        return delegate.getMetadata(bucket, key);
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "getObjectSizeFallback")
    @Retry(name = CIRCUIT_BREAKER_NAME)
    public long getObjectSize(String bucket, String key) {
        return delegate.getObjectSize(bucket, key);
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "getContentTypeFallback")
    @Retry(name = CIRCUIT_BREAKER_NAME)
    public String getContentType(String bucket, String key) {
        return delegate.getContentType(bucket, key);
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "getObjectETagFallback")
    @Retry(name = CIRCUIT_BREAKER_NAME)
    public String getObjectETag(String bucket, String key) {
        return delegate.getObjectETag(bucket, key);
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "initiateMultipartUploadFallback")
    @Retry(name = CIRCUIT_BREAKER_NAME)
    public String initiateMultipartUpload(String bucket, String key, String contentType) {
        return delegate.initiateMultipartUpload(bucket, key, contentType);
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "generatePresignedPartUploadUrlFallback")
    @Retry(name = CIRCUIT_BREAKER_NAME)
    public String generatePresignedPartUploadUrl(String bucket, String key, String uploadId, int partNumber, Duration expiry) {
        return delegate.generatePresignedPartUploadUrl(bucket, key, uploadId, partNumber, expiry);
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "completeMultipartUploadFallback")
    @Retry(name = CIRCUIT_BREAKER_NAME)
    public void completeMultipartUpload(String bucket, String key, String uploadId, Map<Integer, String> partETags) {
        delegate.completeMultipartUpload(bucket, key, uploadId, partETags);
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "abortMultipartUploadFallback")
    @Retry(name = CIRCUIT_BREAKER_NAME)
    public void abortMultipartUpload(String bucket, String key, String uploadId) {
        delegate.abortMultipartUpload(bucket, key, uploadId);
    }

    // ============================================
    // Fallback Methods
    // ============================================

    private String uploadFallback(String bucket, String key, InputStream data, long size, String contentType, Exception e) {
        log.error("Storage upload failed for {}/{}: {}", bucket, key, e.getMessage());
        throw new ServiceUnavailableException("Storage", "Upload temporarily unavailable");
    }

    private String uploadWithMetadataFallback(String bucket, String key, InputStream data, long size, String contentType, Map<String, String> metadata, Exception e) {
        log.error("Storage upload failed for {}/{}: {}", bucket, key, e.getMessage());
        throw new ServiceUnavailableException("Storage", "Upload temporarily unavailable");
    }

    private InputStream downloadFallback(String bucket, String key, Exception e) {
        log.error("Storage download failed for {}/{}: {}", bucket, key, e.getMessage());
        throw new ServiceUnavailableException("Storage", "Download temporarily unavailable");
    }

    private InputStream downloadRangeFallback(String bucket, String key, long offset, long length, Exception e) {
        log.error("Storage range download failed for {}/{} (offset={}, length={}): {}", bucket, key, offset, length, e.getMessage());
        throw new ServiceUnavailableException("Storage", "Range download temporarily unavailable");
    }

    private void deleteFallback(String bucket, String key, Exception e) {
        log.error("Storage delete failed for {}/{}: {}", bucket, key, e.getMessage());
        throw new ServiceUnavailableException("Storage", "Delete temporarily unavailable");
    }

    private boolean existsFallback(String bucket, String key, Exception e) {
        log.error("Storage exists check failed for {}/{}: {}", bucket, key, e.getMessage());
        throw new ServiceUnavailableException("Storage", "Storage temporarily unavailable");
    }

    private String generatePresignedUrlFallback(String bucket, String key, Duration expiry, Exception e) {
        log.error("Presigned URL generation failed for {}/{}: {}", bucket, key, e.getMessage());
        throw new ServiceUnavailableException("Storage", "URL generation temporarily unavailable");
    }

    private String generatePresignedUploadUrlFallback(String bucket, String key, Duration expiry, String contentType, Exception e) {
        log.error("Presigned upload URL generation failed for {}/{}: {}", bucket, key, e.getMessage());
        throw new ServiceUnavailableException("Storage", "URL generation temporarily unavailable");
    }

    private long getUsageFallback(String bucket, String prefix, Exception e) {
        log.error("Storage usage calculation failed for {}/{}: {}", bucket, prefix, e.getMessage());
        throw new ServiceUnavailableException("Storage", "Usage calculation temporarily unavailable");
    }

    private void copyObjectFallback(String sourceBucket, String sourceKey, String destBucket, String destKey, Exception e) {
        log.error("Storage copy failed from {}/{} to {}/{}: {}", sourceBucket, sourceKey, destBucket, destKey, e.getMessage());
        throw new ServiceUnavailableException("Storage", "Copy temporarily unavailable");
    }

    private void setStorageClassFallback(String bucket, String key, StorageTier storageClass, Exception e) {
        log.error("Storage class change failed for {}/{}: {}", bucket, key, e.getMessage());
        throw new ServiceUnavailableException("Storage", "Storage class change temporarily unavailable");
    }

    private Map<String, String> getMetadataFallback(String bucket, String key, Exception e) {
        log.error("Metadata retrieval failed for {}/{}: {}", bucket, key, e.getMessage());
        throw new ServiceUnavailableException("Storage", "Metadata retrieval temporarily unavailable");
    }

    private long getObjectSizeFallback(String bucket, String key, Exception e) {
        log.error("Object size retrieval failed for {}/{}: {}", bucket, key, e.getMessage());
        throw new ServiceUnavailableException("Storage", "Object size retrieval temporarily unavailable");
    }

    private String getContentTypeFallback(String bucket, String key, Exception e) {
        log.error("Content type retrieval failed for {}/{}: {}", bucket, key, e.getMessage());
        throw new ServiceUnavailableException("Storage", "Content type retrieval temporarily unavailable");
    }

    private String getObjectETagFallback(String bucket, String key, Exception e) {
        log.error("ETag retrieval failed for {}/{}: {}", bucket, key, e.getMessage());
        throw new ServiceUnavailableException("Storage", "ETag retrieval temporarily unavailable");
    }

    private String initiateMultipartUploadFallback(String bucket, String key, String contentType, Exception e) {
        log.error("Multipart upload initiation failed for {}/{}: {}", bucket, key, e.getMessage());
        throw new ServiceUnavailableException("Storage", "Multipart upload initiation temporarily unavailable");
    }

    private String generatePresignedPartUploadUrlFallback(String bucket, String key, String uploadId, int partNumber, Duration expiry, Exception e) {
        log.error("Part upload URL generation failed for {}/{} part {}: {}", bucket, key, partNumber, e.getMessage());
        throw new ServiceUnavailableException("Storage", "Part upload URL generation temporarily unavailable");
    }

    private void completeMultipartUploadFallback(String bucket, String key, String uploadId, Map<Integer, String> partETags, Exception e) {
        log.error("Multipart upload completion failed for {}/{}: {}", bucket, key, e.getMessage());
        throw new ServiceUnavailableException("Storage", "Multipart upload completion temporarily unavailable");
    }

    private void abortMultipartUploadFallback(String bucket, String key, String uploadId, Exception e) {
        log.error("Multipart upload abort failed for {}/{}: {}", bucket, key, e.getMessage());
        throw new ServiceUnavailableException("Storage", "Multipart upload abort temporarily unavailable");
    }
}
