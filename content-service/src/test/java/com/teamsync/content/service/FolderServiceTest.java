package com.teamsync.content.service;

import com.teamsync.common.context.TenantContext;
import com.teamsync.common.dto.CursorPage;
import com.teamsync.common.exception.ResourceNotFoundException;
import com.teamsync.content.dto.folder.*;
import com.teamsync.content.mapper.FolderMapper;
import com.teamsync.content.model.Folder;
import com.teamsync.content.model.Folder.FolderStatus;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FolderService Tests")
class FolderServiceTest {

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private FolderMapper folderMapper;

    @InjectMocks
    private FolderService folderService;

    @Captor
    private ArgumentCaptor<Folder> folderCaptor;

    private MockedStatic<TenantContext> tenantContextMock;

    private static final String TENANT_ID = "tenant-123";
    private static final String DRIVE_ID = "drive-456";
    private static final String USER_ID = "user-789";
    private static final String FOLDER_ID = "folder-001";
    private static final String PARENT_FOLDER_ID = "folder-parent";

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

    private Folder createTestFolder() {
        return Folder.builder()
                .id(FOLDER_ID)
                .tenantId(TENANT_ID)
                .driveId(DRIVE_ID)
                .parentId(null)
                .name("Test Folder")
                .description("Test folder description")
                .path("/Test Folder")
                .depth(0)
                .ancestorIds(Collections.emptyList())
                .folderCount(2)
                .documentCount(5)
                .totalSize(10240L)
                .isStarred(false)
                .isPinned(false)
                .ownerId(USER_ID)
                .createdBy(USER_ID)
                .lastModifiedBy(USER_ID)
                .status(FolderStatus.ACTIVE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .accessedAt(Instant.now())
                .build();
    }

    private Folder createChildFolder() {
        List<String> ancestors = new ArrayList<>();
        ancestors.add(PARENT_FOLDER_ID);

        return Folder.builder()
                .id(FOLDER_ID)
                .tenantId(TENANT_ID)
                .driveId(DRIVE_ID)
                .parentId(PARENT_FOLDER_ID)
                .name("Child Folder")
                .description("Child folder description")
                .path("/Parent Folder/Child Folder")
                .depth(1)
                .ancestorIds(ancestors)
                .folderCount(0)
                .documentCount(3)
                .totalSize(5120L)
                .isStarred(false)
                .isPinned(false)
                .ownerId(USER_ID)
                .createdBy(USER_ID)
                .lastModifiedBy(USER_ID)
                .status(FolderStatus.ACTIVE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .accessedAt(Instant.now())
                .build();
    }

    private FolderDTO createTestFolderDTO() {
        return FolderDTO.builder()
                .id(FOLDER_ID)
                .tenantId(TENANT_ID)
                .driveId(DRIVE_ID)
                .name("Test Folder")
                .description("Test folder description")
                .path("/Test Folder")
                .depth(0)
                .folderCount(2)
                .documentCount(5)
                .totalSize(10240L)
                .isStarred(false)
                .isPinned(false)
                .ownerId(USER_ID)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("getFolder Tests")
    class GetFolderTests {

        @Test
        @DisplayName("Should return folder when found")
        void getFolder_WhenFound_ReturnsFolder() {
            // Given
            Folder folder = createTestFolder();
            FolderDTO expectedDTO = createTestFolderDTO();

            when(folderRepository.findByIdAndTenantIdAndDriveId(FOLDER_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(folder));
            when(folderMapper.toDTO(any(Folder.class))).thenReturn(expectedDTO);

            // When
            FolderDTO result = folderService.getFolder(FOLDER_ID);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(FOLDER_ID);
            assertThat(result.getName()).isEqualTo("Test Folder");

            verify(folderRepository).findByIdAndTenantIdAndDriveId(FOLDER_ID, TENANT_ID, DRIVE_ID);
            verify(folderRepository).save(any(Folder.class)); // Updates accessedAt
        }

        @Test
        @DisplayName("Should throw exception when folder not found")
        void getFolder_WhenNotFound_ThrowsException() {
            // Given
            when(folderRepository.findByIdAndTenantIdAndDriveId(FOLDER_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> folderService.getFolder(FOLDER_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Folder not found");
        }
    }

    @Nested
    @DisplayName("createFolder Tests")
    class CreateFolderTests {

        @Test
        @DisplayName("Should create root folder successfully")
        void createFolder_RootFolder_Success() {
            // Given
            CreateFolderRequest request = CreateFolderRequest.builder()
                    .name("New Root Folder")
                    .description("A new root folder")
                    .parentId(null)
                    .build();

            when(folderRepository.existsByTenantIdAndDriveIdAndParentIdAndNameAndStatusNot(
                    anyString(), anyString(), any(), anyString(), any(FolderStatus.class)))
                    .thenReturn(false);
            when(folderRepository.save(any(Folder.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(folderMapper.toDTO(any(Folder.class))).thenReturn(createTestFolderDTO());

            // When
            FolderDTO result = folderService.createFolder(request);

            // Then
            assertThat(result).isNotNull();
            verify(folderRepository).save(folderCaptor.capture());

            Folder savedFolder = folderCaptor.getValue();
            assertThat(savedFolder.getName()).isEqualTo("New Root Folder");
            assertThat(savedFolder.getPath()).isEqualTo("/New Root Folder");
            assertThat(savedFolder.getDepth()).isEqualTo(0);
            assertThat(savedFolder.getAncestorIds()).isEmpty();
            assertThat(savedFolder.getOwnerId()).isEqualTo(USER_ID);
        }

        @Test
        @DisplayName("Should create child folder with correct path and ancestors")
        void createFolder_ChildFolder_Success() {
            // Given
            Folder parentFolder = createTestFolder();
            parentFolder.setId(PARENT_FOLDER_ID);
            parentFolder.setName("Parent Folder");
            parentFolder.setPath("/Parent Folder");

            CreateFolderRequest request = CreateFolderRequest.builder()
                    .name("Child Folder")
                    .description("A child folder")
                    .parentId(PARENT_FOLDER_ID)
                    .build();

            when(folderRepository.existsByTenantIdAndDriveIdAndParentIdAndNameAndStatusNot(
                    anyString(), anyString(), anyString(), anyString(), any(FolderStatus.class)))
                    .thenReturn(false);
            when(folderRepository.findByIdAndTenantIdAndDriveId(PARENT_FOLDER_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(parentFolder));
            when(folderRepository.save(any(Folder.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(folderMapper.toDTO(any(Folder.class))).thenReturn(createTestFolderDTO());

            // When
            FolderDTO result = folderService.createFolder(request);

            // Then
            assertThat(result).isNotNull();
            verify(folderRepository).save(folderCaptor.capture());
            verify(folderRepository).incrementFolderCount(PARENT_FOLDER_ID, TENANT_ID, DRIVE_ID, 1);

            Folder savedFolder = folderCaptor.getValue();
            assertThat(savedFolder.getName()).isEqualTo("Child Folder");
            assertThat(savedFolder.getPath()).isEqualTo("/Parent Folder/Child Folder");
            assertThat(savedFolder.getDepth()).isEqualTo(1);
            assertThat(savedFolder.getAncestorIds()).containsExactly(PARENT_FOLDER_ID);
        }

        @Test
        @DisplayName("Should throw exception when duplicate name exists")
        void createFolder_WhenDuplicateName_ThrowsException() {
            // Given
            CreateFolderRequest request = CreateFolderRequest.builder()
                    .name("Existing Folder")
                    .parentId(null)
                    .build();

            when(folderRepository.existsByTenantIdAndDriveIdAndParentIdAndNameAndStatusNot(
                    TENANT_ID, DRIVE_ID, null, "Existing Folder", FolderStatus.TRASHED))
                    .thenReturn(true);

            // When/Then
            assertThatThrownBy(() -> folderService.createFolder(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already exists");

            verify(folderRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw exception when parent folder not found")
        void createFolder_WhenParentNotFound_ThrowsException() {
            // Given
            CreateFolderRequest request = CreateFolderRequest.builder()
                    .name("Child Folder")
                    .parentId("non-existent-parent")
                    .build();

            when(folderRepository.existsByTenantIdAndDriveIdAndParentIdAndNameAndStatusNot(
                    anyString(), anyString(), anyString(), anyString(), any(FolderStatus.class)))
                    .thenReturn(false);
            when(folderRepository.findByIdAndTenantIdAndDriveId("non-existent-parent", TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> folderService.createFolder(request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Parent folder not found");
        }
    }

    @Nested
    @DisplayName("updateFolder Tests")
    class UpdateFolderTests {

        @Test
        @DisplayName("Should update folder metadata successfully")
        void updateFolder_Metadata_Success() {
            // Given
            Folder existingFolder = createTestFolder();
            UpdateFolderRequest request = UpdateFolderRequest.builder()
                    .description("Updated description")
                    .color("#FF5733")
                    .isStarred(true)
                    .tags(List.of("important", "work"))
                    .build();

            when(folderRepository.findByIdAndTenantIdAndDriveId(FOLDER_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(existingFolder));
            when(folderRepository.save(any(Folder.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(folderMapper.toDTO(any(Folder.class))).thenReturn(createTestFolderDTO());

            // When
            FolderDTO result = folderService.updateFolder(FOLDER_ID, request);

            // Then
            assertThat(result).isNotNull();
            verify(folderRepository).save(folderCaptor.capture());

            Folder savedFolder = folderCaptor.getValue();
            assertThat(savedFolder.getDescription()).isEqualTo("Updated description");
            assertThat(savedFolder.getColor()).isEqualTo("#FF5733");
            assertThat(savedFolder.getIsStarred()).isTrue();
            assertThat(savedFolder.getTags()).containsExactly("important", "work");
        }

        @Test
        @DisplayName("Should update folder name and path")
        void updateFolder_Name_UpdatesPath() {
            // Given
            Folder existingFolder = createTestFolder();
            UpdateFolderRequest request = UpdateFolderRequest.builder()
                    .name("Renamed Folder")
                    .build();

            when(folderRepository.findByIdAndTenantIdAndDriveId(FOLDER_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(existingFolder));
            when(folderRepository.existsByTenantIdAndDriveIdAndParentIdAndNameAndStatusNot(
                    anyString(), anyString(), any(), anyString(), any(FolderStatus.class)))
                    .thenReturn(false);
            when(folderRepository.findDescendants(anyString(), anyString(), anyString(), any(FolderStatus.class), any()))
                    .thenReturn(Collections.emptyList());
            when(folderRepository.save(any(Folder.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(folderMapper.toDTO(any(Folder.class))).thenReturn(createTestFolderDTO());

            // When
            FolderDTO result = folderService.updateFolder(FOLDER_ID, request);

            // Then
            verify(folderRepository).save(folderCaptor.capture());

            Folder savedFolder = folderCaptor.getValue();
            assertThat(savedFolder.getName()).isEqualTo("Renamed Folder");
            assertThat(savedFolder.getPath()).isEqualTo("/Renamed Folder");
        }

        @Test
        @DisplayName("Should throw exception when renaming to existing name")
        void updateFolder_WhenDuplicateName_ThrowsException() {
            // Given
            Folder existingFolder = createTestFolder();
            UpdateFolderRequest request = UpdateFolderRequest.builder()
                    .name("Existing Name")
                    .build();

            when(folderRepository.findByIdAndTenantIdAndDriveId(FOLDER_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(existingFolder));
            when(folderRepository.existsByTenantIdAndDriveIdAndParentIdAndNameAndStatusNot(
                    TENANT_ID, DRIVE_ID, null, "Existing Name", FolderStatus.TRASHED))
                    .thenReturn(true);

            // When/Then
            assertThatThrownBy(() -> folderService.updateFolder(FOLDER_ID, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already exists");
        }
    }

    @Nested
    @DisplayName("moveFolder Tests")
    class MoveFolderTests {

        @Test
        @DisplayName("Should move folder to new parent successfully")
        void moveFolder_Success() {
            // Given
            Folder folder = createTestFolder();
            folder.setName("Moving Folder");
            folder.setPath("/Moving Folder");

            Folder targetParent = createTestFolder();
            targetParent.setId("target-parent-id");
            targetParent.setName("Target Parent");
            targetParent.setPath("/Target Parent");
            targetParent.setAncestorIds(Collections.emptyList());
            targetParent.setDepth(0);

            MoveFolderRequest request = MoveFolderRequest.builder()
                    .targetParentId("target-parent-id")
                    .build();

            when(folderRepository.findByIdAndTenantIdAndDriveId(FOLDER_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(folder));
            when(folderRepository.findByIdAndTenantIdAndDriveId("target-parent-id", TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(targetParent));
            when(folderRepository.existsByTenantIdAndDriveIdAndParentIdAndNameAndStatusNot(
                    anyString(), anyString(), anyString(), anyString(), any(FolderStatus.class)))
                    .thenReturn(false);
            when(folderRepository.findDescendants(anyString(), anyString(), anyString(), any(FolderStatus.class), any()))
                    .thenReturn(Collections.emptyList());
            when(folderMapper.toDTO(any(Folder.class))).thenReturn(createTestFolderDTO());

            // When
            FolderDTO result = folderService.moveFolder(FOLDER_ID, request);

            // Then
            assertThat(result).isNotNull();
            verify(folderRepository).save(folderCaptor.capture());
            verify(folderRepository).incrementFolderCount("target-parent-id", TENANT_ID, DRIVE_ID, 1);

            Folder movedFolder = folderCaptor.getValue();
            assertThat(movedFolder.getParentId()).isEqualTo("target-parent-id");
            assertThat(movedFolder.getPath()).isEqualTo("/Target Parent/Moving Folder");
            assertThat(movedFolder.getDepth()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should throw exception when moving folder to itself")
        void moveFolder_ToItself_ThrowsException() {
            // Given
            Folder folder = createTestFolder();
            MoveFolderRequest request = MoveFolderRequest.builder()
                    .targetParentId(FOLDER_ID)
                    .build();

            when(folderRepository.findByIdAndTenantIdAndDriveId(FOLDER_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(folder));

            // When/Then
            assertThatThrownBy(() -> folderService.moveFolder(FOLDER_ID, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cannot move folder to itself");
        }

        @Test
        @DisplayName("Should throw exception when moving folder to its descendant")
        void moveFolder_ToDescendant_ThrowsException() {
            // Given
            Folder folder = createTestFolder();

            List<String> targetAncestors = new ArrayList<>();
            targetAncestors.add(FOLDER_ID);

            Folder descendant = createTestFolder();
            descendant.setId("descendant-id");
            descendant.setAncestorIds(targetAncestors);

            MoveFolderRequest request = MoveFolderRequest.builder()
                    .targetParentId("descendant-id")
                    .build();

            when(folderRepository.findByIdAndTenantIdAndDriveId(FOLDER_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(folder));
            when(folderRepository.findByIdAndTenantIdAndDriveId("descendant-id", TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(descendant));

            // When/Then
            assertThatThrownBy(() -> folderService.moveFolder(FOLDER_ID, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cannot move folder to its own descendant");
        }

        @Test
        @DisplayName("Should throw exception when duplicate name exists in target")
        void moveFolder_WhenDuplicateNameInTarget_ThrowsException() {
            // Given
            Folder folder = createTestFolder();
            Folder targetParent = createTestFolder();
            targetParent.setId("target-parent-id");
            targetParent.setAncestorIds(Collections.emptyList());

            MoveFolderRequest request = MoveFolderRequest.builder()
                    .targetParentId("target-parent-id")
                    .build();

            when(folderRepository.findByIdAndTenantIdAndDriveId(FOLDER_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(folder));
            when(folderRepository.findByIdAndTenantIdAndDriveId("target-parent-id", TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(targetParent));
            when(folderRepository.existsByTenantIdAndDriveIdAndParentIdAndNameAndStatusNot(
                    TENANT_ID, DRIVE_ID, "target-parent-id", "Test Folder", FolderStatus.TRASHED))
                    .thenReturn(true);

            // When/Then
            assertThatThrownBy(() -> folderService.moveFolder(FOLDER_ID, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already exists in target location");
        }
    }

    @Nested
    @DisplayName("listFolders Tests")
    class ListFoldersTests {

        @Test
        @DisplayName("Should list root folders")
        void listFolders_RootFolders() {
            // Given
            Folder folder1 = createTestFolder();
            Folder folder2 = createTestFolder();
            folder2.setId("folder-002");
            folder2.setName("Folder 2");

            when(folderRepository.findRootFolders(eq(TENANT_ID), eq(DRIVE_ID), eq(FolderStatus.ACTIVE), any()))
                    .thenReturn(List.of(folder1, folder2));
            when(folderMapper.toDTO(any(Folder.class))).thenReturn(createTestFolderDTO());

            // When
            CursorPage<FolderDTO> result = folderService.listFolders(null, null, 10);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getItems()).hasSize(2);
            assertThat(result.isHasMore()).isFalse();
        }

        @Test
        @DisplayName("Should list child folders with pagination")
        void listFolders_ChildFoldersWithPagination() {
            // Given
            List<Folder> folders = new ArrayList<>();
            for (int i = 0; i < 11; i++) {
                Folder folder = createChildFolder();
                folder.setId("folder-" + i);
                folders.add(folder);
            }

            when(folderRepository.findByTenantIdAndDriveIdAndParentIdAndStatus(
                    eq(TENANT_ID), eq(DRIVE_ID), eq(PARENT_FOLDER_ID), eq(FolderStatus.ACTIVE), any()))
                    .thenReturn(folders);
            when(folderMapper.toDTO(any(Folder.class))).thenReturn(createTestFolderDTO());

            // When
            CursorPage<FolderDTO> result = folderService.listFolders(PARENT_FOLDER_ID, null, 10);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getItems()).hasSize(10);
            assertThat(result.isHasMore()).isTrue();
            assertThat(result.getNextCursor()).isNotNull();
        }
    }

    @Nested
    @DisplayName("getStarredFolders Tests")
    class GetStarredFoldersTests {

        @Test
        @DisplayName("Should return starred folders")
        void getStarredFolders_Success() {
            // Given
            Folder starredFolder = createTestFolder();
            starredFolder.setIsStarred(true);

            when(folderRepository.findByTenantIdAndDriveIdAndIsStarredAndStatus(
                    eq(TENANT_ID), eq(DRIVE_ID), eq(true), eq(FolderStatus.ACTIVE), any()))
                    .thenReturn(List.of(starredFolder));
            when(folderMapper.toDTO(any(Folder.class))).thenReturn(createTestFolderDTO());

            // When
            List<FolderDTO> result = folderService.getStarredFolders();

            // Then
            assertThat(result).hasSize(1);
            verify(folderRepository).findByTenantIdAndDriveIdAndIsStarredAndStatus(
                    eq(TENANT_ID), eq(DRIVE_ID), eq(true), eq(FolderStatus.ACTIVE), any());
        }
    }

    @Nested
    @DisplayName("searchFolders Tests")
    class SearchFoldersTests {

        @Test
        @DisplayName("Should search folders by name")
        void searchFolders_Success() {
            // Given
            Folder matchingFolder = createTestFolder();

            when(folderRepository.searchByName(anyString(), anyString(), anyString(), any(FolderStatus.class), any()))
                    .thenReturn(List.of(matchingFolder));
            when(folderMapper.toDTO(any(Folder.class))).thenReturn(createTestFolderDTO());

            // When
            List<FolderDTO> result = folderService.searchFolders("Test", 10);

            // Then
            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("getFolderCount Tests")
    class GetFolderCountTests {

        @Test
        @DisplayName("Should return folder count")
        void getFolderCount_Success() {
            // Given
            when(folderRepository.countByTenantIdAndDriveIdAndStatus(TENANT_ID, DRIVE_ID, FolderStatus.ACTIVE))
                    .thenReturn(15L);

            // When
            long result = folderService.getFolderCount();

            // Then
            assertThat(result).isEqualTo(15L);
        }
    }

    @Nested
    @DisplayName("getFolderTree Tests")
    class GetFolderTreeTests {

        @Test
        @DisplayName("Should return folder tree from root")
        void getFolderTree_FromRoot() {
            // Given
            Folder rootFolder = createTestFolder();
            FolderTreeNode treeNode = FolderTreeNode.builder()
                    .id(FOLDER_ID)
                    .name("Test Folder")
                    .path("/Test Folder")
                    .depth(0)
                    .hasChildren(true)
                    .children(Collections.emptyList())
                    .build();

            when(folderRepository.findRootFolders(eq(TENANT_ID), eq(DRIVE_ID), eq(FolderStatus.ACTIVE), any()))
                    .thenReturn(List.of(rootFolder));
            when(folderMapper.toTreeNode(any(Folder.class))).thenReturn(treeNode);

            // When
            List<FolderTreeNode> result = folderService.getFolderTree(null, 2);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("Test Folder");
        }
    }
}
