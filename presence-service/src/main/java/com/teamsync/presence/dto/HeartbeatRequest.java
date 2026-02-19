package com.teamsync.presence.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request for heartbeat/presence updates.
 *
 * SECURITY FIX (Round 7): Removed userId and tenantId from client-settable fields.
 * SECURITY FIX (Round 15 #M15): Added @Size constraints to prevent DoS attacks.
 * These values are now set by the controller from authenticated headers to prevent
 * parameter pollution attacks where a client could impersonate another user.
 *
 * The userId and tenantId fields are kept as internal setters (package-private or
 * via @JsonIgnore) so the controller can set them from headers, but clients cannot.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class HeartbeatRequest {

    /**
     * User ID - set internally from X-User-ID header, not from request body.
     * SECURITY: This field is ignored during JSON deserialization.
     */
    @JsonIgnore
    private String userId;

    /**
     * Tenant ID - set internally from X-Tenant-ID header, not from request body.
     * SECURITY: This field is ignored during JSON deserialization.
     */
    @JsonIgnore
    private String tenantId;

    private UserPresenceDTO.PresenceStatus status;

    @Size(max = 255, message = "Status message must not exceed 255 characters")
    private String statusMessage;

    @Size(max = 64, message = "Document ID must not exceed 64 characters")
    private String currentDocumentId;

    @Size(max = 64, message = "Device type must not exceed 64 characters")
    private String deviceType;

    @Size(max = 32, message = "Client version must not exceed 32 characters")
    private String clientVersion;

    private ActivityInfo activityInfo;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityInfo {
        private boolean isActive;
        private long idleTimeSeconds;
        @Size(max = 64, message = "Last action must not exceed 64 characters")
        private String lastAction;
    }
}
