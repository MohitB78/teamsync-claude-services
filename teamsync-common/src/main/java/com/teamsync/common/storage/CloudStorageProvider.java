package com.teamsync.common.storage;

import java.io.InputStream;
import java.time.Duration;
import java.util.Map;

/**
 * Cloud-agnostic storage interface.
 * Implementations: S3StorageProvider, GCSStorageProvider, MinIOStorageProvider
 */
public interface CloudStorageProvider {

    /**
     * Upload a file to storage.
     *
     * @param bucket      The bucket/container name
     * @param key         The object key/path
     * @param data        The file data stream
     * @param size        The file size in bytes
     * @param contentType The MIME type of the file
     * @return The storage URL or key
     */
    String upload(String bucket, String key, InputStream data, long size, String contentType);

    /**
     * Upload a file with metadata.
     */
    String upload(String bucket, String key, InputStream data, long size, String contentType, Map<String, String> metadata);

    /**
     * Download a file from storage.
     *
     * @param bucket The bucket/container name
     * @param key    The object key/path
     * @return The file data stream
     */
    InputStream download(String bucket, String key);

    /**
     * Download a byte range of a file from storage.
     * Used for HTTP Range requests to support streaming PDF viewers.
     *
     * @param bucket The bucket/container name
     * @param key    The object key/path
     * @param offset The starting byte offset (0-based)
     * @param length The number of bytes to read
     * @return The file data stream for the requested range
     */
    InputStream downloadRange(String bucket, String key, long offset, long length);

    /**
     * Delete a file from storage.
     *
     * @param bucket The bucket/container name
     * @param key    The object key/path
     */
    void delete(String bucket, String key);

    /**
     * Check if a file exists.
     *
     * @param bucket The bucket/container name
     * @param key    The object key/path
     * @return true if the file exists
     */
    boolean exists(String bucket, String key);

    /**
     * Generate a presigned URL for direct browser upload/download.
     *
     * @param bucket The bucket/container name
     * @param key    The object key/path
     * @param expiry The URL expiration duration
     * @return The presigned URL
     */
    String generatePresignedUrl(String bucket, String key, Duration expiry);

    /**
     * Generate a presigned URL for upload.
     */
    String generatePresignedUploadUrl(String bucket, String key, Duration expiry, String contentType);

    /**
     * Get storage usage for a prefix.
     *
     * @param bucket The bucket/container name
     * @param prefix The prefix to calculate usage for
     * @return Storage usage in bytes
     */
    long getUsage(String bucket, String prefix);

    /**
     * Copy an object within or between buckets.
     */
    void copyObject(String sourceBucket, String sourceKey, String destBucket, String destKey);

    /**
     * Move an object (copy + delete).
     */
    default void moveObject(String sourceBucket, String sourceKey, String destBucket, String destKey) {
        copyObject(sourceBucket, sourceKey, destBucket, destKey);
        delete(sourceBucket, sourceKey);
    }

    /**
     * Set storage class/tier for an object.
     *
     * @param bucket       The bucket/container name
     * @param key          The object key/path
     * @param storageClass The target storage class (HOT, WARM, COLD, ARCHIVE)
     */
    void setStorageClass(String bucket, String key, StorageTier storageClass);

    /**
     * Get object metadata.
     */
    Map<String, String> getMetadata(String bucket, String key);

    /**
     * Get object size in bytes.
     */
    long getObjectSize(String bucket, String key);

    /**
     * Get content type of object.
     */
    String getContentType(String bucket, String key);

    /**
     * Get object ETag (checksum).
     * For single-part uploads, this is typically the MD5 hash.
     * For multipart uploads, this is a composite ETag (MD5 of MD5s + part count).
     *
     * @param bucket The bucket/container name
     * @param key    The object key/path
     * @return The ETag (without quotes)
     */
    String getObjectETag(String bucket, String key);

    /**
     * Object metadata containing size, content type, ETag, and user metadata.
     * Used to reduce multiple API calls to a single call for better performance.
     *
     * @param size         File size in bytes
     * @param contentType  MIME type of the file
     * @param etag         ETag/checksum (without quotes)
     * @param userMetadata User-defined metadata key-value pairs
     */
    record ObjectMetadata(long size, String contentType, String etag, Map<String, String> userMetadata) {}

    /**
     * Get combined object metadata in a single API call.
     * This is more efficient than calling getObjectSize(), getContentType(), and getObjectETag() separately,
     * as it reduces network round trips to the storage provider.
     *
     * @param bucket The bucket/container name
     * @param key    The object key/path
     * @return ObjectMetadata containing size, content type, etag, and user metadata
     */
    default ObjectMetadata getObjectMetadata(String bucket, String key) {
        // Default implementation calls individual methods for backward compatibility
        // Implementations should override with optimized single-call version
        return new ObjectMetadata(
                getObjectSize(bucket, key),
                getContentType(bucket, key),
                getObjectETag(bucket, key),
                getMetadata(bucket, key)
        );
    }

    /**
     * Copy object (alias for copyObject).
     */
    default void copy(String sourceBucket, String sourceKey, String destBucket, String destKey) {
        copyObject(sourceBucket, sourceKey, destBucket, destKey);
    }

    // ============================================
    // Multipart Upload Operations
    // ============================================

    /**
     * Initiate a multipart upload.
     *
     * @param bucket      The bucket name
     * @param key         The object key
     * @param contentType The content type
     * @return The upload ID
     */
    String initiateMultipartUpload(String bucket, String key, String contentType);

    /**
     * Generate presigned URL for uploading a part.
     *
     * @param bucket   The bucket name
     * @param key      The object key
     * @param uploadId The upload ID
     * @param partNumber The part number
     * @param expiry   URL expiration duration
     * @return The presigned URL for part upload
     */
    String generatePresignedPartUploadUrl(String bucket, String key, String uploadId, int partNumber, Duration expiry);

    /**
     * Complete a multipart upload.
     *
     * @param bucket   The bucket name
     * @param key      The object key
     * @param uploadId The upload ID
     * @param partETags Map of part numbers to ETags
     */
    void completeMultipartUpload(String bucket, String key, String uploadId, Map<Integer, String> partETags);

    /**
     * Abort a multipart upload.
     *
     * @param bucket   The bucket name
     * @param key      The object key
     * @param uploadId The upload ID
     */
    void abortMultipartUpload(String bucket, String key, String uploadId);
}
