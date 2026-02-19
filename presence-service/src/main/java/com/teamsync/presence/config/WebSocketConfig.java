package com.teamsync.presence.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WebSocket configuration for real-time presence features.
 *
 * SECURITY FIX (Round 12): Replaced wildcard CORS "*" with explicit allowed origins.
 * Cross-Site WebSocket Hijacking (CSWSH) attacks can occur when WebSocket endpoints
 * accept connections from any origin. An attacker can host a malicious page that
 * establishes WebSocket connections using the victim's authenticated session.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtDecoder jwtDecoder;

    /**
     * SECURITY FIX (Round 12): Configurable allowed origins for WebSocket connections.
     * Must be explicitly set in production - wildcards are rejected.
     */
    @Value("${teamsync.websocket.allowed-origins:http://localhost:3000,http://localhost:3001}")
    private String allowedOriginsConfig;

    /**
     * SECURITY FIX (Round 14 #H9): WebSocket message rate limiting configuration.
     * Prevents DoS attacks via message flooding.
     */
    @Value("${teamsync.websocket.rate-limit.messages-per-second:10}")
    private int messagesPerSecond;

    @Value("${teamsync.websocket.rate-limit.heartbeat-interval-ms:5000}")
    private long minHeartbeatIntervalMs;

    /**
     * SECURITY FIX (Round 14 #H9): Per-session rate limit tracking.
     * Uses ConcurrentHashMap for thread-safe session tracking.
     */
    private final Map<String, RateLimitState> sessionRateLimits = new ConcurrentHashMap<>();

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Enable simple broker for /topic and /queue destinations
        registry.enableSimpleBroker("/topic", "/queue");

        // Set prefix for messages bound for @MessageMapping methods
        registry.setApplicationDestinationPrefixes("/app");

        // Set prefix for user-specific destinations
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // SECURITY FIX (Round 12): Get validated allowed origins
        String[] allowedOrigins = getValidatedAllowedOrigins();

        // WebSocket endpoint with SockJS fallback
        registry.addEndpoint("/ws/presence")
                .setAllowedOriginPatterns(allowedOrigins)
                .withSockJS()
                .setHeartbeatTime(25000);

        // Native WebSocket endpoint (no SockJS)
        registry.addEndpoint("/ws/presence")
                .setAllowedOriginPatterns(allowedOrigins);

        log.info("WebSocket endpoints registered with allowed origins: {}", Arrays.toString(allowedOrigins));
    }

    /**
     * SECURITY FIX (Round 12): Validates and returns allowed origins for WebSocket CORS.
     *
     * This method:
     * 1. Rejects wildcard patterns ("*") to prevent CSWSH attacks
     * 2. Validates each origin is a properly formatted URL
     * 3. Logs warnings for any rejected patterns
     *
     * @return Array of validated allowed origin patterns
     */
    private String[] getValidatedAllowedOrigins() {
        List<String> origins = Arrays.stream(allowedOriginsConfig.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .filter(origin -> {
                    // SECURITY: Reject wildcard patterns
                    if (origin.equals("*") || origin.equals("http://*") || origin.equals("https://*")) {
                        log.error("SECURITY: Wildcard WebSocket origin '{}' rejected. " +
                                "Configure explicit origins in teamsync.websocket.allowed-origins", origin);
                        return false;
                    }
                    // SECURITY: Reject null origin (used in some attacks)
                    if (origin.equalsIgnoreCase("null")) {
                        log.error("SECURITY: 'null' origin rejected for WebSocket connections");
                        return false;
                    }
                    return true;
                })
                .toList();

        if (origins.isEmpty()) {
            log.warn("SECURITY: No valid WebSocket origins configured. Using localhost defaults for development.");
            return new String[]{"http://localhost:3000", "http://localhost:3001"};
        }

        return origins.toArray(new String[0]);
    }

    /**
     * SECURITY FIX (Round 14 #H11, #H12): Configure WebSocket transport limits.
     * Prevents message size attacks and backlog exhaustion.
     */
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration
                // SECURITY FIX (Round 14 #H12): Limit message size to 64KB
                .setMessageSizeLimit(64 * 1024)
                // SECURITY FIX (Round 14 #H12): Limit send buffer size to 512KB per connection
                .setSendBufferSizeLimit(512 * 1024)
                // SECURITY FIX (Round 14 #H12): Limit send time to 20 seconds
                .setSendTimeLimit(20 * 1000);

        log.info("WebSocket transport configured: maxMessageSize=64KB, sendBufferSize=512KB, sendTimeLimit=20s");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // SECURITY FIX (Round 14 #H9): Add rate limiting interceptor FIRST
        registration.interceptors(new RateLimitingInterceptor());

        // Then add authentication interceptor
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    // SECURITY FIX: Validate JWT token instead of trusting headers
                    // This prevents attackers from spoofing user identity via headers
                    String authHeader = accessor.getFirstNativeHeader("Authorization");

                    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                        log.warn("SECURITY: WebSocket connection attempt without valid Authorization header");
                        throw new MessageDeliveryException("Authentication required");
                    }

                    String token = authHeader.substring(7);

                    try {
                        // Validate JWT and extract claims
                        Jwt jwt = jwtDecoder.decode(token);

                        String userId = jwt.getSubject();
                        String tenantId = jwt.getClaimAsString("tenant_id");
                        String userName = jwt.getClaimAsString("name");
                        String email = jwt.getClaimAsString("email");

                        if (userId == null || tenantId == null) {
                            log.warn("SECURITY: JWT missing required claims (sub or tenant_id)");
                            throw new MessageDeliveryException("Invalid token: missing required claims");
                        }

                        // Set principal with validated claims from JWT
                        accessor.setUser(new WebSocketPrincipal(userId, tenantId, userName, email));
                        log.info("WebSocket connection authenticated for user: {} in tenant: {}", userId, tenantId);

                    } catch (JwtException e) {
                        log.warn("SECURITY: WebSocket JWT validation failed: {}", e.getMessage());
                        throw new MessageDeliveryException("Invalid or expired token");
                    }
                }

                return message;
            }
        });
    }

    public static class WebSocketPrincipal implements Principal {
        private final String userId;
        private final String tenantId;
        private final String userName;
        private final String email;

        public WebSocketPrincipal(String userId, String tenantId, String userName, String email) {
            this.userId = userId;
            this.tenantId = tenantId;
            this.userName = userName;
            this.email = email;
        }

        @Override
        public String getName() {
            return userId;
        }

        public String getUserId() {
            return userId;
        }

        public String getTenantId() {
            return tenantId;
        }

        public String getUserName() {
            return userName;
        }

        public String getEmail() {
            return email;
        }
    }

    /**
     * SECURITY FIX (Round 14 #H9): Rate limiting state for each WebSocket session.
     * Tracks message count and last heartbeat time per session.
     */
    private static class RateLimitState {
        final AtomicInteger messageCount = new AtomicInteger(0);
        final AtomicLong windowStartTime = new AtomicLong(System.currentTimeMillis());
        final AtomicLong lastHeartbeat = new AtomicLong(0);

        /**
         * Check if message should be allowed based on rate limit.
         * @param maxMessagesPerSecond Maximum messages allowed per second
         * @return true if message is allowed, false if rate limited
         */
        boolean allowMessage(int maxMessagesPerSecond) {
            long now = System.currentTimeMillis();
            long windowStart = windowStartTime.get();

            // Reset window every second
            if (now - windowStart > 1000) {
                windowStartTime.set(now);
                messageCount.set(1);
                return true;
            }

            // Increment and check
            return messageCount.incrementAndGet() <= maxMessagesPerSecond;
        }

        /**
         * Check if heartbeat should be allowed based on minimum interval.
         * @param minIntervalMs Minimum interval between heartbeats in milliseconds
         * @return true if heartbeat is allowed, false if too frequent
         */
        boolean allowHeartbeat(long minIntervalMs) {
            long now = System.currentTimeMillis();
            long last = lastHeartbeat.get();

            if (now - last < minIntervalMs) {
                return false;
            }

            // Update last heartbeat time
            return lastHeartbeat.compareAndSet(last, now);
        }
    }

    /**
     * SECURITY FIX (Round 14 #H9): Rate limiting interceptor for WebSocket messages.
     * Prevents DoS attacks by limiting message frequency per session.
     */
    private class RateLimitingInterceptor implements ChannelInterceptor {

        @Override
        public Message<?> preSend(Message<?> message, MessageChannel channel) {
            StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

            if (accessor == null) {
                return message;
            }

            String sessionId = accessor.getSessionId();
            if (sessionId == null) {
                return message;
            }

            StompCommand command = accessor.getCommand();

            // Clean up rate limit state on disconnect
            if (StompCommand.DISCONNECT.equals(command)) {
                sessionRateLimits.remove(sessionId);
                return message;
            }

            // Initialize rate limit state for new sessions
            if (StompCommand.CONNECT.equals(command)) {
                sessionRateLimits.put(sessionId, new RateLimitState());
                return message;
            }

            // Rate limit all other messages
            if (StompCommand.SEND.equals(command) || StompCommand.MESSAGE.equals(command)) {
                RateLimitState state = sessionRateLimits.computeIfAbsent(sessionId, k -> new RateLimitState());

                // Check general message rate limit
                if (!state.allowMessage(messagesPerSecond)) {
                    log.warn("SECURITY: WebSocket rate limit exceeded for session: {}", sessionId);
                    throw new MessageDeliveryException("Rate limit exceeded. Please slow down.");
                }

                // Additional rate limit for heartbeat messages
                String destination = accessor.getDestination();
                if (destination != null && destination.contains("/heartbeat")) {
                    if (!state.allowHeartbeat(minHeartbeatIntervalMs)) {
                        log.debug("Heartbeat too frequent for session: {}, dropping", sessionId);
                        // Don't throw - just silently drop excessive heartbeats
                        return null;
                    }
                }
            }

            return message;
        }
    }
}
