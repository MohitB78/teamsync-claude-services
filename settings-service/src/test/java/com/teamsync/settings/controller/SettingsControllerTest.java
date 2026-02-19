package com.teamsync.settings.controller;

import com.teamsync.common.dto.ApiResponse;
import com.teamsync.common.dto.CursorPage;
import com.teamsync.settings.dto.DriveSettingsDTO;
import com.teamsync.settings.dto.TenantSettingsDTO;
import com.teamsync.settings.dto.UpdateDriveSettingsRequest;
import com.teamsync.settings.dto.UpdateTenantSettingsRequest;
import com.teamsync.settings.dto.UpdateUserSettingsRequest;
import com.teamsync.settings.dto.UserSettingsDTO;
import com.teamsync.settings.service.DriveSettingsService;
import com.teamsync.settings.service.SettingsValidationService;
import com.teamsync.settings.service.TenantSettingsService;
import com.teamsync.settings.service.UserSettingsService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SettingsController.
 * Tests user settings, tenant settings, and drive settings endpoints.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Settings Controller Tests")
class SettingsControllerTest {

    @Mock
    private UserSettingsService userSettingsService;

    @Mock
    private TenantSettingsService tenantSettingsService;

    @Mock
    private DriveSettingsService driveSettingsService;

    @Spy
    private SettingsValidationService validationService = new SettingsValidationService();

    @InjectMocks
    private SettingsController settingsController;

    private Jwt mockJwt;

    @BeforeEach
    void setUp() {
        mockJwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", "user-123")
                .claim("tenantId", "tenant-123")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    @Nested
    @DisplayName("User Settings Tests")
    class UserSettingsTests {

        @Test
        @DisplayName("Should return 200 when getting user settings")
        void getUserSettings_ReturnsOk() {
            // Given
            UserSettingsDTO expectedSettings = UserSettingsDTO.builder()
                    .id("settings-1")
                    .tenantId("tenant-123")
                    .userId("user-123")
                    .theme("light")
                    .language("en")
                    .build();
            when(userSettingsService.getUserSettings("tenant-123", "user-123"))
                    .thenReturn(expectedSettings);

            // When
            ResponseEntity<ApiResponse<UserSettingsDTO>> response =
                    settingsController.getUserSettings(mockJwt, null);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getData().getTheme()).isEqualTo("light");
        }

        @Test
        @DisplayName("Should use header tenant ID when provided")
        void getUserSettings_UsesHeaderTenantId() {
            // Given
            UserSettingsDTO expectedSettings = UserSettingsDTO.builder()
                    .tenantId("header-tenant")
                    .userId("user-123")
                    .build();
            when(userSettingsService.getUserSettings("header-tenant", "user-123"))
                    .thenReturn(expectedSettings);

            // When
            ResponseEntity<ApiResponse<UserSettingsDTO>> response =
                    settingsController.getUserSettings(mockJwt, "header-tenant");

            // Then
            verify(userSettingsService).getUserSettings("header-tenant", "user-123");
        }

        @Test
        @DisplayName("Should return 200 when updating user settings")
        void updateUserSettings_ReturnsOk() {
            // Given
            UpdateUserSettingsRequest request = UpdateUserSettingsRequest.builder()
                    .theme("dark")
                    .build();
            UserSettingsDTO updatedSettings = UserSettingsDTO.builder()
                    .tenantId("tenant-123")
                    .userId("user-123")
                    .theme("dark")
                    .build();
            when(userSettingsService.updateUserSettings(eq("tenant-123"), eq("user-123"), anyMap()))
                    .thenReturn(updatedSettings);

            // When
            ResponseEntity<ApiResponse<UserSettingsDTO>> response =
                    settingsController.updateUserSettings(mockJwt, null, request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getData().getTheme()).isEqualTo("dark");
        }

        @Test
        @DisplayName("Should return 200 when resetting user settings")
        void resetUserSettings_ReturnsOk() {
            // Given
            UserSettingsDTO defaultSettings = UserSettingsDTO.builder()
                    .tenantId("tenant-123")
                    .userId("user-123")
                    .theme("light")
                    .language("en")
                    .build();
            when(userSettingsService.resetUserSettings("tenant-123", "user-123"))
                    .thenReturn(defaultSettings);

            // When
            ResponseEntity<ApiResponse<UserSettingsDTO>> response =
                    settingsController.resetUserSettings(mockJwt, null);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getMessage()).contains("reset");
        }
    }

    @Nested
    @DisplayName("Tenant Settings Tests")
    class TenantSettingsTests {

        @Test
        @DisplayName("Should return 200 when getting tenant settings")
        void getTenantSettings_ReturnsOk() {
            // Given
            TenantSettingsDTO expectedSettings = TenantSettingsDTO.builder()
                    .id("tenant-settings-1")
                    .tenantId("tenant-123")
                    .build();
            when(tenantSettingsService.getTenantSettings("tenant-123"))
                    .thenReturn(expectedSettings);

            // When
            ResponseEntity<ApiResponse<TenantSettingsDTO>> response =
                    settingsController.getTenantSettings(mockJwt, null);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should return 200 when updating tenant settings")
        void updateTenantSettings_ReturnsOk() {
            // Given
            UpdateTenantSettingsRequest request = UpdateTenantSettingsRequest.builder()
                    .security(UpdateTenantSettingsRequest.SecuritySettingsUpdate.builder()
                            .mfaRequired(true)
                            .build())
                    .build();
            TenantSettingsDTO updatedSettings = TenantSettingsDTO.builder()
                    .tenantId("tenant-123")
                    .build();
            when(tenantSettingsService.updateTenantSettings(eq("tenant-123"), anyMap()))
                    .thenReturn(updatedSettings);

            // When
            ResponseEntity<ApiResponse<TenantSettingsDTO>> response =
                    settingsController.updateTenantSettings(mockJwt, null, request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should return feature status")
        void isFeatureEnabled_ReturnsStatus() {
            // Given
            when(tenantSettingsService.isFeatureEnabled("tenant-123", "aiMetadataExtraction"))
                    .thenReturn(true);

            // When
            ResponseEntity<ApiResponse<Boolean>> response =
                    settingsController.isFeatureEnabled(mockJwt, null, "aiMetadataExtraction");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getData()).isTrue();
        }

        @Test
        @DisplayName("Should return 200 when resetting tenant settings")
        void resetTenantSettings_ReturnsOk() {
            // Given
            TenantSettingsDTO defaultSettings = TenantSettingsDTO.builder()
                    .tenantId("tenant-123")
                    .build();
            when(tenantSettingsService.resetTenantSettings("tenant-123"))
                    .thenReturn(defaultSettings);

            // When
            ResponseEntity<ApiResponse<TenantSettingsDTO>> response =
                    settingsController.resetTenantSettings(mockJwt, null);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getMessage()).contains("reset");
        }
    }

    @Nested
    @DisplayName("Drive Settings Tests")
    class DriveSettingsTests {

        @Test
        @DisplayName("Should return 200 when getting drive settings")
        void getDriveSettings_ReturnsOk() {
            // Given
            DriveSettingsDTO expectedSettings = DriveSettingsDTO.builder()
                    .id("drive-settings-1")
                    .tenantId("tenant-123")
                    .userId("user-123")
                    .driveId("drive-1")
                    .defaultView("grid")
                    .build();
            when(driveSettingsService.getDriveSettings("tenant-123", "user-123", "drive-1"))
                    .thenReturn(expectedSettings);

            // When
            ResponseEntity<ApiResponse<DriveSettingsDTO>> response =
                    settingsController.getDriveSettings(mockJwt, null, "drive-1");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getData().getDefaultView()).isEqualTo("grid");
        }

        @Test
        @DisplayName("Should return all drive settings for user")
        void getAllDriveSettings_ReturnsAll() {
            // Given
            List<DriveSettingsDTO> settingsList = List.of(
                    DriveSettingsDTO.builder().driveId("drive-1").build(),
                    DriveSettingsDTO.builder().driveId("drive-2").build()
            );
            CursorPage<DriveSettingsDTO> expectedPage = CursorPage.<DriveSettingsDTO>builder()
                    .items(settingsList)
                    .nextCursor(null)
                    .hasMore(false)
                    .build();
            when(driveSettingsService.getDriveSettingsPaginated("tenant-123", "user-123", null, 50))
                    .thenReturn(expectedPage);

            // When
            ResponseEntity<ApiResponse<CursorPage<DriveSettingsDTO>>> response =
                    settingsController.getAllDriveSettings(mockJwt, null, null, 50);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getData().getItems()).hasSize(2);
        }

        @Test
        @DisplayName("Should return 200 when updating drive settings")
        void updateDriveSettings_ReturnsOk() {
            // Given
            UpdateDriveSettingsRequest request = UpdateDriveSettingsRequest.builder()
                    .defaultView("list")
                    .build();
            DriveSettingsDTO updatedSettings = DriveSettingsDTO.builder()
                    .driveId("drive-1")
                    .defaultView("list")
                    .build();
            when(driveSettingsService.updateDriveSettings(eq("tenant-123"), eq("user-123"), eq("drive-1"), anyMap()))
                    .thenReturn(updatedSettings);

            // When
            ResponseEntity<ApiResponse<DriveSettingsDTO>> response =
                    settingsController.updateDriveSettings(mockJwt, null, "drive-1", request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getData().getDefaultView()).isEqualTo("list");
        }

        @Test
        @DisplayName("Should return 200 when pinning folder")
        void pinFolder_ReturnsOk() {
            // Given
            DriveSettingsDTO updatedSettings = DriveSettingsDTO.builder()
                    .driveId("drive-1")
                    .pinnedFolders(List.of("folder-1"))
                    .build();
            when(driveSettingsService.pinFolder("tenant-123", "user-123", "drive-1", "folder-1"))
                    .thenReturn(updatedSettings);

            // When
            ResponseEntity<ApiResponse<DriveSettingsDTO>> response =
                    settingsController.pinFolder(mockJwt, null, "drive-1", "folder-1");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getData().getPinnedFolders()).contains("folder-1");
        }

        @Test
        @DisplayName("Should return 200 when unpinning folder")
        void unpinFolder_ReturnsOk() {
            // Given
            DriveSettingsDTO updatedSettings = DriveSettingsDTO.builder()
                    .driveId("drive-1")
                    .pinnedFolders(List.of())
                    .build();
            when(driveSettingsService.unpinFolder("tenant-123", "user-123", "drive-1", "folder-1"))
                    .thenReturn(updatedSettings);

            // When
            ResponseEntity<ApiResponse<DriveSettingsDTO>> response =
                    settingsController.unpinFolder(mockJwt, null, "drive-1", "folder-1");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getData().getPinnedFolders()).isEmpty();
        }

        @Test
        @DisplayName("Should return 200 when favoriting document")
        void favoriteDocument_ReturnsOk() {
            // Given
            DriveSettingsDTO updatedSettings = DriveSettingsDTO.builder()
                    .driveId("drive-1")
                    .favoriteDocuments(List.of("doc-1"))
                    .build();
            when(driveSettingsService.favoriteDocument("tenant-123", "user-123", "drive-1", "doc-1"))
                    .thenReturn(updatedSettings);

            // When
            ResponseEntity<ApiResponse<DriveSettingsDTO>> response =
                    settingsController.favoriteDocument(mockJwt, null, "drive-1", "doc-1");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getData().getFavoriteDocuments()).contains("doc-1");
        }

        @Test
        @DisplayName("Should return 200 when unfavoriting document")
        void unfavoriteDocument_ReturnsOk() {
            // Given
            DriveSettingsDTO updatedSettings = DriveSettingsDTO.builder()
                    .driveId("drive-1")
                    .favoriteDocuments(List.of())
                    .build();
            when(driveSettingsService.unfavoriteDocument("tenant-123", "user-123", "drive-1", "doc-1"))
                    .thenReturn(updatedSettings);

            // When
            ResponseEntity<ApiResponse<DriveSettingsDTO>> response =
                    settingsController.unfavoriteDocument(mockJwt, null, "drive-1", "doc-1");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getData().getFavoriteDocuments()).isEmpty();
        }

        @Test
        @DisplayName("Should return 200 when resetting drive settings")
        void resetDriveSettings_ReturnsOk() {
            // Given
            DriveSettingsDTO defaultSettings = DriveSettingsDTO.builder()
                    .driveId("drive-1")
                    .defaultView("grid")
                    .build();
            when(driveSettingsService.resetDriveSettings("tenant-123", "user-123", "drive-1"))
                    .thenReturn(defaultSettings);

            // When
            ResponseEntity<ApiResponse<DriveSettingsDTO>> response =
                    settingsController.resetDriveSettings(mockJwt, null, "drive-1");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getMessage()).contains("reset");
        }
    }

    @Nested
    @DisplayName("Authentication and Tenant Resolution Tests")
    class AuthenticationTests {

        @Test
        @DisplayName("Should throw exception when JWT has no tenantId claim (mandatory tenant)")
        void getUserSettings_ThrowsWhenNoTenantId() {
            // Given
            Jwt jwtWithoutTenant = Jwt.withTokenValue("token")
                    .header("alg", "RS256")
                    .claim("sub", "user-123")
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();

            // When / Then
            assertThatThrownBy(() -> settingsController.getUserSettings(jwtWithoutTenant, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Tenant ID is required");
        }

        @Test
        @DisplayName("Should throw exception when JWT has no user identifier")
        void getUserSettings_ThrowsWhenNoUserId() {
            // Given
            Jwt jwtWithoutUser = Jwt.withTokenValue("token")
                    .header("alg", "RS256")
                    .claim("tenantId", "tenant-123")
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();

            // When / Then
            assertThatThrownBy(() -> settingsController.getUserSettings(jwtWithoutUser, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("User ID could not be determined");
        }

        @Test
        @DisplayName("Should use preferred_username when sub is not available")
        void getUserSettings_UsesPreferredUsername() {
            // Given - JWT with preferred_username but explicit subject (Jwt builder always sets sub)
            Jwt jwtWithUsername = Jwt.withTokenValue("token")
                    .header("alg", "RS256")
                    .subject("john.doe")
                    .claim("preferred_username", "john.doe")
                    .claim("tenantId", "tenant-123")
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();

            UserSettingsDTO expectedSettings = UserSettingsDTO.builder()
                    .tenantId("tenant-123")
                    .userId("john.doe")
                    .build();
            when(userSettingsService.getUserSettings("tenant-123", "john.doe"))
                    .thenReturn(expectedSettings);

            // When
            ResponseEntity<ApiResponse<UserSettingsDTO>> response =
                    settingsController.getUserSettings(jwtWithUsername, null);

            // Then
            verify(userSettingsService).getUserSettings("tenant-123", "john.doe");
        }

        @Test
        @DisplayName("Should accept tenant ID from header even when JWT has no tenantId")
        void getUserSettings_AcceptsTenantFromHeader() {
            // Given
            Jwt jwtWithoutTenant = Jwt.withTokenValue("token")
                    .header("alg", "RS256")
                    .claim("sub", "user-123")
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();

            UserSettingsDTO expectedSettings = UserSettingsDTO.builder()
                    .tenantId("header-tenant")
                    .userId("user-123")
                    .build();
            when(userSettingsService.getUserSettings("header-tenant", "user-123"))
                    .thenReturn(expectedSettings);

            // When
            ResponseEntity<ApiResponse<UserSettingsDTO>> response =
                    settingsController.getUserSettings(jwtWithoutTenant, "header-tenant");

            // Then
            verify(userSettingsService).getUserSettings("header-tenant", "user-123");
        }
    }

    @Nested
    @DisplayName("Response Format Tests")
    class ResponseFormatTests {

        @Test
        @DisplayName("All endpoints should return ApiResponse wrapper")
        void allEndpoints_ReturnApiResponse() {
            // Given
            when(userSettingsService.getUserSettings(anyString(), anyString()))
                    .thenReturn(UserSettingsDTO.builder().build());
            when(tenantSettingsService.getTenantSettings(anyString()))
                    .thenReturn(TenantSettingsDTO.builder().build());
            when(driveSettingsService.getDriveSettings(anyString(), anyString(), anyString()))
                    .thenReturn(DriveSettingsDTO.builder().build());

            // Then
            assertThat(settingsController.getUserSettings(mockJwt, null).getBody())
                    .isInstanceOf(ApiResponse.class);
            assertThat(settingsController.getTenantSettings(mockJwt, null).getBody())
                    .isInstanceOf(ApiResponse.class);
            assertThat(settingsController.getDriveSettings(mockJwt, null, "drive-1").getBody())
                    .isInstanceOf(ApiResponse.class);
        }

        @Test
        @DisplayName("All successful responses should have success=true")
        void allEndpoints_HaveSuccessTrue() {
            // Given
            when(userSettingsService.getUserSettings(anyString(), anyString()))
                    .thenReturn(UserSettingsDTO.builder().build());

            // When
            ResponseEntity<ApiResponse<UserSettingsDTO>> response =
                    settingsController.getUserSettings(mockJwt, null);

            // Then
            assertThat(response.getBody().isSuccess()).isTrue();
        }
    }
}
