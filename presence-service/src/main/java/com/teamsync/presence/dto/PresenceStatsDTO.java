package com.teamsync.presence.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PresenceStatsDTO {

    private String tenantId;
    private int totalOnlineUsers;
    private int totalAwayUsers;
    private int totalBusyUsers;
    private int totalActiveDocuments;
    private int totalActiveEditors;
    private Map<String, Integer> usersByDeviceType;
    private Map<String, Integer> topActiveDocuments;  // documentId -> viewer count
    private Instant generatedAt;
}
