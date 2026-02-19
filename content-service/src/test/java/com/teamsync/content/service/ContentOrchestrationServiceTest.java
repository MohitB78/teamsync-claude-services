package com.teamsync.content.service;

import com.teamsync.common.context.TenantContext;
import com.teamsync.common.dto.CursorPage;
import com.teamsync.common.exception.ResourceNotFoundException;
import com.teamsync.common.storage.CloudStorageProvider;
import com.teamsync.content.dto.ContentItemDTO;
import com.teamsync.content.dto.folder.FolderDTO;
import com.teamsync.content.mapper.DocumentMapper;
import com.teamsync.content.mapper.FolderMapper;
import com.teamsync.content.model.Document;
import com.teamsync.content.model.Document.DocumentStatus;
import com.teamsync.content.model.DocumentVersion;
import com.teamsync.content.model.Folder;
import com.teamsync.content.model.Folder.FolderStatus;
import com.teamsync.content.repository.DocumentRepository;
import com.teamsync.content.repository.DocumentVersionRepository;
import com.teamsync.content.repository.FolderRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for ContentOrchestrationService.
 * Tests cascading operations across folders and documents.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Content Orchestration Service Tests")
class ContentOrchestrationServiceTest {

    private static final String TENANT_ID = "tenant-123";
    private static final String DRIVE_ID = "drive-456";
    private static final String USER_ID = "user-789";

    @InjectMocks
    private ContentOrchestrationService orchestrationService;

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentVersionRepository documentVersionRepository;

    @Mock
    private CloudStorageProvider storageProvider;

    @Mock
    private FolderMapper folderMapper;

    @Mock
    private DocumentMapper documentMapper;

    @Captor
    private ArgumentCaptor<List<String>> folderIdsCaptor;

    private MockedStatic<TenantContext> tenantContextMock;

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

    @Nested
    @DisplayName("List Unified Content Tests")
    class ListUnifiedContentTests {

        @Test
        @DisplayName("Should return combined folders and documents")
        void listUnifiedContent_ReturnsAll() {
            // Given
            String parentId = "folder-1";
            int limit = 50;

            List<Folder> folders = List.of(
                    createFolder("folder-a", "Folder A"),
                    createFolder("folder-b", "Folder B")
            );
            List<Document> documents = List.of(
                    createDocument("doc-1", "Document 1.pdf"),
                    createDocument("doc-2", "Document 2.docx")
            );

            // Mock the new sorted repository methods
            when(folderRepository.findByTenantIdAndDriveIdAndParentIdAndStatusSorted(
                    eq(TENANT_ID), eq(DRIVE_ID), eq(parentId), eq(FolderStatus.ACTIVE), any(Pageable.class)))
                    .thenReturn(folders);
            when(documentRepository.findByTenantIdAndDriveIdAndFolderIdAndStatusSorted(
                    eq(TENANT_ID), eq(DRIVE_ID), eq(parentId), eq(DocumentStatus.ACTIVE), any(Pageable.class)))
                    .thenReturn(documents);

            // When
            CursorPage<ContentItemDTO> result = orchestrationService.listUnifiedContent(
                    parentId, null, null, limit);

            // Then
            assertThat(result.getItems()).hasSize(4);
            assertThat(result.isHasMore()).isFalse();
        }

        @Test
        @DisplayName("Should filter by content type - FOLDER only")
        void listUnifiedContent_FolderFilter_ReturnsFoldersOnly() {
            // Given
            String parentId = "folder-1";
            int limit = 50;

            List<Folder> folders = List.of(createFolder("folder-a", "Folder A"));

            when(folderRepository.findByTenantIdAndDriveIdAndParentIdAndStatusSorted(
                    eq(TENANT_ID), eq(DRIVE_ID), eq(parentId), eq(FolderStatus.ACTIVE), any(Pageable.class)))
                    .thenReturn(folders);

            // When
            CursorPage<ContentItemDTO> result = orchestrationService.listUnifiedContent(
                    parentId, ContentItemDTO.ContentType.FOLDER, null, limit);

            // Then
            assertThat(result.getItems()).hasSize(1);
            assertThat(result.getItems().get(0).getType()).isEqualTo(ContentItemDTO.ContentType.FOLDER);
            verify(documentRepository, never()).findByTenantIdAndDriveIdAndFolderIdAndStatusSorted(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should filter by content type - DOCUMENT only")
        void listUnifiedContent_DocumentFilter_ReturnsDocumentsOnly() {
            // Given
            String parentId = "folder-1";
            int limit = 50;

            List<Document> documents = List.of(createDocument("doc-1", "Document 1.pdf"));

            when(documentRepository.findByTenantIdAndDriveIdAndFolderIdAndStatusSorted(
                    eq(TENANT_ID), eq(DRIVE_ID), eq(parentId), eq(DocumentStatus.ACTIVE), any(Pageable.class)))
                    .thenReturn(documents);

            // When
            CursorPage<ContentItemDTO> result = orchestrationService.listUnifiedContent(
                    parentId, ContentItemDTO.ContentType.DOCUMENT, null, limit);

            // Then
            assertThat(result.getItems()).hasSize(1);
            assertThat(result.getItems().get(0).getType()).isEqualTo(ContentItemDTO.ContentType.DOCUMENT);
            verify(folderRepository, never()).findByTenantIdAndDriveIdAndParentIdAndStatusSorted(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should return root content when parentId is null")
        void listUnifiedContent_NullParent_ReturnsRootContent() {
            // Given
            int limit = 50;

            List<Folder> rootFolders = List.of(createFolder("folder-root", "Root Folder"));
            List<Document> rootDocs = List.of(createDocument("doc-root", "Root Doc.pdf"));

            when(folderRepository.findRootFoldersSorted(
                    eq(TENANT_ID), eq(DRIVE_ID), eq(FolderStatus.ACTIVE), any(Pageable.class)))
                    .thenReturn(rootFolders);
            when(documentRepository.findRootDocumentsSorted(
                    eq(TENANT_ID), eq(DRIVE_ID), eq(DocumentStatus.ACTIVE), any(Pageable.class)))
                    .thenReturn(rootDocs);

            // When
            CursorPage<ContentItemDTO> result = orchestrationService.listUnifiedContent(null, null, null, limit);

            // Then
            assertThat(result.getItems()).hasSize(2);
        }

        @Test
        @DisplayName("Should sort folders before documents")
        void listUnifiedContent_SortsFoldersFirst() {
            // Given
            String parentId = "folder-1";
            int limit = 50;

            List<Folder> folders = List.of(createFolder("folder-z", "Zebra Folder"));
            List<Document> documents = List.of(createDocument("doc-a", "Apple.pdf"));

            when(folderRepository.findByTenantIdAndDriveIdAndParentIdAndStatusSorted(
                    eq(TENANT_ID), eq(DRIVE_ID), eq(parentId), eq(FolderStatus.ACTIVE), any(Pageable.class)))
                    .thenReturn(folders);
            when(documentRepository.findByTenantIdAndDriveIdAndFolderIdAndStatusSorted(
                    eq(TENANT_ID), eq(DRIVE_ID), eq(parentId), eq(DocumentStatus.ACTIVE), any(Pageable.class)))
                    .thenReturn(documents);

            // When
            CursorPage<ContentItemDTO> result = orchestrationService.listUnifiedContent(
                    parentId, null, null, limit);

            // Then
            assertThat(result.getItems()).hasSize(2);
            assertThat(result.getItems().get(0).getType()).isEqualTo(ContentItemDTO.ContentType.FOLDER);
            assertThat(result.getItems().get(1).getType()).isEqualTo(ContentItemDTO.ContentType.DOCUMENT);
        }

        @Test
        @DisplayName("Should sort alphabetically within type")
        void listUnifiedContent_SortsAlphabetically() {
            // Given
            String parentId = "folder-1";
            int limit = 50;

            // Note: With the new implementation, sorting is done DB-side
            // So the returned list should already be sorted
            List<Folder> folders = List.of(
                    createFolder("folder-a", "Apple"),
                    createFolder("folder-z", "Zebra")
            );

            when(folderRepository.findByTenantIdAndDriveIdAndParentIdAndStatusSorted(
                    eq(TENANT_ID), eq(DRIVE_ID), eq(parentId), eq(FolderStatus.ACTIVE), any(Pageable.class)))
                    .thenReturn(folders);
            when(documentRepository.findByTenantIdAndDriveIdAndFolderIdAndStatusSorted(
                    eq(TENANT_ID), eq(DRIVE_ID), eq(parentId), eq(DocumentStatus.ACTIVE), any(Pageable.class)))
                    .thenReturn(Collections.emptyList());

            // When
            CursorPage<ContentItemDTO> result = orchestrationService.listUnifiedContent(
                    parentId, null, null, limit);

            // Then
            assertThat(result.getItems()).hasSize(2);
            assertThat(result.getItems().get(0).getName()).isEqualTo("Apple");
            assertThat(result.getItems().get(1).getName()).isEqualTo("Zebra");
        }

        @Test
        @DisplayName("Should apply limit correctly")
        void listUnifiedContent_AppliesLimit() {
            // Given
            String parentId = "folder-1";
            int limit = 2;

            // Return limit+1 items to simulate having more
            List<Folder> folders = List.of(
                    createFolder("folder-a", "Folder A"),
                    createFolder("folder-b", "Folder B"),
                    createFolder("folder-c", "Folder C")
            );

            when(folderRepository.findByTenantIdAndDriveIdAndParentIdAndStatusSorted(
                    eq(TENANT_ID), eq(DRIVE_ID), eq(parentId), eq(FolderStatus.ACTIVE), any(Pageable.class)))
                    .thenReturn(folders);

            // When
            CursorPage<ContentItemDTO> result = orchestrationService.listUnifiedContent(
                    parentId, null, null, limit);

            // Then
            assertThat(result.getItems()).hasSize(2);
            assertThat(result.isHasMore()).isTrue();
        }

        @Test
        @DisplayName("Should handle empty folder")
        void listUnifiedContent_EmptyFolder_ReturnsEmpty() {
            // Given
            String parentId = "empty-folder";
            int limit = 50;

            when(folderRepository.findByTenantIdAndDriveIdAndParentIdAndStatusSorted(
                    eq(TENANT_ID), eq(DRIVE_ID), eq(parentId), eq(FolderStatus.ACTIVE), any(Pageable.class)))
                    .thenReturn(Collections.emptyList());
            when(documentRepository.findByTenantIdAndDriveIdAndFolderIdAndStatusSorted(
                    eq(TENANT_ID), eq(DRIVE_ID), eq(parentId), eq(DocumentStatus.ACTIVE), any(Pageable.class)))
                    .thenReturn(Collections.emptyList());

            // When
            CursorPage<ContentItemDTO> result = orchestrationService.listUnifiedContent(
                    parentId, null, null, limit);

            // Then
            assertThat(result.getItems()).isEmpty();
            assertThat(result.isHasMore()).isFalse();
        }
    }

    @Nested
    @DisplayName("Trash Folder With Contents Tests")
    class TrashFolderWithContentsTests {

        @Test
        @DisplayName("Should trash folder and all descendant folders")
        void trashFolderWithContents_TrashesAllFolders() {
            // Given
            String folderId = "folder-parent";
            Folder parentFolder = createFolder(folderId, "Parent");
            parentFolder.setParentId("grandparent");

            List<Folder> descendants = List.of(
                    createFolder("folder-child-1", "Child 1"),
                    createFolder("folder-child-2", "Child 2")
            );

            when(folderRepository.findByIdAndTenantIdAndDriveId(folderId, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(parentFolder));
            when(folderRepository.findDescendantIds(eq(TENANT_ID), eq(DRIVE_ID), eq(folderId), any()))
                    .thenReturn(descendants);

            // When
            orchestrationService.trashFolderWithContents(folderId);

            // Then
            verify(folderRepository).updateStatusByIdIn(folderIdsCaptor.capture(), eq(TENANT_ID), eq(DRIVE_ID), eq(FolderStatus.TRASHED), any(Instant.class));

            List<String> trashedFolderIds = folderIdsCaptor.getValue();
            assertThat(trashedFolderIds).hasSize(3); // Parent + 2 children
            assertThat(trashedFolderIds).contains(folderId, "folder-child-1", "folder-child-2");
        }

        @Test
        @DisplayName("Should trash all documents in folder hierarchy")
        void trashFolderWithContents_TrashesAllDocuments() {
            // Given
            String folderId = "folder-parent";
            Folder parentFolder = createFolder(folderId, "Parent");

            when(folderRepository.findByIdAndTenantIdAndDriveId(folderId, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(parentFolder));
            when(folderRepository.findDescendantIds(eq(TENANT_ID), eq(DRIVE_ID), eq(folderId), any()))
                    .thenReturn(Collections.emptyList());

            // When
            orchestrationService.trashFolderWithContents(folderId);

            // Then
            verify(documentRepository).updateStatusByFolderIdIn(anyList(), eq(TENANT_ID), eq(DRIVE_ID), eq(DocumentStatus.TRASHED), any(Instant.class));
        }

        @Test
        @DisplayName("Should decrement parent folder count")
        void trashFolderWithContents_DecrementsParentCount() {
            // Given
            String folderId = "folder-child";
            String parentId = "folder-parent";
            Folder childFolder = createFolder(folderId, "Child");
            childFolder.setParentId(parentId);

            when(folderRepository.findByIdAndTenantIdAndDriveId(folderId, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(childFolder));
            when(folderRepository.findDescendantIds(eq(TENANT_ID), eq(DRIVE_ID), eq(folderId), any()))
                    .thenReturn(Collections.emptyList());

            // When
            orchestrationService.trashFolderWithContents(folderId);

            // Then
            verify(folderRepository).incrementFolderCount(parentId, TENANT_ID, DRIVE_ID, -1);
        }

        @Test
        @DisplayName("Should not decrement count for root folders")
        void trashFolderWithContents_RootFolder_NoDecrement() {
            // Given
            String folderId = "folder-root";
            Folder rootFolder = createFolder(folderId, "Root");
            rootFolder.setParentId(null);

            when(folderRepository.findByIdAndTenantIdAndDriveId(folderId, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(rootFolder));
            when(folderRepository.findDescendantIds(eq(TENANT_ID), eq(DRIVE_ID), eq(folderId), any()))
                    .thenReturn(Collections.emptyList());

            // When
            orchestrationService.trashFolderWithContents(folderId);

            // Then
            verify(folderRepository, never()).incrementFolderCount(any(), any(), any(), anyInt());
        }

        @Test
        @DisplayName("Should throw exception if folder not found")
        void trashFolderWithContents_NotFound_ThrowsException() {
            // Given
            String folderId = "nonexistent";

            when(folderRepository.findByIdAndTenantIdAndDriveId(folderId, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> orchestrationService.trashFolderWithContents(folderId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(folderId);
        }
    }

    @Nested
    @DisplayName("Restore Folder With Contents Tests")
    class RestoreFolderWithContentsTests {

        @Test
        @DisplayName("Should restore folder and all contents")
        void restoreFolderWithContents_RestoresAll() {
            // Given
            String folderId = "folder-trashed";
            Folder trashedFolder = createFolder(folderId, "Trashed Folder");
            trashedFolder.setStatus(FolderStatus.TRASHED);
            trashedFolder.setParentId(null);

            when(folderRepository.findByIdAndTenantId(folderId, TENANT_ID))
                    .thenReturn(Optional.of(trashedFolder));
            when(folderRepository.findDescendantIds(eq(TENANT_ID), eq(DRIVE_ID), eq(folderId), any()))
                    .thenReturn(Collections.emptyList());
            when(folderRepository.existsByTenantIdAndDriveIdAndParentIdAndNameAndStatusNot(
                    TENANT_ID, DRIVE_ID, null, "Trashed Folder", FolderStatus.TRASHED))
                    .thenReturn(false);
            when(folderRepository.save(any(Folder.class))).thenAnswer(inv -> inv.getArgument(0));
            when(folderMapper.toDTO(any(Folder.class))).thenReturn(FolderDTO.builder().build());

            // When
            FolderDTO result = orchestrationService.restoreFolderWithContents(folderId);

            // Then
            verify(folderRepository).updateStatusByIdIn(anyList(), eq(TENANT_ID), eq(DRIVE_ID), eq(FolderStatus.ACTIVE), any(Instant.class));
            verify(documentRepository).updateStatusByFolderIdIn(anyList(), eq(TENANT_ID), eq(DRIVE_ID), eq(DocumentStatus.ACTIVE), any(Instant.class));
        }

        @Test
        @DisplayName("Should move to root if parent was deleted")
        void restoreFolderWithContents_DeletedParent_MovesToRoot() {
            // Given
            String folderId = "folder-trashed";
            String parentId = "deleted-parent";
            Folder trashedFolder = createFolder(folderId, "Trashed Folder");
            trashedFolder.setStatus(FolderStatus.TRASHED);
            trashedFolder.setParentId(parentId);

            when(folderRepository.findByIdAndTenantId(folderId, TENANT_ID))
                    .thenReturn(Optional.of(trashedFolder));
            when(folderRepository.findByIdAndTenantIdAndDriveId(parentId, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.empty()); // Parent was deleted
            when(folderRepository.findDescendantIds(eq(TENANT_ID), eq(DRIVE_ID), eq(folderId), any()))
                    .thenReturn(Collections.emptyList());
            when(folderRepository.existsByTenantIdAndDriveIdAndParentIdAndNameAndStatusNot(
                    eq(TENANT_ID), eq(DRIVE_ID), isNull(), anyString(), eq(FolderStatus.TRASHED)))
                    .thenReturn(false);
            when(folderRepository.save(any(Folder.class))).thenAnswer(inv -> inv.getArgument(0));
            when(folderMapper.toDTO(any(Folder.class))).thenReturn(FolderDTO.builder().build());

            // When
            orchestrationService.restoreFolderWithContents(folderId);

            // Then - Parent should be set to null (root)
            ArgumentCaptor<Folder> folderCaptor = ArgumentCaptor.forClass(Folder.class);
            verify(folderRepository).save(folderCaptor.capture());
            assertThat(folderCaptor.getValue().getParentId()).isNull();
        }

        @Test
        @DisplayName("Should rename folder if name conflicts")
        void restoreFolderWithContents_NameConflict_Renames() {
            // Given
            String folderId = "folder-trashed";
            Folder trashedFolder = createFolder(folderId, "Existing Name");
            trashedFolder.setStatus(FolderStatus.TRASHED);
            trashedFolder.setParentId(null);
            trashedFolder.setPath("/Existing Name");

            when(folderRepository.findByIdAndTenantId(folderId, TENANT_ID))
                    .thenReturn(Optional.of(trashedFolder));
            when(folderRepository.findDescendantIds(eq(TENANT_ID), eq(DRIVE_ID), eq(folderId), any()))
                    .thenReturn(Collections.emptyList());
            when(folderRepository.existsByTenantIdAndDriveIdAndParentIdAndNameAndStatusNot(
                    TENANT_ID, DRIVE_ID, null, "Existing Name", FolderStatus.TRASHED))
                    .thenReturn(true); // Name conflict
            when(folderRepository.save(any(Folder.class))).thenAnswer(inv -> inv.getArgument(0));
            when(folderMapper.toDTO(any(Folder.class))).thenReturn(FolderDTO.builder().build());

            // When
            orchestrationService.restoreFolderWithContents(folderId);

            // Then - Name should be changed
            ArgumentCaptor<Folder> folderCaptor = ArgumentCaptor.forClass(Folder.class);
            verify(folderRepository).save(folderCaptor.capture());
            assertThat(folderCaptor.getValue().getName()).contains("_restored_");
        }

        @Test
        @DisplayName("Should throw exception if folder is not in trash")
        void restoreFolderWithContents_NotTrashed_ThrowsException() {
            // Given
            String folderId = "folder-active";
            Folder activeFolder = createFolder(folderId, "Active Folder");
            activeFolder.setStatus(FolderStatus.ACTIVE);

            when(folderRepository.findByIdAndTenantId(folderId, TENANT_ID))
                    .thenReturn(Optional.of(activeFolder));

            // When/Then
            assertThatThrownBy(() -> orchestrationService.restoreFolderWithContents(folderId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not in trash");
        }

        @Test
        @DisplayName("Should increment parent folder count")
        void restoreFolderWithContents_IncrementsParentCount() {
            // Given
            String folderId = "folder-trashed";
            String parentId = "folder-parent";
            Folder trashedFolder = createFolder(folderId, "Trashed");
            trashedFolder.setStatus(FolderStatus.TRASHED);
            trashedFolder.setParentId(parentId);

            Folder parentFolder = createFolder(parentId, "Parent");
            parentFolder.setStatus(FolderStatus.ACTIVE);

            when(folderRepository.findByIdAndTenantId(folderId, TENANT_ID))
                    .thenReturn(Optional.of(trashedFolder));
            when(folderRepository.findByIdAndTenantIdAndDriveId(parentId, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(parentFolder));
            when(folderRepository.findDescendantIds(eq(TENANT_ID), eq(DRIVE_ID), eq(folderId), any()))
                    .thenReturn(Collections.emptyList());
            when(folderRepository.existsByTenantIdAndDriveIdAndParentIdAndNameAndStatusNot(
                    eq(TENANT_ID), eq(DRIVE_ID), eq(parentId), anyString(), eq(FolderStatus.TRASHED)))
                    .thenReturn(false);
            when(folderRepository.save(any(Folder.class))).thenAnswer(inv -> inv.getArgument(0));
            when(folderMapper.toDTO(any(Folder.class))).thenReturn(FolderDTO.builder().build());

            // When
            orchestrationService.restoreFolderWithContents(folderId);

            // Then
            verify(folderRepository).incrementFolderCount(parentId, TENANT_ID, DRIVE_ID, 1);
        }
    }

    @Nested
    @DisplayName("Delete Folder With Contents Tests")
    class DeleteFolderWithContentsTests {

        @Test
        @DisplayName("Should permanently delete folder and all contents")
        void deleteFolderWithContents_DeletesAll() {
            // Given
            String folderId = "folder-to-delete";
            Folder folder = createFolder(folderId, "To Delete");

            when(folderRepository.findByIdAndTenantIdAndDriveId(folderId, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(folder));
            when(folderRepository.findDescendantIds(eq(TENANT_ID), eq(DRIVE_ID), eq(folderId), any()))
                    .thenReturn(Collections.emptyList());
            when(documentRepository.findStorageInfoByFolderIdIn(anyList(), eq(TENANT_ID), eq(DRIVE_ID)))
                    .thenReturn(Collections.emptyList());

            // When
            orchestrationService.deleteFolderWithContents(folderId);

            // Then
            verify(folderRepository).deleteByIdIn(anyList(), eq(TENANT_ID), eq(DRIVE_ID));
        }

        @Test
        @DisplayName("Should delete files from storage")
        void deleteFolderWithContents_DeletesStorageFiles() {
            // Given
            String folderId = "folder-to-delete";
            Folder folder = createFolder(folderId, "To Delete");

            Document doc = createDocument("doc-1", "Document.pdf");
            doc.setStorageBucket("bucket");
            doc.setStorageKey("key/doc.pdf");

            when(folderRepository.findByIdAndTenantIdAndDriveId(folderId, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(folder));
            when(folderRepository.findDescendantIds(eq(TENANT_ID), eq(DRIVE_ID), eq(folderId), any()))
                    .thenReturn(Collections.emptyList());
            when(documentRepository.findStorageInfoByFolderIdIn(anyList(), eq(TENANT_ID), eq(DRIVE_ID)))
                    .thenReturn(List.of(doc));
            when(documentVersionRepository.findStorageInfoByDocumentIdIn(anyList(), any()))
                    .thenReturn(Collections.emptyList());

            // When
            orchestrationService.deleteFolderWithContents(folderId);

            // Then
            verify(storageProvider).delete("bucket", "key/doc.pdf");
        }

        @Test
        @DisplayName("Should delete document versions from storage")
        void deleteFolderWithContents_DeletesVersions() {
            // Given
            String folderId = "folder-to-delete";
            Folder folder = createFolder(folderId, "To Delete");

            Document doc = createDocument("doc-1", "Document.pdf");

            DocumentVersion version = new DocumentVersion();
            version.setId("version-1");
            version.setStorageBucket("bucket");
            version.setStorageKey("versions/v1.pdf");

            when(folderRepository.findByIdAndTenantIdAndDriveId(folderId, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(folder));
            when(folderRepository.findDescendantIds(eq(TENANT_ID), eq(DRIVE_ID), eq(folderId), any()))
                    .thenReturn(Collections.emptyList());
            when(documentRepository.findStorageInfoByFolderIdIn(anyList(), eq(TENANT_ID), eq(DRIVE_ID)))
                    .thenReturn(List.of(doc));
            when(documentVersionRepository.findStorageInfoByDocumentIdIn(anyList(), any()))
                    .thenReturn(List.of(version));

            // When
            orchestrationService.deleteFolderWithContents(folderId);

            // Then
            verify(storageProvider).delete("bucket", "versions/v1.pdf");
            verify(documentVersionRepository).deleteByDocumentIdIn(anyList());
        }

        @Test
        @DisplayName("Should continue deletion even if storage fails")
        void deleteFolderWithContents_StorageFailure_ContinuesDeletion() {
            // Given
            String folderId = "folder-to-delete";
            Folder folder = createFolder(folderId, "To Delete");

            Document doc = createDocument("doc-1", "Document.pdf");
            doc.setStorageBucket("bucket");
            doc.setStorageKey("key/doc.pdf");

            when(folderRepository.findByIdAndTenantIdAndDriveId(folderId, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(folder));
            when(folderRepository.findDescendantIds(eq(TENANT_ID), eq(DRIVE_ID), eq(folderId), any()))
                    .thenReturn(Collections.emptyList());
            when(documentRepository.findStorageInfoByFolderIdIn(anyList(), eq(TENANT_ID), eq(DRIVE_ID)))
                    .thenReturn(List.of(doc));
            when(documentVersionRepository.findStorageInfoByDocumentIdIn(anyList(), any()))
                    .thenReturn(Collections.emptyList());
            doThrow(new RuntimeException("Storage error")).when(storageProvider).delete(any(), any());

            // When - Should not throw
            orchestrationService.deleteFolderWithContents(folderId);

            // Then - Database deletion should still happen
            verify(folderRepository).deleteByIdIn(anyList(), eq(TENANT_ID), eq(DRIVE_ID));
        }

        @Test
        @DisplayName("Should handle documents without storage info")
        void deleteFolderWithContents_NoStorageInfo_SkipsStorage() {
            // Given
            String folderId = "folder-to-delete";
            Folder folder = createFolder(folderId, "To Delete");

            Document doc = createDocument("doc-1", "Document.pdf");
            doc.setStorageBucket(null);
            doc.setStorageKey(null);

            when(folderRepository.findByIdAndTenantIdAndDriveId(folderId, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(folder));
            when(folderRepository.findDescendantIds(eq(TENANT_ID), eq(DRIVE_ID), eq(folderId), any()))
                    .thenReturn(Collections.emptyList());
            when(documentRepository.findStorageInfoByFolderIdIn(anyList(), eq(TENANT_ID), eq(DRIVE_ID)))
                    .thenReturn(List.of(doc));
            when(documentVersionRepository.findStorageInfoByDocumentIdIn(anyList(), any()))
                    .thenReturn(Collections.emptyList());

            // When
            orchestrationService.deleteFolderWithContents(folderId);

            // Then - Storage should not be called
            verify(storageProvider, never()).delete(any(), any());
        }
    }

    @Nested
    @DisplayName("Get Folder Contents Stats Tests")
    class GetFolderContentsStatsTests {

        @Test
        @DisplayName("Should return correct folder statistics")
        void getFolderContentsStats_ReturnsCorrectStats() {
            // Given
            String folderId = "folder-with-content";
            Folder folder = createFolder(folderId, "Folder");
            folder.setTotalSize(1024000L);

            List<Folder> descendants = List.of(
                    createFolder("child-1", "Child 1"),
                    createFolder("child-2", "Child 2")
            );

            when(folderRepository.findByIdAndTenantIdAndDriveId(folderId, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(folder));
            when(folderRepository.findDescendantIds(eq(TENANT_ID), eq(DRIVE_ID), eq(folderId), any()))
                    .thenReturn(descendants);
            // Uses batch count method (Phase 2 optimization)
            when(documentRepository.countByTenantIdAndDriveIdAndFolderIdInAndStatus(
                    eq(TENANT_ID), eq(DRIVE_ID), anyList(), eq(DocumentStatus.ACTIVE)))
                    .thenReturn(15L);

            // When
            ContentOrchestrationService.FolderContentsStats stats =
                    orchestrationService.getFolderContentsStats(folderId);

            // Then
            assertThat(stats.getFolderId()).isEqualTo(folderId);
            assertThat(stats.getFolderCount()).isEqualTo(2);
            assertThat(stats.getDocumentCount()).isEqualTo(15L);
            assertThat(stats.getTotalSize()).isEqualTo(1024000L);
        }

        @Test
        @DisplayName("Should return zero for empty folder")
        void getFolderContentsStats_EmptyFolder_ReturnsZero() {
            // Given
            String folderId = "empty-folder";
            Folder folder = createFolder(folderId, "Empty");
            folder.setTotalSize(null);

            when(folderRepository.findByIdAndTenantIdAndDriveId(folderId, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(folder));
            when(folderRepository.findDescendantIds(eq(TENANT_ID), eq(DRIVE_ID), eq(folderId), any()))
                    .thenReturn(Collections.emptyList());
            // Uses batch count method (Phase 2 optimization)
            when(documentRepository.countByTenantIdAndDriveIdAndFolderIdInAndStatus(
                    eq(TENANT_ID), eq(DRIVE_ID), anyList(), eq(DocumentStatus.ACTIVE)))
                    .thenReturn(0L);

            // When
            ContentOrchestrationService.FolderContentsStats stats =
                    orchestrationService.getFolderContentsStats(folderId);

            // Then
            assertThat(stats.getFolderCount()).isZero();
            assertThat(stats.getDocumentCount()).isZero();
            assertThat(stats.getTotalSize()).isZero();
        }

        @Test
        @DisplayName("Should throw exception for non-existent folder")
        void getFolderContentsStats_NotFound_ThrowsException() {
            // Given
            String folderId = "nonexistent";

            when(folderRepository.findByIdAndTenantIdAndDriveId(folderId, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> orchestrationService.getFolderContentsStats(folderId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // Helper methods

    private Folder createFolder(String id, String name) {
        Folder folder = new Folder();
        folder.setId(id);
        folder.setName(name);
        folder.setTenantId(TENANT_ID);
        folder.setDriveId(DRIVE_ID);
        folder.setStatus(FolderStatus.ACTIVE);
        folder.setOwnerId(USER_ID);
        folder.setCreatedBy(USER_ID);
        folder.setCreatedAt(Instant.now());
        folder.setUpdatedAt(Instant.now());
        folder.setFolderCount(0);
        folder.setDocumentCount(0);
        folder.setTotalSize(0L);
        folder.setDepth(0);
        folder.setPath("/" + name);
        folder.setAncestorIds(Collections.emptyList());
        return folder;
    }

    private Document createDocument(String id, String name) {
        Document doc = new Document();
        doc.setId(id);
        doc.setName(name);
        doc.setTenantId(TENANT_ID);
        doc.setDriveId(DRIVE_ID);
        doc.setStatus(DocumentStatus.ACTIVE);
        doc.setOwnerId(USER_ID);
        doc.setCreatedBy(USER_ID);
        doc.setCreatedAt(Instant.now());
        doc.setUpdatedAt(Instant.now());
        doc.setVersionCount(1);
        doc.setFileSize(1024L);
        doc.setContentType("application/pdf");
        doc.setExtension("pdf");
        return doc;
    }
}
