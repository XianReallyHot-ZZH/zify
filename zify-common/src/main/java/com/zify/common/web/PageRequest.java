package com.zify.common.web;

/**
 * 分页请求参数。
 */
public class PageRequest {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private Integer page = DEFAULT_PAGE;
    private Integer pageSize = DEFAULT_PAGE_SIZE;

    public Integer getPage() {
        return normalizePage(page);
    }

    public void setPage(Integer page) {
        this.page = normalizePage(page);
    }

    public Integer getPageSize() {
        return normalizePageSize(pageSize);
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = normalizePageSize(pageSize);
    }

    public long offset() {
        return (long) (getPage() - 1) * getPageSize();
    }

    private static Integer normalizePage(Integer value) {
        if (value == null || value < 1) {
            return DEFAULT_PAGE;
        }
        return value;
    }

    private static Integer normalizePageSize(Integer value) {
        if (value == null || value < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(value, MAX_PAGE_SIZE);
    }
}
