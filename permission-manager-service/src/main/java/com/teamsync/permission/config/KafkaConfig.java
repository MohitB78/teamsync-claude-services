package com.teamsync.permission.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka configuration for event-driven permission updates.
 *
 * SECURITY FIX (Round 13 #11): Added explicit consumer configuration with:
 * - Strict trusted packages (only com.teamsync.* and java.util)
 * - Disabled type info headers to prevent type confusion attacks
 * - Explicit error handling to prevent unbounded retries
 */
@Configuration
@EnableKafka
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:permission-manager-service}")
    private String groupId;

    /**
     * SECURITY FIX (Round 13 #11): Consumer factory with secure deserialization.
     *
     * Security measures:
     * - TRUSTED_PACKAGES limited to com.teamsync.* and java.util only
     * - USE_TYPE_INFO_HEADERS disabled to prevent type confusion attacks
     * - MAX_POLL_RECORDS limited to prevent memory exhaustion
     * - AUTO_COMMIT disabled for reliable processing
     */
    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);

        // SECURITY FIX (Round 13 #11): Strict trusted packages - prevents deserialization attacks
        // Only allow TeamSync classes and standard Java collections
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.teamsync.common.event,com.teamsync.permission.event,java.util,java.time");

        // SECURITY FIX (Round 13 #12): Disable type info headers to prevent type confusion attacks
        // This forces deserializer to use the configured default type, not attacker-controlled headers
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, "java.util.HashMap");

        // SECURITY: Disable auto-commit for reliable at-least-once processing
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        // SECURITY: Limit poll records to prevent memory exhaustion
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10);

        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Kafka listener container factory with error handling.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);

        // SECURITY FIX (Round 13 #13): Configure bounded error handler
        // Prevents infinite retry loops which could be exploited for resource exhaustion
        factory.setCommonErrorHandler(new DefaultErrorHandler(
                new FixedBackOff(1000L, 3L)  // 3 retries with 1 second delay, then fail
        ));

        return factory;
    }
}
