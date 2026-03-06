package com.teamsync.audit.config;

import com.teamsync.audit.dto.AuditEvent;
import com.teamsync.audit.dto.SignatureAuditEvent;
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

        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, AuditEvent.class.getName());
       // props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.teamsync.common.event,com.teamsync.audit.dto");
        props.put(JsonDeserializer.REMOVE_TYPE_INFO_HEADERS, false);

        return new DefaultKafkaConsumerFactory<>(props);
    }



    /*
        it controls number of consumer threads
        * Concurrency ≤ number of topic partitions.
        If topic has 2 partitions and concurrency is 3:
        👉 one thread idle.
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

        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, SignatureAuditEvent.class.getName());
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.teamsync.common.event,com.teamsync.audit.dto");
        props.put(JsonDeserializer.REMOVE_TYPE_INFO_HEADERS, false);
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, true);

        return new DefaultKafkaConsumerFactory<>(props);
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
