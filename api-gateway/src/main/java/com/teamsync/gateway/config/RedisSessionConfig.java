package com.teamsync.gateway.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.session.data.redis.config.annotation.web.server.EnableRedisIndexedWebSession;
import org.springframework.web.server.session.CookieWebSessionIdResolver;
import org.springframework.web.server.session.WebSessionIdResolver;

import java.time.Duration;

/**
 * Redis session configuration for BFF pattern.
 *
 * <p>This configuration enables server-side session storage in Redis with:
 * <ul>
 *   <li>HttpOnly session cookie (not accessible via JavaScript)</li>
 *   <li>Secure cookie flag for production</li>
 *   <li>Configurable SameSite attribute (None for cross-origin OAuth flows)</li>
 *   <li>Configurable session timeout</li>
 * </ul>
 *
 * <p>The BFF stores the Keycloak access token in Redis, and only the session ID
 * is sent to the browser in an HttpOnly cookie.
 *
 * <p><b>Note:</b> For OAuth2 flows with Keycloak on a different domain, SameSite=None
 * is required so the session cookie is sent when Keycloak redirects back to the callback.
 *
 * @see BffProperties
 */
@Configuration
@ConditionalOnProperty(name = "teamsync.bff.enabled", havingValue = "true")
@EnableRedisIndexedWebSession(
    redisNamespace = "${teamsync.bff.session.redis-namespace:teamsync:bff:session}",
    maxInactiveIntervalInSeconds = 28800 // 8 hours default, overridden by property
)
@RequiredArgsConstructor
@Slf4j
public class RedisSessionConfig {

    private final BffProperties bffProperties;

    /**
     * Configures the session cookie with HttpOnly, Secure, and SameSite attributes.
     *
     * <p>Cookie settings:
     * <ul>
     *   <li><b>HttpOnly</b>: Prevents JavaScript access (XSS protection)</li>
     *   <li><b>Secure</b>: Cookie only sent over HTTPS (required for SameSite=None)</li>
     *   <li><b>SameSite</b>: Configurable - use "None" for cross-origin OAuth flows</li>
     *   <li><b>Path=/</b>: Cookie sent with all requests to the domain</li>
     * </ul>
     */
    @Bean
    public WebSessionIdResolver webSessionIdResolver() {
        CookieWebSessionIdResolver resolver = new CookieWebSessionIdResolver();

        BffProperties.SessionProperties sessionProps = bffProperties.session();

        resolver.setCookieName(sessionProps.cookieName());
        resolver.setCookieMaxAge(sessionProps.cookieMaxAge());

        // Configure cookie attributes
        resolver.addCookieInitializer(builder -> {
            builder.httpOnly(sessionProps.cookieHttpOnly());
            builder.secure(sessionProps.cookieSecure());
            builder.sameSite(sessionProps.cookieSameSite());
            builder.path("/");
            // Set cookie domain for cross-subdomain support (e.g., ".up.railway.app")
            if (sessionProps.cookieDomain() != null && !sessionProps.cookieDomain().isBlank()) {
                builder.domain(sessionProps.cookieDomain());
            }
        });

        log.info("BFF Session cookie configured: name={}, maxAge={}, domain={}, secure={}, httpOnly={}, sameSite={}",
            sessionProps.cookieName(),
            sessionProps.cookieMaxAge(),
            sessionProps.cookieDomain(),
            sessionProps.cookieSecure(),
            sessionProps.cookieHttpOnly(),
            sessionProps.cookieSameSite()
        );

        return resolver;
    }

    /**
     * ReactiveRedisTemplate for BFF session data with JSON serialization.
     *
     * <p>This template is used by BffAuthController to store/retrieve BffSession objects.
     */
    @Bean
    public ReactiveRedisTemplate<String, Object> bffSessionRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory) {

        Jackson2JsonRedisSerializer<Object> serializer =
            new Jackson2JsonRedisSerializer<>(Object.class);

        RedisSerializationContext<String, Object> context =
            RedisSerializationContext.<String, Object>newSerializationContext(new StringRedisSerializer())
                .value(serializer)
                .hashValue(serializer)
                .build();

        return new ReactiveRedisTemplate<>(connectionFactory, context);
    }
}
