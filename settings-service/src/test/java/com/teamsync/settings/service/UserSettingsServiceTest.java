package com.teamsync.settings.service;

import com.teamsync.settings.dto.UserSettingsDTO;
import com.teamsync.settings.model.UserSettings;
import com.teamsync.settings.repository.UserSettingsRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserSettingsService.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("User Settings Service Tests")
class UserSettingsServiceTest {

    @Mock
    private UserSettingsRepository repository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private SettingsMapper settingsMapper;

    @InjectMocks
    private UserSettingsService userSettingsService;

    private static final String TENANT_ID = "tenant-123";
    private static final String USER_ID = "user-123";

    @Nested
    @DisplayName("Get User Settings Tests")
    class GetUserSettingsTests {

        @Test
        @DisplayName("Should return existing settings when found")
        void getUserSettings_ReturnsExisting() {
            // Given
            UserSettings existingSettings = UserSettings.builder()
                    .id("settings-1")
                    .tenantId(TENANT_ID)
                    .userId(USER_ID)
                    .theme("dark")
                    .language("es")
                    .build();
            when(repository.findByTenantIdAndUserId(TENANT_ID, USER_ID))
                    .thenReturn(Optional.of(existingSettings));

            // When
            UserSettingsDTO result = userSettingsService.getUserSettings(TENANT_ID, USER_ID);

            // Then
            assertThat(result.getTheme()).isEqualTo("dark");
            assertThat(result.getLanguage()).isEqualTo("es");
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("Should create default settings when not found")
        void getUserSettings_CreatesDefault() {
            // Given
            when(repository.findByTenantIdAndUserId(TENANT_ID, USER_ID))
                    .thenReturn(Optional.empty());
            when(repository.save(any(UserSettings.class)))
                    .thenAnswer(invocation -> {
                        UserSettings settings = invocation.getArgument(0);
                        settings.setId("new-settings-id");
                        return settings;
                    });

            // When
            UserSettingsDTO result = userSettingsService.getUserSettings(TENANT_ID, USER_ID);

            // Then
            assertThat(result).isNotNull();
            verify(repository).save(any(UserSettings.class));
        }
    }

    @Nested
    @DisplayName("Update User Settings Tests")
    class UpdateUserSettingsTests {

        @Test
        @DisplayName("Should update existing settings")
        void updateUserSettings_UpdatesExisting() {
            // Given
            UserSettings existingSettings = UserSettings.builder()
                    .id("settings-1")
                    .tenantId(TENANT_ID)
                    .userId(USER_ID)
                    .theme("light")
                    .build();
            when(repository.findByTenantIdAndUserId(TENANT_ID, USER_ID))
                    .thenReturn(Optional.of(existingSettings));
            when(repository.save(any(UserSettings.class)))
                    .thenReturn(existingSettings);

            Map<String, Object> updates = Map.of("theme", "dark");

            // When
            UserSettingsDTO result = userSettingsService.updateUserSettings(TENANT_ID, USER_ID, updates);

            // Then
            verify(settingsMapper).applyUserSettingsUpdates(any(UserSettings.class), eq(updates));
            verify(repository).save(any(UserSettings.class));
        }

        @Test
        @DisplayName("Should create and update when settings not found")
        void updateUserSettings_CreatesWhenNotFound() {
            // Given
            when(repository.findByTenantIdAndUserId(TENANT_ID, USER_ID))
                    .thenReturn(Optional.empty());
            when(repository.save(any(UserSettings.class)))
                    .thenAnswer(invocation -> {
                        UserSettings settings = invocation.getArgument(0);
                        settings.setId("new-id");
                        return settings;
                    });

            Map<String, Object> updates = Map.of("theme", "dark");

            // When
            userSettingsService.updateUserSettings(TENANT_ID, USER_ID, updates);

            // Then
            verify(repository, times(2)).save(any(UserSettings.class)); // Create + Update
        }

        @Test
        @DisplayName("Should publish Kafka event on update")
        void updateUserSettings_PublishesEvent() {
            // Given
            UserSettings existingSettings = UserSettings.builder()
                    .id("settings-1")
                    .tenantId(TENANT_ID)
                    .userId(USER_ID)
                    .build();
            when(repository.findByTenantIdAndUserId(TENANT_ID, USER_ID))
                    .thenReturn(Optional.of(existingSettings));
            when(repository.save(any(UserSettings.class)))
                    .thenReturn(existingSettings);

            Map<String, Object> updates = Map.of("theme", "dark");

            // When
            userSettingsService.updateUserSettings(TENANT_ID, USER_ID, updates);

            // Then
            verify(kafkaTemplate).send(anyString(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("Reset User Settings Tests")
    class ResetUserSettingsTests {

        @Test
        @DisplayName("Should delete and recreate settings on reset")
        void resetUserSettings_DeletesAndRecreates() {
            // Given
            when(repository.save(any(UserSettings.class)))
                    .thenAnswer(invocation -> {
                        UserSettings settings = invocation.getArgument(0);
                        settings.setId("new-id");
                        return settings;
                    });

            // When
            UserSettingsDTO result = userSettingsService.resetUserSettings(TENANT_ID, USER_ID);

            // Then
            verify(repository).deleteByTenantIdAndUserId(TENANT_ID, USER_ID);
            verify(repository).save(any(UserSettings.class));
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should return default values after reset")
        void resetUserSettings_ReturnsDefaults() {
            // Given
            ArgumentCaptor<UserSettings> captor = ArgumentCaptor.forClass(UserSettings.class);
            when(repository.save(captor.capture()))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            UserSettingsDTO result = userSettingsService.resetUserSettings(TENANT_ID, USER_ID);

            // Then
            UserSettings savedSettings = captor.getValue();
            assertThat(savedSettings.getTenantId()).isEqualTo(TENANT_ID);
            assertThat(savedSettings.getUserId()).isEqualTo(USER_ID);
        }
    }
}
