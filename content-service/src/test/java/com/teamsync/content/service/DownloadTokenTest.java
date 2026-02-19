package com.teamsync.content.service;

import com.teamsync.common.context.TenantContext;
import com.teamsync.common.exception.ResourceNotFoundException;
import com.teamsync.common.permission.PermissionService;
import com.teamsync.common.storage.CloudStorageProvider;
import com.teamsync.common.util.DownloadTokenUtil;
import com.teamsync.content.client.StorageServiceClient;
import com.teamsync.content.mapper.DocumentMapper;
import com.teamsync.content.model.Document;
import com.teamsync.content.model.Document.DocumentStatus;
import com.teamsync.content.repository.DocumentRepository;
import com.teamsync.content.repository.DocumentVersionRepository;
import com.teamsync.content.repository.FolderRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for Download functionality.
 * Covers:
 * - Token-based download URL generation
 * - Token validation and parsing
 * - File streaming through Content Service
 * - Token expiration handling
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Download Token Tests")
class DownloadTokenTest {

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
    private DownloadTokenUtil downloadTokenUtilMock;

    @InjectMocks
    private DocumentService documentService;

    // Real instance for token generation/validation tests
    private DownloadTokenUtil downloadTokenUtil;

    private static final String SECRET_KEY = "test-secret-key-for-signing-download-tokens-must-be-32chars";
    private static final String BUCKET = "teamsync-documents";

    private MockedStatic<TenantContext> tenantContextMock;

    private static final String TENANT_ID = "tenant-123";
    private static final String DRIVE_ID = "drive-456";
    private static final String USER_ID = "user-789";
    private static final String DOCUMENT_ID = "doc-001";
    private static final String STORAGE_KEY = "tenant-123/drive-456/12345678/test-document.pdf";
    private static final String STORAGE_BUCKET = "teamsync-documents";
    private static final String CONTENT_SERVICE_URL = "http://localhost:9081";

    @BeforeEach
    void setUp() {
        tenantContextMock = mockStatic(TenantContext.class);
        tenantContextMock.when(TenantContext::getTenantId).thenReturn(TENANT_ID);
        tenantContextMock.when(TenantContext::getDriveId).thenReturn(DRIVE_ID);
        tenantContextMock.when(TenantContext::getUserId).thenReturn(USER_ID);

        ReflectionTestUtils.setField(documentService, "contentServiceUrl", CONTENT_SERVICE_URL);

        // Create real DownloadTokenUtil for token tests
        downloadTokenUtil = new DownloadTokenUtil();
        ReflectionTestUtils.setField(downloadTokenUtil, "secret", SECRET_KEY);
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
                .name("test-document.pdf")
                .description("Test document")
                .contentType("application/pdf")
                .fileSize(1024L)
                .extension("pdf")
                .storageKey(STORAGE_KEY)
                .storageBucket(STORAGE_BUCKET)
                .currentVersion(1)
                .ownerId(USER_ID)
                .createdBy(USER_ID)
                .status(DocumentStatus.ACTIVE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("Generate Download URL Tests")
    class GenerateDownloadUrlTests {

        @Test
        @DisplayName("Should generate download URL with signed token")
        void generateDownloadUrl_Success() {
            // Given
            Document document = createTestDocument();
            Duration expiry = Duration.ofHours(1);
            String mockToken = "base64encodedpayload|signature";

            when(documentRepository.findByIdAndTenantIdAndDriveId(DOCUMENT_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(document));
            when(downloadTokenUtilMock.generateToken(
                    eq(TENANT_ID), eq(DRIVE_ID), eq(USER_ID),
                    eq(STORAGE_BUCKET), eq(STORAGE_KEY), any(Instant.class)))
                    .thenReturn(mockToken);

            // When
            String url = documentService.generateDownloadUrl(DOCUMENT_ID, expiry);

            // Then
            assertThat(url).isNotNull();
            assertThat(url).startsWith(CONTENT_SERVICE_URL + "/api/documents/download?token=");
            assertThat(url).contains("filename=test-document.pdf");

            // Verify token generation was called with correct parameters
            verify(downloadTokenUtilMock).generateToken(
                    eq(TENANT_ID),
                    eq(DRIVE_ID),
                    eq(USER_ID),
                    eq(STORAGE_BUCKET),
                    eq(STORAGE_KEY),
                    any(Instant.class)
            );
        }

        @Test
        @DisplayName("Should use default bucket when document bucket is null")
        void generateDownloadUrl_DefaultBucket() {
            // Given
            Document document = createTestDocument();
            document.setStorageBucket(null); // No bucket specified
            Duration expiry = Duration.ofHours(1);

            when(documentRepository.findByIdAndTenantIdAndDriveId(DOCUMENT_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(document));
            when(downloadTokenUtilMock.generateToken(anyString(), anyString(), anyString(),
                    eq("teamsync-documents"), anyString(), any(Instant.class)))
                    .thenReturn("token");

            // When
            String url = documentService.generateDownloadUrl(DOCUMENT_ID, expiry);

            // Then
            assertThat(url).isNotNull();
            verify(downloadTokenUtilMock).generateToken(
                    anyString(), anyString(), anyString(),
                    eq("teamsync-documents"), // Default bucket
                    anyString(), any(Instant.class)
            );
        }

        @Test
        @DisplayName("Should throw exception when document not found")
        void generateDownloadUrl_DocumentNotFound() {
            // Given
            when(documentRepository.findByIdAndTenantIdAndDriveId(DOCUMENT_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> documentService.generateDownloadUrl(DOCUMENT_ID, Duration.ofHours(1)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Document not found");
        }

        @Test
        @DisplayName("Should URL-encode token and filename")
        void generateDownloadUrl_UrlEncodesParameters() {
            // Given
            Document document = createTestDocument();
            document.setName("file with spaces & special.pdf");
            String tokenWithSpecialChars = "token+with/special=chars";

            when(documentRepository.findByIdAndTenantIdAndDriveId(DOCUMENT_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(document));
            when(downloadTokenUtilMock.generateToken(anyString(), anyString(), anyString(),
                    anyString(), anyString(), any(Instant.class)))
                    .thenReturn(tokenWithSpecialChars);

            // When
            String url = documentService.generateDownloadUrl(DOCUMENT_ID, Duration.ofHours(1));

            // Then
            assertThat(url).isNotNull();
            // URL-encoded values should not contain raw spaces or special characters
            assertThat(url).doesNotContain(" ");
        }
    }

    @Nested
    @DisplayName("Download File With Token Tests")
    class DownloadFileWithTokenTests {

        @Test
        @DisplayName("Should stream file from storage with token")
        void downloadFileWithToken_Success() {
            // Given
            String bucket = "teamsync-documents";
            String storageKey = "tenant/drive/file.pdf";
            byte[] fileContent = "test file content".getBytes();
            InputStream inputStream = new ByteArrayInputStream(fileContent);

            when(storageProvider.getObjectSize(bucket, storageKey)).thenReturn((long) fileContent.length);
            when(storageProvider.getContentType(bucket, storageKey)).thenReturn("application/pdf");
            when(storageProvider.download(bucket, storageKey)).thenReturn(inputStream);

            // When
            DocumentService.FileDownload download = documentService.downloadFileWithToken(bucket, storageKey);

            // Then
            assertThat(download).isNotNull();
            assertThat(download.inputStream()).isNotNull();
            assertThat(download.contentType()).isEqualTo("application/pdf");
            assertThat(download.fileSize()).isEqualTo(fileContent.length);

            // Verify storage provider was called
            verify(storageProvider).getObjectSize(bucket, storageKey);
            verify(storageProvider).getContentType(bucket, storageKey);
            verify(storageProvider).download(bucket, storageKey);
        }

        @Test
        @DisplayName("Should handle various content types")
        void downloadFileWithToken_VariousContentTypes() {
            // Given
            String bucket = "teamsync-documents";
            String storageKey = "tenant/drive/video.mp4";

            when(storageProvider.getObjectSize(bucket, storageKey)).thenReturn(1024L);
            when(storageProvider.getContentType(bucket, storageKey)).thenReturn("video/mp4");
            when(storageProvider.download(bucket, storageKey)).thenReturn(new ByteArrayInputStream(new byte[0]));

            // When
            DocumentService.FileDownload download = documentService.downloadFileWithToken(bucket, storageKey);

            // Then
            assertThat(download.contentType()).isEqualTo("video/mp4");
        }

        @Test
        @DisplayName("Should not require permission check for token-based downloads")
        void downloadFileWithToken_NoPermissionCheck() {
            // Given
            String bucket = "teamsync-documents";
            String storageKey = "tenant/drive/file.pdf";

            when(storageProvider.getObjectSize(bucket, storageKey)).thenReturn(100L);
            when(storageProvider.getContentType(bucket, storageKey)).thenReturn("application/pdf");
            when(storageProvider.download(bucket, storageKey)).thenReturn(new ByteArrayInputStream(new byte[0]));

            // When
            documentService.downloadFileWithToken(bucket, storageKey);

            // Then - permission service should NOT be called
            // Token validation already happened at controller level
            verifyNoInteractions(permissionService);
        }
    }

    @Nested
    @DisplayName("Token Utility Integration Tests")
    class TokenUtilityIntegrationTests {

        @Test
        @DisplayName("Token should contain all required fields")
        void tokenGeneration_ContainsAllFields() {
            // Given
            Document document = createTestDocument();
            Duration expiry = Duration.ofHours(1);

            when(documentRepository.findByIdAndTenantIdAndDriveId(DOCUMENT_ID, TENANT_ID, DRIVE_ID))
                    .thenReturn(Optional.of(document));
            when(downloadTokenUtilMock.generateToken(anyString(), anyString(), anyString(),
                    anyString(), anyString(), any(Instant.class)))
                    .thenReturn("encoded_token");

            // When
            documentService.generateDownloadUrl(DOCUMENT_ID, expiry);

            // Then - verify all context fields are passed to token generator
            verify(downloadTokenUtilMock).generateToken(
                    eq(TENANT_ID),      // tenantId
                    eq(DRIVE_ID),       // driveId
                    eq(USER_ID),        // userId
                    eq(STORAGE_BUCKET), // bucket
                    eq(STORAGE_KEY),    // storageKey
                    any(Instant.class)  // expiresAt
            );
        }
    }
}

/**
 * Standalone tests for DownloadTokenUtil class.
 * These test the token generation and validation logic directly.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DownloadTokenUtil Tests")
class DownloadTokenUtilTest {

    private DownloadTokenUtil downloadTokenUtil;

    private static final String SECRET = "test-secret-key-for-hmac-signing";
    private static final String TENANT_ID = "tenant-123";
    private static final String DRIVE_ID = "drive-456";
    private static final String USER_ID = "user-789";
    private static final String BUCKET = "teamsync-documents";
    private static final String STORAGE_KEY = "tenant/drive/file.pdf";

    @BeforeEach
    void setUp() {
        downloadTokenUtil = new DownloadTokenUtil();
        ReflectionTestUtils.setField(downloadTokenUtil, "secret", SECRET);
    }

    @Nested
    @DisplayName("Token Generation Tests")
    class TokenGenerationTests {

        @Test
        @DisplayName("Should generate valid token")
        void generateToken_CreatesValidToken() {
            // Given
            Instant expiresAt = Instant.now().plus(Duration.ofHours(1));

            // When
            String token = downloadTokenUtil.generateToken(
                    TENANT_ID, DRIVE_ID, USER_ID, BUCKET, STORAGE_KEY, expiresAt);

            // Then
            assertThat(token).isNotNull();
            assertThat(token).isNotBlank();
            assertThat(token).contains("|"); // Contains delimiter between payload and signature
        }

        @Test
        @DisplayName("Should generate different tokens for different inputs")
        void generateToken_UniqueForDifferentInputs() {
            // Given
            Instant expiresAt = Instant.now().plus(Duration.ofHours(1));

            // When
            String token1 = downloadTokenUtil.generateToken(
                    TENANT_ID, DRIVE_ID, USER_ID, BUCKET, STORAGE_KEY, expiresAt);
            String token2 = downloadTokenUtil.generateToken(
                    TENANT_ID, DRIVE_ID, "different-user", BUCKET, STORAGE_KEY, expiresAt);

            // Then
            assertThat(token1).isNotEqualTo(token2);
        }

        @Test
        @DisplayName("Should generate consistent tokens for same inputs")
        void generateToken_ConsistentForSameInputs() {
            // Given - fixed expiration time
            Instant expiresAt = Instant.ofEpochMilli(1700000000000L);

            // When
            String token1 = downloadTokenUtil.generateToken(
                    TENANT_ID, DRIVE_ID, USER_ID, BUCKET, STORAGE_KEY, expiresAt);
            String token2 = downloadTokenUtil.generateToken(
                    TENANT_ID, DRIVE_ID, USER_ID, BUCKET, STORAGE_KEY, expiresAt);

            // Then
            assertThat(token1).isEqualTo(token2);
        }
    }

    @Nested
    @DisplayName("Token Validation Tests")
    class TokenValidationTests {

        @Test
        @DisplayName("Should validate and parse valid token")
        void validateToken_ValidToken_ReturnsTokenData() {
            // Given
            Instant expiresAt = Instant.now().plus(Duration.ofHours(1));
            String token = downloadTokenUtil.generateToken(
                    TENANT_ID, DRIVE_ID, USER_ID, BUCKET, STORAGE_KEY, expiresAt);

            // When
            DownloadTokenUtil.TokenData tokenData = downloadTokenUtil.validateToken(token);

            // Then
            assertThat(tokenData).isNotNull();
            assertThat(tokenData.tenantId()).isEqualTo(TENANT_ID);
            assertThat(tokenData.driveId()).isEqualTo(DRIVE_ID);
            assertThat(tokenData.userId()).isEqualTo(USER_ID);
            assertThat(tokenData.bucket()).isEqualTo(BUCKET);
            assertThat(tokenData.storageKey()).isEqualTo(STORAGE_KEY);
        }

        @Test
        @DisplayName("Should reject expired token")
        void validateToken_ExpiredToken_ReturnsNull() {
            // Given - token that expired in the past
            Instant expiresAt = Instant.now().minus(Duration.ofHours(1));
            String token = downloadTokenUtil.generateToken(
                    TENANT_ID, DRIVE_ID, USER_ID, BUCKET, STORAGE_KEY, expiresAt);

            // When
            DownloadTokenUtil.TokenData tokenData = downloadTokenUtil.validateToken(token);

            // Then
            assertThat(tokenData).isNull();
        }

        @Test
        @DisplayName("Should reject tampered token")
        void validateToken_TamperedToken_ReturnsNull() {
            // Given
            Instant expiresAt = Instant.now().plus(Duration.ofHours(1));
            String validToken = downloadTokenUtil.generateToken(
                    TENANT_ID, DRIVE_ID, USER_ID, BUCKET, STORAGE_KEY, expiresAt);

            // Tamper with the token
            String tamperedToken = validToken.substring(0, validToken.length() - 5) + "XXXXX";

            // When
            DownloadTokenUtil.TokenData tokenData = downloadTokenUtil.validateToken(tamperedToken);

            // Then
            assertThat(tokenData).isNull();
        }

        @Test
        @DisplayName("Should reject token with invalid format")
        void validateToken_InvalidFormat_ReturnsNull() {
            // Given
            String invalidToken = "not-a-valid-token-format";

            // When
            DownloadTokenUtil.TokenData tokenData = downloadTokenUtil.validateToken(invalidToken);

            // Then
            assertThat(tokenData).isNull();
        }

        @Test
        @DisplayName("Should reject empty token")
        void validateToken_EmptyToken_ReturnsNull() {
            // When
            DownloadTokenUtil.TokenData tokenData = downloadTokenUtil.validateToken("");

            // Then
            assertThat(tokenData).isNull();
        }

        @Test
        @DisplayName("Should reject null token")
        void validateToken_NullToken_ReturnsNull() {
            // When
            DownloadTokenUtil.TokenData tokenData = downloadTokenUtil.validateToken(null);

            // Then
            assertThat(tokenData).isNull();
        }
    }

    @Nested
    @DisplayName("Token Security Tests")
    class TokenSecurityTests {

        @Test
        @DisplayName("Should use constant-time comparison for signature")
        void signatureValidation_UsesConstantTimeComparison() {
            // This test verifies the behavior, not timing
            // The actual constant-time comparison is implementation detail

            // Given
            Instant expiresAt = Instant.now().plus(Duration.ofHours(1));
            String token = downloadTokenUtil.generateToken(
                    TENANT_ID, DRIVE_ID, USER_ID, BUCKET, STORAGE_KEY, expiresAt);

            // When/Then - multiple validation attempts should give consistent results
            for (int i = 0; i < 10; i++) {
                DownloadTokenUtil.TokenData result = downloadTokenUtil.validateToken(token);
                assertThat(result).isNotNull();
            }

            // Tampered token should consistently fail
            String tamperedToken = token.replace('a', 'b');
            for (int i = 0; i < 10; i++) {
                DownloadTokenUtil.TokenData result = downloadTokenUtil.validateToken(tamperedToken);
                assertThat(result).isNull();
            }
        }

        @Test
        @DisplayName("Should handle special characters in payload")
        void tokenGeneration_HandlesSpecialCharacters() {
            // Given - storage key with special characters (but not pipe which is the delimiter)
            String specialStorageKey = "tenant/drive/file with spaces & special-chars.pdf";
            Instant expiresAt = Instant.now().plus(Duration.ofHours(1));

            // When
            String token = downloadTokenUtil.generateToken(
                    TENANT_ID, DRIVE_ID, USER_ID, BUCKET, specialStorageKey, expiresAt);

            // Then
            DownloadTokenUtil.TokenData tokenData = downloadTokenUtil.validateToken(token);
            assertThat(tokenData).isNotNull();
            assertThat(tokenData.storageKey()).isEqualTo(specialStorageKey);
        }
    }
}
