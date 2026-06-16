package com.zify.tool.api.dto;

/**
 * MCP Server 已发现的单个工具（详情/展开列表用）。
 */
public class McpDiscoveredToolResponse {

    private String id;
    private String name;
    private String description;
    private String sourceType;
    private Integer enabled;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
}
