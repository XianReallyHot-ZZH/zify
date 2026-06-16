package com.zify.tool.api.dto;

/**
 * 创建 MCP Server 请求。
 */
public class CreateMcpServerRequest {

    private String name;
    private String description;
    private String baseUrl;
    /** STREAMABLE_HTTP / SSE。 */
    private String transportType;
    /** NONE / API_KEY / BEARER。 */
    private String authType;
    /** API_KEY 时的自定义 header 名（默认 X-Api-Key）。 */
    private String authHeaderName;
    /** 明文凭据（加密存储；API_KEY→headerName+apiKey JSON、BEARER→token）。 */
    private String credential;

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
}
