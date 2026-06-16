package com.zify.tool.api.dto;

/**
 * 更新 MCP Server 请求（部分覆盖；credential 留空不改）。
 */
public class UpdateMcpServerRequest {

    private String name;
    private String description;
    private String baseUrl;
    private String transportType;
    private String authType;
    private String authHeaderName;
    private String credential;
    private Integer enabled;

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
    public String getAuthHeaderName() { return authHeaderName; }
    public void setAuthHeaderName(String authHeaderName) { this.authHeaderName = authHeaderName; }
    public String getCredential() { return credential; }
    public void setCredential(String credential) { this.credential = credential; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
}
