package com.teamsync.presence.model;

import com.teamsync.presence.dto.DocumentPresenceDTO;
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
public class DocumentPresence implements Serializable {

    private static final long serialVersionUID = 1L;

    private String documentId;
    private String documentName;
    private String tenantId;
    private String driveId;
    private Instant createdAt;

    public static String buildRedisKey(String tenantId, String documentId) {
        return String.format("presence:document:%s:%s", tenantId, documentId);
    }

    public static String buildViewersSetKey(String tenantId, String documentId) {
        return String.format("presence:document:viewers:%s:%s", tenantId, documentId);
    }

    public static String buildEditorsSetKey(String tenantId, String documentId) {
        return String.format("presence:document:editors:%s:%s", tenantId, documentId);
    }

    public static String buildActiveDocumentsSetKey(String tenantId) {
        return String.format("presence:active-documents:%s", tenantId);
    }
}
