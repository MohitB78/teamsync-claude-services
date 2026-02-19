package com.teamsync.gateway.config;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for OpenTelemetry tracing in the API Gateway.
 *
 * <p>This configuration provides the necessary beans for distributed tracing:</p>
 * <ul>
 *   <li>{@link Tracer} - For creating spans</li>
 *   <li>{@link TextMapPropagator} - For extracting/injecting trace context in headers</li>
 * </ul>
 *
 * <p>The gateway uses W3C Trace Context format for interoperability with
 * the frontend OpenTelemetry instrumentation and downstream services.</p>
 */
@Configuration
public class TracingConfig {

    @Value("${spring.application.name:api-gateway}")
    private String serviceName;

    /**
     * Provides the W3C Trace Context propagator for header-based trace propagation.
     */
    @Bean
    @ConditionalOnMissingBean
    public TextMapPropagator textMapPropagator() {
        return W3CTraceContextPropagator.getInstance();
    }

    /**
     * Provides a Tracer for the API Gateway.
     * Uses the global OpenTelemetry instance if available (set by teamsync-common),
     * otherwise creates a no-op tracer.
     */
    @Bean
    @ConditionalOnMissingBean
    public Tracer tracer() {
        OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
        return openTelemetry.getTracer(serviceName);
    }
}
