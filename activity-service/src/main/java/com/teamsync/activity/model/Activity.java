package com.teamsync.activity.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "activities")
@CompoundIndexes({
        @CompoundIndex(name = "tenant_idx", def = "{'tenantId': 1}"),
        @CompoundIndex(name = "tenant_user_idx", def = "{'tenantId': 1, 'userId': 1}"),
        @CompoundIndex(name = "tenant_resource_idx", def = "{'tenantId': 1, 'resourceType': 1, 'resourceId': 1}"),
        @CompoundIndex(name = "created_idx", def = "{'createdAt': -1}")
})
public class Activity {

    @Id
    private String id;

    private String tenantId;
    private String driveId;

    // Who performed the action
    private String userId;
    private String userName;
    private String userEmail;

    // What action was performed
    private ActivityAction action;
    private String actionDescription;

    // On what resource
    private String resourceType;  // document, folder, share, etc.
    private String resourceId;
    private String resourceName;

    // Additional context
    private Map<String, Object> metadata;
    private Map<String, Object> changes;  // Before/after for updates

    // Request info
    private String ipAddress;
    private String userAgent;
    private String requestId;

    // Timestamp
    private Instant createdAt;

    public enum ActivityAction {
        // Document actions
        DOCUMENT_CREATED,
        DOCUMENT_VIEWED,
        DOCUMENT_UPDATED,
        DOCUMENT_DELETED,
        DOCUMENT_RESTORED,
        DOCUMENT_DOWNLOADED,
        DOCUMENT_MOVED,
        DOCUMENT_COPIED,

        // Folder actions
        FOLDER_CREATED,
        FOLDER_UPDATED,
        FOLDER_DELETED,
        FOLDER_MOVED,

        // Share actions
        SHARE_CREATED,
        SHARE_UPDATED,
        SHARE_DELETED,
        PUBLIC_LINK_CREATED,
        PUBLIC_LINK_ACCESSED,

        // Comment actions
        COMMENT_ADDED,
        COMMENT_EDITED,
        COMMENT_DELETED,

        // Workflow actions
        WORKFLOW_STARTED,
        WORKFLOW_APPROVED,
        WORKFLOW_REJECTED,
        WORKFLOW_COMPLETED,

        // Auth actions
        USER_LOGIN,
        USER_LOGOUT
    }
}
