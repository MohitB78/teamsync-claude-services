package com.teamsync.settings.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka configuration for publishing settings change events.
 * Pre-initializes the producer on startup to avoid memory spike on first message.
 */
@Configuration
@Slf4j
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        // Smaller buffer for lightweight service
        configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 1048576); // 1MB instead of 32MB default
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 8192); // 8KB instead of 16KB default
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 0); // Send immediately, no batching
        configProps.put(ProducerConfig.ACKS_CONFIG, "1"); // Single ack is enough for settings events
        configProps.put(ProducerConfig.RETRIES_CONFIG, 1);
        configProps.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    /**
     * Pre-initialize the Kafka producer after application startup.
     * This avoids memory spikes when the first message is sent.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUpKafkaProducer(ApplicationReadyEvent event) {
        try {
            KafkaTemplate<String, Object> template = event.getApplicationContext()
                    .getBean("kafkaTemplate", KafkaTemplate.class);
            // Flush forces the producer to connect and initialize
            template.flush();
            log.info("Kafka producer pre-initialized successfully");
        } catch (Exception e) {
            log.warn("Failed to pre-initialize Kafka producer (will retry on first message): {}", e.getMessage());
        }
    }
}
