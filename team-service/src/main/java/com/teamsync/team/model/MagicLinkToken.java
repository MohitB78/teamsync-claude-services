package com.teamsync.team.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Magic link token for portal authentication.
 * Tokens expire after 15 minutes by default.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "magic_link_tokens")
public class MagicLinkToken {

    @Id
    private String id;

    /**
     * The tenant this token belongs to.
     */
    @Indexed
    private String tenantId;

    /**
     * Email address the magic link was sent to.
     */
    @Indexed
    private String email;

    /**
     * The hashed token value (SHA-256).
     */
    @Indexed(unique = true)
    private String tokenHash;

    /**
     * Optional invitation ID if this is linked to a team invitation.
     */
    private String invitationId;

    /**
     * When the token expires (default 15 minutes).
     */
    @Indexed(expireAfterSeconds = 0)
    private Instant expiresAt;

    /**
     * Whether this token has been used.
     */
    private boolean used;

    /**
     * When the token was used.
     */
    private Instant usedAt;

    /**
     * Creation timestamp.
     */
    private Instant createdAt;
}
