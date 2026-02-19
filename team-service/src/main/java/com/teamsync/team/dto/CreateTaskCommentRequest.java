package com.teamsync.team.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for creating a task comment.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTaskCommentRequest {

    @NotBlank(message = "Comment content is required")
    @Size(max = 10000, message = "Comment cannot exceed 10000 characters")
    private String content;

    /**
     * Users mentioned in the comment (@mentions).
     * Will trigger notifications.
     */
    private List<String> mentionedUserIds;

    /**
     * Document IDs from team drive to attach.
     */
    private List<String> attachmentIds;

    /**
     * Parent comment ID for replies.
     */
    private String parentCommentId;
}
