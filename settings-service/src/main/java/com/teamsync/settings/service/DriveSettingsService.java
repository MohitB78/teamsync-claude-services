package com.teamsync.settings.service;

import com.teamsync.common.config.KafkaTopics;
import com.teamsync.common.dto.CursorPage;
import com.teamsync.settings.dto.DriveSettingsDTO;
import com.teamsync.settings.event.SettingsChangedEvent;
import com.teamsync.settings.model.DriveSettings;
import com.teamsync.settings.repository.DriveSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Service for managing drive-specific settings.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DriveSettingsService {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_PINNED_FOLDERS = 100;
    private static final int MAX_FAVORITE_DOCUMENTS = 100;

    private final DriveSettingsRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final SettingsMapper settingsMapper;

    /**
     * Get drive settings, creating defaults if they don't exist.
     */
    @Transactional
    public DriveSettingsDTO getDriveSettings(String tenantId, String userId, String driveId) {
        log.debug("Getting drive settings for tenant: {}, user: {}, drive: {}", tenantId, userId, driveId);

        DriveSettings settings = repository.findByTenantIdAndUserIdAndDriveId(tenantId, userId, driveId)
                .orElseGet(() -> createDefaultDriveSettings(tenantId, userId, driveId));

        return DriveSettingsDTO.fromEntity(settings);
    }

    /**
     * Get all drive settings for a user (limited to MAX_PAGE_SIZE for safety).
     * @deprecated Use {@link #getDriveSettingsPaginated} for better memory efficiency
     */
    @Deprecated
    public List<DriveSettingsDTO> getAllDriveSettings(String tenantId, String userId) {
        log.debug("Getting all drive settings for tenant: {}, user: {}", tenantId, userId);

        // Use pagination with max page size to prevent memory exhaustion
        PageRequest pageRequest = PageRequest.of(0, MAX_PAGE_SIZE, Sort.by(Sort.Direction.ASC, "id"));
        return repository.findByTenantIdAndUserId(tenantId, userId, pageRequest).stream()
                .map(DriveSettingsDTO::fromEntity)
                .toList();
    }

    /**
     * Get drive settings with cursor-based pagination.
     *
     * @param tenantId the tenant ID
     * @param userId the user ID
     * @param cursor the cursor for pagination (null for first page)
     * @param limit the page size (default 50, max 100)
     * @return paginated drive settings
     */
    public CursorPage<DriveSettingsDTO> getDriveSettingsPaginated(String tenantId, String userId, String cursor, Integer limit) {
        log.debug("Getting paginated drive settings for tenant: {}, user: {}, cursor: {}", tenantId, userId, cursor);

        int pageSize = (limit != null && limit > 0) ? Math.min(limit, MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;
        PageRequest pageRequest = PageRequest.of(0, pageSize + 1, Sort.by(Sort.Direction.ASC, "id"));

        List<DriveSettings> results;
        if (cursor == null || cursor.isEmpty()) {
            results = repository.findByTenantIdAndUserIdOrderByIdAsc(tenantId, userId, pageRequest);
        } else {
            results = repository.findByTenantIdAndUserIdWithCursor(tenantId, userId, cursor, pageRequest);
        }

        boolean hasMore = results.size() > pageSize;
        if (hasMore) {
            results = results.subList(0, pageSize);
        }

        List<DriveSettingsDTO> items = results.stream()
                .map(DriveSettingsDTO::fromEntity)
                .toList();

        String nextCursor = hasMore && !results.isEmpty() ? results.getLast().getId() : null;

        return CursorPage.<DriveSettingsDTO>builder()
                .items(items)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .limit(pageSize)
                .build();
    }

    /**
     * Update drive settings with partial update support.
     */
    @Transactional
    public DriveSettingsDTO updateDriveSettings(String tenantId, String userId, String driveId, Map<String, Object> updates) {
        log.info("Updating drive settings for tenant: {}, user: {}, drive: {}", tenantId, userId, driveId);

        DriveSettings settings = repository.findByTenantIdAndUserIdAndDriveId(tenantId, userId, driveId)
                .orElseGet(() -> createDefaultDriveSettings(tenantId, userId, driveId));

        // Apply updates
        settingsMapper.applyDriveSettingsUpdates(settings, updates);

        // Save
        DriveSettings saved = repository.save(settings);
        log.info("Drive settings updated for drive: {}", driveId);

        // Publish event
        publishSettingsChangedEvent(tenantId, userId, driveId, updates);

        return DriveSettingsDTO.fromEntity(saved);
    }

    /**
     * Add a folder to pinned folders.
     *
     * @throws IllegalStateException if the maximum number of pinned folders (100) is reached
     */
    @Transactional
    public DriveSettingsDTO pinFolder(String tenantId, String userId, String driveId, String folderId) {
        log.info("Pinning folder {} for user {} in drive {}", folderId, userId, driveId);

        DriveSettings settings = repository.findByTenantIdAndUserIdAndDriveId(tenantId, userId, driveId)
                .orElseGet(() -> createDefaultDriveSettings(tenantId, userId, driveId));

        if (!settings.getPinnedFolders().contains(folderId)) {
            if (settings.getPinnedFolders().size() >= MAX_PINNED_FOLDERS) {
                throw new IllegalStateException(
                        "Maximum number of pinned folders (" + MAX_PINNED_FOLDERS + ") reached. " +
                        "Please unpin some folders before adding new ones.");
            }
            settings.getPinnedFolders().add(folderId);
            settings = repository.save(settings);
        }

        return DriveSettingsDTO.fromEntity(settings);
    }

    /**
     * Remove a folder from pinned folders.
     */
    @Transactional
    public DriveSettingsDTO unpinFolder(String tenantId, String userId, String driveId, String folderId) {
        log.info("Unpinning folder {} for user {} in drive {}", folderId, userId, driveId);

        DriveSettings settings = repository.findByTenantIdAndUserIdAndDriveId(tenantId, userId, driveId)
                .orElseGet(() -> createDefaultDriveSettings(tenantId, userId, driveId));

        settings.getPinnedFolders().remove(folderId);
        settings = repository.save(settings);

        return DriveSettingsDTO.fromEntity(settings);
    }

    /**
     * Add a document to favorites.
     *
     * @throws IllegalStateException if the maximum number of favorites (100) is reached
     */
    @Transactional
    public DriveSettingsDTO favoriteDocument(String tenantId, String userId, String driveId, String documentId) {
        log.info("Adding document {} to favorites for user {} in drive {}", documentId, userId, driveId);

        DriveSettings settings = repository.findByTenantIdAndUserIdAndDriveId(tenantId, userId, driveId)
                .orElseGet(() -> createDefaultDriveSettings(tenantId, userId, driveId));

        if (!settings.getFavoriteDocuments().contains(documentId)) {
            if (settings.getFavoriteDocuments().size() >= MAX_FAVORITE_DOCUMENTS) {
                throw new IllegalStateException(
                        "Maximum number of favorite documents (" + MAX_FAVORITE_DOCUMENTS + ") reached. " +
                        "Please remove some favorites before adding new ones.");
            }
            settings.getFavoriteDocuments().add(documentId);
            settings = repository.save(settings);
        }

        return DriveSettingsDTO.fromEntity(settings);
    }

    /**
     * Remove a document from favorites.
     */
    @Transactional
    public DriveSettingsDTO unfavoriteDocument(String tenantId, String userId, String driveId, String documentId) {
        log.info("Removing document {} from favorites for user {} in drive {}", documentId, userId, driveId);

        DriveSettings settings = repository.findByTenantIdAndUserIdAndDriveId(tenantId, userId, driveId)
                .orElseGet(() -> createDefaultDriveSettings(tenantId, userId, driveId));

        settings.getFavoriteDocuments().remove(documentId);
        settings = repository.save(settings);

        return DriveSettingsDTO.fromEntity(settings);
    }

    /**
     * Update last visited folder.
     */
    @Transactional
    public void updateLastFolder(String tenantId, String userId, String driveId, String folderId) {
        DriveSettings settings = repository.findByTenantIdAndUserIdAndDriveId(tenantId, userId, driveId)
                .orElseGet(() -> createDefaultDriveSettings(tenantId, userId, driveId));

        if (settings.isRememberLastFolder()) {
            settings.setLastFolderId(folderId);
            repository.save(settings);
        }
    }

    /**
     * Reset drive settings to defaults.
     */
    @Transactional
    public DriveSettingsDTO resetDriveSettings(String tenantId, String userId, String driveId) {
        log.info("Resetting drive settings for tenant: {}, user: {}, drive: {}", tenantId, userId, driveId);

        repository.deleteByTenantIdAndUserIdAndDriveId(tenantId, userId, driveId);
        DriveSettings settings = createDefaultDriveSettings(tenantId, userId, driveId);

        return DriveSettingsDTO.fromEntity(settings);
    }

    /**
     * Delete all settings for a drive (called when drive is deleted).
     */
    @Transactional
    public void deleteDriveSettings(String tenantId, String driveId) {
        log.info("Deleting all drive settings for tenant: {}, drive: {}", tenantId, driveId);
        repository.deleteByTenantIdAndDriveId(tenantId, driveId);
    }

    private DriveSettings createDefaultDriveSettings(String tenantId, String userId, String driveId) {
        log.debug("Creating default drive settings for tenant: {}, user: {}, drive: {}", tenantId, userId, driveId);

        DriveSettings settings = DriveSettings.builder()
                .tenantId(tenantId)
                .userId(userId)
                .driveId(driveId)
                .build();

        return repository.save(settings);
    }

    private void publishSettingsChangedEvent(String tenantId, String userId, String driveId, Map<String, Object> changes) {
        try {
            SettingsChangedEvent event = SettingsChangedEvent.builder()
                    .tenantId(tenantId)
                    .userId(userId)
                    .driveId(driveId)
                    .settingsType("DRIVE")
                    .changedFields(changes.keySet().stream().toList())
                    .build();

            kafkaTemplate.send(KafkaTopics.SETTINGS_DRIVE_UPDATED, driveId, event);
            log.debug("Published drive settings changed event for drive: {}", driveId);
        } catch (Exception e) {
            log.warn("Failed to publish settings changed event: {}", e.getMessage());
        }
    }
}
