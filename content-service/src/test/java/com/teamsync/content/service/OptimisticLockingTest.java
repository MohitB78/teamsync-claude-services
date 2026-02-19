package com.teamsync.content.service;

import com.teamsync.common.context.TenantContext;
import com.teamsync.common.exception.ResourceNotFoundException;
import com.teamsync.common.storage.CloudStorageProvider;
import com.teamsync.common.util.DownloadTokenUtil;
import com.teamsync.content.dto.document.DocumentDTO;
import com.teamsync.content.dto.document.UpdateDocumentRequest;
import com.teamsync.content.mapper.DocumentMapper;
import com.teamsync.content.model.Document;
import com.teamsync.content.model.Document.DocumentStatus;
import com.teamsync.content.repository.DocumentRepository;
import com.teamsync.content.repository.DocumentVersionRepository;
import com.teamsync.content.repository.FolderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for Enhancement 4: Concurrent Edit Detection (Optimistic Locking).
 *
 * These tests verify that the @Version field (entityVersion) on Document
 * provides optimistic locking to detect concurrent edits and prevent
 * data loss from conflicting updates.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Enhancement 4: Optimistic Locking Tests")
class OptimisticLockingTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentVersionRepository versionRepository;

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private CloudStorageProvider storageProvider;

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private DownloadTokenUtil downloadTokenUtil;

    @InjectMocks
    private DocumentService documentService;

    @Captor
    private ArgumentCaptor<Document> documentCaptor;

    private MockedStatic<TenantContext> tenantContextMock;

    private static final String TENANT_ID = "tenant-123";
    private static final String DRIVE_ID = "drive-456";
    private static final String USER_ID = "user-789";
    private static final String DOCUMENT_ID = "doc-001";
    private static final String FOLDER_ID = "folder-001";

    @BeforeEach
    void setUp() {
        tenantContextMock = mockStatic(TenantContext.class);
        tenantContextMock.when(TenantContext::getTenantId).thenReturn(TENANT_ID);
        tenantContextMock.when(TenantContext::getDriveId).thenReturn(DRIVE_ID);
        tenantContextMock.when(TenantContext::getUserId).thenReturn(USER_ID);
    }

    @AfterEach
    void tearDown() {
        tenantContextMock.close();
    }

    private Document createTestDocument(Long entityVersion) {
        return Document.builder()
                .id(DOCUMENT_ID)
                .tenantId(TENANT_ID)
                .driveId(DRIVE_ID)
                .folderId(FOLDER_ID)
                .name("test-document.pdf")
                .description("Test document description")
                .contentType("application/pdf")
                .fileSize(1024L)
                .extension("pdf")
                .storageKey("storage/test-document.pdf")
                .storageBucket("teamsync-documents")
                .currentVersion(1)
                .versionCount(1)
                .entityVersion(entityVersion) // Optimistic locking version
                .isStarred(false)
                .isPinned(false)
                .isLocked(false)
                .ownerId(USER_ID)
                .createdBy(USER_ID)
                .lastModifiedBy(USER_ID)
                .status(DocumentStatus.ACTIVE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .accessedAt(Instant.now())
                .build();
    }

    private DocumentDTO createTestDocumentDTO(Long entityVersion) {
        return DocumentDTO.builder()
                .id(DOCUMENT_ID)
                .tenantId(TENANT_ID)
                .driveId(DRIVE_ID)
                .folderId(FOLDER_ID)
                .name("test-document.pdf")
                .description("Test document description")
                .entityVersion(entityVersion)
                .build();
    }

    @Nested
    @DisplayName("Version Check on Update Tests")
    class VersionCheckOnUpdateTests {

        @Test
        @DisplayName("Should update document when version matches")
        void updateDocument_VersionMatches_Success() {
            // Given
            Long currentVersion = 5L;
            Document existingDocument = createTestDocument(currentVersion);
            UpdateDocumentRequest request = UpdateDocumentRequest.builder()
                    .description("Updated description")
                    .build();

            when(documentRepository.findByIdAndTenantIdAndDriveId(DOCUMENT_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(existingDocument));
            when(documentRepository.save(any(Document.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(documentMapper.toDTO(any(Document.class)))
                    .thenReturn(createTestDocumentDTO(currentVersion + 1));

            // When - pass the expected version that matches
            DocumentDTO result = documentService.updateDocument(DOCUMENT_ID, request, currentVersion);

            // Then
            assertThat(result).isNotNull();
            verify(documentRepository).save(documentCaptor.capture());
            Document savedDocument = documentCaptor.getValue();
            assertThat(savedDocument.getDescription()).isEqualTo("Updated description");
        }

        @Test
        @DisplayName("Should throw OptimisticLockingFailureException when version mismatch")
        void updateDocument_VersionMismatch_ThrowsException() {
            // Given
            Long currentVersion = 5L;
            Long staleVersion = 3L;
            Document existingDocument = createTestDocument(currentVersion);
            UpdateDocumentRequest request = UpdateDocumentRequest.builder()
                    .description("Updated description")
                    .build();

            when(documentRepository.findByIdAndTenantIdAndDriveId(DOCUMENT_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(existingDocument));

            // When/Then - pass a stale version
            assertThatThrownBy(() -> documentService.updateDocument(DOCUMENT_ID, request, staleVersion))
                    .isInstanceOf(OptimisticLockingFailureException.class)
                    .hasMessageContaining("modified by another user");

            // Verify save was never called
            verify(documentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should skip version check when expectedVersion is null")
        void updateDocument_NullExpectedVersion_SkipsCheck() {
            // Given
            Long currentVersion = 5L;
            Document existingDocument = createTestDocument(currentVersion);
            UpdateDocumentRequest request = UpdateDocumentRequest.builder()
                    .description("Updated description")
                    .build();

            when(documentRepository.findByIdAndTenantIdAndDriveId(DOCUMENT_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(existingDocument));
            when(documentRepository.save(any(Document.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(documentMapper.toDTO(any(Document.class)))
                    .thenReturn(createTestDocumentDTO(currentVersion + 1));

            // When - pass null for expectedVersion (skip check)
            DocumentDTO result = documentService.updateDocument(DOCUMENT_ID, request, null);

            // Then - should succeed without version check
            assertThat(result).isNotNull();
            verify(documentRepository).save(any(Document.class));
        }

        @Test
        @DisplayName("Should throw exception when entity version is null but expected version is provided")
        void updateDocument_EntityVersionNull_ThrowsException() {
            // Given
            Document existingDocument = createTestDocument(null); // No version yet
            UpdateDocumentRequest request = UpdateDocumentRequest.builder()
                    .description("Updated description")
                    .build();

            when(documentRepository.findByIdAndTenantIdAndDriveId(DOCUMENT_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(existingDocument));

            // When/Then - entityVersion is null but expectedVersion is 1L, so it's a mismatch
            assertThatThrownBy(() -> documentService.updateDocument(DOCUMENT_ID, request, 1L))
                    .isInstanceOf(OptimisticLockingFailureException.class);

            // Verify save was never called due to version mismatch
            verify(documentRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Version Propagation Tests")
    class VersionPropagationTests {

        @Test
        @DisplayName("Should include entityVersion in DTO")
        void getDocument_ReturnsEntityVersion() {
            // Given
            Long currentVersion = 10L;
            Document document = createTestDocument(currentVersion);
            DocumentDTO expectedDTO = createTestDocumentDTO(currentVersion);

            when(documentRepository.findByIdAndTenantIdAndDriveId(DOCUMENT_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(document));
            when(documentMapper.toDTO(any(Document.class))).thenReturn(expectedDTO);
            when(documentRepository.save(any(Document.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            DocumentDTO result = documentService.getDocument(DOCUMENT_ID);

            // Then
            assertThat(result.getEntityVersion()).isEqualTo(currentVersion);
        }
    }

    @Nested
    @DisplayName("Concurrent Modification Scenarios")
    class ConcurrentModificationScenarios {

        @Test
        @DisplayName("Should detect stale client update after another user modified document")
        void updateDocument_StaleAfterOtherUserEdit_Detected() {
            // Scenario: User A reads document (version 1), User B edits (version becomes 2),
            //           User A tries to save with version 1 - should fail

            // Given - User A reads document
            Long versionWhenRead = 1L;
            Long versionAfterOtherUserEdit = 2L;

            // Simulate document now has updated version due to User B's edit
            Document documentAfterUserBEdit = createTestDocument(versionAfterOtherUserEdit);

            UpdateDocumentRequest userARequest = UpdateDocumentRequest.builder()
                    .description("User A's changes")
                    .build();

            when(documentRepository.findByIdAndTenantIdAndDriveId(DOCUMENT_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(documentAfterUserBEdit));

            // When/Then - User A tries to update with stale version
            assertThatThrownBy(() -> documentService.updateDocument(DOCUMENT_ID, userARequest, versionWhenRead))
                    .isInstanceOf(OptimisticLockingFailureException.class)
                    .hasMessageContaining("modified by another user")
                    .hasMessageContaining("refresh");

            verify(documentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should allow update when no concurrent modification occurred")
        void updateDocument_NoConcurrentModification_Success() {
            // Scenario: User reads document (version 5), edits locally, no one else edits,
            //           User saves with version 5 - should succeed

            Long version = 5L;
            Document document = createTestDocument(version);

            UpdateDocumentRequest request = UpdateDocumentRequest.builder()
                    .description("Safe update")
                    .tags(List.of("safe", "update"))
                    .build();

            when(documentRepository.findByIdAndTenantIdAndDriveId(DOCUMENT_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(document));
            when(documentRepository.save(any(Document.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(documentMapper.toDTO(any(Document.class)))
                    .thenReturn(createTestDocumentDTO(version + 1));

            // When
            DocumentDTO result = documentService.updateDocument(DOCUMENT_ID, request, version);

            // Then
            assertThat(result).isNotNull();
            verify(documentRepository).save(documentCaptor.capture());
            assertThat(documentCaptor.getValue().getDescription()).isEqualTo("Safe update");
        }

        @Test
        @DisplayName("Should fail fast on version mismatch without modifying document")
        void updateDocument_VersionMismatch_NoSideEffects() {
            // Given
            Long currentVersion = 10L;
            Long staleVersion = 5L;
            Document document = createTestDocument(currentVersion);

            UpdateDocumentRequest request = UpdateDocumentRequest.builder()
                    .description("Changes that should not be applied")
                    .build();

            when(documentRepository.findByIdAndTenantIdAndDriveId(DOCUMENT_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(document));

            // When
            try {
                documentService.updateDocument(DOCUMENT_ID, request, staleVersion);
            } catch (OptimisticLockingFailureException ignored) {
                // Expected
            }

            // Then - verify no writes occurred
            verify(documentRepository, never()).save(any());
            verify(versionRepository, never()).save(any());
            verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Should handle version 0 correctly")
        void updateDocument_VersionZero_HandledCorrectly() {
            // Given
            Long version = 0L;
            Document document = createTestDocument(version);

            UpdateDocumentRequest request = UpdateDocumentRequest.builder()
                    .description("First edit")
                    .build();

            when(documentRepository.findByIdAndTenantIdAndDriveId(DOCUMENT_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(document));
            when(documentRepository.save(any(Document.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(documentMapper.toDTO(any(Document.class)))
                    .thenReturn(createTestDocumentDTO(version + 1));

            // When
            DocumentDTO result = documentService.updateDocument(DOCUMENT_ID, request, 0L);

            // Then
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should handle very large version numbers")
        void updateDocument_LargeVersionNumber_HandledCorrectly() {
            // Given
            Long version = Long.MAX_VALUE - 1;
            Document document = createTestDocument(version);

            UpdateDocumentRequest request = UpdateDocumentRequest.builder()
                    .description("Update with large version")
                    .build();

            when(documentRepository.findByIdAndTenantIdAndDriveId(DOCUMENT_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(document));
            when(documentRepository.save(any(Document.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(documentMapper.toDTO(any(Document.class)))
                    .thenReturn(createTestDocumentDTO(version + 1));

            // When
            DocumentDTO result = documentService.updateDocument(DOCUMENT_ID, request, version);

            // Then
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should allow star toggle via updateDocument without version check when expectedVersion is null")
        void starDocument_ViaUpdateDocument_NoVersionCheck() {
            // Star/pin operations go through updateDocument with null expectedVersion
            // This allows simple toggles without requiring version tracking

            Long version = 5L;
            Document document = createTestDocument(version);
            document.setIsStarred(false);

            UpdateDocumentRequest request = UpdateDocumentRequest.builder()
                    .isStarred(true)
                    .build();

            when(documentRepository.findByIdAndTenantIdAndDriveId(DOCUMENT_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(document));
            when(documentRepository.save(any(Document.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(documentMapper.toDTO(any(Document.class)))
                    .thenReturn(createTestDocumentDTO(version));

            // When - pass null for expectedVersion to skip version check
            DocumentDTO result = documentService.updateDocument(DOCUMENT_ID, request, null);

            // Then - should succeed without version check
            assertThat(result).isNotNull();
            verify(documentRepository).save(documentCaptor.capture());
            assertThat(documentCaptor.getValue().getIsStarred()).isTrue();
        }
    }
}
