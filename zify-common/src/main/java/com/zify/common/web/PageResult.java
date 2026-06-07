package com.zify.common.web;

import java.util.Collections;
import java.util.List;

/**
 * 分页结果。
 *
 * @param <T> 单条记录类型
 */
public class PageResult<T> {

    private final List<T> records;
    private final Long total;
    private final Integer page;
    private final Integer pageSize;

    public PageResult(List<T> records, Long total, Integer page, Integer pageSize) {
        this.records = records == null ? Collections.emptyList() : records;
        this.total = total == null ? 0L : total;
        this.page = page == null ? PageRequest.DEFAULT_PAGE : page;
        this.pageSize = pageSize == null ? PageRequest.DEFAULT_PAGE_SIZE : pageSize;
    }

    public static <T> PageResult<T> of(List<T> records, Long total, Integer page, Integer pageSize) {
        return new PageResult<>(records, total, page, pageSize);
    }

    public List<T> getRecords() {
        return records;
    }

    public Long getTotal() {
        return total;
    }

    public Integer getPage() {
        return page;
    }

    public Integer getPageSize() {
        return pageSize;
    }
}
