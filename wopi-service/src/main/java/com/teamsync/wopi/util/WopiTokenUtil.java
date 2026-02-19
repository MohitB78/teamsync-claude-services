package com.teamsync.wopi.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;

/**
 * SECURITY FIX (Round 13 #1): Secure WOPI token utility.
 *
 * Replaces the previous plaintext token format with cryptographically signed tokens.
 * Tokens are HMAC-SHA256 signed to prevent tampering and forgery.
 *
 * Token Format: base64url(payload).base64url(signature)
 * Payload: JSON containing tenantId, userId, documentId, driveId, permissions, expiry
 *
 * Security Properties:
 * - Tokens cannot be forged without the secret key
 * - Tokens have a configurable expiry time
 * - Token payload is tamper-evident (signature verification)
 * - Uses constant-time comparison to prevent timing attacks
 */
@Slf4j
public class WopiTokenUtil {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Token payload containing all WOPI access information.
     */
    public record WopiTokenPayload(
            String tenantId,
            String userId,
            String userName,
            String documentId,
            String driveId,
            String ownerId,
            boolean canWrite,
            long expiresAt  // Unix timestamp in seconds
    ) {}

    /**
     * Generate a secure WOPI access token.
     *
     * @param tenantId   The tenant ID
     * @param userId     The user ID
     * @param userName   The user's display name
     * @param documentId The document ID being accessed
     * @param driveId    The drive ID
     * @param ownerId    The document owner's ID
     * @param canWrite   Whether the user has write permission
     * @param ttlSeconds Token time-to-live in seconds
     * @param secretKey  The HMAC secret key
     * @return A signed WOPI token
     */
    public static String generateToken(
            String tenantId,
            String userId,
            String userName,
            String documentId,
            String driveId,
            String ownerId,
            boolean canWrite,
            long ttlSeconds,
            String secretKey) {

        // Validate inputs
        if (tenantId == null || userId == null || documentId == null || secretKey == null) {
            throw new IllegalArgumentException("Required token parameters cannot be null");
        }

        if (secretKey.length() < 32) {
            throw new IllegalArgumentException("Secret key must be at least 32 characters");
        }

        long expiresAt = Instant.now().getEpochSecond() + ttlSeconds;

        WopiTokenPayload payload = new WopiTokenPayload(
                tenantId,
                userId,
                userName != null ? userName : "User",
                documentId,
                driveId,
                ownerId != null ? ownerId : userId,
                canWrite,
                expiresAt
        );

        try {
            // Serialize payload to JSON
            String payloadJson = objectMapper.writeValueAsString(payload);
            String payloadBase64 = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));

            // Generate HMAC signature
            String signature = generateHmac(payloadBase64, secretKey);

            // Combine: payload.signature
            return payloadBase64 + "." + signature;

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize WOPI token payload", e);
            throw new RuntimeException("Failed to generate WOPI token", e);
        }
    }

    /**
     * Validate and decode a WOPI access token.
     *
     * @param token     The token to validate
     * @param secretKey The HMAC secret key
     * @return The decoded token payload
     * @throws InvalidWopiTokenException if the token is invalid, tampered, or expired
     */
    public static WopiTokenPayload validateToken(String token, String secretKey)
            throws InvalidWopiTokenException {

        if (token == null || token.isBlank()) {
            throw new InvalidWopiTokenException("Token is required");
        }

        // Split token into payload and signature
        String[] parts = token.split("\\.");
        if (parts.length != 2) {
            log.warn("SECURITY: WOPI token has invalid format (expected 2 parts, got {})", parts.length);
            throw new InvalidWopiTokenException("Invalid token format");
        }

        String payloadBase64 = parts[0];
        String providedSignature = parts[1];

        // Verify signature using constant-time comparison
        String expectedSignature = generateHmac(payloadBase64, secretKey);
        if (!constantTimeEquals(expectedSignature, providedSignature)) {
            log.warn("SECURITY: WOPI token signature mismatch - possible tampering attempt");
            throw new InvalidWopiTokenException("Invalid token signature");
        }

        // Decode payload
        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(payloadBase64);
            String payloadJson = new String(payloadBytes, StandardCharsets.UTF_8);
            WopiTokenPayload payload = objectMapper.readValue(payloadJson, WopiTokenPayload.class);

            // Check expiry
            if (payload.expiresAt() < Instant.now().getEpochSecond()) {
                log.debug("WOPI token expired at {}", Instant.ofEpochSecond(payload.expiresAt()));
                throw new InvalidWopiTokenException("Token has expired");
            }

            return payload;

        } catch (IllegalArgumentException e) {
            log.warn("SECURITY: WOPI token has invalid base64 encoding");
            throw new InvalidWopiTokenException("Invalid token encoding");
        } catch (JsonProcessingException e) {
            log.warn("SECURITY: WOPI token has invalid JSON payload");
            throw new InvalidWopiTokenException("Invalid token payload");
        }
    }

    /**
     * Validate token and verify it matches the requested document.
     *
     * @param token      The token to validate
     * @param documentId The document ID being accessed
     * @param secretKey  The HMAC secret key
     * @return The decoded token payload
     * @throws InvalidWopiTokenException if validation fails or document doesn't match
     */
    public static WopiTokenPayload validateTokenForDocument(String token, String documentId, String secretKey)
            throws InvalidWopiTokenException {

        WopiTokenPayload payload = validateToken(token, secretKey);

        // Verify document ID matches
        if (!payload.documentId().equals(documentId)) {
            log.warn("SECURITY: WOPI token document ID mismatch - token: {}, requested: {}",
                    payload.documentId(), documentId);
            throw new InvalidWopiTokenException("Token is not valid for this document");
        }

        return payload;
    }

    /**
     * Generate HMAC-SHA256 signature.
     */
    private static String generateHmac(String data, String secretKey) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    secretKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hmacBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to generate HMAC", e);
        }
    }

    /**
     * SECURITY: Constant-time string comparison to prevent timing attacks.
     * Uses MessageDigest.isEqual which performs constant-time comparison.
     */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * Exception thrown when WOPI token validation fails.
     */
    public static class InvalidWopiTokenException extends Exception {
        public InvalidWopiTokenException(String message) {
            super(message);
        }
    }
}
