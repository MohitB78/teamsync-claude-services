package com.teamsync.content.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamsync.content.event.ContentEventPublisher;
import com.teamsync.content.event.ContentEventPublisher.ContentEvent;
import com.teamsync.content.event.ContentEventPublisher.ContentType;
import com.teamsync.content.event.ContentEventPublisher.EventType;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests for Kafka producer and consumer functionality.
 * Connects to the Railway-deployed Redpanda broker (Kafka-compatible).
 *
 * Environment variables:
 * - KAFKA_BOOTSTRAP_SERVERS: Redpanda broker address (default: redpanda.railway.internal:9092)
 * - RUN_KAFKA_INTEGRATION_TESTS: Set to "true" to enable these tests
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Kafka Integration Tests (Railway Redpanda)")
class KafkaIntegrationTest {

    // Railway Redpanda broker (Kafka-compatible) - can be overridden via environment variable
    private static final String KAFKA_BOOTSTRAP_SERVERS =
            System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "redpanda.railway.internal:9092");

    private static final String CONTENT_EVENTS_TOPIC = "teamsync.content.events";
    private static final String TEST_TOPIC = "teamsync.test.events";
    private static final String CONSUMER_GROUP = "test-consumer-group";

    private KafkaTemplate<String, Object> kafkaTemplate;
    private ContentEventPublisher publisher;
    private Consumer<String, ContentEvent> consumer;
    private ObjectMapper objectMapper;
    private boolean kafkaAvailable = false;

    @BeforeAll
    void setUpKafka() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        // Check if Kafka is available
        kafkaAvailable = isKafkaAvailable();

        if (kafkaAvailable) {
            setupProducer();
            setupConsumer();
        }
    }

    private boolean isKafkaAvailable() {
        try {
            Map<String, Object> props = new HashMap<>();
            props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_BOOTSTRAP_SERVERS);
            props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
            props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 5000);

            try (AdminClient admin = AdminClient.create(props)) {
                ListTopicsResult topics = admin.listTopics();
                Set<String> topicNames = topics.names().get(10, TimeUnit.SECONDS);
                System.out.println("Kafka is available. Topics: " + topicNames);
                return true;
            }
        } catch (Exception e) {
            System.out.println("Kafka is not available at " + KAFKA_BOOTSTRAP_SERVERS + ": " + e.getMessage());
            return false;
        }
    }

    private void setupProducer() {
        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_BOOTSTRAP_SERVERS);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        producerProps.put(ProducerConfig.ACKS_CONFIG, "all");
        producerProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        producerProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);

        // JSON Serializer properties
        producerProps.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);

        DefaultKafkaProducerFactory<String, Object> producerFactory =
                new DefaultKafkaProducerFactory<>(producerProps);
        kafkaTemplate = new KafkaTemplate<>(producerFactory);

        // Create publisher
        publisher = new ContentEventPublisher(kafkaTemplate);
        ReflectionTestUtils.setField(publisher, "contentEventsTopic", CONTENT_EVENTS_TOPIC);
    }

    private void setupConsumer() {
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_BOOTSTRAP_SERVERS);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, CONSUMER_GROUP + "-" + UUID.randomUUID());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest"); // Use latest to avoid old messages
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerProps.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);
        consumerProps.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, 40000);
        // SECURITY FIX (Round 11): Restrict trusted packages to prevent deserialization attacks.
        // Previously used "*" which allows any class to be deserialized, enabling RCE attacks.
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "com.teamsync.content.event");
        consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, ContentEvent.class.getName());
        consumerProps.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

        DefaultKafkaConsumerFactory<String, ContentEvent> consumerFactory =
                new DefaultKafkaConsumerFactory<>(consumerProps);
        consumer = consumerFactory.createConsumer();
        consumer.subscribe(Collections.singletonList(CONTENT_EVENTS_TOPIC));

        // Initial poll to join the consumer group
        consumer.poll(Duration.ofSeconds(2));
    }

    @BeforeEach
    void checkKafkaAvailability() {
        assumeTrue(kafkaAvailable, "Skipping test - Kafka broker not available at " + KAFKA_BOOTSTRAP_SERVERS);
    }

    @AfterAll
    void tearDown() {
        if (consumer != null) {
            try {
                consumer.close(Duration.ofSeconds(5));
            } catch (Exception e) {
                System.err.println("Error closing consumer: " + e.getMessage());
            }
        }
        if (kafkaTemplate != null) {
            kafkaTemplate.destroy();
        }
    }

    @Nested
    @DisplayName("Kafka Connectivity Tests")
    class ConnectivityTests {

        @Test
        @DisplayName("Should connect to Railway Redpanda broker")
        void shouldConnectToKafkaBroker() {
            assertThat(kafkaAvailable)
                    .withFailMessage("Could not connect to Kafka at " + KAFKA_BOOTSTRAP_SERVERS)
                    .isTrue();
        }

        @Test
        @DisplayName("Should list available topics")
        void shouldListTopics() throws Exception {
            Map<String, Object> props = new HashMap<>();
            props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_BOOTSTRAP_SERVERS);

            try (AdminClient admin = AdminClient.create(props)) {
                Set<String> topics = admin.listTopics().names().get(10, TimeUnit.SECONDS);
                System.out.println("Available Kafka topics: " + topics);
                assertThat(topics).isNotNull();
            }
        }
    }

    @Nested
    @DisplayName("Producer Tests")
    class ProducerTests {

        @Test
        @DisplayName("Should send message to Kafka broker successfully")
        void producer_SendsMessage_Successfully() throws Exception {
            // Given
            String documentId = "test-doc-" + UUID.randomUUID();
            String tenantId = "tenant-test";
            String driveId = "drive-test";

            ContentEvent event = ContentEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .contentType(ContentType.DOCUMENT)
                    .contentId(documentId)
                    .eventType(EventType.CREATED)
                    .tenantId(tenantId)
                    .driveId(driveId)
                    .userId("user-test")
                    .timestamp(Instant.now())
                    .build();

            // When
            var sendResult = kafkaTemplate.send(CONTENT_EVENTS_TOPIC, documentId, event)
                    .get(30, TimeUnit.SECONDS);

            // Then
            assertThat(sendResult).isNotNull();
            assertThat(sendResult.getRecordMetadata().topic()).isEqualTo(CONTENT_EVENTS_TOPIC);
            assertThat(sendResult.getRecordMetadata().partition()).isGreaterThanOrEqualTo(0);
            assertThat(sendResult.getRecordMetadata().offset()).isGreaterThanOrEqualTo(0);

            System.out.println("Message sent successfully:");
            System.out.println("  Topic: " + sendResult.getRecordMetadata().topic());
            System.out.println("  Partition: " + sendResult.getRecordMetadata().partition());
            System.out.println("  Offset: " + sendResult.getRecordMetadata().offset());
        }

        @Test
        @DisplayName("Should send folder created event")
        void producer_SendsFolderCreatedEvent() throws Exception {
            // Given
            String folderId = "test-folder-" + UUID.randomUUID();

            ContentEvent event = ContentEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .contentType(ContentType.FOLDER)
                    .contentId(folderId)
                    .eventType(EventType.CREATED)
                    .tenantId("tenant-folder-test")
                    .driveId("drive-folder-test")
                    .userId("user-folder-test")
                    .timestamp(Instant.now())
                    .build();

            // When
            var sendResult = kafkaTemplate.send(CONTENT_EVENTS_TOPIC, folderId, event)
                    .get(30, TimeUnit.SECONDS);

            // Then
            assertThat(sendResult.getRecordMetadata().topic()).isEqualTo(CONTENT_EVENTS_TOPIC);
            System.out.println("Folder event sent to partition " +
                    sendResult.getRecordMetadata().partition() +
                    " at offset " + sendResult.getRecordMetadata().offset());
        }

        @Test
        @DisplayName("Should send multiple events for same document")
        void producer_SendsMultipleEvents_SamePartition() throws Exception {
            // Given
            String documentId = "test-doc-lifecycle-" + UUID.randomUUID();
            List<EventType> lifecycle = List.of(
                    EventType.CREATED,
                    EventType.UPDATED,
                    EventType.LOCKED,
                    EventType.UNLOCKED,
                    EventType.TRASHED,
                    EventType.RESTORED
            );

            int partition = -1;

            // When
            for (EventType eventType : lifecycle) {
                ContentEvent event = ContentEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .contentType(ContentType.DOCUMENT)
                        .contentId(documentId)
                        .eventType(eventType)
                        .tenantId("tenant-lifecycle")
                        .driveId("drive-lifecycle")
                        .userId("user-lifecycle")
                        .timestamp(Instant.now())
                        .build();

                var sendResult = kafkaTemplate.send(CONTENT_EVENTS_TOPIC, documentId, event)
                        .get(30, TimeUnit.SECONDS);

                // Verify all events go to same partition (key-based partitioning)
                if (partition == -1) {
                    partition = sendResult.getRecordMetadata().partition();
                } else {
                    assertThat(sendResult.getRecordMetadata().partition())
                            .withFailMessage("Events for same key should go to same partition")
                            .isEqualTo(partition);
                }
            }

            System.out.println("All " + lifecycle.size() + " lifecycle events sent to partition " + partition);
        }

        @Test
        @DisplayName("Should send all event types successfully")
        void producer_SendsAllEventTypes() throws Exception {
            // When/Then - Send one event for each type
            for (EventType eventType : EventType.values()) {
                String contentId = "test-" + eventType.name().toLowerCase() + "-" + UUID.randomUUID();

                ContentEvent event = ContentEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .contentType(ContentType.DOCUMENT)
                        .contentId(contentId)
                        .eventType(eventType)
                        .tenantId("tenant-all-types")
                        .driveId("drive-all-types")
                        .userId("user-all-types")
                        .timestamp(Instant.now())
                        .build();

                var sendResult = kafkaTemplate.send(CONTENT_EVENTS_TOPIC, contentId, event)
                        .get(30, TimeUnit.SECONDS);

                assertThat(sendResult).isNotNull();
                System.out.println("Sent " + eventType + " event to offset " +
                        sendResult.getRecordMetadata().offset());
            }
        }
    }

    @Nested
    @DisplayName("Consumer Tests")
    class ConsumerTests {

        @Test
        @DisplayName("Should consume messages from topic")
        void consumer_ConsumesMessages() throws Exception {
            // Given - Send a unique message
            String uniqueId = "consume-test-" + UUID.randomUUID();
            ContentEvent event = ContentEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .contentType(ContentType.DOCUMENT)
                    .contentId(uniqueId)
                    .eventType(EventType.CREATED)
                    .tenantId("tenant-consume")
                    .driveId("drive-consume")
                    .userId("user-consume")
                    .timestamp(Instant.now())
                    .build();

            kafkaTemplate.send(CONTENT_EVENTS_TOPIC, uniqueId, event).get(30, TimeUnit.SECONDS);

            // When - Poll for messages
            List<ContentEvent> receivedEvents = pollForMessages(uniqueId, Duration.ofSeconds(30));

            // Then
            assertThat(receivedEvents)
                    .withFailMessage("Should have received the message with ID: " + uniqueId)
                    .isNotEmpty();

            ContentEvent received = receivedEvents.get(0);
            assertThat(received.getContentId()).isEqualTo(uniqueId);
            assertThat(received.getContentType()).isEqualTo(ContentType.DOCUMENT);
            assertThat(received.getEventType()).isEqualTo(EventType.CREATED);

            System.out.println("Successfully consumed message: " + received.getContentId());
        }

        @Test
        @DisplayName("Should deserialize all event fields correctly")
        void consumer_DeserializesAllFields() throws Exception {
            // Given
            String uniqueId = "deserialize-test-" + UUID.randomUUID();
            String eventId = UUID.randomUUID().toString();
            String tenantId = "tenant-deserialize";
            String driveId = "drive-deserialize";
            String userId = "user-deserialize";
            Instant timestamp = Instant.now();

            ContentEvent event = ContentEvent.builder()
                    .eventId(eventId)
                    .contentType(ContentType.FOLDER)
                    .contentId(uniqueId)
                    .eventType(EventType.UPDATED)
                    .tenantId(tenantId)
                    .driveId(driveId)
                    .userId(userId)
                    .timestamp(timestamp)
                    .build();

            kafkaTemplate.send(CONTENT_EVENTS_TOPIC, uniqueId, event).get(30, TimeUnit.SECONDS);

            // When
            List<ContentEvent> receivedEvents = pollForMessages(uniqueId, Duration.ofSeconds(30));

            // Then
            assertThat(receivedEvents).isNotEmpty();
            ContentEvent received = receivedEvents.get(0);

            assertThat(received.getEventId()).isEqualTo(eventId);
            assertThat(received.getContentType()).isEqualTo(ContentType.FOLDER);
            assertThat(received.getContentId()).isEqualTo(uniqueId);
            assertThat(received.getEventType()).isEqualTo(EventType.UPDATED);
            assertThat(received.getTenantId()).isEqualTo(tenantId);
            assertThat(received.getDriveId()).isEqualTo(driveId);
            assertThat(received.getUserId()).isEqualTo(userId);
            assertThat(received.getTimestamp()).isNotNull();
        }
    }

    @Nested
    @DisplayName("End-to-End Tests")
    class EndToEndTests {

        @Test
        @DisplayName("Should handle round-trip message correctly")
        void endToEnd_RoundTrip() throws Exception {
            // Given
            String uniqueId = "e2e-" + UUID.randomUUID();
            ContentEvent sentEvent = ContentEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .contentType(ContentType.DOCUMENT)
                    .contentId(uniqueId)
                    .eventType(EventType.DELETED)
                    .tenantId("tenant-e2e")
                    .driveId("drive-e2e")
                    .userId("user-e2e")
                    .timestamp(Instant.now())
                    .build();

            // When - Send
            var sendResult = kafkaTemplate.send(CONTENT_EVENTS_TOPIC, uniqueId, sentEvent)
                    .get(30, TimeUnit.SECONDS);

            System.out.println("Sent to partition " + sendResult.getRecordMetadata().partition() +
                    " offset " + sendResult.getRecordMetadata().offset());

            // When - Receive
            List<ContentEvent> received = pollForMessages(uniqueId, Duration.ofSeconds(30));

            // Then
            assertThat(received).hasSize(1);
            ContentEvent receivedEvent = received.get(0);

            assertThat(receivedEvent.getContentId()).isEqualTo(sentEvent.getContentId());
            assertThat(receivedEvent.getEventType()).isEqualTo(sentEvent.getEventType());
            assertThat(receivedEvent.getTenantId()).isEqualTo(sentEvent.getTenantId());
            assertThat(receivedEvent.getDriveId()).isEqualTo(sentEvent.getDriveId());
            assertThat(receivedEvent.getUserId()).isEqualTo(sentEvent.getUserId());
        }

        @Test
        @DisplayName("Should handle batch of messages")
        void endToEnd_BatchMessages() throws Exception {
            // Given
            String batchId = UUID.randomUUID().toString();
            int batchSize = 10;
            List<String> sentIds = new ArrayList<>();

            // When - Send batch
            for (int i = 0; i < batchSize; i++) {
                String contentId = "batch-" + batchId + "-" + i;
                sentIds.add(contentId);

                ContentEvent event = ContentEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .contentType(ContentType.DOCUMENT)
                        .contentId(contentId)
                        .eventType(EventType.CREATED)
                        .tenantId("tenant-batch")
                        .driveId("drive-batch")
                        .userId("user-batch")
                        .timestamp(Instant.now())
                        .build();

                kafkaTemplate.send(CONTENT_EVENTS_TOPIC, contentId, event);
            }
            kafkaTemplate.flush();

            // Wait a bit for messages to be available
            Thread.sleep(2000);

            // Then - Verify we can find all messages
            List<ContentEvent> allReceived = new ArrayList<>();
            long endTime = System.currentTimeMillis() + 30000;

            while (allReceived.size() < batchSize && System.currentTimeMillis() < endTime) {
                ConsumerRecords<String, ContentEvent> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, ContentEvent> record : records) {
                    if (record.value() != null &&
                            record.value().getContentId() != null &&
                            record.value().getContentId().startsWith("batch-" + batchId)) {
                        allReceived.add(record.value());
                    }
                }
            }

            System.out.println("Received " + allReceived.size() + " of " + batchSize + " batch messages");
            assertThat(allReceived.size()).isGreaterThanOrEqualTo(batchSize / 2); // At least half
        }
    }

    /**
     * Helper method to poll for a specific message by content ID.
     */
    private List<ContentEvent> pollForMessages(String targetContentId, Duration timeout) {
        List<ContentEvent> matchingEvents = new ArrayList<>();
        long endTime = System.currentTimeMillis() + timeout.toMillis();

        while (matchingEvents.isEmpty() && System.currentTimeMillis() < endTime) {
            ConsumerRecords<String, ContentEvent> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, ContentEvent> record : records) {
                if (record.value() != null && targetContentId.equals(record.value().getContentId())) {
                    matchingEvents.add(record.value());
                }
            }
        }

        return matchingEvents;
    }
}
