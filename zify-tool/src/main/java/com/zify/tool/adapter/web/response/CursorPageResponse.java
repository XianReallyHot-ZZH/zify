package com.zify.tool.adapter.web.response;

import java.util.List;

/**
 * Keyset 分页 HTTP 响应：{records,nextCursor,hasMore}（tool 模块本地副本）。
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
