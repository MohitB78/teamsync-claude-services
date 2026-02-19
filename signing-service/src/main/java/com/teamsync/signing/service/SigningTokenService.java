package com.teamsync.signing.service;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Service for generating and validating secure signing tokens.
 *
 * Follows the pattern from PortalAuthService in team-service:
 * - Generate cryptographically secure tokens
 * - Store only the hash (SHA-256)
 * - Validate by hashing the input and comparing
 *
 * Token format: {requestId}.{signerId}.{randomBytes}
 * This embeds context in the token while maintaining security.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SigningTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;  // 256 bits of entropy

    @Value("${teamsync.signing.token.expiry-days:7}")
    private int defaultExpiryDays;

    @Value("${teamsync.signing.download.expiry-days:30}")
    private int downloadExpiryDays;

    /**
     * Generate a secure signing token for a signer.
     *
     * @param requestId The signature request ID
     * @param signerId The signer ID within the request
     * @return SigningToken with plainToken (for email), tokenHash (for storage), and expiresAt
     */
    public SigningToken generateSigningToken(String requestId, String signerId) {
        return generateSigningToken(requestId, signerId, Duration.ofDays(defaultExpiryDays));
    }

    /**
     * Generate a secure signing token with custom validity.
     *
     * @param requestId The signature request ID
     * @param signerId The signer ID within the request
     * @param validity Duration the token is valid
     * @return SigningToken with plainToken, tokenHash, and expiresAt
     */
    public SigningToken generateSigningToken(String requestId, String signerId, Duration validity) {
        // Generate random bytes
        byte[] tokenBytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(tokenBytes);
        String randomPart = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

        // Create full token with context
        String plainToken = requestId + "." + signerId + "." + randomPart;

        // Hash for storage (only the hash is stored in database)
        String tokenHash = hashToken(plainToken);

        Instant expiresAt = Instant.now().plus(validity);

        log.debug("Generated signing token for request: {}, signer: {}", requestId, signerId);

        return SigningToken.builder()
                .plainToken(plainToken)
                .tokenHash(tokenHash)
                .expiresAt(expiresAt)
                .requestId(requestId)
                .signerId(signerId)
                .build();
    }

    /**
     * Generate a simple signing token without embedded context.
     * This is useful when the signer info will be retrieved from the database.
     *
     * @return Plain token string (to send in email)
     */
    public String generateSigningToken() {
        byte[] tokenBytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    /**
     * Generate a download token for completed document.
     * Download tokens have longer validity than signing tokens.
     *
     * @param requestId The signature request ID
     * @return Plain token string for download
     */
    public String generateDownloadToken(String requestId) {
        byte[] tokenBytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(tokenBytes);
        String randomPart = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

        String plainToken = "download." + requestId + "." + randomPart;
        log.debug("Generated download token for request: {}", requestId);

        return plainToken;
    }

    /**
     * Generate a download token with additional context.
     *
     * @param requestId The signature request ID
     * @param signerId The signer ID
     * @param bucket The storage bucket
     * @param storageKey The storage key
     * @return Plain token string for download
     */
    public String generateDownloadToken(String requestId, String signerId, String bucket, String storageKey) {
        byte[] tokenBytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(tokenBytes);
        String randomPart = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

        String plainToken = "download." + requestId + "." + signerId + "." + randomPart;
        log.debug("Generated download token for request: {}, signer: {}", requestId, signerId);

        return plainToken;
    }

    /**
     * Hash a token using SHA-256.
     * This is a one-way operation - the original token cannot be recovered.
     *
     * @param token The plain token to hash
     * @return Base64-encoded SHA-256 hash
     */
    public String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Validate a token by comparing its hash.
     *
     * @param plainToken The token to validate
     * @param storedHash The stored hash to compare against
     * @return true if the token matches
     */
    public boolean validateToken(String plainToken, String storedHash) {
        if (plainToken == null || storedHash == null) {
            return false;
        }
        String computedHash = hashToken(plainToken);
        return MessageDigest.isEqual(
                computedHash.getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * Parse token parts if needed for validation.
     * Token format: {requestId}.{signerId}.{randomBytes}
     *
     * @param plainToken The token to parse
     * @return TokenParts or null if invalid format
     */
    public TokenParts parseToken(String plainToken) {
        if (plainToken == null) return null;

        String[] parts = plainToken.split("\\.", 3);
        if (parts.length != 3) return null;

        return TokenParts.builder()
                .requestId(parts[0])
                .signerId(parts[1])
                .randomPart(parts[2])
                .build();
    }

    /**
     * Token generation result.
     */
    @Data
    @Builder
    public static class SigningToken {
        private String plainToken;   // Send this in email (never store)
        private String tokenHash;    // Store this in database
        private Instant expiresAt;
        private String requestId;
        private String signerId;
    }

    /**
     * Parsed token parts.
     */
    @Data
    @Builder
    public static class TokenParts {
        private String requestId;
        private String signerId;
        private String randomPart;
    }
}
