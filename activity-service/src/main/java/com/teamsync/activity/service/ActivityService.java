package com.teamsync.activity.service;

import com.teamsync.activity.dto.ActivityCursorPage;
import com.teamsync.activity.dto.ActivityDTO;
import com.teamsync.activity.model.Activity;
import com.teamsync.activity.repository.ActivityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing and retrieving activities.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityService {

    private final ActivityRepository activityRepository;

    /**
     * Get activities for a team/drive with cursor-based pagination.
     *
     * @param tenantId Tenant ID
     * @param driveId Drive ID (team drive)
     * @param cursor Optional cursor for pagination (base64 encoded timestamp)
     * @param limit Number of items to fetch
     * @return Paginated activities
     */
    public ActivityCursorPage getTeamActivities(String tenantId, String driveId, String cursor, int limit) {
        log.debug("Fetching team activities: tenantId={}, driveId={}, cursor={}, limit={}",
                tenantId, driveId, cursor, limit);

        // Ensure limit is reasonable
        int effectiveLimit = Math.min(Math.max(limit, 1), 100);

        // Fetch one extra to determine hasMore
        Pageable pageable = PageRequest.of(0, effectiveLimit + 1, Sort.by(Sort.Direction.DESC, "createdAt"));

        List<Activity> activities;

        if (cursor != null && !cursor.isEmpty()) {
            // Decode cursor (base64 encoded ISO timestamp)
            Instant cursorTime = decodeCursor(cursor);
            activities = activityRepository.findByTenantIdAndDriveIdAndCreatedAtBefore(
                    tenantId, driveId, cursorTime, pageable);
        } else {
            activities = activityRepository.findByTenantIdAndDriveIdOrderByCreatedAtDesc(
                    tenantId, driveId, pageable);
        }

        // Check if there are more results
        boolean hasMore = activities.size() > effectiveLimit;
        if (hasMore) {
            activities = activities.subList(0, effectiveLimit);
        }

        // Convert to DTOs
        List<ActivityDTO> items = activities.stream()
                .map(ActivityDTO::fromEntity)
                .collect(Collectors.toList());

        // Generate next cursor from last item
        String nextCursor = null;
        if (hasMore && !activities.isEmpty()) {
            Activity lastActivity = activities.get(activities.size() - 1);
            nextCursor = encodeCursor(lastActivity.getCreatedAt());
        }

        // Get total count (optional, may be expensive for large datasets)
        Long totalCount = null;
        if (cursor == null) {
            totalCount = activityRepository.countByTenantIdAndDriveId(tenantId, driveId);
        }

        return ActivityCursorPage.builder()
                .items(items)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .totalCount(totalCount)
                .build();
    }

    /**
     * Get activities for a specific document.
     */
    public ActivityCursorPage getDocumentActivities(String tenantId, String driveId, String documentId, int limit) {
        log.debug("Fetching document activities: documentId={}", documentId);

        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Activity> activities = activityRepository.findByTenantIdAndDriveIdAndResourceIdOrderByCreatedAtDesc(
                tenantId, driveId, documentId, pageable);

        List<ActivityDTO> items = activities.stream()
                .map(ActivityDTO::fromEntity)
                .collect(Collectors.toList());

        return ActivityCursorPage.builder()
                .items(items)
                .hasMore(false)
                .build();
    }

    /**
     * Get activities for the current user.
     */
    public ActivityCursorPage getUserActivities(String tenantId, String userId, int limit) {
        log.debug("Fetching user activities: userId={}", userId);

        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Activity> activities = activityRepository.findByTenantIdAndUserIdOrderByCreatedAtDesc(
                tenantId, userId, pageable);

        List<ActivityDTO> items = activities.stream()
                .map(ActivityDTO::fromEntity)
                .collect(Collectors.toList());

        return ActivityCursorPage.builder()
                .items(items)
                .hasMore(false)
                .build();
    }

    /**
     * Record a new activity.
     */
    public Activity recordActivity(Activity activity) {
        if (activity.getCreatedAt() == null) {
            activity.setCreatedAt(Instant.now());
        }
        log.info("Recording activity: action={}, resource={}, user={}",
                activity.getAction(), activity.getResourceId(), activity.getUserId());
        return activityRepository.save(activity);
    }

    /**
     * Encode an Instant to a cursor string.
     */
    private String encodeCursor(Instant instant) {
        return Base64.getUrlEncoder().encodeToString(instant.toString().getBytes());
    }

    /**
     * Decode a cursor string to an Instant.
     */
    private Instant decodeCursor(String cursor) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor));
            return Instant.parse(decoded);
        } catch (Exception e) {
            log.warn("Invalid cursor format: {}", cursor);
            return Instant.now();
        }
    }
}
