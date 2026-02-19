package com.teamsync.content.event;

import com.teamsync.common.context.TenantContext;
import com.teamsync.content.event.ContentEventPublisher.ContentEvent;
import com.teamsync.content.event.ContentEventPublisher.ContentType;
import com.teamsync.content.event.ContentEventPublisher.EventType;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ContentEventPublisher (Kafka Producer).
 * Tests that events are correctly published to Kafka topics.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ContentEventPublisher (Kafka Producer) Tests")
class ContentEventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private ContentEventPublisher publisher;

    private MockedStatic<TenantContext> tenantContextMock;

    @Captor
    private ArgumentCaptor<String> topicCaptor;

    @Captor
    private ArgumentCaptor<String> keyCaptor;

    @Captor
    private ArgumentCaptor<ContentEvent> eventCaptor;

    private static final String TENANT_ID = "tenant-123";
    private static final String DRIVE_ID = "drive-456";
    private static final String USER_ID = "user-789";
    private static final String CONTENT_EVENTS_TOPIC = "teamsync.content.events";

    @BeforeEach
    void setUp() {
        publisher = new ContentEventPublisher(kafkaTemplate);
        ReflectionTestUtils.setField(publisher, "contentEventsTopic", CONTENT_EVENTS_TOPIC);

        tenantContextMock = mockStatic(TenantContext.class);
        tenantContextMock.when(TenantContext::getUserId).thenReturn(USER_ID);

        // Mock successful send
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition(CONTENT_EVENTS_TOPIC, 0), 0, 0, 0, 0, 0);
        SendResult<String, Object> result = new SendResult<>(
                new ProducerRecord<>(CONTENT_EVENTS_TOPIC, "key", new Object()), metadata);
        future.complete(result);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);
    }

    @AfterEach
    void tearDown() {
        tenantContextMock.close();
    }

    @Nested
    @DisplayName("publishFolderEvent Tests")
    class PublishFolderEventTests {

        @Test
        @DisplayName("Should publish CREATED event for folder")
        void publishFolderEvent_Created_Success() {
            // Given
            String folderId = "folder-001";

            // When
            publisher.publishFolderEvent(folderId, EventType.CREATED, TENANT_ID, DRIVE_ID);

            // Then
            verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), eventCaptor.capture());

            assertThat(topicCaptor.getValue()).isEqualTo(CONTENT_EVENTS_TOPIC);
            assertThat(keyCaptor.getValue()).isEqualTo(folderId);

            ContentEvent event = eventCaptor.getValue();
            assertThat(event.getEventId()).isNotNull();
            assertThat(event.getContentType()).isEqualTo(ContentType.FOLDER);
            assertThat(event.getContentId()).isEqualTo(folderId);
            assertThat(event.getEventType()).isEqualTo(EventType.CREATED);
            assertThat(event.getTenantId()).isEqualTo(TENANT_ID);
            assertThat(event.getDriveId()).isEqualTo(DRIVE_ID);
            assertThat(event.getUserId()).isEqualTo(USER_ID);
            assertThat(event.getTimestamp()).isNotNull();
        }

        @Test
        @DisplayName("Should publish UPDATED event for folder")
        void publishFolderEvent_Updated_Success() {
            // Given
            String folderId = "folder-002";

            // When
            publisher.publishFolderEvent(folderId, EventType.UPDATED, TENANT_ID, DRIVE_ID);

            // Then
            verify(kafkaTemplate).send(eq(CONTENT_EVENTS_TOPIC), eq(folderId), eventCaptor.capture());
            assertThat(eventCaptor.getValue().getEventType()).isEqualTo(EventType.UPDATED);
        }

        @Test
        @DisplayName("Should publish TRASHED event for folder")
        void publishFolderEvent_Trashed_Success() {
            // Given
            String folderId = "folder-003";

            // When
            publisher.publishFolderEvent(folderId, EventType.TRASHED, TENANT_ID, DRIVE_ID);

            // Then
            verify(kafkaTemplate).send(eq(CONTENT_EVENTS_TOPIC), eq(folderId), eventCaptor.capture());
            assertThat(eventCaptor.getValue().getEventType()).isEqualTo(EventType.TRASHED);
        }

        @Test
        @DisplayName("Should publish RESTORED event for folder")
        void publishFolderEvent_Restored_Success() {
            // Given
            String folderId = "folder-004";

            // When
            publisher.publishFolderEvent(folderId, EventType.RESTORED, TENANT_ID, DRIVE_ID);

            // Then
            verify(kafkaTemplate).send(eq(CONTENT_EVENTS_TOPIC), eq(folderId), eventCaptor.capture());
            assertThat(eventCaptor.getValue().getEventType()).isEqualTo(EventType.RESTORED);
        }

        @Test
        @DisplayName("Should publish DELETED event for folder")
        void publishFolderEvent_Deleted_Success() {
            // Given
            String folderId = "folder-005";

            // When
            publisher.publishFolderEvent(folderId, EventType.DELETED, TENANT_ID, DRIVE_ID);

            // Then
            verify(kafkaTemplate).send(eq(CONTENT_EVENTS_TOPIC), eq(folderId), eventCaptor.capture());
            assertThat(eventCaptor.getValue().getEventType()).isEqualTo(EventType.DELETED);
        }

        @Test
        @DisplayName("Should publish MOVED event for folder")
        void publishFolderEvent_Moved_Success() {
            // Given
            String folderId = "folder-006";

            // When
            publisher.publishFolderEvent(folderId, EventType.MOVED, TENANT_ID, DRIVE_ID);

            // Then
            verify(kafkaTemplate).send(eq(CONTENT_EVENTS_TOPIC), eq(folderId), eventCaptor.capture());
            assertThat(eventCaptor.getValue().getEventType()).isEqualTo(EventType.MOVED);
        }

        @Test
        @DisplayName("Should handle Kafka failure gracefully")
        void publishFolderEvent_KafkaFailure_HandlesGracefully() {
            // Given
            String folderId = "folder-007";
            when(kafkaTemplate.send(anyString(), anyString(), any()))
                    .thenThrow(new RuntimeException("Kafka connection failed"));

            // When/Then - Should not throw exception
            assertThatCode(() ->
                    publisher.publishFolderEvent(folderId, EventType.CREATED, TENANT_ID, DRIVE_ID)
            ).doesNotThrowAnyException();

            verify(kafkaTemplate).send(anyString(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("publishDocumentEvent Tests")
    class PublishDocumentEventTests {

        @Test
        @DisplayName("Should publish CREATED event for document")
        void publishDocumentEvent_Created_Success() {
            // Given
            String documentId = "doc-001";

            // When
            publisher.publishDocumentEvent(documentId, EventType.CREATED, TENANT_ID, DRIVE_ID);

            // Then
            verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), eventCaptor.capture());

            assertThat(topicCaptor.getValue()).isEqualTo(CONTENT_EVENTS_TOPIC);
            assertThat(keyCaptor.getValue()).isEqualTo(documentId);

            ContentEvent event = eventCaptor.getValue();
            assertThat(event.getEventId()).isNotNull();
            assertThat(event.getContentType()).isEqualTo(ContentType.DOCUMENT);
            assertThat(event.getContentId()).isEqualTo(documentId);
            assertThat(event.getEventType()).isEqualTo(EventType.CREATED);
            assertThat(event.getTenantId()).isEqualTo(TENANT_ID);
            assertThat(event.getDriveId()).isEqualTo(DRIVE_ID);
            assertThat(event.getUserId()).isEqualTo(USER_ID);
            assertThat(event.getTimestamp()).isNotNull();
        }

        @Test
        @DisplayName("Should publish UPDATED event for document")
        void publishDocumentEvent_Updated_Success() {
            // Given
            String documentId = "doc-002";

            // When
            publisher.publishDocumentEvent(documentId, EventType.UPDATED, TENANT_ID, DRIVE_ID);

            // Then
            verify(kafkaTemplate).send(eq(CONTENT_EVENTS_TOPIC), eq(documentId), eventCaptor.capture());
            assertThat(eventCaptor.getValue().getEventType()).isEqualTo(EventType.UPDATED);
            assertThat(eventCaptor.getValue().getContentType()).isEqualTo(ContentType.DOCUMENT);
        }

        @Test
        @DisplayName("Should publish LOCKED event for document")
        void publishDocumentEvent_Locked_Success() {
            // Given
            String documentId = "doc-003";

            // When
            publisher.publishDocumentEvent(documentId, EventType.LOCKED, TENANT_ID, DRIVE_ID);

            // Then
            verify(kafkaTemplate).send(eq(CONTENT_EVENTS_TOPIC), eq(documentId), eventCaptor.capture());
            assertThat(eventCaptor.getValue().getEventType()).isEqualTo(EventType.LOCKED);
        }

        @Test
        @DisplayName("Should publish UNLOCKED event for document")
        void publishDocumentEvent_Unlocked_Success() {
            // Given
            String documentId = "doc-004";

            // When
            publisher.publishDocumentEvent(documentId, EventType.UNLOCKED, TENANT_ID, DRIVE_ID);

            // Then
            verify(kafkaTemplate).send(eq(CONTENT_EVENTS_TOPIC), eq(documentId), eventCaptor.capture());
            assertThat(eventCaptor.getValue().getEventType()).isEqualTo(EventType.UNLOCKED);
        }

        @Test
        @DisplayName("Should handle Kafka failure gracefully for documents")
        void publishDocumentEvent_KafkaFailure_HandlesGracefully() {
            // Given
            String documentId = "doc-005";
            when(kafkaTemplate.send(anyString(), anyString(), any()))
                    .thenThrow(new RuntimeException("Kafka broker unavailable"));

            // When/Then - Should not throw exception
            assertThatCode(() ->
                    publisher.publishDocumentEvent(documentId, EventType.CREATED, TENANT_ID, DRIVE_ID)
            ).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Event ID and Timestamp Tests")
    class EventMetadataTests {

        @Test
        @DisplayName("Should generate unique event IDs for each event")
        void publishMultipleEvents_GeneratesUniqueEventIds() {
            // When
            publisher.publishFolderEvent("folder-1", EventType.CREATED, TENANT_ID, DRIVE_ID);
            publisher.publishFolderEvent("folder-2", EventType.CREATED, TENANT_ID, DRIVE_ID);
            publisher.publishDocumentEvent("doc-1", EventType.CREATED, TENANT_ID, DRIVE_ID);

            // Then
            verify(kafkaTemplate, times(3)).send(anyString(), anyString(), eventCaptor.capture());

            var events = eventCaptor.getAllValues();
            assertThat(events).hasSize(3);
            assertThat(events.get(0).getEventId()).isNotEqualTo(events.get(1).getEventId());
            assertThat(events.get(1).getEventId()).isNotEqualTo(events.get(2).getEventId());
            assertThat(events.get(0).getEventId()).isNotEqualTo(events.get(2).getEventId());
        }

        @Test
        @DisplayName("Should set timestamp close to current time")
        void publishEvent_SetsCurrentTimestamp() {
            // Given
            long beforePublish = System.currentTimeMillis();

            // When
            publisher.publishDocumentEvent("doc-1", EventType.CREATED, TENANT_ID, DRIVE_ID);

            // Then
            long afterPublish = System.currentTimeMillis();
            verify(kafkaTemplate).send(anyString(), anyString(), eventCaptor.capture());

            long eventTimestamp = eventCaptor.getValue().getTimestamp().toEpochMilli();
            assertThat(eventTimestamp).isBetween(beforePublish, afterPublish);
        }
    }

    @Nested
    @DisplayName("All Event Types Coverage")
    class AllEventTypesTests {

        @Test
        @DisplayName("Should support all defined event types")
        void allEventTypes_AreSupported() {
            String folderId = "folder-test";

            for (EventType eventType : EventType.values()) {
                // When
                publisher.publishFolderEvent(folderId, eventType, TENANT_ID, DRIVE_ID);
            }

            // Then - Should have published events for all event types
            verify(kafkaTemplate, times(EventType.values().length))
                    .send(anyString(), anyString(), any(ContentEvent.class));
        }
    }
}
