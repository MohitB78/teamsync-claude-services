package com.teamsync.common.config;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Micrometer Observation API integration with OpenTelemetry.
 *
 * <p>This configuration enables the {@code @Observed} annotation which provides:</p>
 * <ul>
 *   <li><b>Automatic span creation</b> - Creates OTel spans for annotated methods</li>
 *   <li><b>Timing metrics</b> - Records method execution time in Micrometer</li>
 *   <li><b>Error tracking</b> - Automatically records exceptions in spans</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * @Observed(name = "document.list", contextualName = "list-documents")
 * public List<DocumentDTO> listDocuments(String folderId) {
 *     // Method will be traced automatically
 * }
 * }</pre>
 *
 * <h2>Span Attributes</h2>
 * <p>You can add custom attributes using low cardinality key-values:</p>
 * <pre>{@code
 * @Observed(
 *     name = "document.get",
 *     lowCardinalityKeyValues = {"operation", "read", "entity", "document"}
 * )
 * }</pre>
 *
 * <h2>Viewing Traces</h2>
 * <p>Traces are exported to the OTLP endpoint configured in application.yml.
 * Use Jaeger, Grafana Tempo, or another OTLP-compatible backend to view traces.</p>
 *
 * @author TeamSync Platform Team
 * @version 1.0.0
 * @since Spring Boot 4.0.0
 * @see io.micrometer.observation.annotation.Observed
 */
@Configuration
public class ObservationConfig {

    /**
     * Creates the ObservedAspect bean that processes @Observed annotations.
     *
     * <p>This aspect intercepts methods annotated with {@code @Observed} and:</p>
     * <ol>
     *   <li>Creates an observation (which becomes an OTel span via the bridge)</li>
     *   <li>Records timing metrics</li>
     *   <li>Handles errors by recording exceptions in the span</li>
     *   <li>Ends the observation when the method completes</li>
     * </ol>
     *
     * @param observationRegistry the registry used to create observations
     * @return the configured ObservedAspect
     */
    @Bean
    @ConditionalOnMissingBean
    public ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
        return new ObservedAspect(observationRegistry);
    }
}