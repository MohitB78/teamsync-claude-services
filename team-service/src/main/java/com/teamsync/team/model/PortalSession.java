package com.teamsync.team.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Portal session for external users.
 * Contains the user's access information and tokens.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "portal_sessions")
public class PortalSession {

    @Id
    private String id;

    /**
     * The tenant this session belongs to.
     */
    @Indexed
    private String tenantId;

    /**
     * External user ID (from ExternalUser collection).
     */
    @Indexed
    private String userId;

    /**
     * User's email address.
     */
    private String email;

    /**
     * User's display name.
     */
    private String displayName;

    /**
     * Hashed refresh token.
     */
    @Indexed(unique = true)
    private String refreshTokenHash;

    /**
     * Teams the user has access to.
     */
    private List<TeamAccess> teamAccess;

    /**
     * Session expiration (for refresh token).
     */
    @Indexed(expireAfterSeconds = 0)
    private Instant expiresAt;

    /**
     * Creation timestamp.
     */
    private Instant createdAt;

    /**
     * Last activity timestamp.
     */
    private Instant lastActiveAt;

    /**
     * Team access information.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamAccess {
        private String teamId;
        private String teamName;
        private String roleId;
        private String roleName;
        private Set<String> permissions;
    }
}
