package com.teamsync.audit.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Result of verifying a single audit event's integrity.
 */
@Data
@Builder
public class VerificationResult {

    /**
     * The event ID that was verified.
     */
    private String eventId;

    /**
     * Whether the event was successfully verified.
     */
    private boolean verified;

    /**
     * The verification method used.
     */
    private String verificationMethod;

    /**
     * ImmuDB transaction ID for this event.
     */
    private Long immudbTransactionId;

    /**
     * Hash chain value for this event.
     */
    private String hashChain;

    /**
     * Whether the hash chain is valid (links correctly to previous events).
     */
    private boolean hashChainValid;

    /**
     * When the verification was performed.
     */
    private Instant verifiedAt;

    /**
     * Error message if verification failed.
     */
    private String errorMessage;

    /**
     * Verification methods.
     */
    public static final String METHOD_IMMUDB_VERIFIED = "IMMUDB_VERIFIED";
    public static final String METHOD_HASH_CHAIN = "HASH_CHAIN";
    public static final String METHOD_MONGODB_MIRROR = "MONGODB_MIRROR";
}
