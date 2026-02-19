package com.teamsync.chat.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "messages")
@CompoundIndexes({
        @CompoundIndex(name = "tenant_channel_idx", def = "{'tenantId': 1, 'channelId': 1}"),
        @CompoundIndex(name = "tenant_document_idx", def = "{'tenantId': 1, 'documentId': 1}"),
        @CompoundIndex(name = "channel_created_idx", def = "{'channelId': 1, 'createdAt': -1}")
})
public class Message {

    @Id
    private String id;

    private String tenantId;
    private String channelId;     // For team/project channels
    private String documentId;    // For document comments
    private String threadId;      // For threaded replies

    private String senderId;
    private String senderName;
    private String content;

    private MessageType type;

    // For file attachments
    private List<Attachment> attachments;

    // Reactions
    private Map<String, List<String>> reactions;  // emoji -> userIds

    // Mentions
    private List<String> mentionedUserIds;

    // Edit history
    private Boolean isEdited;
    private Instant editedAt;

    // Status
    private Boolean isDeleted;
    private Instant deletedAt;

    // Audit
    private Instant createdAt;
    private Instant updatedAt;

    public enum MessageType {
        TEXT,
        FILE,
        SYSTEM,
        COMMENT  // Document comment
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Attachment {
        private String id;
        private String name;
        private String contentType;
        private Long size;
        private String storageKey;
        private String thumbnailUrl;
    }
}
