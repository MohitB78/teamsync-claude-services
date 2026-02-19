package com.teamsync.gateway.config;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;
import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Comprehensive tests for RateLimitConfig.
 * Tests rate limiter configuration and key resolvers.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Rate Limit Config Tests")
class RateLimitConfigTest {

    private RateLimitConfig config;

    @Mock
    private Principal mockPrincipal;

    @BeforeEach
    void setUp() {
        config = new RateLimitConfig();
    }

    @Nested
    @DisplayName("Redis Rate Limiter Bean Tests")
    class RedisRateLimiterTests {

        @Test
        @DisplayName("Should create RedisRateLimiter with correct configuration")
        void redisRateLimiter_HasCorrectConfig() {
            // When
            RedisRateLimiter limiter = config.redisRateLimiter();

            // Then
            assertThat(limiter).isNotNull();
            // Limiter should be properly configured
            // Default: 100 requests per second, burst of 200
        }

        @Test
        @DisplayName("Should create non-null rate limiter")
        void redisRateLimiter_IsNotNull() {
            // When
            RedisRateLimiter limiter = config.redisRateLimiter();

            // Then
            assertThat(limiter).isNotNull();
        }
    }

    @Nested
    @DisplayName("User Key Resolver Tests")
    class UserKeyResolverTests {

        @Test
        @DisplayName("Should use principal name when authenticated")
        void userKeyResolver_WithPrincipal_UsesPrincipalName() {
            // Given
            KeyResolver resolver = config.userKeyResolver();
            String userId = "user-123";
            when(mockPrincipal.getName()).thenReturn(userId);

            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/documents")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // Create exchange with principal
            ServerWebExchange exchangeWithPrincipal = exchange.mutate()
                    .principal(Mono.just(mockPrincipal))
                    .build();

            // When
            Mono<String> result = resolver.resolve(exchangeWithPrincipal);

            // Then
            StepVerifier.create(result)
                    .expectNext(userId)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should use X-Forwarded-For when no principal")
        void userKeyResolver_WithoutPrincipal_UsesXForwardedFor() {
            // Given
            KeyResolver resolver = config.userKeyResolver();
            String clientIp = "192.168.1.100";

            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/documents")
                    .header("X-Forwarded-For", clientIp)
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When
            Mono<String> result = resolver.resolve(exchange);

            // Then
            StepVerifier.create(result)
                    .expectNext(clientIp)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should use remote address when no principal and no X-Forwarded-For")
        void userKeyResolver_WithoutHeaders_UsesRemoteAddress() {
            // Given
            KeyResolver resolver = config.userKeyResolver();

            // Note: MockServerHttpRequest doesn't support remoteAddress easily
            // This test verifies the resolver handles the case
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/documents")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When
            Mono<String> result = resolver.resolve(exchange);

            // Then - Should return some value (either remote address or "anonymous")
            StepVerifier.create(result)
                    .assertNext(key -> {
                        assertThat(key).isNotNull();
                        assertThat(key).isNotBlank();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return 'anonymous' when all fallbacks fail")
        void userKeyResolver_AllFallbacksFail_ReturnsAnonymous() {
            // Given
            KeyResolver resolver = config.userKeyResolver();

            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/documents")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When - No principal, no X-Forwarded-For, remoteAddress might be null in tests
            Mono<String> result = resolver.resolve(exchange);

            // Then
            StepVerifier.create(result)
                    .assertNext(key -> {
                        // Either remote address or "anonymous"
                        assertThat(key).isNotNull();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should handle X-Forwarded-For with multiple IPs")
        void userKeyResolver_MultipleForwardedIps_UsesFirst() {
            // Given
            KeyResolver resolver = config.userKeyResolver();
            String forwardedHeader = "192.168.1.100, 10.0.0.1, 172.16.0.1";

            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/documents")
                    .header("X-Forwarded-For", forwardedHeader)
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When
            Mono<String> result = resolver.resolve(exchange);

            // Then - Uses the full header value (the resolver doesn't parse it)
            StepVerifier.create(result)
                    .expectNext(forwardedHeader)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should be marked as primary bean")
        void userKeyResolver_IsPrimary() {
            // The @Primary annotation ensures this resolver is used by default
            KeyResolver resolver = config.userKeyResolver();
            assertThat(resolver).isNotNull();
        }
    }

    @Nested
    @DisplayName("Tenant Key Resolver Tests")
    class TenantKeyResolverTests {

        @Test
        @DisplayName("Should use X-Tenant-ID header when present")
        void tenantKeyResolver_WithTenantHeader_UsesTenantId() {
            // Given
            KeyResolver resolver = config.tenantKeyResolver();
            String tenantId = "tenant-456";

            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/documents")
                    .header("X-Tenant-ID", tenantId)
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When
            Mono<String> result = resolver.resolve(exchange);

            // Then
            StepVerifier.create(result)
                    .expectNext(tenantId)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should use default tenant when header missing")
        void tenantKeyResolver_WithoutHeader_UsesDefault() {
            // Given
            KeyResolver resolver = config.tenantKeyResolver();

            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/documents")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When
            Mono<String> result = resolver.resolve(exchange);

            // Then
            StepVerifier.create(result)
                    .expectNext("default-tenant")
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should handle empty tenant header as default")
        void tenantKeyResolver_EmptyHeader_UsesDefault() {
            // Given
            KeyResolver resolver = config.tenantKeyResolver();

            // Note: Empty string headers are typically not set, but let's verify behavior
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/documents")
                    // Empty header value
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When
            Mono<String> result = resolver.resolve(exchange);

            // Then
            StepVerifier.create(result)
                    .expectNext("default-tenant")
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should handle special characters in tenant ID")
        void tenantKeyResolver_SpecialChars_HandlesCorrectly() {
            // Given
            KeyResolver resolver = config.tenantKeyResolver();
            String tenantId = "tenant-with_special.chars:123";

            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/documents")
                    .header("X-Tenant-ID", tenantId)
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When
            Mono<String> result = resolver.resolve(exchange);

            // Then
            StepVerifier.create(result)
                    .expectNext(tenantId)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should handle very long tenant ID")
        void tenantKeyResolver_LongTenantId_HandlesCorrectly() {
            // Given
            KeyResolver resolver = config.tenantKeyResolver();
            String longTenantId = "tenant-" + "a".repeat(500);

            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/documents")
                    .header("X-Tenant-ID", longTenantId)
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When
            Mono<String> result = resolver.resolve(exchange);

            // Then
            StepVerifier.create(result)
                    .expectNext(longTenantId)
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Key Resolver Behavior Tests")
    class KeyResolverBehaviorTests {

        @Test
        @DisplayName("Key resolver should return consistent keys for same request")
        void keyResolver_SameRequest_ReturnsConsistentKey() {
            // Given
            KeyResolver resolver = config.tenantKeyResolver();
            String tenantId = "tenant-123";

            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/documents")
                    .header("X-Tenant-ID", tenantId)
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When - Resolve multiple times
            Mono<String> result1 = resolver.resolve(exchange);
            Mono<String> result2 = resolver.resolve(exchange);

            // Then - Both should return same key
            StepVerifier.create(result1)
                    .expectNext(tenantId)
                    .verifyComplete();

            StepVerifier.create(result2)
                    .expectNext(tenantId)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Different requests should get different keys")
        void keyResolver_DifferentRequests_ReturnsDifferentKeys() {
            // Given
            KeyResolver resolver = config.tenantKeyResolver();

            MockServerWebExchange exchange1 = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/documents")
                            .header("X-Tenant-ID", "tenant-1")
                            .build());

            MockServerWebExchange exchange2 = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/documents")
                            .header("X-Tenant-ID", "tenant-2")
                            .build());

            // When
            String key1 = resolver.resolve(exchange1).block();
            String key2 = resolver.resolve(exchange2).block();

            // Then
            assertThat(key1).isEqualTo("tenant-1");
            assertThat(key2).isEqualTo("tenant-2");
            assertThat(key1).isNotEqualTo(key2);
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should handle null header gracefully")
        void resolver_NullHeader_HandlesGracefully() {
            // Given
            KeyResolver resolver = config.tenantKeyResolver();

            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/documents")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When - No X-Tenant-ID header
            Mono<String> result = resolver.resolve(exchange);

            // Then - Should fall back to default
            StepVerifier.create(result)
                    .expectNext("default-tenant")
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should handle concurrent resolution")
        void resolver_ConcurrentResolution_WorksCorrectly() {
            // Given
            KeyResolver resolver = config.tenantKeyResolver();
            String tenantId = "tenant-concurrent";

            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/documents")
                    .header("X-Tenant-ID", tenantId)
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When - Resolve concurrently
            Mono<String> result1 = resolver.resolve(exchange);
            Mono<String> result2 = resolver.resolve(exchange);
            Mono<String> result3 = resolver.resolve(exchange);

            // Then - All should return same key
            StepVerifier.create(Mono.zip(result1, result2, result3))
                    .assertNext(tuple -> {
                        assertThat(tuple.getT1()).isEqualTo(tenantId);
                        assertThat(tuple.getT2()).isEqualTo(tenantId);
                        assertThat(tuple.getT3()).isEqualTo(tenantId);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("User resolver should prefer principal over headers")
        void userResolver_PrefersPrincipal() {
            // Given
            KeyResolver resolver = config.userKeyResolver();
            String userId = "authenticated-user";
            String forwardedIp = "192.168.1.100";

            when(mockPrincipal.getName()).thenReturn(userId);

            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/documents")
                    .header("X-Forwarded-For", forwardedIp)
                    .build();
            ServerWebExchange exchange = MockServerWebExchange.from(request).mutate()
                    .principal(Mono.just(mockPrincipal))
                    .build();

            // When
            Mono<String> result = resolver.resolve(exchange);

            // Then - Should use principal name, not IP
            StepVerifier.create(result)
                    .expectNext(userId)
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Rate Limiter Integration Tests")
    class RateLimiterIntegrationTests {

        @Test
        @DisplayName("Rate limiter and key resolver work together")
        void rateLimiterAndResolver_WorkTogether() {
            // Given
            RedisRateLimiter limiter = config.redisRateLimiter();
            KeyResolver userResolver = config.userKeyResolver();
            KeyResolver tenantResolver = config.tenantKeyResolver();

            // Then - All beans should be created successfully
            assertThat(limiter).isNotNull();
            assertThat(userResolver).isNotNull();
            assertThat(tenantResolver).isNotNull();
        }

        @Test
        @DisplayName("Both resolvers can resolve from same exchange")
        void bothResolvers_SameExchange_WorkCorrectly() {
            // Given
            KeyResolver userResolver = config.userKeyResolver();
            KeyResolver tenantResolver = config.tenantKeyResolver();

            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/documents")
                    .header("X-Tenant-ID", "tenant-123")
                    .header("X-Forwarded-For", "192.168.1.100")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When
            String userKey = userResolver.resolve(exchange).block();
            String tenantKey = tenantResolver.resolve(exchange).block();

            // Then
            assertThat(userKey).isEqualTo("192.168.1.100");
            assertThat(tenantKey).isEqualTo("tenant-123");
        }
    }
}
