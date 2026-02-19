package com.teamsync.presence.config;

import com.teamsync.common.permission.PermissionService;
import com.teamsync.common.model.Permission;
import com.teamsync.presence.service.DocumentPresenceService;
import com.teamsync.presence.service.UserPresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.security.Principal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * WebSocket event listener for presence management.
 *
 * SECURITY FIX (Round 12): Added authorization validation for document subscriptions.
 * Previously, any authenticated user could subscribe to any document's presence topic,
 * enabling information disclosure about who is viewing documents they shouldn't access.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final UserPresenceService userPresenceService;
    private final DocumentPresenceService documentPresenceService;
    private final PermissionService permissionService;

    // Track which documents each session is subscribed to
    private final Map<String, SessionInfo> sessionInfoMap = new ConcurrentHashMap<>();

    /**
     * SECURITY FIX (Round 12): Valid document/tenant ID pattern.
     * IDs must be alphanumeric with hyphens only (MongoDB ObjectIds or UUIDs).
     */
    private static final Pattern VALID_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9-]{1,64}$");

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = accessor.getUser();

        if (principal instanceof WebSocketConfig.WebSocketPrincipal wsPrincipal) {
            String sessionId = accessor.getSessionId();

            sessionInfoMap.put(sessionId, new SessionInfo(
                    wsPrincipal.getUserId(),
                    wsPrincipal.getTenantId(),
                    wsPrincipal.getUserName()
            ));

            log.info("WebSocket connected: sessionId={}, userId={}, tenantId={}",
                    sessionId, wsPrincipal.getUserId(), wsPrincipal.getTenantId());
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();

        SessionInfo sessionInfo = sessionInfoMap.remove(sessionId);

        if (sessionInfo != null) {
            log.info("WebSocket disconnected: sessionId={}, userId={}, tenantId={}",
                    sessionId, sessionInfo.userId(), sessionInfo.tenantId());

            // Clean up document subscriptions
            for (String documentId : sessionInfo.subscribedDocuments()) {
                try {
                    documentPresenceService.leaveDocument(
                            sessionInfo.tenantId(),
                            documentId,
                            sessionInfo.userId()
                    );
                } catch (Exception e) {
                    log.warn("Error leaving document on disconnect: {}", e.getMessage());
                }
            }

            // Mark user as offline if this was their only connection
            // In a multi-instance setup, we would check Redis for other sessions
            try {
                userPresenceService.setUserOffline(sessionInfo.tenantId(), sessionInfo.userId());
            } catch (Exception e) {
                log.warn("Error setting user offline on disconnect: {}", e.getMessage());
            }
        }
    }

    /**
     * SECURITY FIX (Round 12): Added authorization validation for document subscriptions.
     * Previously, any authenticated user could subscribe to any document's presence topic,
     * enabling information disclosure about who is viewing documents they shouldn't access.
     */
    @EventListener
    public void handleSubscribeEvent(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination = accessor.getDestination();
        String sessionId = accessor.getSessionId();

        if (destination != null && destination.startsWith("/topic/presence/document/")) {
            // Extract tenant and document ID from destination: /topic/presence/document/{tenantId}/{documentId}
            String[] parts = destination.split("/");
            if (parts.length >= 6) {
                String topicTenantId = parts[4];
                String documentId = parts[5];
                SessionInfo sessionInfo = sessionInfoMap.get(sessionId);

                if (sessionInfo == null) {
                    log.warn("SECURITY: Subscription attempt without session info for destination: {}", destination);
                    throw new MessageDeliveryException("Not authenticated");
                }

                // SECURITY FIX (Round 12): Validate tenant ID format to prevent injection
                if (!VALID_ID_PATTERN.matcher(topicTenantId).matches() ||
                    !VALID_ID_PATTERN.matcher(documentId).matches()) {
                    log.warn("SECURITY: Invalid ID format in subscription - tenantId: {}, documentId: {}",
                            topicTenantId.length() > 64 ? "TOO_LONG" : topicTenantId,
                            documentId.length() > 64 ? "TOO_LONG" : documentId);
                    throw new MessageDeliveryException("Invalid subscription destination");
                }

                // SECURITY FIX (Round 12): Verify tenant ID matches user's tenant
                if (!topicTenantId.equals(sessionInfo.tenantId())) {
                    log.warn("SECURITY: Cross-tenant subscription attempt - user tenant: {}, topic tenant: {}",
                            sessionInfo.tenantId(), topicTenantId);
                    throw new MessageDeliveryException("Access denied: cross-tenant subscription not allowed");
                }

                // SECURITY FIX (Round 12): Verify user has READ permission on the document
                try {
                    boolean hasAccess = permissionService.hasPermission(
                            sessionInfo.tenantId(),
                            sessionInfo.userId(),
                            documentId,
                            Permission.READ
                    );

                    if (!hasAccess) {
                        log.warn("SECURITY: Unauthorized document subscription - user: {}, document: {}",
                                sessionInfo.userId(), documentId);
                        throw new MessageDeliveryException("Access denied: insufficient permissions");
                    }
                } catch (MessageDeliveryException e) {
                    throw e; // Re-throw our security exceptions
                } catch (Exception e) {
                    log.error("Error checking document permissions: {}", e.getMessage());
                    throw new MessageDeliveryException("Unable to verify permissions");
                }

                sessionInfo.subscribedDocuments().add(documentId);
                log.debug("User {} subscribed to document {} (authorized)", sessionInfo.userId(), documentId);
            }
        }
    }

    @EventListener
    public void handleUnsubscribeEvent(SessionUnsubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();

        // Note: destination is not available in unsubscribe events
        // We track subscriptions via subscription IDs if needed
        log.debug("Unsubscribe event for session: {}", sessionId);
    }

    private static class SessionInfo {
        private final String userId;
        private final String tenantId;
        private final String userName;
        private final java.util.Set<String> subscribedDocuments = ConcurrentHashMap.newKeySet();

        public SessionInfo(String userId, String tenantId, String userName) {
            this.userId = userId;
            this.tenantId = tenantId;
            this.userName = userName;
        }

        public String userId() {
            return userId;
        }

        public String tenantId() {
            return tenantId;
        }

        public String userName() {
            return userName;
        }

        public java.util.Set<String> subscribedDocuments() {
            return subscribedDocuments;
        }
    }
}
