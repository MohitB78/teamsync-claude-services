package com.teamsync.storage.repository;

import com.teamsync.storage.model.StorageQuota;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

@Repository
public interface StorageQuotaRepository extends MongoRepository<StorageQuota, String> {

    Optional<StorageQuota> findByTenantIdAndDriveId(String tenantId, String driveId);

    /**
     * Find all storage quotas for a tenant with pagination.
     * SECURITY FIX (Round 14 #H43): Added pagination to prevent memory exhaustion.
     */
    List<StorageQuota> findByTenantId(String tenantId, Pageable pageable);

    @Query("{ 'tenantId': ?0, 'driveId': ?1 }")
    @Update("{ '$inc': { 'usedStorage': ?2, 'currentFileCount': ?3 } }")
    void incrementUsage(String tenantId, String driveId, long storageBytes, int fileCount);

    @Query("{ 'tenantId': ?0, 'driveId': ?1 }")
    @Update("{ '$inc': { 'reservedStorage': ?2 } }")
    void updateReservedStorage(String tenantId, String driveId, long reservedBytes);

    /**
     * Find drives exceeding quota threshold (e.g., 90%) with pagination.
     * SECURITY FIX (Round 14 #H43): Added pagination to prevent memory exhaustion.
     */
    @Query("{ 'tenantId': ?0, '$expr': { '$gte': [ { '$divide': ['$usedStorage', '$quotaLimit'] }, ?1 ] } }")
    List<StorageQuota> findDrivesExceedingThreshold(String tenantId, double threshold, Pageable pageable);

    /**
     * SECURITY FIX: Atomic operation to finalize upload - moves bytes from reserved to used.
     * Prevents race condition where concurrent uploads could corrupt quota tracking.
     */
    @Query("{ 'tenantId': ?0, 'driveId': ?1 }")
    @Update("{ '$inc': { 'reservedStorage': ?#{-[2]}, 'usedStorage': ?2, 'currentFileCount': 1 }, '$set': { 'updatedAt': ?3 } }")
    void atomicFinalizeUpload(String tenantId, String driveId, long bytes, java.time.Instant updatedAt);

    /**
     * SECURITY FIX: Atomic operation to release reservation without incrementing used storage.
     * Used when upload is cancelled or fails.
     */
    @Query("{ 'tenantId': ?0, 'driveId': ?1 }")
    @Update("{ '$inc': { 'reservedStorage': ?#{-[2]} }, '$set': { 'updatedAt': ?3 } }")
    void atomicReleaseReservation(String tenantId, String driveId, long bytes, java.time.Instant updatedAt);

    /**
     * SECURITY FIX (Round 5): Atomic quota reservation with availability check.
     * Prevents race condition where concurrent uploads could both pass quota check
     * before either reserves storage.
     *
     * Uses MongoDB's conditional update: only reserves if sufficient space available.
     * Returns number of documents modified (1 if success, 0 if quota exceeded).
     *
     * Condition: quotaLimit - usedStorage - reservedStorage >= requiredBytes
     */
    @Query("{ 'tenantId': ?0, 'driveId': ?1, '$expr': { '$gte': [ { '$subtract': [ { '$subtract': ['$quotaLimit', '$usedStorage'] }, '$reservedStorage' ] }, ?2 ] } }")
    @Update("{ '$inc': { 'reservedStorage': ?2 }, '$set': { 'updatedAt': ?3 } }")
    long atomicReserveIfAvailable(String tenantId, String driveId, long requiredBytes, java.time.Instant updatedAt);

    /**
     * SECURITY FIX (Round 5): Atomic file count check and reservation.
     * Prevents race condition where concurrent uploads could exceed file count limit.
     */
    @Query("{ 'tenantId': ?0, 'driveId': ?1, '$or': [ { 'maxFileCount': null }, { '$expr': { '$lt': ['$currentFileCount', '$maxFileCount'] } } ] }")
    @Update("{ '$inc': { 'reservedStorage': ?2 }, '$set': { 'updatedAt': ?3 } }")
    long atomicReserveWithFileCountCheck(String tenantId, String driveId, long requiredBytes, java.time.Instant updatedAt);
}
