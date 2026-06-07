package com.zify.common.web;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Cursor pagination result for large tables.
 *
 * @param <T> single record type
 */
public class CursorPageResult<T> {

    private final List<T> records;
    private final String nextCursorId;
    private final LocalDateTime nextCursorCreatedAt;
    private final Boolean hasMore;

    public CursorPageResult(List<T> records, String nextCursorId, LocalDateTime nextCursorCreatedAt, Boolean hasMore) {
        this.records = records == null ? Collections.emptyList() : records;
        this.nextCursorId = nextCursorId;
        this.nextCursorCreatedAt = nextCursorCreatedAt;
        this.hasMore = hasMore == null ? Boolean.FALSE : hasMore;
    }

    public static <T> CursorPageResult<T> of(
            List<T> records,
            String nextCursorId,
            LocalDateTime nextCursorCreatedAt,
            Boolean hasMore
    ) {
        return new CursorPageResult<>(records, nextCursorId, nextCursorCreatedAt, hasMore);
    }

    public List<T> getRecords() {
        return records;
    }

    public String getNextCursorId() {
        return nextCursorId;
    }

    public LocalDateTime getNextCursorCreatedAt() {
        return nextCursorCreatedAt;
    }

    public Boolean getHasMore() {
        return hasMore;
    }
}
