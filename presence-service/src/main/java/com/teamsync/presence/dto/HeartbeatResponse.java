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
public class HeartbeatResponse {

    private boolean acknowledged;
    private Instant serverTime;
    private int heartbeatIntervalSeconds;
    private int timeoutSeconds;
    private UserPresenceDTO.PresenceStatus currentStatus;
    private String sessionId;
}
