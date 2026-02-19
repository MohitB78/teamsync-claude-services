package com.teamsync.common.tracing;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.semconv.ServiceAttributes;
import io.opentelemetry.api.common.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * OpenTelemetry configuration for TeamSync services.
 *
 * <p>Updated for OpenTelemetry SDK 1.58.0 with best practices from January 2026.</p>
 *
 * <p>This configuration sets up distributed tracing with the following features:</p>
 * <ul>
 *   <li>OTLP HTTP exporter to send traces to the OpenTelemetry Collector (more reliable than gRPC over Railway)</li>
 *   <li>W3C Trace Context + Baggage propagation for cross-service tracing</li>
 *   <li>Resource attributes for service identification</li>
 *   <li>Tenant context span processor for business context</li>
 *   <li>Configurable sampling with parent-based trace ID ratio</li>
 *   <li>Optimized batch span processor settings</li>
 * </ul>
 *
 * <p>Configuration properties (following OTel SDK autoconfigure conventions):</p>
 * <ul>
 *   <li>{@code otel.exporter.otlp.endpoint} - OTel Collector endpoint (default: http://localhost:4317)</li>
 *   <li>{@code otel.exporter.otlp.timeout} - Export timeout in ms (default: 10000)</li>
 *   <li>{@code otel.traces.sampler.probability} - Sampling probability 0.0-1.0 (default: 1.0)</li>
 *   <li>{@code otel.bsp.schedule.delay} - Batch delay in ms (default: 5000)</li>
 *   <li>{@code otel.bsp.max.queue.size} - Max queue size (default: 2048)</li>
 *   <li>{@code otel.bsp.max.export.batch.size} - Max batch size (default: 512)</li>
 *   <li>{@code otel.environment} - Deployment environment (default: development)</li>
 *   <li>{@code otel.tracing.enabled} - Enable/disable tracing (default: true)</li>
 * </ul>
 *
 * @see <a href="https://opentelemetry.io/docs/languages/java/configuration/">OTel Java Configuration</a>
 */
@Configuration
@ConditionalOnProperty(name = "otel.tracing.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class OpenTelemetryConfig {

    @Value("${spring.application.name:unknown-service}")
    private String serviceName;

    @Value("${otel.exporter.otlp.endpoint:http://localhost:4317}")
    private String otlpEndpoint;

    @Value("${otel.exporter.otlp.timeout:10000}")
    private long otlpTimeoutMs;

    @Value("${otel.traces.sampler.probability:1.0}")
    private double samplingProbability;

    @Value("${otel.bsp.schedule.delay:5000}")
    private long bspScheduleDelayMs;

    @Value("${otel.bsp.max.queue.size:2048}")
    private int bspMaxQueueSize;

    @Value("${otel.bsp.max.export.batch.size:512}")
    private int bspMaxExportBatchSize;

    @Value("${otel.environment:development}")
    private String environment;

    /**
     * Creates the OpenTelemetry Resource with service identification attributes.
     * These attributes are added to every span and help identify the source service.
     */
    // Attribute keys for incubating semconv attributes (using string keys for portability)
    // These are not yet stable in semconv 1.37.0, so we define them manually
    private static final AttributeKey<String> DEPLOYMENT_ENVIRONMENT =
            AttributeKey.stringKey("deployment.environment.name");
    private static final AttributeKey<String> SERVICE_NAMESPACE =
            AttributeKey.stringKey("service.namespace");

    @Bean
    public Resource otelResource() {
        return Resource.getDefault()
            .merge(Resource.create(Attributes.builder()
                .put(ServiceAttributes.SERVICE_NAME, serviceName)
                .put(SERVICE_NAMESPACE, "teamsync")
                .put(DEPLOYMENT_ENVIRONMENT, environment)
                .put(ServiceAttributes.SERVICE_VERSION, getServiceVersion())
                .build()));
    }

    /**
     * Creates the OTLP span exporter that sends traces to the OTel Collector.
     *
     * <p>Uses HTTP protocol instead of gRPC for better reliability over Railway's
     * internal networking. gRPC connections were experiencing "Connection reset"
     * errors due to keep-alive timeout mismatches.</p>
     *
     * <p>The HTTP endpoint is derived from the gRPC endpoint by:
     * - Replacing port 4317 with 4318 (OTLP HTTP port)
     * - Appending /v1/traces path</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public OtlpHttpSpanExporter otlpSpanExporter() {
        // Convert gRPC endpoint (4317) to HTTP endpoint (4318)
        String httpEndpoint = otlpEndpoint
            .replace(":4317", ":4318")
            .replace("/v1/traces", ""); // Remove if already present

        // Ensure the endpoint ends with /v1/traces for HTTP protocol
        if (!httpEndpoint.endsWith("/v1/traces")) {
            httpEndpoint = httpEndpoint + "/v1/traces";
        }

        log.info("Configuring OTLP HTTP span exporter - endpoint: {}, timeout: {}ms",
                httpEndpoint, otlpTimeoutMs);

        return OtlpHttpSpanExporter.builder()
            .setEndpoint(httpEndpoint)
            .setTimeout(Duration.ofMillis(otlpTimeoutMs))
            .build();
    }

    /**
     * Creates the TenantContextSpanProcessor that adds tenant context to spans.
     */
    @Bean
    public TenantContextSpanProcessor tenantContextSpanProcessor() {
        return new TenantContextSpanProcessor();
    }

    /**
     * Creates a parent-based sampler with configurable trace ID ratio.
     * This respects the parent span's sampling decision while applying
     * ratio sampling to root spans.
     */
    @Bean
    @ConditionalOnMissingBean
    public Sampler sampler() {
        log.info("Configuring parent-based sampler with probability: {}", samplingProbability);
        return Sampler.parentBased(Sampler.traceIdRatioBased(samplingProbability));
    }

    /**
     * Creates the SDK Tracer Provider with batch processing, sampling, and tenant context.
     * Batch processor settings follow OTel SDK autoconfigure conventions.
     */
    @Bean
    @ConditionalOnMissingBean
    public SdkTracerProvider tracerProvider(
            Resource resource,
            OtlpHttpSpanExporter spanExporter,
            TenantContextSpanProcessor tenantContextSpanProcessor,
            Sampler sampler) {

        log.info("Configuring OpenTelemetry tracer provider - service: {}, bsp.delay: {}ms, bsp.queue: {}, bsp.batch: {}",
                serviceName, bspScheduleDelayMs, bspMaxQueueSize, bspMaxExportBatchSize);

        return SdkTracerProvider.builder()
            .setSampler(sampler)
            .addSpanProcessor(tenantContextSpanProcessor)
            .addSpanProcessor(BatchSpanProcessor.builder(spanExporter)
                .setMaxQueueSize(bspMaxQueueSize)
                .setMaxExportBatchSize(bspMaxExportBatchSize)
                .setScheduleDelay(Duration.ofMillis(bspScheduleDelayMs))
                .setExporterTimeout(Duration.ofMillis(otlpTimeoutMs))
                .build())
            .setResource(resource)
            .build();
    }

    /**
     * Creates a composite text map propagator with W3C Trace Context and Baggage.
     * This enables both trace context and baggage propagation across services.
     */
    @Bean
    @ConditionalOnMissingBean
    public TextMapPropagator textMapPropagator() {
        return TextMapPropagator.composite(
            W3CTraceContextPropagator.getInstance(),
            W3CBaggagePropagator.getInstance()
        );
    }

    /**
     * Creates the OpenTelemetry SDK with trace propagation configured.
     * Uses W3C Trace Context + Baggage propagation for cross-service tracing.
     */
    @Bean
    @ConditionalOnMissingBean
    public OpenTelemetry openTelemetry(SdkTracerProvider tracerProvider, TextMapPropagator propagator) {
        log.info("Initializing OpenTelemetry SDK 1.58.0 - service: {}, environment: {}",
                serviceName, environment);

        return OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setPropagators(ContextPropagators.create(propagator))
            .buildAndRegisterGlobal();
    }

    /**
     * Creates a Tracer instance for the service.
     */
    @Bean
    @ConditionalOnMissingBean
    public Tracer tracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer(serviceName);
    }

    private String getServiceVersion() {
        String version = getClass().getPackage().getImplementationVersion();
        return version != null ? version : "1.0.0-SNAPSHOT";
    }
}
