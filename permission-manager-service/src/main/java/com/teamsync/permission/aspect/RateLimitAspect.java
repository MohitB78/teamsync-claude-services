package com.teamsync.permission.aspect;

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
 * SECURITY: Rate limiting aspect for Permission Manager controller endpoints.
 *
 * Prevents:
 * - Permission enumeration attacks
 * - DoS attacks on permission check endpoints
 * - Brute-force access pattern discovery
 *
 * SECURITY FIX (Round 10 #20): Added per-user Redis-based rate limiting in addition
 * to the global in-memory rate limiter. This prevents individual users from abusing
 * the permission check endpoint for enumeration attacks, even if the global limit
 * is not reached (e.g., in a distributed deployment).
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitAspect {

    private final RateLimiter permissionCheckLimiter;
    private final RateLimiter roleAssignmentLimiter;
    private final RateLimiter driveCreationLimiter;
    private final StringRedisTemplate redisTemplate;

    /**
     * SECURITY FIX (Round 10 #20): Per-user rate limits (requests per minute).
     * These are stricter than the global limits to prevent individual abuse.
     */
    private static final int PER_USER_PERMISSION_CHECK_LIMIT = 60;  // 1/sec average
    private static final int PER_USER_ROLE_ASSIGNMENT_LIMIT = 10;
    private static final int PER_USER_DRIVE_CREATION_LIMIT = 5;
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(1);

    /**
     * Pointcut for permission check operations.
     */
    @Pointcut("execution(* com.teamsync.permission.controller.PermissionController.checkPermission(..)) || " +
              "execution(* com.teamsync.permission.controller.PermissionController.quickCheck(..)) || " +
              "execution(* com.teamsync.permission.controller.PermissionController.hasAccess(..))")
    public void permissionCheckOperations() {}

    /**
     * Pointcut for role assignment operations.
     */
    @Pointcut("execution(* com.teamsync.permission.controller.PermissionController.assignRole(..)) || " +
              "execution(* com.teamsync.permission.controller.PermissionController.revokeAccess(..)) || " +
              "execution(* com.teamsync.permission.controller.PermissionController.assignDepartmentMembers(..))")
    public void roleAssignmentOperations() {}

    /**
     * Pointcut for drive creation operations.
     */
    @Pointcut("execution(* com.teamsync.permission.controller.PermissionController.createDrive(..))")
    public void driveCreationOperations() {}

    /**
     * Apply rate limiting to permission check operations.
     */
    @Around("permissionCheckOperations()")
    public Object limitPermissionChecks(ProceedingJoinPoint joinPoint) throws Throwable {
        return executeWithRateLimiter(joinPoint, permissionCheckLimiter, "permission-check",
                PER_USER_PERMISSION_CHECK_LIMIT);
    }

    /**
     * Apply rate limiting to role assignment operations.
     */
    @Around("roleAssignmentOperations()")
    public Object limitRoleAssignments(ProceedingJoinPoint joinPoint) throws Throwable {
        return executeWithRateLimiter(joinPoint, roleAssignmentLimiter, "role-assignment",
                PER_USER_ROLE_ASSIGNMENT_LIMIT);
    }

    /**
     * Apply rate limiting to drive creation operations.
     */
    @Around("driveCreationOperations()")
    public Object limitDriveCreation(ProceedingJoinPoint joinPoint) throws Throwable {
        return executeWithRateLimiter(joinPoint, driveCreationLimiter, "drive-creation",
                PER_USER_DRIVE_CREATION_LIMIT);
    }

    /**
     * SECURITY FIX (Round 10 #20): Enhanced rate limiting with per-user Redis tracking.
     * Checks both global in-memory limit and per-user Redis-based limit.
     */
    private Object executeWithRateLimiter(ProceedingJoinPoint joinPoint, RateLimiter limiter,
                                          String operationType, int perUserLimit) throws Throwable {
        // Check per-user rate limit first (SECURITY FIX Round 10 #20)
        String userId = TenantContext.getUserId();
        if (userId != null && !userId.isBlank()) {
            if (!checkPerUserRateLimit(userId, operationType, perUserLimit)) {
                log.warn("SECURITY: Per-user rate limit exceeded for {} operation by user {} - possible enumeration attack",
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
                            .error("Rate limit exceeded. Please try again later.")
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
     * SECURITY FIX (Round 10 #20, Round 14 #H39): Check per-user rate limit using Redis.
     * Uses atomic INCR with conditional EXPIRE to prevent race conditions.
     *
     * Changed from fail-open to fail-closed after consecutive failures to prevent
     * rate limit bypass during Redis outages. This is critical for permission checks
     * to prevent enumeration attacks.
     *
     * @param userId The user ID
     * @param operationType The type of operation being limited
     * @param limit Maximum requests per window
     * @return true if request is allowed, false if rate limited
     */
    private boolean checkPerUserRateLimit(String userId, String operationType, int limit) {
        String key = "ratelimit:permission:" + operationType + ":" + userId;

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

            // SECURITY FIX: After consecutive failures, fail closed to prevent enumeration attacks
            if (consecutiveRedisFailures >= MAX_CONSECUTIVE_FAILURES) {
                log.warn("SECURITY: Redis rate limiting unavailable - failing closed to prevent enumeration. User: {}", userId);
                return false;  // Fail closed after consecutive failures
            }

            // Allow first few failures for transient issues, but log warning
            log.warn("SECURITY: Allowing request due to transient Redis failure (failure #{}/{}). User: {}",
                    consecutiveRedisFailures, MAX_CONSECUTIVE_FAILURES, userId);
            return true;  // Allow only during transient failures
        }
    }
}
