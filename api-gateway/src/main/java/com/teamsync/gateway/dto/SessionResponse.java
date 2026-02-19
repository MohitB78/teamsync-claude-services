package com.teamsync.gateway.dto;

import com.teamsync.gateway.model.BffSession;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Session information response DTO.
 *
 * <p>Returned by GET /bff/auth/session to check authentication status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionResponse {

    /**
     * Whether user is authenticated.
     */
    private boolean authenticated;

    /**
     * User information (if authenticated).
     */
    private UserInfo user;

    /**
     * When the session expires.
     */
    private Instant expiresAt;

    /**
     * When the session was created.
     */
    private Instant createdAt;

    /**
     * Create an authenticated session response.
     */
    public static SessionResponse authenticated(BffSession session) {
        return SessionResponse.builder()
            .authenticated(true)
            .user(UserInfo.fromSession(session))
            .expiresAt(session.getAccessTokenExpiresAt())
            .createdAt(session.getCreatedAt())
            .build();
    }

    /**
     * Create an unauthenticated session response.
     */
    public static SessionResponse unauthenticated() {
        return SessionResponse.builder()
            .authenticated(false)
            .build();
    }
}
