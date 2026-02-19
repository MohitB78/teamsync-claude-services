package com.teamsync.presence.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserPresenceDTO {

    private String userId;
    private String tenantId;
    private String userName;
    private String email;
    private String avatarUrl;
    private PresenceStatus status;
    private String statusMessage;
    private String currentDocumentId;
    private String currentDocumentName;
    private String deviceType;
    private String clientVersion;
    private Instant lastHeartbeat;
    private Instant lastActivity;
    private Instant sessionStartedAt;

    public enum PresenceStatus {
        ONLINE,
        AWAY,
        BUSY,
        OFFLINE
    }
}
