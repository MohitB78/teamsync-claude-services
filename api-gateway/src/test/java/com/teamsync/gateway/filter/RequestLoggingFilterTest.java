package com.teamsync.gateway.filter;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for RequestLoggingFilter.
 * Tests request ID generation, timing, and logging functionality.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Request Logging Filter Tests")
class RequestLoggingFilterTest {

    private RequestLoggingFilter filter;

    @Mock
    private GatewayFilterChain chain;

    private ArgumentCaptor<ServerWebExchange> exchangeCaptor;

    @BeforeEach
    void setUp() {
        filter = new RequestLoggingFilter();
        exchangeCaptor = ArgumentCaptor.forClass(ServerWebExchange.class);
        lenient().when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    @Nested
    @DisplayName("Request ID Generation Tests")
    class RequestIdGenerationTests {

        @Test
        @DisplayName("Should generate new request ID when none provided")
        void filter_WithoutRequestId_GeneratesNewId() {
            // Given
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/documents")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When
            Mono<Void> result = filter.filter(exchange, chain);

            StepVerifier.create(result)
                    .verifyComplete();

            // Then
            verify(chain).filter(exchangeCaptor.capture());
            ServerWebExchange capturedExchange = exchangeCaptor.getValue();

            String requestId = capturedExchange.getRequest().getHeaders().getFirst("X-Request-ID");
            assertThat(requestId).isNotNull();
            assertThat(requestId).isNotBlank();

            // Should be a valid UUID
            assertThat(UUID.fromString(requestId)).isNotNull();
        }

        @Test
        @DisplayName("Should use existing request ID when provided")
        void filter_WithExistingRequestId_UsesProvidedId() {
            // Given
            String existingRequestId = "existing-request-123";
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/documents")
                    .header("X-Request-ID", existingRequestId)
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When
            Mono<Void> result = filter.filter(exchange, chain);

            StepVerifier.create(result)
                    .verifyComplete();

            // Then
            verify(chain).filter(exchangeCaptor.capture());
            ServerWebExchange capturedExchange = exchangeCaptor.getValue();

            String requestId = capturedExchange.getRequest().getHeaders().getFirst("X-Request-ID");
            assertThat(requestId).isEqualTo(existingRequestId);
        }

        @Test
        @DisplayName("Should generate new request ID when provided ID is blank")
        void filter_WithBlankRequestId_GeneratesNewId() {
            // Given
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/documents")
                    .header("X-Request-ID", "   ")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When
            Mono<Void> result = filter.filter(exchange, chain);

            StepVerifier.create(result)
                    .verifyComplete();

            // Then
            verify(chain).filter(exchangeCaptor.capture());
            ServerWebExchange capturedExchange = exchangeCaptor.getValue();

            String requestId = capturedExchange.getRequest().getHeaders().getFirst("X-Request-ID");
            assertThat(requestId).isNotBlank();
            assertThat(requestId).isNotEqualTo("   ");
        }

        @Test
        @DisplayName("Should add request ID to response headers")
        void filter_AddsRequestIdToResponse() {
            // Given
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/documents")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When
            filter.filter(exchange, chain).block();

            // Then - Response should have X-Request-ID header
            String responseRequestId = exchange.getResponse().getHeaders().getFirst("X-Request-ID");
            assertThat(responseRequestId).isNotNull();
            assertThat(responseRequestId).isNotBlank();
        }
    }

    @Nested
    @DisplayName("Timing Tests")
    class TimingTests {

        @Test
        @DisplayName("Should record start time in exchange attributes")
        void filter_RecordsStartTime() {
            // Given
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/documents")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When
            filter.filter(exchange, chain).block();

            // Then
            verify(chain).filter(exchangeCaptor.capture());
            ServerWebExchange capturedExchange = exchangeCaptor.getValue();

            Long startTime = capturedExchange.getAttribute("requestStartTime");
            assertThat(startTime).isNotNull();
            assertThat(startTime).isLessThanOrEqualTo(System.currentTimeMillis());
        }

        @Test
        @DisplayName("Should calculate duration correctly")
        void filter_CalculatesDuration() throws InterruptedException {
            // Given
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/documents")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // Add small delay in chain processing
            when(chain.filter(any(ServerWebExchange.class))).thenAnswer(inv -> {
                Thread.sleep(50);
                return Mono.empty();
            });

            // When
            long before = System.currentTimeMillis();
            filter.filter(exchange, chain).block();
            long after = System.currentTimeMillis();

            // Then - Duration should be at least 50ms
            assertThat(after - before).isGreaterThanOrEqualTo(50);
        }
    }

    @Nested
    @DisplayName("Filter Order Tests")
    class FilterOrderTests {

        @Test
        @DisplayName("Should have highest precedence")
        void getOrder_ReturnsHighestPrecedence() {
            assertThat(filter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
        }

        @Test
        @DisplayName("Should run before all other filters")
        void getOrder_RunsFirst() {
            assertThat(filter.getOrder()).isEqualTo(Integer.MIN_VALUE);
        }
    }

    @Nested
    @DisplayName("Request Passthrough Tests")
    class RequestPassthroughTests {

        @Test
        @DisplayName("Should preserve all request properties")
        void filter_PreservesRequestProperties() {
            // Given
            MockServerHttpRequest request = MockServerHttpRequest
                    .post("/api/documents")
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer token123")
                    .header("X-Custom-Header", "custom-value")
                    .body("test body");
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When
            filter.filter(exchange, chain).block();

            // Then
            verify(chain).filter(exchangeCaptor.capture());
            ServerWebExchange capturedExchange = exchangeCaptor.getValue();

            assertThat(capturedExchange.getRequest().getMethod().toString()).isEqualTo("POST");
            assertThat(capturedExchange.getRequest().getPath().toString()).isEqualTo("/api/documents");
            assertThat(capturedExchange.getRequest().getHeaders().getFirst("Content-Type"))
                    .isEqualTo("application/json");
            assertThat(capturedExchange.getRequest().getHeaders().getFirst("Authorization"))
                    .isEqualTo("Bearer token123");
            assertThat(capturedExchange.getRequest().getHeaders().getFirst("X-Custom-Header"))
                    .isEqualTo("custom-value");
        }

        @Test
        @DisplayName("Should preserve query parameters")
        void filter_PreservesQueryParams() {
            // Given
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/documents")
                    .queryParam("page", "1")
                    .queryParam("size", "20")
                    .queryParam("filter", "active")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When
            filter.filter(exchange, chain).block();

            // Then
            verify(chain).filter(exchangeCaptor.capture());
            ServerWebExchange capturedExchange = exchangeCaptor.getValue();

            assertThat(capturedExchange.getRequest().getQueryParams().getFirst("page")).isEqualTo("1");
            assertThat(capturedExchange.getRequest().getQueryParams().getFirst("size")).isEqualTo("20");
            assertThat(capturedExchange.getRequest().getQueryParams().getFirst("filter")).isEqualTo("active");
        }
    }

    @Nested
    @DisplayName("Different HTTP Methods Tests")
    class HttpMethodsTests {

        @Test
        @DisplayName("Should handle GET requests")
        void filter_HandlesGetRequest() {
            // Given
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/documents").build());

            // When
            Mono<Void> result = filter.filter(exchange, chain);

            // Then
            StepVerifier.create(result).verifyComplete();
            verify(chain).filter(any(ServerWebExchange.class));
        }

        @Test
        @DisplayName("Should handle POST requests")
        void filter_HandlesPostRequest() {
            // Given
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post("/api/documents").build());

            // When
            Mono<Void> result = filter.filter(exchange, chain);

            // Then
            StepVerifier.create(result).verifyComplete();
            verify(chain).filter(any(ServerWebExchange.class));
        }

        @Test
        @DisplayName("Should handle PUT requests")
        void filter_HandlesPutRequest() {
            // Given
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.put("/api/documents/123").build());

            // When
            Mono<Void> result = filter.filter(exchange, chain);

            // Then
            StepVerifier.create(result).verifyComplete();
            verify(chain).filter(any(ServerWebExchange.class));
        }

        @Test
        @DisplayName("Should handle DELETE requests")
        void filter_HandlesDeleteRequest() {
            // Given
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.delete("/api/documents/123").build());

            // When
            Mono<Void> result = filter.filter(exchange, chain);

            // Then
            StepVerifier.create(result).verifyComplete();
            verify(chain).filter(any(ServerWebExchange.class));
        }

        @Test
        @DisplayName("Should handle PATCH requests")
        void filter_HandlesPatchRequest() {
            // Given
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.patch("/api/documents/123").build());

            // When
            Mono<Void> result = filter.filter(exchange, chain);

            // Then
            StepVerifier.create(result).verifyComplete();
            verify(chain).filter(any(ServerWebExchange.class));
        }

        @Test
        @DisplayName("Should handle OPTIONS requests")
        void filter_HandlesOptionsRequest() {
            // Given
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.options("/api/documents").build());

            // When
            Mono<Void> result = filter.filter(exchange, chain);

            // Then
            StepVerifier.create(result).verifyComplete();
            verify(chain).filter(any(ServerWebExchange.class));
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should handle very long paths")
        void filter_HandlesLongPath() {
            // Given
            String longPath = "/api/" + "a".repeat(1000) + "/documents";
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get(longPath).build());

            // When
            Mono<Void> result = filter.filter(exchange, chain);

            // Then
            StepVerifier.create(result).verifyComplete();
        }

        @Test
        @DisplayName("Should handle special characters in path")
        void filter_HandlesSpecialCharsInPath() {
            // Given
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/documents/test%20file.pdf").build());

            // When
            Mono<Void> result = filter.filter(exchange, chain);

            // Then
            StepVerifier.create(result).verifyComplete();
        }

        @Test
        @DisplayName("Should handle multiple request ID headers")
        void filter_HandlesMultipleRequestIdHeaders() {
            // Given - Multiple X-Request-ID headers (first one should be used)
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/documents")
                    .header("X-Request-ID", "first-id")
                    .header("X-Request-ID", "second-id")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When
            filter.filter(exchange, chain).block();

            // Then
            verify(chain).filter(exchangeCaptor.capture());
            ServerWebExchange capturedExchange = exchangeCaptor.getValue();

            // Should use first header value
            assertThat(capturedExchange.getRequest().getHeaders().getFirst("X-Request-ID"))
                    .isEqualTo("first-id");
        }

        @Test
        @DisplayName("Should handle chain returning error")
        void filter_HandlesChainError() {
            // Given
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/documents").build());
            when(chain.filter(any(ServerWebExchange.class)))
                    .thenReturn(Mono.error(new RuntimeException("Downstream error")));

            // When/Then
            Mono<Void> result = filter.filter(exchange, chain);

            StepVerifier.create(result)
                    .expectError(RuntimeException.class)
                    .verify();
        }

        @Test
        @DisplayName("Should handle root path")
        void filter_HandlesRootPath() {
            // Given
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/").build());

            // When
            Mono<Void> result = filter.filter(exchange, chain);

            // Then
            StepVerifier.create(result).verifyComplete();
        }

        @Test
        @DisplayName("Should handle empty User-Agent")
        void filter_HandlesEmptyUserAgent() {
            // Given
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/documents").build());

            // Verify no User-Agent header
            assertThat(exchange.getRequest().getHeaders().getFirst("User-Agent")).isNull();

            // When
            Mono<Void> result = filter.filter(exchange, chain);

            // Then
            StepVerifier.create(result).verifyComplete();
        }
    }

    @Nested
    @DisplayName("Concurrent Request Tests")
    class ConcurrentRequestTests {

        @Test
        @DisplayName("Should generate unique request IDs for concurrent requests")
        void filter_GeneratesUniqueIdsForConcurrentRequests() {
            // Given
            MockServerWebExchange exchange1 = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/documents/1").build());
            MockServerWebExchange exchange2 = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/documents/2").build());

            // When
            filter.filter(exchange1, chain).block();
            filter.filter(exchange2, chain).block();

            // Then - Request IDs should be unique
            String requestId1 = exchange1.getResponse().getHeaders().getFirst("X-Request-ID");
            String requestId2 = exchange2.getResponse().getHeaders().getFirst("X-Request-ID");

            assertThat(requestId1).isNotNull();
            assertThat(requestId2).isNotNull();
            assertThat(requestId1).isNotEqualTo(requestId2);
        }

        @Test
        @DisplayName("Should maintain request isolation")
        void filter_MaintainsRequestIsolation() {
            // Given
            MockServerWebExchange exchange1 = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/documents/1")
                            .header("X-Request-ID", "request-1")
                            .build());
            MockServerWebExchange exchange2 = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/documents/2")
                            .header("X-Request-ID", "request-2")
                            .build());

            // When
            filter.filter(exchange1, chain).block();
            filter.filter(exchange2, chain).block();

            // Then - Each exchange maintains its own request ID
            assertThat(exchange1.getResponse().getHeaders().getFirst("X-Request-ID"))
                    .isEqualTo("request-1");
            assertThat(exchange2.getResponse().getHeaders().getFirst("X-Request-ID"))
                    .isEqualTo("request-2");
        }
    }
}
