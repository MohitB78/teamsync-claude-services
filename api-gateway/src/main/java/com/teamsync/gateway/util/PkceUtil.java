package com.teamsync.gateway.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Utility class for PKCE (Proof Key for Code Exchange) operations.
 *
 * <p>PKCE is an extension to the OAuth 2.0 Authorization Code flow that prevents
 * authorization code interception attacks. It works by:
 * <ol>
 *   <li>Client generates a random code_verifier</li>
 *   <li>Client sends code_challenge (SHA256 hash of code_verifier) with auth request</li>
 *   <li>Client sends original code_verifier with token exchange request</li>
 *   <li>Server verifies that code_challenge matches SHA256(code_verifier)</li>
 * </ol>
 *
 * <p>This adds security even for confidential clients as it ensures the entity
 * exchanging the code is the same one that initiated the flow.
 *
 * @see <a href="https://tools.ietf.org/html/rfc7636">RFC 7636 - PKCE</a>
 */
public final class PkceUtil {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int CODE_VERIFIER_LENGTH = 64;

    private PkceUtil() {
        // Utility class - no instantiation
    }

    /**
     * Generate a cryptographically random code verifier.
     *
     * <p>Per RFC 7636, the code verifier should be:
     * <ul>
     *   <li>43-128 characters long</li>
     *   <li>Use unreserved characters: [A-Z] / [a-z] / [0-9] / "-" / "." / "_" / "~"</li>
     * </ul>
     *
     * <p>We use 64 bytes of random data, Base64URL encoded to ~86 characters.
     *
     * @return A random code verifier string
     */
    public static String generateCodeVerifier() {
        byte[] randomBytes = new byte[CODE_VERIFIER_LENGTH];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * Generate the code challenge from a code verifier using SHA-256.
     *
     * <p>The code challenge is BASE64URL(SHA256(code_verifier)).
     *
     * @param codeVerifier The code verifier
     * @return The code challenge
     * @throws IllegalStateException if SHA-256 is not available
     */
    public static String generateCodeChallenge(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required by Java spec, this should never happen
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Generate a random state parameter for CSRF protection.
     *
     * <p>The state parameter ties the authorization request to the session,
     * preventing CSRF attacks on the OAuth2 flow.
     *
     * @return A random state string
     */
    public static String generateState() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * Generate a random nonce for OpenID Connect.
     *
     * <p>The nonce is included in the ID token and can be verified to prevent
     * replay attacks.
     *
     * @return A random nonce string
     */
    public static String generateNonce() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * Data class to hold PKCE parameters for an authorization request.
     */
    public record PkceParameters(
        String codeVerifier,
        String codeChallenge,
        String state,
        String nonce
    ) {
        /**
         * Generate a complete set of PKCE parameters.
         */
        public static PkceParameters generate() {
            String verifier = generateCodeVerifier();
            return new PkceParameters(
                verifier,
                generateCodeChallenge(verifier),
                generateState(),
                generateNonce()
            );
        }
    }
}
