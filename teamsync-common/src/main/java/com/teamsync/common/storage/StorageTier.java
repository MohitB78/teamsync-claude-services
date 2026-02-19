package com.teamsync.common.storage;

/**
 * Logical storage tiers mapped to cloud-specific storage classes.
 */
public enum StorageTier {
    /**
     * Hot tier: Frequently accessed data.
     * AWS: STANDARD, GCP: STANDARD, MinIO: STANDARD
     */
    HOT,

    /**
     * Warm tier: Infrequently accessed data (< 30 days).
     * AWS: STANDARD_IA, GCP: NEARLINE, MinIO: STANDARD
     */
    WARM,

    /**
     * Cold tier: Rarely accessed data (30 days - 1 year).
     * AWS: GLACIER_IR, GCP: COLDLINE, MinIO: STANDARD
     */
    COLD,

    /**
     * Archive tier: Long-term retention (> 1 year).
     * AWS: DEEP_ARCHIVE, GCP: ARCHIVE, MinIO: STANDARD
     */
    ARCHIVE
}
