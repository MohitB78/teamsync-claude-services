package com.teamsync.content.service;

import com.teamsync.common.context.TenantContext;
import com.teamsync.common.exception.AccessDeniedException;
import com.teamsync.common.exception.ResourceNotFoundException;
import com.teamsync.common.permission.PermissionService;
import com.teamsync.common.storage.CloudStorageProvider;
import com.teamsync.common.util.DownloadTokenUtil;
import com.teamsync.content.client.StorageServiceClient;
import com.teamsync.content.dto.document.DocumentDTO;
import com.teamsync.content.mapper.DocumentMapper;
import com.teamsync.content.model.Document;
import com.teamsync.content.model.Document.DocumentStatus;
import com.teamsync.content.model.DocumentVersion;
import com.teamsync.content.repository.DocumentRepository;
import com.teamsync.content.repository.DocumentVersionRepository;
import com.teamsync.content.repository.FolderRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for Two-Stage Deletion functionality.
 * Covers:
 * - Stage 1: Soft delete (move to trash)
 * - Stage 2: Permanent delete (remove from storage)
 * - Restore from trash
 * - Folder count and size updates
 * - Locked document handling
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Two-Stage Delete Tests")
class TwoStageDeleteTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentVersionRepository versionRepository;

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private CloudStorageProvider storageProvider;

    @Mock
    private StorageServiceClient storageServiceClient;

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private PermissionService permissionService;

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
    private static final String STORAGE_BUCKET = "teamsync-documents";
    private static final String STORAGE_KEY = "tenant-123/drive-456/12345678/document.pdf";

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

    private Document createTestDocument() {
        return Document.builder()
                .id(DOCUMENT_ID)
                .tenantId(TENANT_ID)
                .driveId(DRIVE_ID)
                .folderId(FOLDER_ID)
                .name("test-document.pdf")
                .description("Test document")
                .contentType("application/pdf")
                .fileSize(1024L)
                .extension("pdf")
                .storageKey(STORAGE_KEY)
                .storageBucket(STORAGE_BUCKET)
                .currentVersion(1)
                .versionCount(1)
                .isStarred(false)
                .isPinned(false)
                .isLocked(false)
                .ownerId(USER_ID)
                .createdBy(USER_ID)
                .lastModifiedBy(USER_ID)
                .status(DocumentStatus.ACTIVE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private DocumentVersion createTestVersion(String documentId, int versionNumber) {
        return DocumentVersion.builder()
                .id(UUID.randomUUID().toString())
                .documentId(documentId)
                .tenantId(TENANT_ID)
                .versionNumber(versionNumber)
                .storageKey(STORAGE_KEY + ".v" + versionNumber)
                .storageBucket(STORAGE_BUCKET)
                .fileSize(1024L)
                .contentType("application/pdf")
                .createdBy(USER_ID)
                .createdAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("Stage 1: Soft Delete (Trash) Tests")
    class SoftDeleteTests {

        @Test
        @DisplayName("Should move document to trash (soft delete)")
        void trashDocument_Success() {
            // Given
            Document document = createTestDocument();

            when(documentRepository.findByIdAndTenantIdAndDriveId(DOCUMENT_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(document));
            when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            documentService.trashDocument(DOCUMENT_ID);

            // Then
            verify(documentRepository).save(documentCaptor.capture());
            Document trashedDocument = documentCaptor.getValue();

            assertThat(trashedDocument.getStatus()).isEqualTo(DocumentStatus.TRASHED);
            assertThat(trashedDocument.getLastModifiedBy()).isEqualTo(USER_ID);
            assertThat(trashedDocument.getUpdatedAt()).isNotNull();

            // Verify storage is NOT deleted yet (soft delete)
            verify(storageProvider, never()).delete(anyString(), anyString());
            verify(storageServiceClient, never()).deleteFile(anyString(), anyString());
        }

        @Test
        @DisplayName("Should update folder counts when trashing document")
        void trashDocument_UpdatesFolderCounts() {
            // Given
            Document document = createTestDocument();
            document.setFileSize(2048L);

            when(documentRepository.findByIdAndTenantIdAndDriveId(DOCUMENT_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(document));
            when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            documentService.trashDocument(DOCUMENT_ID);

            // Then
            verify(folderRepository).incrementDocumentCount(FOLDER_ID, TENANT_ID, DRIVE_ID, -1);
            verify(folderRepository).incrementTotalSize(FOLDER_ID, TENANT_ID, DRIVE_ID, -2048L);
        }

        @Test
        @DisplayName("Should not update folder counts for root documents")
        void trashDocument_RootDocument_NoFolderUpdate() {
            // Given
            Document document = createTestDocument();
            document.setFolderId(null); // Root document

            when(documentRepository.findByIdAndTenantIdAndDriveId(DOCUMENT_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(document));
            when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            documentService.trashDocument(DOCUMENT_ID);

            // Then
            verify(folderRepository, never()).incrementDocumentCount(anyString(), anyString(), anyString(), anyInt());
            verify(folderRepository, never()).incrementTotalSize(anyString(), anyString(), anyString(), anyLong());
        }

        @Test
        @DisplayName("Should fail to trash locked document by another user")
        void trashDocument_LockedByAnotherUser_ThrowsException() {
            // Given
            Document document = createTestDocument();
            document.setIsLocked(true);
            document.setLockedBy("other-user");

            when(documentRepository.findByIdAndTenantIdAndDriveId(DOCUMENT_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(document));

            // When/Then
            assertThatThrownBy(() -> documentService.trashDocument(DOCUMENT_ID))
                    .isInstanceOf(AccessDeniedException.class);

            verify(documentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should allow owner to trash locked document")
        void trashDocument_LockedByOwner_Success() {
            // Given
            Document document = createTestDocument();
            document.setIsLocked(true);
            document.setLockedBy(USER_ID); // Locked by current user

            when(documentRepository.findByIdAndTenantIdAndDriveId(DOCUMENT_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(document));
            when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            documentService.trashDocument(DOCUMENT_ID);

            // Then
            verify(documentRepository).save(documentCaptor.capture());
            assertThat(documentCaptor.getValue().getStatus()).isEqualTo(DocumentStatus.TRASHED);
        }

        @Test
        @DisplayName("Should publish trash event to Kafka")
        void trashDocument_PublishesEvent() {
            // Given
            Document document = createTestDocument();

            when(documentRepository.findByIdAndTenantIdAndDriveId(DOCUMENT_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(document));
            when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            documentService.trashDocument(DOCUMENT_ID);

            // Then
            verify(kafkaTemplate).send(eq("teamsync.content.events"), eq(DOCUMENT_ID), any());
        }
    }

    @Nested
    @DisplayName("Stage 2: Permanent Delete Tests")
    class PermanentDeleteTests {

        @Test
        @DisplayName("Should permanently delete document and all versions")
        void deleteDocument_RemovesFromStorage() {
            // Given
            Document document = createTestDocument();
            List<DocumentVersion> versions = List.of(
                    createTestVersion(DOCUMENT_ID, 1),
                    createTestVersion(DOCUMENT_ID, 2)
            );

            when(documentRepository.findByIdAndTenantIdAndDriveId(DOCUMENT_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(document));
            when(versionRepository.findByDocumentIdOrderByVersionNumberDesc(eq(DOCUMENT_ID), any()))
                    .thenReturn(versions);

            // When
            documentService.deleteDocument(DOCUMENT_ID);

            // Then
            // Verify each version is deleted from storage
            verify(storageServiceClient, times(2)).deleteFile(eq(STORAGE_BUCKET), anyString());

            // Verify version records are deleted
            verify(versionRepository).deleteByDocumentId(DOCUMENT_ID);

            // Verify document record is deleted
            verify(documentRepository).delete(document);
        }

        @Test
        @DisplayName("Should continue deletion even if storage delete fails")
        void deleteDocument_StorageFailure_ContinuesDeleting() {
            // Given
            Document document = createTestDocument();
            List<DocumentVersion> versions = List.of(
                    createTestVersion(DOCUMENT_ID, 1),
                    createTestVersion(DOCUMENT_ID, 2)
            );

            when(documentRepository.findByIdAndTenantIdAndDriveId(DOCUMENT_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(document));
            when(versionRepository.findByDocumentIdOrderByVersionNumberDesc(eq(DOCUMENT_ID), any()))
                    .thenReturn(versions);
            // First storage delete fails, second succeeds
            when(storageServiceClient.deleteFile(anyString(), anyString()))
                    .thenThrow(new RuntimeException("Storage error"))
                    .thenReturn(null);

            // When
            documentService.deleteDocument(DOCUMENT_ID);

            // Then - should still delete records even if storage fails
            verify(versionRepository).deleteByDocumentId(DOCUMENT_ID);
            verify(documentRepository).delete(document);
        }

        @Test
        @DisplayName("Should handle document with no versions")
        void deleteDocument_NoVersions() {
            // Given
            Document document = createTestDocument();

            when(documentRepository.findByIdAndTenantIdAndDriveId(DOCUMENT_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(document));
            when(versionRepository.findByDocumentIdOrderByVersionNumberDesc(eq(DOCUMENT_ID), any()))
                    .thenReturn(new ArrayList<>());

            // When
            documentService.deleteDocument(DOCUMENT_ID);

            // Then
            verify(storageServiceClient, never()).deleteFile(anyString(), anyString());
            verify(versionRepository).deleteByDocumentId(DOCUMENT_ID);
            verify(documentRepository).delete(document);
        }

        @Test
        @DisplayName("Should throw exception when document not found")
        void deleteDocument_NotFound_ThrowsException() {
            // Given
            when(documentRepository.findByIdAndTenantIdAndDriveId(DOCUMENT_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> documentService.deleteDocument(DOCUMENT_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Document not found");
        }

        @Test
        @DisplayName("Should publish delete event to Kafka")
        void deleteDocument_PublishesEvent() {
            // Given
            Document document = createTestDocument();

            when(documentRepository.findByIdAndTenantIdAndDriveId(DOCUMENT_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(document));
            when(versionRepository.findByDocumentIdOrderByVersionNumberDesc(eq(DOCUMENT_ID), any()))
                    .thenReturn(new ArrayList<>());

            // When
            documentService.deleteDocument(DOCUMENT_ID);

            // Then
            verify(kafkaTemplate).send(eq("teamsync.content.events"), eq(DOCUMENT_ID), any());
        }
    }

    @Nested
    @DisplayName("Restore from Trash Tests")
    class RestoreFromTrashTests {

        @Test
        @DisplayName("Should restore document from trash")
        void restoreDocument_Success() {
            // Given
            Document trashedDocument = createTestDocument();
            trashedDocument.setStatus(DocumentStatus.TRASHED);

            DocumentDTO expectedDTO = DocumentDTO.builder()
                    .id(DOCUMENT_ID)
                    .name("test-document.pdf")
                    .build();

            when(documentRepository.findByIdAndTenantId(DOCUMENT_ID, TENANT_ID))
                    .thenReturn(Optional.of(trashedDocument));
            when(documentRepository.existsByTenantIdAndDriveIdAndFolderIdAndNameAndStatusNot(
                    anyString(), anyString(), anyString(), anyString(), any()))
                    .thenReturn(false); // No name conflict
            when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));
            when(documentMapper.toDTO(any(Document.class))).thenReturn(expectedDTO);

            // When
            DocumentDTO result = documentService.restoreDocument(DOCUMENT_ID);

            // Then
            assertThat(result).isNotNull();
            verify(documentRepository).save(documentCaptor.capture());
            Document restoredDocument = documentCaptor.getValue();

            assertThat(restoredDocument.getStatus()).isEqualTo(DocumentStatus.ACTIVE);
            assertThat(restoredDocument.getLastModifiedBy()).isEqualTo(USER_ID);
        }

        @Test
        @DisplayName("Should update folder counts when restoring")
        void restoreDocument_UpdatesFolderCounts() {
            // Given
            Document trashedDocument = createTestDocument();
            trashedDocument.setStatus(DocumentStatus.TRASHED);
            trashedDocument.setFileSize(3072L);

            when(documentRepository.findByIdAndTenantId(DOCUMENT_ID, TENANT_ID))
                    .thenReturn(Optional.of(trashedDocument));
            when(documentRepository.existsByTenantIdAndDriveIdAndFolderIdAndNameAndStatusNot(
                    anyString(), anyString(), anyString(), anyString(), any()))
                    .thenReturn(false);
            when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));
            when(documentMapper.toDTO(any(Document.class))).thenReturn(DocumentDTO.builder().build());

            // When
            documentService.restoreDocument(DOCUMENT_ID);

            // Then
            verify(folderRepository).incrementDocumentCount(FOLDER_ID, TENANT_ID, DRIVE_ID, 1);
            verify(folderRepository).incrementTotalSize(FOLDER_ID, TENANT_ID, DRIVE_ID, 3072L);
        }

        @Test
        @DisplayName("Should rename document on name conflict")
        void restoreDocument_NameConflict_RenamesDocument() {
            // Given
            Document trashedDocument = createTestDocument();
            trashedDocument.setStatus(DocumentStatus.TRASHED);

            when(documentRepository.findByIdAndTenantId(DOCUMENT_ID, TENANT_ID))
                    .thenReturn(Optional.of(trashedDocument));
            when(documentRepository.existsByTenantIdAndDriveIdAndFolderIdAndNameAndStatusNot(
                    anyString(), anyString(), anyString(), anyString(), any()))
                    .thenReturn(true); // Name conflict exists
            when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));
            when(documentMapper.toDTO(any(Document.class))).thenReturn(DocumentDTO.builder().build());

            // When
            documentService.restoreDocument(DOCUMENT_ID);

            // Then
            verify(documentRepository).save(documentCaptor.capture());
            Document restoredDocument = documentCaptor.getValue();

            assertThat(restoredDocument.getName()).contains("_restored_");
            assertThat(restoredDocument.getName()).isNotEqualTo("test-document.pdf");
        }

        @Test
        @DisplayName("Should fail when document is not in trash")
        void restoreDocument_NotTrashed_ThrowsException() {
            // Given
            Document activeDocument = createTestDocument(); // Status is ACTIVE

            when(documentRepository.findByIdAndTenantId(DOCUMENT_ID, TENANT_ID))
                    .thenReturn(Optional.of(activeDocument));

            // When/Then
            assertThatThrownBy(() -> documentService.restoreDocument(DOCUMENT_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not in trash");
        }

        @Test
        @DisplayName("Should throw exception when document not found")
        void restoreDocument_NotFound_ThrowsException() {
            // Given
            when(documentRepository.findByIdAndTenantId(DOCUMENT_ID, TENANT_ID))
                    .thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> documentService.restoreDocument(DOCUMENT_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Document not found");
        }

        @Test
        @DisplayName("Should publish restore event to Kafka")
        void restoreDocument_PublishesEvent() {
            // Given
            Document trashedDocument = createTestDocument();
            trashedDocument.setStatus(DocumentStatus.TRASHED);

            when(documentRepository.findByIdAndTenantId(DOCUMENT_ID, TENANT_ID))
                    .thenReturn(Optional.of(trashedDocument));
            when(documentRepository.existsByTenantIdAndDriveIdAndFolderIdAndNameAndStatusNot(
                    anyString(), anyString(), anyString(), anyString(), any()))
                    .thenReturn(false);
            when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));
            when(documentMapper.toDTO(any(Document.class))).thenReturn(DocumentDTO.builder().build());

            // When
            documentService.restoreDocument(DOCUMENT_ID);

            // Then
            verify(kafkaTemplate).send(eq("teamsync.content.events"), eq(DOCUMENT_ID), any());
        }
    }

    @Nested
    @DisplayName("Delete Workflow Integration Tests")
    class DeleteWorkflowTests {

        @Test
        @DisplayName("Should complete full delete workflow: trash then permanent delete")
        void fullDeleteWorkflow() {
            // Given
            Document document = createTestDocument();
            Document trashedDocument = createTestDocument();
            trashedDocument.setStatus(DocumentStatus.TRASHED);

            when(documentRepository.findByIdAndTenantIdAndDriveId(DOCUMENT_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(document))
                    .thenReturn(Optional.of(trashedDocument));
            when(documentRepository.save(any(Document.class))).thenAnswer(inv -> {
                Document d = inv.getArgument(0);
                d.setStatus(DocumentStatus.TRASHED);
                return d;
            });
            when(versionRepository.findByDocumentIdOrderByVersionNumberDesc(eq(DOCUMENT_ID), any()))
                    .thenReturn(new ArrayList<>());

            // Stage 1: Trash
            documentService.trashDocument(DOCUMENT_ID);

            // Verify document is now trashed
            verify(documentRepository, times(1)).save(documentCaptor.capture());
            assertThat(documentCaptor.getValue().getStatus()).isEqualTo(DocumentStatus.TRASHED);

            // Stage 2: Permanent delete
            documentService.deleteDocument(DOCUMENT_ID);

            // Verify document is deleted
            verify(documentRepository).delete(any(Document.class));
        }

        @Test
        @DisplayName("Should preserve document history until permanent delete")
        void deletePreservesHistoryUntilPermanent() {
            // Given - document with multiple versions
            Document document = createTestDocument();
            document.setVersionCount(3);

            List<DocumentVersion> versions = List.of(
                    createTestVersion(DOCUMENT_ID, 1),
                    createTestVersion(DOCUMENT_ID, 2),
                    createTestVersion(DOCUMENT_ID, 3)
            );

            // Stage 1: Trash
            when(documentRepository.findByIdAndTenantIdAndDriveId(DOCUMENT_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(document));
            when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

            documentService.trashDocument(DOCUMENT_ID);

            // Versions should NOT be deleted during trash
            verify(versionRepository, never()).deleteByDocumentId(anyString());
            verify(storageServiceClient, never()).deleteFile(anyString(), anyString());

            // Stage 2: Permanent delete
            Document trashedDocument = createTestDocument();
            trashedDocument.setStatus(DocumentStatus.TRASHED);

            when(documentRepository.findByIdAndTenantIdAndDriveId(DOCUMENT_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(trashedDocument));
            when(versionRepository.findByDocumentIdOrderByVersionNumberDesc(eq(DOCUMENT_ID), any()))
                    .thenReturn(versions);

            documentService.deleteDocument(DOCUMENT_ID);

            // Now versions should be deleted
            verify(versionRepository).deleteByDocumentId(DOCUMENT_ID);
            verify(storageServiceClient, times(3)).deleteFile(eq(STORAGE_BUCKET), anyString());
        }
    }
}
