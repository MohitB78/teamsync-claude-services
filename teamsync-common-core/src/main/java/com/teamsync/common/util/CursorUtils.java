package com.teamsync.common.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * Utility for cursor-based pagination.
 */
public final class CursorUtils {

    private CursorUtils() {}

    /**
     * SECURITY FIX (Round 5): MongoDB ObjectId validation pattern.
     * ObjectIds are 24 hex characters. Invalid cursors should be rejected
     * to prevent NoSQL injection or MongoDB query errors.
     */
    private static final Pattern OBJECT_ID_PATTERN = Pattern.compile("^[a-fA-F0-9]{24}$");

    /**
     * Maximum allowed cursor length to prevent memory exhaustion attacks.
     */
    private static final int MAX_CURSOR_LENGTH = 500;

    /**
     * Encode a cursor value (typically the last document ID).
     */
    public static String encode(String value) {
        if (value == null) {
            return null;
        }
        return Base64.getUrlEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decode a cursor value.
     */
    public static String decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        // SECURITY FIX (Round 5): Limit cursor length to prevent memory exhaustion
        if (cursor.length() > MAX_CURSOR_LENGTH) {
            return null;
        }
        try {
            return new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * SECURITY FIX (Round 5): Decode and validate cursor as a MongoDB ObjectId.
     * Returns null if cursor is invalid or doesn't match ObjectId format.
     * This prevents injection attacks via malformed cursor values.
     */
    public static String decodeAndValidateObjectId(String cursor) {
        String decoded = decode(cursor);
        if (decoded == null) {
            return null;
        }
        // Validate it matches MongoDB ObjectId format (24 hex characters)
        if (!OBJECT_ID_PATTERN.matcher(decoded).matches()) {
            return null;
        }
        return decoded;
    }

    /**
     * SECURITY FIX (Round 5): Validate that a string is a valid MongoDB ObjectId.
     */
    public static boolean isValidObjectId(String value) {
        return value != null && OBJECT_ID_PATTERN.matcher(value).matches();
    }

    /**
     * Encode a compound cursor with multiple values.
     */
    public static String encodeCompound(String id, Object sortValue) {
        String compound = id + "|" + (sortValue != null ? sortValue.toString() : "");
        return encode(compound);
    }

    /**
     * Decode a compound cursor.
     *
     * SECURITY FIX (Round 10 #21): Validate split result has expected number of parts.
     * Returns null if format is invalid to prevent ArrayIndexOutOfBoundsException
     * and null pointer dereference in calling code.
     */
    public static String[] decodeCompound(String cursor) {
        String decoded = decode(cursor);
        if (decoded == null) {
            return null;
        }
        String[] parts = decoded.split("\\|", 2);

        // SECURITY FIX (Round 10 #21): Validate we got expected number of parts
        // Compound cursor must have at least ID part
        if (parts.length < 1 || parts[0] == null || parts[0].isBlank()) {
            return null;
        }

        // Ensure we always return 2 parts for consistency
        if (parts.length == 1) {
            return new String[] { parts[0], "" };
        }

        return parts;
    }

    /**
     * SECURITY FIX (Round 10 #21): Decode compound cursor with validation.
     * Returns null if cursor is invalid or parts don't match expected format.
     *
     * @param cursor The encoded cursor
     * @param validateIdAsObjectId If true, validates the ID part is a valid MongoDB ObjectId
     * @return Array of [id, sortValue] or null if invalid
     */
    public static String[] decodeCompoundValidated(String cursor, boolean validateIdAsObjectId) {
        String[] parts = decodeCompound(cursor);
        if (parts == null) {
            return null;
        }

        // Validate ID part if required
        if (validateIdAsObjectId && !isValidObjectId(parts[0])) {
            return null;
        }

        return parts;
    }
}
