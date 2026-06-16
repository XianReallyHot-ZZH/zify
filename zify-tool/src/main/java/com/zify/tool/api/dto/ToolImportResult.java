package com.zify.tool.api.dto;

import java.util.List;

/**
 * OpenAPI 导入结果。
 */
public class ToolImportResult {

    private List<ToolDetailResponse> created;
    private List<String> skipped;

    public List<ToolDetailResponse> getCreated() { return created; }
    public void setCreated(List<ToolDetailResponse> created) { this.created = created; }
    public List<String> getSkipped() { return skipped; }
    public void setSkipped(List<String> skipped) { this.skipped = skipped; }
}
