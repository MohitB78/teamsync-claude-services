package com.teamsync.team.model;

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

/**
 * Comment on a task.
 * Supports @mentions, attachments, and reactions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "task_comments")
@CompoundIndexes({
        @CompoundIndex(name = "tenant_task_idx", def = "{'tenantId': 1, 'taskId': 1}"),
        @CompoundIndex(name = "tenant_team_idx", def = "{'tenantId': 1, 'teamId': 1}"),
        @CompoundIndex(name = "created_idx", def = "{'createdAt': -1}")
})
public class TaskComment {

    @Id
    private String id;

    private String tenantId;
    private String taskId;
    private String teamId;

    /**
     * Comment content (supports Markdown).
     */
    private String content;

    /**
     * User IDs mentioned in this comment (@username).
     * These users receive notifications.
     */
    private List<String> mentionedUserIds;

    /**
     * Document IDs attached to this comment.
     */
    private List<String> attachmentIds;

    // Author information
    private String authorId;

    /**
     * Denormalized author name for display.
     */
    private String authorName;

    /**
     * Denormalized author avatar URL for display.
     */
    private String authorAvatarUrl;

    /**
     * Whether the author is an external user.
     */
    private Boolean isAuthorExternal;

    /**
     * Parent comment ID for replies (threaded comments).
     */
    private String parentCommentId;

    /**
     * Number of replies to this comment.
     */
    private Integer replyCount;

    /**
     * Whether this comment is pinned.
     */
    private Boolean isPinned;

    // Edit tracking
    private Boolean isEdited;
    private Instant editedAt;

    /**
     * Reactions on this comment.
     * Map of emoji to list of user IDs who reacted.
     * Example: {"👍": ["user1", "user2"], "❤️": ["user3"]}
     */
    private Map<String, List<String>> reactions;

    // Timestamps
    private Instant createdAt;
    private Instant updatedAt;

    // Helper methods

    /**
     * Checks if this comment has been edited.
     */
    public boolean wasEdited() {
        return Boolean.TRUE.equals(isEdited);
    }

    /**
     * Gets the total reaction count.
     */
    public int getTotalReactionCount() {
        if (reactions == null) {
            return 0;
        }
        return reactions.values().stream()
                .mapToInt(List::size)
                .sum();
    }

    /**
     * Checks if a user has reacted with a specific emoji.
     */
    public boolean hasUserReacted(String userId, String emoji) {
        if (reactions == null) {
            return false;
        }
        List<String> users = reactions.get(emoji);
        return users != null && users.contains(userId);
    }
}
