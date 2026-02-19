package com.teamsync.activity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Cursor-based pagination response for activities.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityCursorPage {

    private List<ActivityDTO> items;
    private String nextCursor;
    private boolean hasMore;
    private Long totalCount;
}
