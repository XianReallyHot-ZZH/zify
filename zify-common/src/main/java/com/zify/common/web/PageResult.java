package com.zify.common.web;

import java.util.List;

/**
 * 分页响应体
 *
 * @param <T> 单条记录类型
 */
public class PageResult<T> extends Result<List<T>> {

    private final long total;
    private final int page;
    private final int size;

    public PageResult(List<T> data, long total, int page, int size) {
        super(200, "success", data);
        this.total = total;
        this.page = page;
        this.size = size;
    }

    public long getTotal() {
        return total;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }
}
