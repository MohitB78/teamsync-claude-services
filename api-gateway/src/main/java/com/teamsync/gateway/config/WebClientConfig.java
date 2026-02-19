package com.teamsync.gateway.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * WebClient configuration for BFF services.
 *
 * <p>Provides a pre-configured WebClient.Builder bean with:
 * <ul>
 *   <li>Connection timeout</li>
 *   <li>Read/Write timeouts</li>
 *   <li>Request/Response logging</li>
 * </ul>
 *
 * @see ZitadelAuthService
 */
@Configuration
@ConditionalOnProperty(name = "teamsync.bff.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class WebClientConfig {

    private final BffProperties bffProperties;

    @Bean
    public WebClient.Builder webClientBuilder() {
        BffProperties.ZitadelProperties zitadel = bffProperties.zitadel();

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        zitadel.connectionTimeoutSeconds() * 1000)
                .responseTimeout(Duration.ofSeconds(zitadel.readTimeoutSeconds()))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(zitadel.readTimeoutSeconds(), TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(zitadel.readTimeoutSeconds(), TimeUnit.SECONDS)));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .filter(logRequest())
                .filter(logResponse());
    }

    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            if (log.isDebugEnabled()) {
                log.debug("Request: {} {}", clientRequest.method(), clientRequest.url());
            }
            return Mono.just(clientRequest);
        });
    }

    private ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            if (log.isDebugEnabled()) {
                log.debug("Response: {}", clientResponse.statusCode());
            }
            return Mono.just(clientResponse);
        });
    }

    /**
     * ObjectMapper bean for JSON serialization/deserialization.
     *
     * <p>Configured with:
     * <ul>
     *   <li>Java 8 date/time support</li>
     *   <li>Ignore unknown properties</li>
     *   <li>ISO 8601 date format</li>
     * </ul>
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        return mapper;
    }
}
