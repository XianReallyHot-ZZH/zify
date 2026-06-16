package com.zify.tool.api.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 工具详情（含 inputSchema/configJson/authType/hasAuth；不返回 auth_config 密文）。
 */
public class ToolDetailResponse {

    private String id;
    private String name;
    private String description;
    private String sourceType;
    private String mcpServerId;
    private String mcpServerName;
    private String inputSchema;
    private String endpoint;
    private String method;
    private Map<String, Object> configJson;
    private String authType;
    private boolean hasAuth;
    private Integer timeoutSeconds;
    private Integer idempotent;
    private Integer enabled;
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
    public String getInputSchema() { return inputSchema; }
    public void setInputSchema(String inputSchema) { this.inputSchema = inputSchema; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public Map<String, Object> getConfigJson() { return configJson; }
    public void setConfigJson(Map<String, Object> configJson) { this.configJson = configJson; }
    public String getAuthType() { return authType; }
    public void setAuthType(String authType) { this.authType = authType; }
    public boolean isHasAuth() { return hasAuth; }
    public void setHasAuth(boolean hasAuth) { this.hasAuth = hasAuth; }
    public Integer getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(Integer timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public Integer getIdempotent() { return idempotent; }
    public void setIdempotent(Integer idempotent) { this.idempotent = idempotent; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
