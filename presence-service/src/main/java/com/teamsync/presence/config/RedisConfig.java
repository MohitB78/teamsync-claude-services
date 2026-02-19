package com.teamsync.presence.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    /**
     * Creates a secure ObjectMapper for Redis serialization.
     *
     * SECURITY FIX (Round 8): Replaced LaissezFaireSubTypeValidator with BasicPolymorphicTypeValidator
     * to prevent deserialization attacks. LaissezFaireSubTypeValidator allows ANY class to be
     * deserialized, which could enable Remote Code Execution if an attacker can inject malicious
     * serialized objects into Redis.
     *
     * BasicPolymorphicTypeValidator restricts deserialization to known, safe types within the
     * com.teamsync package hierarchy, preventing arbitrary class instantiation.
     */
    @Bean
    public ObjectMapper redisObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // SECURITY: Use BasicPolymorphicTypeValidator to restrict deserializable types
        // Only allow types from our package hierarchy to prevent RCE attacks
        BasicPolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.teamsync.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.time.")
                .build();

        // SECURITY FIX (Round 11): Changed from NON_FINAL to JAVA_LANG_OBJECT which is more restrictive.
        // NON_FINAL allows type info on all non-final types, which is too permissive.
        // JAVA_LANG_OBJECT only adds type info when the declared type is Object, which is
        // the minimum needed for proper deserialization while limiting attack surface.
        objectMapper.activateDefaultTyping(
                ptv,
                ObjectMapper.DefaultTyping.JAVA_LANG_OBJECT,
                JsonTypeInfo.As.PROPERTY
        );
        return objectMapper;
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory connectionFactory,
            ObjectMapper redisObjectMapper) {

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(redisObjectMapper);

        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }
}
