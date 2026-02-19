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

import java.time.Duration;
import java.time.Instant;

/**
 * Team invitation for inviting users (internal or external) to join a team.
 *
 * Invitation lifecycle:
 * 1. PENDING - Invitation sent, waiting for response
 * 2. ACCEPTED - User accepted and joined the team
 * 3. DECLINED - User declined the invitation
 * 4. EXPIRED - Invitation expired before response
 * 5. CANCELLED - Inviter cancelled the invitation
 * 6. REVOKED - Admin revoked the invitation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "team_invitations")
@CompoundIndexes({
        @CompoundIndex(name = "tenant_team_idx", def = "{'tenantId': 1, 'teamId': 1}"),
        @CompoundIndex(name = "tenant_email_idx", def = "{'tenantId': 1, 'email': 1}"),
        @CompoundIndex(name = "tenant_user_pending_idx",
                       def = "{'tenantId': 1, 'userId': 1, 'status': 1}"),
        @CompoundIndex(name = "expires_idx", def = "{'expiresAt': 1}")
})
public class TeamInvitation {

    /** Default invitation expiry duration (7 days) */
    public static final Duration DEFAULT_EXPIRY = Duration.ofDays(7);

    /** Maximum number of resends allowed */
    public static final int MAX_RESEND_COUNT = 3;

    @Id
    private String id;

    private String tenantId;
    private String teamId;

    /**
     * Denormalized team name for display in invitation emails/UI.
     */
    private String teamName;

    // Invitee information
    private String email;

    /**
     * User ID if this is an internal user (looked up from Zitadel).
     * Null for external users until they create an account.
     */
    private String userId;

    /**
     * Type of invitee.
     */
    private InviteeType inviteeType;

    // Invitation details
    /**
     * Role to assign when invitation is accepted.
     * For external users, this is always EXTERNAL regardless of what's set here.
     */
    private String roleId;

    /**
     * Denormalized role name for display.
     */
    private String roleName;

    /**
     * Personal message from the inviter.
     */
    private String message;

    // Inviter information
    private String invitedById;
    private String invitedByName;
    private String invitedByEmail;

    // Token for acceptance (used in magic link)
    /**
     * UUID token included in the acceptance URL.
     */
    @Indexed(unique = true)
    private String token;

    /**
     * Hashed token stored for security (bcrypt or SHA-256).
     * The plain token is only included in emails, never stored.
     */
    private String tokenHash;

    // Status
    private InvitationStatus status;

    // Timestamps
    private Instant createdAt;
    private Instant expiresAt;
    private Instant respondedAt;
    private Instant resentAt;

    /**
     * Number of times this invitation has been resent.
     */
    private Integer resendCount;

    /**
     * Type of invitee.
     */
    public enum InviteeType {
        /** Existing tenant user (has Zitadel account) */
        INTERNAL,
        /** External guest (will use portal, may not have Zitadel account) */
        EXTERNAL
    }

    /**
     * Invitation status.
     */
    public enum InvitationStatus {
        /** Awaiting response */
        PENDING,
        /** User accepted and joined */
        ACCEPTED,
        /** User declined */
        DECLINED,
        /** Expired before response */
        EXPIRED,
        /** Inviter cancelled */
        CANCELLED,
        /** Admin revoked */
        REVOKED
    }

    // Helper methods

    /**
     * Checks if this invitation is still pending.
     */
    public boolean isPending() {
        return status == InvitationStatus.PENDING;
    }

    /**
     * Checks if this invitation has expired.
     */
    public boolean isExpired() {
        return status == InvitationStatus.EXPIRED ||
               (status == InvitationStatus.PENDING && expiresAt != null && Instant.now().isAfter(expiresAt));
    }

    /**
     * Checks if this invitation can be resent.
     */
    public boolean canResend() {
        return status == InvitationStatus.PENDING &&
               (resendCount == null || resendCount < MAX_RESEND_COUNT);
    }

    /**
     * Checks if this is for an external user.
     */
    public boolean isExternal() {
        return inviteeType == InviteeType.EXTERNAL;
    }

    /**
     * Gets the effective role ID (enforces EXTERNAL role for external users).
     */
    public String getEffectiveRoleId() {
        if (inviteeType == InviteeType.EXTERNAL) {
            return TeamRole.ROLE_ID_EXTERNAL;
        }
        return roleId;
    }

    /**
     * Creates a builder with default values.
     */
    public static TeamInvitationBuilder withDefaults() {
        Instant now = Instant.now();
        return TeamInvitation.builder()
                .status(InvitationStatus.PENDING)
                .createdAt(now)
                .expiresAt(now.plus(DEFAULT_EXPIRY))
                .resendCount(0);
    }
}
