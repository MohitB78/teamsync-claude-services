package com.teamsync.content.service;

import com.teamsync.common.context.TenantContext;
import com.teamsync.common.dto.CursorPage;
import com.teamsync.common.exception.AccessDeniedException;
import com.teamsync.common.exception.ResourceNotFoundException;
import com.teamsync.common.storage.CloudStorageProvider;
import com.teamsync.common.util.DownloadTokenUtil;
import com.teamsync.content.dto.document.CreateDocumentRequest;
import com.teamsync.content.dto.document.DocumentDTO;
import com.teamsync.content.dto.document.UpdateDocumentRequest;
import com.teamsync.content.mapper.DocumentMapper;
import com.teamsync.content.model.Document;
import com.teamsync.content.model.Document.DocumentStatus;
import com.teamsync.content.model.DocumentVersion;
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
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentService Tests")
class DocumentServiceTest {

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

    @Captor
    private ArgumentCaptor<DocumentVersion> versionCaptor;

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

    private Document createTestDocument() {
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

    private DocumentDTO createTestDocumentDTO() {
        return DocumentDTO.builder()
                .id(DOCUMENT_ID)
                .tenantId(TENANT_ID)
                .driveId(DRIVE_ID)
                .folderId(FOLDER_ID)
                .name("test-document.pdf")
                .description("Test document description")
                .contentType("application/pdf")
                .fileSize(1024L)
                .extension("pdf")
                .versionCount(1)
                .isStarred(false)
                .isPinned(false)
                .isLocked(false)
                .ownerId(USER_ID)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("getDocument Tests")
    class GetDocumentTests {

        @Test
        @DisplayName("Should return document when found")
        void getDocument_WhenFound_ReturnsDocument() {
            // Given
            Document document = createTestDocument();
            DocumentDTO expectedDTO = createTestDocumentDTO();

            when(documentRepository.findByIdAndTenantIdAndDriveId(DOCUMENT_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(document));
            when(documentMapper.toDTO(any(Document.class))).thenReturn(expectedDTO);

            // When
            DocumentDTO result = documentService.getDocument(DOCUMENT_ID);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(DOCUMENT_ID);
            assertThat(result.getName()).isEqualTo("test-document.pdf");

            verify(documentRepository).findByIdAndTenantIdAndDriveId(DOCUMENT_ID, TENANT_ID, DRIVE_ID);
            verify(documentRepository).save(any(Document.class)); // Updates accessedAt
        }

        @Test
        @DisplayName("Should throw exception when document not found")
        void getDocument_WhenNotFound_ThrowsException() {
            // Given
            when(documentRepository.findByIdAndTenantIdAndDriveId(DOCUMENT_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> documentService.getDocument(DOCUMENT_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Document not found");
        }
    }

    @Nested
    @DisplayName("createDocument Tests")
    class CreateDocumentTests {

        @Test
        @DisplayName("Should create document successfully")
        void createDocument_Success() {
            // Given
            CreateDocumentRequest request = CreateDocumentRequest.builder()
                    .name("new-document.docx")
                    .description("New document")
                    .contentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                    .fileSize(2048L)
                    .folderId(FOLDER_ID)
                    .storageKey("storage/new-document.docx")
                    .storageBucket("teamsync-documents")
                    .build();

            when(documentRepository.existsByTenantIdAndDriveIdAndFolderIdAndNameAndStatusNot(
                    anyString(), anyString(), anyString(), anyString(), any(DocumentStatus.class)))
                    .thenReturn(false);
            when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(documentMapper.toDTO(any(Document.class))).thenReturn(createTestDocumentDTO());

            // When
            DocumentDTO result = documentService.createDocument(request);

            // Then
            assertThat(result).isNotNull();
            verify(documentRepository).save(documentCaptor.capture());
            verify(versionRepository).save(versionCaptor.capture());
            verify(folderRepository).incrementDocumentCount(FOLDER_ID, TENANT_ID, DRIVE_ID, 1);
            verify(folderRepository).incrementTotalSize(FOLDER_ID, TENANT_ID, DRIVE_ID, 2048L);

            Document savedDocument = documentCaptor.getValue();
            assertThat(savedDocument.getName()).isEqualTo("new-document.docx");
            assertThat(savedDocument.getExtension()).isEqualTo("docx");
            assertThat(savedDocument.getOwnerId()).isEqualTo(USER_ID);
            assertThat(savedDocument.getStatus()).isEqualTo(DocumentStatus.ACTIVE);
        }

        @Test
        @DisplayName("Should throw exception when duplicate name exists")
        void createDocument_WhenDuplicateName_ThrowsException() {
            // Given
            CreateDocumentRequest request = CreateDocumentRequest.builder()
                    .name("existing-document.pdf")
                    .folderId(FOLDER_ID)
                    .build();

            when(documentRepository.existsByTenantIdAndDriveIdAndFolderIdAndNameAndStatusNot(
                    TENANT_ID, DRIVE_ID, FOLDER_ID, "existing-document.pdf", DocumentStatus.TRASHED))
                    .thenReturn(true);

            // When/Then
            assertThatThrownBy(() -> documentService.createDocument(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already exists");

            verify(documentRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("updateDocument Tests")
    class UpdateDocumentTests {

        @Test
        @DisplayName("Should update document metadata successfully")
        void updateDocument_Success() {
            // Given
            Document existingDocument = createTestDocument();
            UpdateDocumentRequest request = UpdateDocumentRequest.builder()
                    .description("Updated description")
                    .tags(List.of("tag1", "tag2"))
                    .isStarred(true)
                    .build();

            when(documentRepository.findByIdAndTenantIdAndDriveId(DOCUMENT_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(existingDocument));
            when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(documentMapper.toDTO(any(Document.class))).thenReturn(createTestDocumentDTO());

            // When
            DocumentDTO result = documentService.updateDocument(DOCUMENT_ID, request, null);

            // Then
            assertThat(result).isNotNull();
            verify(documentRepository).save(documentCaptor.capture());

            Document savedDocument = documentCaptor.getValue();
            assertThat(savedDocument.getDescription()).isEqualTo("Updated description");
            assertThat(savedDocument.getTags()).containsExactly("tag1", "tag2");
            assertThat(savedDocument.getIsStarred()).isTrue();
        }

        @Test
        @DisplayName("Should throw exception when document is locked by another user")
        void updateDocument_WhenLockedByAnotherUser_ThrowsException() {
            // Given
            Document lockedDocument = createTestDocument();
            lockedDocument.setIsLocked(true);
            lockedDocument.setLockedBy("other-user");

            UpdateDocumentRequest request = UpdateDocumentRequest.builder()
                    .description("Updated description")
                    .build();

            when(documentRepository.findByIdAndTenantIdAndDriveId(DOCUMENT_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(lockedDocument));

            // When/Then
            assertThatThrownBy(() -> documentService.updateDocument(DOCUMENT_ID, request, null))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("locked by another user");
        }
    }

    @Nested
    @DisplayName("trashDocument Tests")
    class TrashDocumentTests {

        @Test
        @DisplayName("Should move document to trash successfully")
        void trashDocument_Success() {
            // Given
            Document document = createTestDocument();

            when(documentRepository.findByIdAndTenantIdAndDriveId(DOCUMENT_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(document));

            // When
            documentService.trashDocument(DOCUMENT_ID);

            // Then
            verify(documentRepository).save(documentCaptor.capture());
            Document trashedDocument = documentCaptor.getValue();

            assertThat(trashedDocument.getStatus()).isEqualTo(DocumentStatus.TRASHED);
            verify(folderRepository).incrementDocumentCount(FOLDER_ID, TENANT_ID, DRIVE_ID, -1);
            verify(folderRepository).incrementTotalSize(FOLDER_ID, TENANT_ID, DRIVE_ID, -1024L);
        }

        @Test
        @DisplayName("Should throw exception when trashing locked document")
        void trashDocument_WhenLocked_ThrowsException() {
            // Given
            Document lockedDocument = createTestDocument();
            lockedDocument.setIsLocked(true);
            lockedDocument.setLockedBy("other-user");

            when(documentRepository.findByIdAndTenantIdAndDriveId(DOCUMENT_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(lockedDocument));

            // When/Then
            assertThatThrownBy(() -> documentService.trashDocument(DOCUMENT_ID))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Nested
    @DisplayName("restoreDocument Tests")
    class RestoreDocumentTests {

        @Test
        @DisplayName("Should restore document from trash successfully")
        void restoreDocument_Success() {
            // Given
            Document trashedDocument = createTestDocument();
            trashedDocument.setStatus(DocumentStatus.TRASHED);

            when(documentRepository.findByIdAndTenantId(DOCUMENT_ID, TENANT_ID))
                    .thenReturn(Optional.of(trashedDocument));
            when(documentRepository.existsByTenantIdAndDriveIdAndFolderIdAndNameAndStatusNot(
                    anyString(), anyString(), anyString(), anyString(), any(DocumentStatus.class)))
                    .thenReturn(false);
            when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(documentMapper.toDTO(any(Document.class))).thenReturn(createTestDocumentDTO());

            // When
            DocumentDTO result = documentService.restoreDocument(DOCUMENT_ID);

            // Then
            assertThat(result).isNotNull();
            verify(documentRepository).save(documentCaptor.capture());

            Document restoredDocument = documentCaptor.getValue();
            assertThat(restoredDocument.getStatus()).isEqualTo(DocumentStatus.ACTIVE);
        }

        @Test
        @DisplayName("Should throw exception when document is not in trash")
        void restoreDocument_WhenNotTrashed_ThrowsException() {
            // Given
            Document activeDocument = createTestDocument();

            when(documentRepository.findByIdAndTenantId(DOCUMENT_ID, TENANT_ID))
                    .thenReturn(Optional.of(activeDocument));

            // When/Then
            assertThatThrownBy(() -> documentService.restoreDocument(DOCUMENT_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not in trash");
        }
    }

    @Nested
    @DisplayName("lockDocument Tests")
    class LockDocumentTests {

        @Test
        @DisplayName("Should lock document successfully")
        void lockDocument_Success() {
            // Given
            Document document = createTestDocument();

            when(documentRepository.findByIdAndTenantIdAndDriveId(DOCUMENT_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(document));
            when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(documentMapper.toDTO(any(Document.class))).thenReturn(createTestDocumentDTO());

            // When
            DocumentDTO result = documentService.lockDocument(DOCUMENT_ID);

            // Then
            verify(documentRepository).save(documentCaptor.capture());
            Document lockedDocument = documentCaptor.getValue();

            assertThat(lockedDocument.getIsLocked()).isTrue();
            assertThat(lockedDocument.getLockedBy()).isEqualTo(USER_ID);
            assertThat(lockedDocument.getLockedAt()).isNotNull();
        }

        @Test
        @DisplayName("Should throw exception when document is already locked")
        void lockDocument_WhenAlreadyLocked_ThrowsException() {
            // Given
            Document lockedDocument = createTestDocument();
            lockedDocument.setIsLocked(true);
            lockedDocument.setLockedBy("other-user");

            when(documentRepository.findByIdAndTenantIdAndDriveId(DOCUMENT_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(lockedDocument));

            // When/Then
            assertThatThrownBy(() -> documentService.lockDocument(DOCUMENT_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already locked");
        }
    }

    @Nested
    @DisplayName("unlockDocument Tests")
    class UnlockDocumentTests {

        @Test
        @DisplayName("Should unlock document successfully")
        void unlockDocument_Success() {
            // Given
            Document lockedDocument = createTestDocument();
            lockedDocument.setIsLocked(true);
            lockedDocument.setLockedBy(USER_ID);
            lockedDocument.setLockedAt(Instant.now());

            when(documentRepository.findByIdAndTenantIdAndDriveId(DOCUMENT_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(lockedDocument));
            when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(documentMapper.toDTO(any(Document.class))).thenReturn(createTestDocumentDTO());

            // When
            DocumentDTO result = documentService.unlockDocument(DOCUMENT_ID);

            // Then
            verify(documentRepository).save(documentCaptor.capture());
            Document unlockedDocument = documentCaptor.getValue();

            assertThat(unlockedDocument.getIsLocked()).isFalse();
            assertThat(unlockedDocument.getLockedBy()).isNull();
            assertThat(unlockedDocument.getLockedAt()).isNull();
        }

        @Test
        @DisplayName("Should throw exception when unlocking document locked by another user")
        void unlockDocument_WhenLockedByAnotherUser_ThrowsException() {
            // Given
            Document lockedDocument = createTestDocument();
            lockedDocument.setIsLocked(true);
            lockedDocument.setLockedBy("other-user");

            when(documentRepository.findByIdAndTenantIdAndDriveId(DOCUMENT_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(lockedDocument));

            // When/Then
            assertThatThrownBy(() -> documentService.unlockDocument(DOCUMENT_ID))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Only the user who locked");
        }
    }

    @Nested
    @DisplayName("listDocuments Tests")
    class ListDocumentsTests {

        @Test
        @DisplayName("Should list documents with cursor pagination")
        void listDocuments_WithPagination() {
            // Given
            Document doc1 = createTestDocument();
            Document doc2 = createTestDocument();
            doc2.setId("doc-002");
            doc2.setName("document-2.pdf");

            List<Document> documents = List.of(doc1, doc2);

            when(documentRepository.findByTenantIdAndDriveIdAndFolderIdAndStatus(
                    eq(TENANT_ID), eq(DRIVE_ID), eq(FOLDER_ID), eq(DocumentStatus.ACTIVE), any()))
                    .thenReturn(documents);
            when(documentMapper.toDTO(any(Document.class))).thenReturn(createTestDocumentDTO());

            // When
            CursorPage<DocumentDTO> result = documentService.listDocuments(FOLDER_ID, null, 10);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getItems()).hasSize(2);
            assertThat(result.isHasMore()).isFalse();
        }
    }

    @Nested
    @DisplayName("generateDownloadUrl Tests")
    class GenerateDownloadUrlTests {

        @Test
        @DisplayName("Should generate token-based download URL")
        void generateDownloadUrl_Success() {
            // Given
            Document document = createTestDocument();
            String signedToken = "signed-token-abc123";

            when(documentRepository.findByIdAndTenantIdAndDriveId(DOCUMENT_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(document));
            when(downloadTokenUtil.generateToken(
                    eq(TENANT_ID), eq(DRIVE_ID), eq(USER_ID),
                    anyString(), anyString(), any(Instant.class)))
                    .thenReturn(signedToken);

            // When
            String result = documentService.generateDownloadUrl(DOCUMENT_ID, Duration.ofHours(1));

            // Then
            assertThat(result).contains("/api/documents/download?token=");
            assertThat(result).contains("filename=");
            verify(downloadTokenUtil).generateToken(
                    eq(TENANT_ID), eq(DRIVE_ID), eq(USER_ID),
                    eq("teamsync-documents"),
                    eq("storage/test-document.pdf"),
                    any(Instant.class)
            );
        }
    }
}
