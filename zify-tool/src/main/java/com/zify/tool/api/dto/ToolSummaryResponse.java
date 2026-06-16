package com.zify.tool.api.dto;

import java.time.LocalDateTime;

/**
 * 工具列表卡片（不返回 input_schema/config_json/auth_config 大字段）。
 */
public class ToolSummaryResponse {

    private String id;
    private String name;
    private String description;
    private String sourceType;
    private String mcpServerId;
    private String mcpServerName;
    private Integer enabled;
    private String method;
    private String endpoint;
    /** 可用性：AVAILABLE / UNAVAILABLE。 */
    private String status;
    private LocalDateTime createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getMcpServerId() { return mcpServerId; }
    public void setMcpServerId(String mcpServerId) { this.mcpServerId = mcpServerId; }
    public String getMcpServerName() { return mcpServerName; }
    public void setMcpServerName(String mcpServerName) { this.mcpServerName = mcpServerName; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
