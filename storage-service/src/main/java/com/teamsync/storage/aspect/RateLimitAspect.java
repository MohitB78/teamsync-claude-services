package com.teamsync.storage.aspect;

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
 * SECURITY FIX (Round 13 #36): Rate limiting aspect for Storage Service endpoints.
 *
 * Prevents:
 * - DoS attacks on storage endpoints
 * - Storage quota exhaustion through rapid uploads
 * - Resource exhaustion from presigned URL generation
 * - Individual user abuse through per-user limits
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitAspect {

    private final RateLimiter uploadInitLimiter;
    private final RateLimiter presignedUrlLimiter;
    private final RateLimiter directUploadLimiter;
    private final RateLimiter downloadLimiter;
    private final StringRedisTemplate redisTemplate;

    /**
     * Per-user rate limits (requests per minute).
     */
    private static final int PER_USER_UPLOAD_LIMIT = 20;        // 20 uploads/min per user
    private static final int PER_USER_PRESIGN_LIMIT = 50;       // 50 presigned URLs/min per user
    private static final int PER_USER_DIRECT_UPLOAD_LIMIT = 10; // 10 direct uploads/min per user
    private static final int PER_USER_DOWNLOAD_LIMIT = 100;     // 100 downloads/min per user
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(1);

    /**
     * Pointcut for upload initialization operations.
     */
    @Pointcut("execution(* com.teamsync.storage.controller.StorageController.initializeUpload(..))")
    public void uploadInitOperations() {}

    /**
     * Pointcut for presigned URL generation.
     */
    @Pointcut("execution(* com.teamsync.storage.controller.StorageController.getDownloadUrl(..))")
    public void presignedUrlOperations() {}

    /**
     * Pointcut for direct upload operations.
     */
    @Pointcut("execution(* com.teamsync.storage.controller.StorageController.uploadDirect(..))")
    public void directUploadOperations() {}

    /**
     * Pointcut for download operations.
     */
    @Pointcut("execution(* com.teamsync.storage.controller.StorageController.downloadFile(..))")
    public void downloadOperations() {}

    @Around("uploadInitOperations()")
    public Object limitUploadInit(ProceedingJoinPoint joinPoint) throws Throwable {
        return executeWithRateLimiter(joinPoint, uploadInitLimiter, "upload-init", PER_USER_UPLOAD_LIMIT);
    }

    @Around("presignedUrlOperations()")
    public Object limitPresignedUrl(ProceedingJoinPoint joinPoint) throws Throwable {
        return executeWithRateLimiter(joinPoint, presignedUrlLimiter, "presigned-url", PER_USER_PRESIGN_LIMIT);
    }

    @Around("directUploadOperations()")
    public Object limitDirectUpload(ProceedingJoinPoint joinPoint) throws Throwable {
        return executeWithRateLimiter(joinPoint, directUploadLimiter, "direct-upload", PER_USER_DIRECT_UPLOAD_LIMIT);
    }

    @Around("downloadOperations()")
    public Object limitDownload(ProceedingJoinPoint joinPoint) throws Throwable {
        return executeWithRateLimiter(joinPoint, downloadLimiter, "download", PER_USER_DOWNLOAD_LIMIT);
    }

    /**
     * Execute method with rate limiting checks.
     * Checks both global in-memory limit and per-user Redis-based limit.
     */
    private Object executeWithRateLimiter(ProceedingJoinPoint joinPoint, RateLimiter limiter,
                                          String operationType, int perUserLimit) throws Throwable {
        // Check per-user rate limit first
        String userId = TenantContext.getUserId();
        String tenantId = TenantContext.getTenantId();

        if (userId != null && !userId.isBlank()) {
            if (!checkPerUserRateLimit(tenantId, userId, operationType, perUserLimit)) {
                log.warn("SECURITY: Per-user rate limit exceeded for {} operation by user {} in tenant {} - possible abuse",
                        operationType, userId, tenantId);
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
                            .error("Service is currently busy. Please try again later.")
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
     * This prevents attackers from exploiting Redis failures to bypass rate limits.
     */
    private boolean checkPerUserRateLimit(String tenantId, String userId, String operationType, int limit) {
        String key = "ratelimit:storage:" + operationType + ":" + tenantId + ":" + userId;

        try {
            redisTemplate.opsForValue().setIfAbsent(key, "0", RATE_LIMIT_WINDOW);
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
                log.warn("SECURITY: Redis rate limiting unavailable - failing closed to prevent bypass attacks. User: {}", userId);
                return false;  // Fail closed after consecutive failures
            }

            // Allow first few failures for transient issues, but log warning
            log.warn("SECURITY: Allowing request due to transient Redis failure (failure #{}/{}). User: {}",
                    consecutiveRedisFailures, MAX_CONSECUTIVE_FAILURES, userId);
            return true;  // Allow only during transient failures
        }
    }
}
