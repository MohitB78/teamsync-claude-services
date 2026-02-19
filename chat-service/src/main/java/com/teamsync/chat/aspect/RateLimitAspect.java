package com.teamsync.chat.aspect;

import com.teamsync.common.context.TenantContext;
import com.teamsync.common.dto.ApiResponse;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * SECURITY FIX (Round 12): Rate limiting aspect for Chat Service endpoints.
 *
 * Prevents:
 * - Message spam/flooding attacks
 * - AI resource exhaustion (DocuTalk abuse)
 * - Comment spam on documents
 * - DoS attacks via excessive message retrieval
 *
 * Uses dual-layer rate limiting:
 * 1. Global in-memory rate limiter (Resilience4j) - protects the service
 * 2. Per-user Redis-based rate limiter - prevents individual abuse
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitAspect {

    private final RateLimiter messageSendLimiter;
    private final RateLimiter commentLimiter;
    private final RateLimiter aiChatLimiter;
    private final RateLimiter messageReadLimiter;
    private final StringRedisTemplate redisTemplate;

    /**
     * Per-user rate limits (requests per minute).
     * These are stricter than global limits to prevent individual abuse.
     */
    private static final int PER_USER_MESSAGE_SEND_LIMIT = 30;    // 30 messages per minute
    private static final int PER_USER_COMMENT_LIMIT = 20;          // 20 comments per minute
    private static final int PER_USER_AI_CHAT_LIMIT = 10;          // 10 AI queries per minute (expensive!)
    private static final int PER_USER_MESSAGE_READ_LIMIT = 120;    // 120 reads per minute
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(1);

    /**
     * Pointcut for message sending operations.
     */
    @Pointcut("execution(* com.teamsync.chat.controller.ChatController.sendMessage(..))")
    public void messageSendOperations() {}

    /**
     * Pointcut for comment operations.
     */
    @Pointcut("execution(* com.teamsync.chat.controller.ChatController.addComment(..))")
    public void commentOperations() {}

    /**
     * Pointcut for AI chat operations (DocuTalk).
     */
    @Pointcut("execution(* com.teamsync.chat.controller.ChatController.askAI(..)) || " +
              "execution(* com.teamsync.chat.controller.ChatController.chat(..))")
    public void aiChatOperations() {}

    /**
     * Pointcut for message read operations.
     */
    @Pointcut("execution(* com.teamsync.chat.controller.ChatController.getMessages(..)) || " +
              "execution(* com.teamsync.chat.controller.ChatController.getComments(..))")
    public void messageReadOperations() {}

    @Around("messageSendOperations()")
    public Object limitMessageSending(ProceedingJoinPoint joinPoint) throws Throwable {
        return executeWithRateLimiter(joinPoint, messageSendLimiter, "message-send",
                PER_USER_MESSAGE_SEND_LIMIT);
    }

    @Around("commentOperations()")
    public Object limitComments(ProceedingJoinPoint joinPoint) throws Throwable {
        return executeWithRateLimiter(joinPoint, commentLimiter, "comment",
                PER_USER_COMMENT_LIMIT);
    }

    @Around("aiChatOperations()")
    public Object limitAiChat(ProceedingJoinPoint joinPoint) throws Throwable {
        return executeWithRateLimiter(joinPoint, aiChatLimiter, "ai-chat",
                PER_USER_AI_CHAT_LIMIT);
    }

    @Around("messageReadOperations()")
    public Object limitMessageReading(ProceedingJoinPoint joinPoint) throws Throwable {
        return executeWithRateLimiter(joinPoint, messageReadLimiter, "message-read",
                PER_USER_MESSAGE_READ_LIMIT);
    }

    /**
     * Execute with dual-layer rate limiting.
     */
    private Object executeWithRateLimiter(ProceedingJoinPoint joinPoint, RateLimiter limiter,
                                          String operationType, int perUserLimit) throws Throwable {
        // Check per-user rate limit first
        String userId = TenantContext.getUserId();
        if (userId != null && !userId.isBlank()) {
            if (!checkPerUserRateLimit(userId, operationType, perUserLimit)) {
                log.warn("SECURITY: Per-user rate limit exceeded for {} operation by user {} - possible abuse",
                        operationType, userId);
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .header("Retry-After", "60")
                        .body(ApiResponse.builder()
                                .success(false)
                                .error("Rate limit exceeded. Please try again later.")
                                .code("RATE_LIMIT_EXCEEDED")
                                .build());
            }
        }

        // Check global rate limit
        try {
            RateLimiter.waitForPermission(limiter);
            return joinPoint.proceed();
        } catch (RequestNotPermitted e) {
            log.warn("SECURITY: Global rate limit exceeded for {} operation: {} - possible DoS attack",
                    operationType, joinPoint.getSignature().getName());

            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", "1")
                    .body(ApiResponse.builder()
                            .success(false)
                            .error("Service is busy. Please try again later.")
                            .code("RATE_LIMIT_EXCEEDED")
                            .build());
        }
    }

    /**
     * Consecutive Redis failure counter for fail-closed behavior.
     * After MAX_CONSECUTIVE_FAILURES, we fail closed to prevent abuse during Redis outages.
     */
    private static final int MAX_CONSECUTIVE_FAILURES = 3;
    private volatile int consecutiveRedisFailures = 0;

    /**
     * Check per-user rate limit using Redis.
     * Uses atomic INCR with conditional EXPIRE to prevent race conditions.
     *
     * SECURITY FIX (Round 14 #H39): Changed from fail-open to fail-closed after
     * consecutive failures to prevent rate limit bypass during Redis outages.
     * This is especially important for AI chat operations which are expensive.
     */
    private boolean checkPerUserRateLimit(String userId, String operationType, int limit) {
        String key = "ratelimit:chat:" + operationType + ":" + userId;

        try {
            // Atomic increment with conditional TTL
            Boolean isNew = redisTemplate.opsForValue().setIfAbsent(key, "0", RATE_LIMIT_WINDOW);
            Long count = redisTemplate.opsForValue().increment(key);

            // Reset failure counter on successful Redis operation
            consecutiveRedisFailures = 0;

            if (count == null) {
                log.warn("SECURITY: Redis rate limit check returned null for user {} - denying request", userId);
                return false;  // Fail closed for security
            }

            return count <= limit;
        } catch (Exception e) {
            consecutiveRedisFailures++;
            log.error("SECURITY: Redis rate limit check failed for user {} (failure #{}/{}): {}",
                    userId, consecutiveRedisFailures, MAX_CONSECUTIVE_FAILURES, e.getMessage());

            // SECURITY FIX: After consecutive failures, fail closed to prevent abuse
            if (consecutiveRedisFailures >= MAX_CONSECUTIVE_FAILURES) {
                log.warn("SECURITY: Redis rate limiting unavailable - failing closed to prevent AI abuse. User: {}", userId);
                return false;  // Fail closed after consecutive failures
            }

            // Allow first few failures for transient issues, but log warning
            log.warn("SECURITY: Allowing request due to transient Redis failure (failure #{}/{}). User: {}",
                    consecutiveRedisFailures, MAX_CONSECUTIVE_FAILURES, userId);
            return true;  // Allow only during transient failures
        }
    }
}
