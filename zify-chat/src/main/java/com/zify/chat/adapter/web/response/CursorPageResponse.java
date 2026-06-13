package com.zify.chat.adapter.web.response;

import java.util.List;

/**
 * Keyset 分页 HTTP 响应：Controller 把 {@code CursorPageResult} 编码为 {records,nextCursor,hasMore}。
 * nextCursor 为 opaque 字符串（Base64Url(timestamp#id)），前端透传。
 */
public class CursorPageResponse<T> {

    private List<T> records;
    private String nextCursor;
    private boolean hasMore;

    public CursorPageResponse() {
    }

    public CursorPageResponse(List<T> records, String nextCursor, boolean hasMore) {
        this.records = records;
        this.nextCursor = nextCursor;
        this.hasMore = hasMore;
    }

    public List<T> getRecords() {
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records;
    }

    public String getNextCursor() {
        return nextCursor;
    }

    public void setNextCursor(String nextCursor) {
        this.nextCursor = nextCursor;
    }

    public boolean getHasMore() {
        return hasMore;
    }

    public void setHasMore(boolean hasMore) {
        this.hasMore = hasMore;
    }
}
