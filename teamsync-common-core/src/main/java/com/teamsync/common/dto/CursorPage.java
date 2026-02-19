package com.teamsync.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CursorPage<T> {

    private List<T> items;
    private String nextCursor;
    private String previousCursor;
    private boolean hasMore;
    private long totalCount;
    private int limit;

    public static <T> CursorPage<T> of(List<T> items, String nextCursor, boolean hasMore) {
        return CursorPage.<T>builder()
                .items(items)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .build();
    }

    public static <T> CursorPage<T> of(List<T> items, String nextCursor, boolean hasMore, long totalCount) {
        return CursorPage.<T>builder()
                .items(items)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .totalCount(totalCount)
                .build();
    }

    public static <T> CursorPage<T> empty() {
        return CursorPage.<T>builder()
                .items(List.of())
                .hasMore(false)
                .totalCount(0)
                .build();
    }
}
