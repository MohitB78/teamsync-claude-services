package com.teamsync.project.model;

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
@Document(collection = "projects")
@CompoundIndexes({
        @CompoundIndex(name = "tenant_idx", def = "{'tenantId': 1}"),
        @CompoundIndex(name = "tenant_team_idx", def = "{'tenantId': 1, 'teamId': 1}"),
        @CompoundIndex(name = "tenant_status_idx", def = "{'tenantId': 1, 'status': 1}")
})
public class Project {

    @Id
    private String id;

    private String tenantId;
    private String teamId;  // Optional team association

    private String name;
    private String description;
    private String icon;
    private String color;

    // Associated drive for project files
    private String driveId;

    // Project dates
    private Instant startDate;
    private Instant endDate;

    // Members with project-specific roles
    private List<ProjectMember> members;

    // Metadata
    private Map<String, Object> metadata;
    private List<String> tags;

    // Status
    private ProjectStatus status;

    // Ownership
    private String ownerId;
    private String createdBy;
    private String lastModifiedBy;

    // Audit
    private Instant createdAt;
    private Instant updatedAt;

    public enum ProjectStatus {
        ACTIVE,
        ON_HOLD,
        COMPLETED,
        ARCHIVED,
        CANCELLED
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectMember {
        private String userId;
        private ProjectRole role;
        private Instant addedAt;
    }

    public enum ProjectRole {
        OWNER,
        MANAGER,
        CONTRIBUTOR,
        VIEWER
    }
}
