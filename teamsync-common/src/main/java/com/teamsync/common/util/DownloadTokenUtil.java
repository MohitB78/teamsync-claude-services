package com.teamsync.common.util;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * Utility for generating and validating signed download tokens.
 *
 * <p>This provides time-limited, tamper-proof download URLs that allow
 * direct browser downloads without requiring authentication headers.</p>
 *
 * <p>Token format: base64(tenantId|driveId|userId|bucket|storageKey|expiresAt)|signature</p>
 *
 * <p>This component is only loaded when {@code teamsync.security.download-token-secret}
 * is properly configured (not blank, at least 32 chars, no placeholder values).
 * Services that don't handle file downloads (e.g., Permission Manager) will skip
 * loading this bean entirely.</p>
 *
 * @see OnValidDownloadTokenSecretCondition
 */
@Component
@Conditional(OnValidDownloadTokenSecretCondition.class)
@Slf4j
public class DownloadTokenUtil {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String DELIMITER = "|";

    @Value("${teamsync.security.download-token-secret:#{null}}")
    private String secret;

    private static final int MINIMUM_SECRET_LENGTH = 32;

    /**
     * SECURITY FIX (Round 10 #23): Maximum allowed token length.
     * Calculated as: base64(tenantId + driveId + userId + bucket + storageKey + timestamp)
     *                + delimiter + signature (base64 HMAC-SHA256 = 43 chars)
     * With generous estimates:
     *   - tenantId: 64 chars, driveId: 128 chars, userId: 64 chars
     *   - bucket: 128 chars, storageKey: 512 chars, timestamp: 13 chars
     *   - Total payload: ~909 chars -> base64 ~1212 chars + delimiter + 43 = ~1260
     * Setting limit to 2048 for safety margin.
     *
     * This prevents resource exhaustion attacks where attackers send very long
     * tokens to consume CPU/memory during Base64 decoding and signature verification.
     */
    private static final int MAX_TOKEN_LENGTH = 2048;

    /**
     * Validates that the download token secret is properly configured at startup.
     *
     * <p>Security requirement: The secret must be:</p>
     * <ul>
     *   <li>Explicitly configured (not using default)</li>
     *   <li>At least 32 characters long</li>
     *   <li>Not contain placeholder text like "change-in-production"</li>
     * </ul>
     *
     * @throws IllegalStateException if secret is not properly configured
     */
    @PostConstruct
    void validateSecret() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "SECURITY ERROR: teamsync.security.download-token-secret must be configured. " +
                    "Set DOWNLOAD_TOKEN_SECRET environment variable with a secure random string (min 32 chars).");
        }

        if (secret.contains("change-in-production") || secret.contains("default-secret")) {
            throw new IllegalStateException(
                    "SECURITY ERROR: teamsync.security.download-token-secret contains insecure default value. " +
                    "Set DOWNLOAD_TOKEN_SECRET environment variable with a secure random string.");
        }

        if (secret.length() < MINIMUM_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "SECURITY ERROR: teamsync.security.download-token-secret must be at least " +
                    MINIMUM_SECRET_LENGTH + " characters. Current length: " + secret.length());
        }

        log.info("Download token secret validation passed (length: {} chars)", secret.length());
    }

    /**
     * Generate a signed download token.
     *
     * @param tenantId   Tenant ID
     * @param driveId    Drive ID
     * @param userId     User ID (for audit)
     * @param bucket     Storage bucket
     * @param storageKey Storage key
     * @param expiresAt  Token expiration time
     * @return Signed token string
     */
    public String generateToken(String tenantId, String driveId, String userId,
                                String bucket, String storageKey, Instant expiresAt) {
        try {
            // Build payload
            String payload = String.join(DELIMITER,
                    tenantId,
                    driveId,
                    userId,
                    bucket,
                    storageKey,
                    String.valueOf(expiresAt.toEpochMilli()));

            // Encode payload
            String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

            // Generate signature
            String signature = sign(encodedPayload);

            return encodedPayload + DELIMITER + signature;
        } catch (Exception e) {
            log.error("Failed to generate download token", e);
            throw new RuntimeException("Failed to generate download token", e);
        }
    }

    /**
     * Validate and parse a download token.
     *
     * @param token The token to validate
     * @return Parsed token data, or null if invalid
     */
    public TokenData validateToken(String token) {
        try {
            // SECURITY FIX (Round 10 #23): Validate token length before any processing
            // This prevents resource exhaustion from excessively long tokens
            if (token == null || token.isEmpty()) {
                log.warn("SECURITY: Empty or null download token");
                return null;
            }

            if (token.length() > MAX_TOKEN_LENGTH) {
                log.warn("SECURITY: Download token exceeds maximum length ({} > {}), possible attack",
                        token.length(), MAX_TOKEN_LENGTH);
                return null;
            }

            // Split token into payload and signature
            int lastDelimiter = token.lastIndexOf(DELIMITER);
            if (lastDelimiter == -1) {
                log.warn("Invalid token format: no delimiter found");
                return null;
            }

            String encodedPayload = token.substring(0, lastDelimiter);
            String providedSignature = token.substring(lastDelimiter + 1);

            // Verify signature
            String expectedSignature = sign(encodedPayload);
            if (!constantTimeEquals(expectedSignature, providedSignature)) {
                log.warn("Invalid token signature");
                return null;
            }

            // Decode payload
            String payload = new String(
                    Base64.getUrlDecoder().decode(encodedPayload),
                    StandardCharsets.UTF_8);

            String[] parts = payload.split("\\" + DELIMITER);
            if (parts.length != 6) {
                log.warn("Invalid token payload: expected 6 parts, got {}", parts.length);
                return null;
            }

            // Check expiration
            long expiresAtMillis = Long.parseLong(parts[5]);
            if (Instant.now().toEpochMilli() > expiresAtMillis) {
                log.warn("Token expired");
                return null;
            }

            return new TokenData(
                    parts[0], // tenantId
                    parts[1], // driveId
                    parts[2], // userId
                    parts[3], // bucket
                    parts[4], // storageKey
                    Instant.ofEpochMilli(expiresAtMillis)
            );
        } catch (Exception e) {
            log.warn("Failed to validate download token: {}", e.getMessage());
            return null;
        }
    }

    private String sign(String data) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        SecretKeySpec secretKeySpec = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
        mac.init(secretKeySpec);
        byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hmacBytes);
    }

    /**
     * Constant-time string comparison to prevent timing attacks.
     *
     * SECURITY FIX (Round 10 #5): Use MessageDigest.isEqual for proper constant-time
     * comparison. The previous implementation leaked length information through
     * early return, allowing attackers to probe signature lengths.
     */
    private boolean constantTimeEquals(String a, String b) {
        // SECURITY FIX (Round 10 #5): Use Java's MessageDigest.isEqual which is
        // designed for constant-time comparison and handles all edge cases.
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(aBytes, bBytes);
    }

    /**
     * Parsed token data.
     */
    public record TokenData(
            String tenantId,
            String driveId,
            String userId,
            String bucket,
            String storageKey,
            Instant expiresAt
    ) {}
}
