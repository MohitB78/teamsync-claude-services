package com.teamsync.presence.model;

import com.teamsync.presence.dto.UserPresenceDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPresence implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;  // Same as userId
    private String tenantId;
    private String userId;
    private String userName;
    private String email;
    private String avatarUrl;
    private UserPresenceDTO.PresenceStatus status;
    private String statusMessage;
    private String currentDocumentId;
    private String currentDocumentName;
    private String deviceType;
    private String clientVersion;
    private String sessionId;
    private Instant lastHeartbeat;
    private Instant lastActivity;
    private Instant sessionStartedAt;
    private long idleTimeSeconds;
    private boolean isActive;

    public static String buildRedisKey(String tenantId, String userId) {
        return String.format("presence:user:%s:%s", tenantId, userId);
    }

    public static String buildTenantOnlineSetKey(String tenantId) {
        return String.format("presence:online:%s", tenantId);
    }

    public static String buildGlobalOnlineSetKey() {
        return "presence:online:global";
    }

    public boolean isExpired(int timeoutSeconds) {
        if (lastHeartbeat == null) {
            return true;
        }
        return Instant.now().isAfter(lastHeartbeat.plusSeconds(timeoutSeconds));
    }

    public boolean shouldMarkAway(int awayThresholdSeconds) {
        if (lastActivity == null) {
            return idleTimeSeconds > awayThresholdSeconds;
        }
        long inactiveSeconds = Instant.now().getEpochSecond() - lastActivity.getEpochSecond();
        return inactiveSeconds > awayThresholdSeconds || idleTimeSeconds > awayThresholdSeconds;
    }
}
