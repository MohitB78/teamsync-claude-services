package com.teamsync.gateway.controller;

import com.teamsync.gateway.config.BffProperties;
import com.teamsync.gateway.dto.MobileRefreshRequest;
import com.teamsync.gateway.dto.MobileRefreshResponse;
import com.teamsync.gateway.dto.TokenResponse;
import com.teamsync.gateway.service.ZitadelAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * Mobile Authentication Controller.
 *
 * <p>Provides authentication endpoints specifically for mobile apps.
 * Unlike the BFF pattern used for web apps (which stores tokens server-side
 * in Redis), mobile apps need tokens returned directly to store in the
 * device's secure storage (Keychain on iOS, Keystore on Android).
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /bff/auth/mobile/refresh - Refresh tokens, returns new tokens in response</li>
 * </ul>
 *
 * @see ZitadelAuthService
 */
@RestController
@RequestMapping("/bff/auth/mobile")
@ConditionalOnProperty(name = "teamsync.bff.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class MobileAuthController {

    private final ZitadelAuthService zitadelAuthService;
    private final BffProperties bffProperties;

    /**
     * Refresh tokens for mobile apps.
     *
     * <p>Unlike the web BFF refresh endpoint which stores tokens server-side,
     * this endpoint returns new tokens directly for mobile apps to store
     * in secure device storage.
     *
     * @param request Contains the refresh token
     * @return New access and refresh tokens
     */
    @PostMapping("/refresh")
    public Mono<ResponseEntity<MobileRefreshResponse>> refresh(
            @Valid @RequestBody MobileRefreshRequest request) {

        log.debug("Mobile token refresh request received");

        if (request.getRefreshToken() == null || request.getRefreshToken().isBlank()) {
            log.warn("Mobile refresh called without refresh token");
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(MobileRefreshResponse.failure("Refresh token is required", "MISSING_TOKEN")));
        }

        return zitadelAuthService.refresh(request.getRefreshToken())
            .map(tokenResponse -> {
                log.info("Mobile token refresh successful");

                return ResponseEntity.ok(MobileRefreshResponse.success(
                    tokenResponse.getAccessToken(),
                    tokenResponse.getRefreshToken(),
                    tokenResponse.getExpiresIn()
                ));
            })
            .onErrorResume(ZitadelAuthService.AuthenticationException.class, e -> {
                log.warn("Mobile token refresh failed: {} ({})", e.getMessage(), e.getErrorCode());
                return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(MobileRefreshResponse.failure(e.getMessage(), e.getErrorCode())));
            })
            .onErrorResume(e -> {
                log.error("Unexpected error during mobile token refresh: {}", e.getMessage(), e);
                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(MobileRefreshResponse.failure("Token refresh failed", "INTERNAL_ERROR")));
            });
    }
}
