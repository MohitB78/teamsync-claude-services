package com.teamsync.common.security;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;

/**
 * SECURITY FIX (Round 7): Utility for generating and validating service tokens.
 *
 * <p>This utility is used by the API Gateway to generate service tokens that are
 * verified by backend services using {@link ServiceTokenFilter}.
 *
 * <p>The service token is an HMAC-SHA256 signature that proves the request came
 * from the API Gateway and that the context headers (X-Tenant-ID, X-User-ID)
 * haven't been tampered with.
 *
 * @see ServiceTokenFilter
 */
@Slf4j
public final class ServiceTokenUtil {

    public static final String SERVICE_TOKEN_HEADER = "X-Service-Token";
    public static final String SERVICE_TIMESTAMP_HEADER = "X-Service-Timestamp";

    private ServiceTokenUtil() {
        // Utility class
    }

    /**
     * Minimum required secret length for security.
     * SECURITY FIX (Round 9): Enforce minimum key length to prevent weak keys.
     */
    private static final int MIN_SECRET_LENGTH = 32;

    /**
     * Generate a service token for the given context.
     *
     * @param tenantId the tenant ID (may be null)
     * @param userId   the user ID (may be null)
     * @param secret   the shared secret for HMAC computation
     * @return a ServiceToken containing the token string and timestamp
     * @throws ServiceTokenException if token generation fails
     */
    public static ServiceToken generate(String tenantId, String userId, String secret) {
        if (secret == null || secret.isBlank()) {
            throw new ServiceTokenException("Service token secret is not configured");
        }

        // SECURITY FIX (Round 9): Enforce minimum key length to prevent predictable/weak keys
        // This prevents attackers from brute-forcing short secrets
        if (secret.length() < MIN_SECRET_LENGTH) {
            log.error("SECURITY: Service token secret is too short ({} chars, minimum {} required)",
                    secret.length(), MIN_SECRET_LENGTH);
            throw new ServiceTokenException(
                    "Service token secret must be at least " + MIN_SECRET_LENGTH + " characters");
        }

        long timestamp = Instant.now().getEpochSecond();

        String dataToSign = String.format("%s:%s:%d",
                tenantId != null ? tenantId : "",
                userId != null ? userId : "",
                timestamp);

        try {
            String token = computeHmac(dataToSign, secret);
            return new ServiceToken(token, timestamp);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Failed to generate service token", e);
            throw new ServiceTokenException("Failed to generate service token", e);
        }
    }

    /**
     * Validate a service token.
     *
     * @param token                    the service token to validate
     * @param timestamp                the timestamp from the request
     * @param tenantId                 the tenant ID from the request
     * @param userId                   the user ID from the request
     * @param secret                   the shared secret for HMAC computation
     * @param timestampToleranceSeconds the maximum allowed time difference in seconds
     * @return true if the token is valid, false otherwise
     */
    public static boolean validate(String token, long timestamp, String tenantId, String userId,
                                   String secret, long timestampToleranceSeconds) {
        if (token == null || token.isBlank() || secret == null || secret.isBlank()) {
            return false;
        }

        // Check timestamp tolerance
        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - timestamp) > timestampToleranceSeconds) {
            log.debug("Service token validation failed: timestamp {} is outside tolerance window", timestamp);
            return false;
        }

        // Compute expected token
        String dataToSign = String.format("%s:%s:%d",
                tenantId != null ? tenantId : "",
                userId != null ? userId : "",
                timestamp);

        try {
            String expectedToken = computeHmac(dataToSign, secret);

            // Constant-time comparison to prevent timing attacks
            return java.security.MessageDigest.isEqual(
                    token.getBytes(StandardCharsets.UTF_8),
                    expectedToken.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Failed to validate service token", e);
            return false;
        }
    }

    private static String computeHmac(String data, String secret)
            throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hmacBytes);
    }

    /**
     * Holder for a generated service token and its timestamp.
     */
    public record ServiceToken(String token, long timestamp) {
    }

    /**
     * Exception thrown when service token operations fail.
     */
    public static class ServiceTokenException extends RuntimeException {
        public ServiceTokenException(String message) {
            super(message);
        }

        public ServiceTokenException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
