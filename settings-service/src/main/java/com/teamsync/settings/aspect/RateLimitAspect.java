package com.teamsync.settings.aspect;

import com.teamsync.settings.config.RateLimitConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Aspect for applying rate limiting to Settings controller endpoints.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitAspect {

    private final RateLimiter settingsReadLimiter;
    private final RateLimiter settingsWriteLimiter;
    private final RateLimiter tenantSettingsLimiter;

    @Pointcut("execution(* com.teamsync.settings.controller.SettingsController.get*(..))")
    public void readOperations() {}

    @Pointcut("execution(* com.teamsync.settings.controller.SettingsController.update*(..))")
    public void writeOperations() {}

    @Pointcut("execution(* com.teamsync.settings.controller.SettingsController.reset*(..))")
    public void resetOperations() {}

    @Pointcut("execution(* com.teamsync.settings.controller.SettingsController.*TenantSettings(..))")
    public void tenantOperations() {}

    @Pointcut("execution(* com.teamsync.settings.controller.SettingsController.pinFolder(..)) || " +
              "execution(* com.teamsync.settings.controller.SettingsController.unpinFolder(..)) || " +
              "execution(* com.teamsync.settings.controller.SettingsController.favoriteDocument(..)) || " +
              "execution(* com.teamsync.settings.controller.SettingsController.unfavoriteDocument(..))")
    public void driveModificationOperations() {}

    /**
     * Apply rate limiting to read operations.
     */
    @Around("readOperations() && !tenantOperations()")
    public Object limitReads(ProceedingJoinPoint joinPoint) throws Throwable {
        return executeWithRateLimiter(joinPoint, settingsReadLimiter, "read");
    }

    /**
     * Apply rate limiting to write operations (updates).
     */
    @Around("(writeOperations() || driveModificationOperations()) && !tenantOperations()")
    public Object limitWrites(ProceedingJoinPoint joinPoint) throws Throwable {
        return executeWithRateLimiter(joinPoint, settingsWriteLimiter, "write");
    }

    /**
     * Apply rate limiting to reset operations.
     */
    @Around("resetOperations() && !tenantOperations()")
    public Object limitResets(ProceedingJoinPoint joinPoint) throws Throwable {
        return executeWithRateLimiter(joinPoint, settingsWriteLimiter, "reset");
    }

    /**
     * Apply stricter rate limiting to tenant settings operations.
     */
    @Around("tenantOperations()")
    public Object limitTenantOperations(ProceedingJoinPoint joinPoint) throws Throwable {
        return executeWithRateLimiter(joinPoint, tenantSettingsLimiter, "tenant");
    }

    private Object executeWithRateLimiter(ProceedingJoinPoint joinPoint, RateLimiter limiter, String operationType) throws Throwable {
        try {
            RateLimiter.waitForPermission(limiter);
            return joinPoint.proceed();
        } catch (RequestNotPermitted e) {
            log.warn("Rate limit exceeded for {} operation: {}", operationType, joinPoint.getSignature().getName());
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of(
                            "success", false,
                            "error", "Rate limit exceeded. Please try again later.",
                            "code", "RATE_LIMIT_EXCEEDED",
                            "retryAfterSeconds", limiter.getRateLimiterConfig().getLimitRefreshPeriod().getSeconds()
                    ));
        }
    }
}
