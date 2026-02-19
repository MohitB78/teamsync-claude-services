package com.teamsync.audit.config;

import com.teamsync.common.event.AuditEvent;
import com.teamsync.common.event.SignatureAuditEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka configuration for audit event consumers.
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    /**
     * Consumer factory for AuditEvent.
     */
    @Bean
    public ConsumerFactory<String, AuditEvent> auditEventConsumerFactory() {
        Map<String, Object> props = commonConsumerProperties();

        JsonDeserializer<AuditEvent> deserializer = new JsonDeserializer<>(AuditEvent.class);
        deserializer.setRemoveTypeHeaders(false);
        deserializer.addTrustedPackages("com.teamsync.common.event");
        deserializer.setUseTypeMapperForKey(true);

        return new DefaultKafkaConsumerFactory<>(
                props,
                new ErrorHandlingDeserializer<>(new StringDeserializer()),
                new ErrorHandlingDeserializer<>(deserializer)
        );
    }

    /**
     * Kafka listener container factory for AuditEvent.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, AuditEvent> auditKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, AuditEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(auditEventConsumerFactory());
        factory.setConcurrency(3);
        return factory;
    }

    /**
     * Consumer factory for SignatureAuditEvent.
     */
    @Bean
    public ConsumerFactory<String, SignatureAuditEvent> signatureAuditEventConsumerFactory() {
        Map<String, Object> props = commonConsumerProperties();

        JsonDeserializer<SignatureAuditEvent> deserializer = new JsonDeserializer<>(SignatureAuditEvent.class);
        deserializer.setRemoveTypeHeaders(false);
        deserializer.addTrustedPackages("com.teamsync.common.event");
        deserializer.setUseTypeMapperForKey(true);

        return new DefaultKafkaConsumerFactory<>(
                props,
                new ErrorHandlingDeserializer<>(new StringDeserializer()),
                new ErrorHandlingDeserializer<>(deserializer)
        );
    }

    /**
     * Kafka listener container factory for SignatureAuditEvent.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, SignatureAuditEvent> signatureAuditKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, SignatureAuditEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(signatureAuditEventConsumerFactory());
        factory.setConcurrency(3);
        return factory;
    }

    /**
     * Common consumer properties.
     */
    private Map<String, Object> commonConsumerProperties() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
        return props;
    }
}
