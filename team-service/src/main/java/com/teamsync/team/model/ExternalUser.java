package com.teamsync.team.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * External user who accesses teams via the portal.
 * These users don't have Zitadel accounts - they authenticate via magic links.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "external_users")
@CompoundIndexes({
    @CompoundIndex(name = "tenant_email_idx", def = "{'tenantId': 1, 'email': 1}", unique = true)
})
public class ExternalUser {

    @Id
    private String id;

    /**
     * The tenant this external user belongs to.
     */
    @Indexed
    private String tenantId;

    /**
     * User's email address (unique per tenant).
     */
    @Indexed
    private String email;

    /**
     * Display name for the user.
     */
    private String displayName;

    /**
     * Avatar URL if provided.
     */
    private String avatar;

    /**
     * User's status.
     */
    private ExternalUserStatus status;

    /**
     * When the user was created.
     */
    private Instant createdAt;

    /**
     * When the user last logged in.
     */
    private Instant lastLoginAt;

    /**
     * Total login count.
     */
    @Builder.Default
    private int loginCount = 0;

    /**
     * External user status.
     */
    public enum ExternalUserStatus {
        ACTIVE,
        SUSPENDED,
        DELETED
    }
}
