package com.teamsync.settings.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson configuration for JSON serialization/deserialization.
 */
@Configuration
public class JacksonConfig {

    /**
     * ObjectMapper bean for JSON serialization/deserialization.
     *
     * <p>Configured with:
     * <ul>
     *   <li>Java 8 date/time support (JavaTimeModule)</li>
     *   <li>Ignore unknown properties for forward compatibility</li>
     *   <li>ISO 8601 date format instead of timestamps</li>
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
