package com.zify.tool.api.dto;

import com.zify.common.web.PageRequest;

/**
 * 工具列表查询（OFFSET 分页）。
 */
public class ToolListQuery extends PageRequest {

    private String sourceType;
    private String mcpServerId;
    private Integer enabled;

    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getMcpServerId() { return mcpServerId; }
    public void setMcpServerId(String mcpServerId) { this.mcpServerId = mcpServerId; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
}
