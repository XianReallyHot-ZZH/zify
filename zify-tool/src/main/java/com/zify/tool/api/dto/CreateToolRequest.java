package com.zify.tool.api.dto;

import java.util.Map;

/**
 * 创建 HTTP 工具请求（手动配置）。OpenAPI 导入走 import-openapi。
 */
public class CreateToolRequest {

    private String name;
    private String description;
    private String method;
    private String endpoint;
    /** 工具输入参数 JSON Schema（字符串）。 */
    private String inputSchema;
    /** HTTP 配置：paramsMapping/headersTemplate/bodyTemplate。 */
    private Map<String, Object> configJson;
    private String authType;
    private String authHeaderName;
    private String credential;
    private Integer timeoutSeconds;
    /** null → 按 method 推断（GET/HEAD=1，其余=0）。 */
    private Integer idempotent;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getInputSchema() { return inputSchema; }
    public void setInputSchema(String inputSchema) { this.inputSchema = inputSchema; }
    public Map<String, Object> getConfigJson() { return configJson; }
    public void setConfigJson(Map<String, Object> configJson) { this.configJson = configJson; }
    public String getAuthType() { return authType; }
    public void setAuthType(String authType) { this.authType = authType; }
    public String getAuthHeaderName() { return authHeaderName; }
    public void setAuthHeaderName(String authHeaderName) { this.authHeaderName = authHeaderName; }
    public String getCredential() { return credential; }
    public void setCredential(String credential) { this.credential = credential; }
    public Integer getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(Integer timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public Integer getIdempotent() { return idempotent; }
    public void setIdempotent(Integer idempotent) { this.idempotent = idempotent; }
}
