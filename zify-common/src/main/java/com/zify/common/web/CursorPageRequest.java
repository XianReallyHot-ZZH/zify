package com.zify.common.web;

import java.time.LocalDateTime;

/**
 * Cursor pagination request for large tables.
 */
public class CursorPageRequest {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;

    private LocalDateTime cursorCreatedAt;
    private String cursorId;
    private Integer limit = DEFAULT_LIMIT;

    public LocalDateTime getCursorCreatedAt() {
        return cursorCreatedAt;
    }

    public void setCursorCreatedAt(LocalDateTime cursorCreatedAt) {
        this.cursorCreatedAt = cursorCreatedAt;
    }

    public String getCursorId() {
        return cursorId;
    }

    public void setCursorId(String cursorId) {
        this.cursorId = cursorId;
    }

    public Integer getLimit() {
        return normalizeLimit(limit);
    }

    public void setLimit(Integer limit) {
        this.limit = normalizeLimit(limit);
    }

    public boolean hasCursor() {
        return cursorCreatedAt != null && cursorId != null && !cursorId.isBlank();
    }

    public boolean isValidCursor() {
        boolean hasCreatedAt = cursorCreatedAt != null;
        boolean hasId = cursorId != null && !cursorId.isBlank();
        return hasCreatedAt == hasId;
    }

    private static Integer normalizeLimit(Integer value) {
        if (value == null || value < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(value, MAX_LIMIT);
    }
}
