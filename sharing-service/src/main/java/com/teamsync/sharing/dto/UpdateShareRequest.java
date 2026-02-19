package com.teamsync.sharing.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.teamsync.sharing.model.Share.SharePermission;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;

/**
 * Request to update share settings.
 *
 * SECURITY FIX (Round 7): Added validation constraints to ensure proper input handling.
 * All fields are optional - only provided fields are updated.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateShareRequest {

    /**
     * New permissions to set (replaces existing permissions).
     */
    private Set<SharePermission> permissions;

    private Boolean notifyOnAccess;

    private Boolean allowReshare;

    /**
     * New password for protected shares.
     * Minimum 8 characters, maximum 128 characters if provided.
     *
     * SECURITY FIX (Round 15 #M27): Added max size constraint.
     */
    @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
    private String password;

    /**
     * Set to true to remove password protection.
     */
    private Boolean removePassword;

    /**
     * New expiration date (must be in the future if provided).
     */
    @Future(message = "Expiration date must be in the future")
    private Instant expiresAt;
}
