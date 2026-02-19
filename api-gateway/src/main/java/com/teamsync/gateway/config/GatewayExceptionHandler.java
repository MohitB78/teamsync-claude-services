package com.teamsync.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Global exception handler for the API Gateway.
 *
 * <p>This handler catches exceptions that occur during request processing,
 * including session-related errors from Spring Session Redis.
 *
 * <p>Notable exceptions handled:
 * <ul>
 *   <li>IllegalStateException with "key must not be null" - Corrupt Redis session data
 *       (e.g., after Redis restart or partial data loss)</li>
 * </ul>
 *
 * <p>For corrupt session errors, returns 401 Unauthorized to prompt re-authentication
 * rather than 500 Internal Server Error.
 */
@Component
@Order(-2) // Run before default Spring Boot error handler
@Slf4j
public class GatewayExceptionHandler implements WebExceptionHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        String errorMessage = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
        String path = exchange.getRequest().getPath().value();

        // Check for corrupt Redis session error
        // Spring Session Redis throws IllegalStateException when session data is incomplete
        // (e.g., "creationTime key must not be null" after Redis restart)
        if (ex instanceof IllegalStateException && errorMessage.contains("key must not be null")) {
            log.warn("Corrupt Redis session detected for path: {}, error: {}. " +
                "Returning 401 to prompt re-authentication.", path, errorMessage);

            return writeJsonResponse(exchange, HttpStatus.UNAUTHORIZED,
                    "{\"success\":false,\"error\":\"Session expired or invalid. Please log in again.\",\"code\":\"SESSION_INVALID\"}");
        }

        // Let other exceptions propagate to default handlers
        return Mono.error(ex);
    }

    private Mono<Void> writeJsonResponse(ServerWebExchange exchange, HttpStatus status, String body) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);

        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
