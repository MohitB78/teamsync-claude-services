package com.teamsync.team.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Data Transfer Object for TaskComment entity.
 * Used for API responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskCommentDTO {

    private String id;
    private String tenantId;
    private String taskId;
    private String teamId;

    private String content;

    private String authorId;
    private String authorName;
    private String authorAvatar;

    private List<String> mentionedUserIds;
    private List<MentionedUserDTO> mentionedUsers;

    private List<String> attachmentIds;

    private Map<String, Integer> reactions;

    private String parentCommentId;
    private Integer replyCount;

    private Boolean isEdited;
    private Boolean isPinned;

    private Instant createdAt;
    private Instant updatedAt;

    /**
     * DTO for mentioned user info.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MentionedUserDTO {
        private String userId;
        private String displayName;
        private String avatar;
    }
}
