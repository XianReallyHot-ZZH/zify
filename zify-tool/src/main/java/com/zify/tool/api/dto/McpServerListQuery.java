package com.zify.tool.api.dto;

import com.zify.common.web.PageRequest;

/**
 * MCP Server 列表查询（OFFSET 分页）。
 */
public class McpServerListQuery extends PageRequest {

    private Integer enabled;
    private String status;

    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
