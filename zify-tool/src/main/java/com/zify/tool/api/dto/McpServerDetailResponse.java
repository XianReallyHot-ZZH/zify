package com.zify.tool.api.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MCP Server 详情（含已发现工具列表）。
 */
public class McpServerDetailResponse {

    private String id;
    private String name;
    private String description;
    private String baseUrl;
    private String transportType;
    private String authType;
    private boolean hasAuth;
    private Integer enabled;
    private String status;
    private String statusMessage;
    private LocalDateTime lastConnectedAt;
    private LocalDateTime createdAt;
    private List<McpDiscoveredToolResponse> discoveredTools;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getTransportType() { return transportType; }
    public void setTransportType(String transportType) { this.transportType = transportType; }
    public String getAuthType() { return authType; }
    public void setAuthType(String authType) { this.authType = authType; }
    public boolean isHasAuth() { return hasAuth; }
    public void setHasAuth(boolean hasAuth) { this.hasAuth = hasAuth; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStatusMessage() { return statusMessage; }
    public void setStatusMessage(String statusMessage) { this.statusMessage = statusMessage; }
    public LocalDateTime getLastConnectedAt() { return lastConnectedAt; }
    public void setLastConnectedAt(LocalDateTime lastConnectedAt) { this.lastConnectedAt = lastConnectedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public List<McpDiscoveredToolResponse> getDiscoveredTools() { return discoveredTools; }
    public void setDiscoveredTools(List<McpDiscoveredToolResponse> discoveredTools) { this.discoveredTools = discoveredTools; }
}
