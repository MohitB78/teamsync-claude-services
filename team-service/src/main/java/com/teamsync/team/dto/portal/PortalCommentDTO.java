package com.teamsync.team.dto.portal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Comment DTO for portal users.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortalCommentDTO {

    private String id;
    private String taskId;
    private String content;
    private String authorId;
    private String authorName;
    private String authorAvatar;
    private Instant createdAt;
    private Instant updatedAt;
    private boolean isEdited;
}
