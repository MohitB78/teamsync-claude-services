package com.teamsync.gateway.filter;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Global filter for request logging and distributed tracing.
 *
 * <p>This filter is the entry point for all requests and performs:</p>
 * <ul>
 *   <li>Request ID generation and propagation</li>
 *   <li>W3C Trace Context extraction from incoming requests (from frontend)</li>
 *   <li>Span creation for the gateway request</li>
 *   <li>Trace context injection into downstream service requests</li>
 *   <li>Request/response logging with timing</li>
 * </ul>
 *
 * <p>The trace context is propagated using W3C Trace Context standard headers:</p>
 * <ul>
 *   <li>{@code traceparent} - Contains trace ID, span ID, and trace flags</li>
 *   <li>{@code tracestate} - Contains vendor-specific trace information</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String TRACE_ID_HEADER = "X-Trace-ID";
    private static final String START_TIME_ATTR = "requestStartTime";
    private static final String SPAN_ATTR = "otelSpan";
    private static final String SCOPE_ATTR = "otelScope";

    private final Tracer tracer;
    private final TextMapPropagator propagator;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Extract trace context from incoming request (may be from frontend)
        Context extractedContext = propagator.extract(
            Context.current(),
            exchange.getRequest().getHeaders(),
            HttpHeadersGetter.INSTANCE
        );

        // Create span for the gateway request
        Span span = tracer.spanBuilder("gateway.request")
            .setParent(extractedContext)
            .setSpanKind(SpanKind.SERVER)
            .startSpan();

        // Make span current for this request
        Scope scope = span.makeCurrent();

        SpanContext spanContext = span.getSpanContext();
        String traceId = spanContext.getTraceId();
        String spanId = spanContext.getSpanId();

        // Generate or use existing request ID
        String requestId = exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        final String finalRequestId = requestId;
        final String finalTraceId = traceId;

        // Add span attributes
        span.setAttribute("http.method", exchange.getRequest().getMethod().name());
        span.setAttribute("http.url", exchange.getRequest().getPath().toString());
        span.setAttribute("http.request_id", finalRequestId);

        String tenantId = exchange.getRequest().getHeaders().getFirst("X-Tenant-ID");
        String userId = exchange.getRequest().getHeaders().getFirst("X-User-ID");
        String driveId = exchange.getRequest().getHeaders().getFirst("X-Drive-ID");

        if (tenantId != null) {
            span.setAttribute("teamsync.tenant.id", tenantId);
        }
        if (userId != null) {
            span.setAttribute("teamsync.user.id", userId);
        }
        if (driveId != null) {
            span.setAttribute("teamsync.drive.id", driveId);
        }

        // Add headers to response
        exchange.getResponse().getHeaders().add(REQUEST_ID_HEADER, finalRequestId);
        exchange.getResponse().getHeaders().add(TRACE_ID_HEADER, finalTraceId);

        // Mutate request to propagate trace context to downstream services
        ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate()
            .header(REQUEST_ID_HEADER, finalRequestId)
            .header(TRACE_ID_HEADER, finalTraceId);

        // Inject trace context headers for W3C Trace Context propagation
        propagator.inject(Context.current(), requestBuilder, HttpRequestBuilderSetter.INSTANCE);

        ServerHttpRequest mutatedRequest = requestBuilder.build();
        ServerWebExchange mutatedExchange = exchange.mutate()
            .request(mutatedRequest)
            .build();

        // Store span and scope for cleanup
        mutatedExchange.getAttributes().put(SPAN_ATTR, span);
        mutatedExchange.getAttributes().put(SCOPE_ATTR, scope);
        mutatedExchange.getAttributes().put(START_TIME_ATTR, System.currentTimeMillis());

        // Log request with trace context
        log.info("Request: {} {} | TraceID: {} | SpanID: {} | RequestID: {} | Tenant: {}",
            exchange.getRequest().getMethod(),
            exchange.getRequest().getPath(),
            finalTraceId,
            spanId,
            finalRequestId,
            tenantId);

        return chain.filter(mutatedExchange)
            .doOnSuccess(v -> completeSpan(mutatedExchange, null))
            .doOnError(error -> completeSpan(mutatedExchange, error))
            .doFinally(signalType -> {
                // Ensure scope is closed
                Scope storedScope = mutatedExchange.getAttribute(SCOPE_ATTR);
                if (storedScope != null) {
                    storedScope.close();
                }
            });
    }

    private void completeSpan(ServerWebExchange exchange, Throwable error) {
        Span span = exchange.getAttribute(SPAN_ATTR);
        Long startTime = exchange.getAttribute(START_TIME_ATTR);

        if (span != null) {
            if (error != null) {
                span.setStatus(StatusCode.ERROR, error.getMessage());
                span.recordException(error);
            } else {
                HttpStatusCode statusCode = exchange.getResponse().getStatusCode();
                if (statusCode != null) {
                    span.setAttribute("http.status_code", statusCode.value());
                    if (statusCode.isError()) {
                        span.setStatus(StatusCode.ERROR, "HTTP " + statusCode.value());
                    } else {
                        span.setStatus(StatusCode.OK);
                    }
                }
            }

            if (startTime != null) {
                long duration = System.currentTimeMillis() - startTime;
                span.setAttribute("http.duration_ms", duration);

                log.info("Response: {} {} | Status: {} | Duration: {}ms | TraceID: {}",
                    exchange.getRequest().getMethod(),
                    exchange.getRequest().getPath(),
                    exchange.getResponse().getStatusCode(),
                    duration,
                    span.getSpanContext().getTraceId());
            }

            span.end();
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    /**
     * TextMapGetter implementation for extracting trace context from HTTP headers.
     */
    private enum HttpHeadersGetter implements TextMapGetter<HttpHeaders> {
        INSTANCE;

        @Override
        public Iterable<String> keys(HttpHeaders headers) {
            // Spring Framework 7.x removed keySet() - use headerNames() instead
            return headers.headerNames();
        }

        @Override
        public String get(HttpHeaders headers, String key) {
            return headers.getFirst(key);
        }
    }

    /**
     * TextMapSetter implementation for injecting trace context into HTTP request builder.
     */
    private enum HttpRequestBuilderSetter implements TextMapSetter<ServerHttpRequest.Builder> {
        INSTANCE;

        @Override
        public void set(ServerHttpRequest.Builder carrier, String key, String value) {
            if (carrier != null && key != null && value != null) {
                carrier.header(key, value);
            }
        }
    }
}
