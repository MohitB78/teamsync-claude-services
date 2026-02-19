package com.teamsync.audit.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * Cryptographic proof for an audit event.
 * Can be used for independent verification.
 */
@Data
@Builder
public class CryptographicProof {

    /**
     * The event ID this proof is for.
     */
    private String eventId;

    /**
     * ImmuDB transaction ID.
     */
    private Long immudbTransactionId;

    /**
     * Merkle root at the time of insertion.
     */
    private String merkleRoot;

    /**
     * Merkle proof path for verification.
     */
    private List<String> merkleProofPath;

    /**
     * Hash chain value for this event.
     */
    private String hashChain;

    /**
     * Hash chain value of the previous event.
     */
    private String previousHashChain;

    /**
     * Event timestamp.
     */
    private Instant timestamp;

    /**
     * Proof format identifier.
     */
    private String proofFormat;

    /**
     * Base64-encoded proof for external verification.
     */
    private String base64EncodedProof;

    /**
     * Proof format versions.
     */
    public static final String FORMAT_IMMUDB_V1 = "IMMUDB_V1";
    public static final String FORMAT_HASH_CHAIN_V1 = "HASH_CHAIN_V1";
}
