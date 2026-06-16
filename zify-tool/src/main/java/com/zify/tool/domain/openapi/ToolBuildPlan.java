package com.zify.tool.domain.openapi;

import java.util.Map;

/**
 * 单个 operation → HTTP 工具配置（OpenApiParser.toToolConfig 产物）。
 * <p>
 * 由 ToolService 落库为一条 {@code source_type=HTTP} 的 tool 记录。
 */
public class ToolBuildPlan {

    private String name;
    private String description;
    private String method;
    private String endpoint;
    /** 工具输入参数 JSON Schema（字符串）。 */
    private String inputSchema;
    /** HTTP 工具配置（paramsMapping/headersTemplate/bodyTemplate）。 */
    private Map<String, Object> configJson;

    public ToolBuildPlan() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getInputSchema() {
        return inputSchema;
    }

    public void setInputSchema(String inputSchema) {
        this.inputSchema = inputSchema;
    }

    public Map<String, Object> getConfigJson() {
        return configJson;
    }

    public void setConfigJson(Map<String, Object> configJson) {
        this.configJson = configJson;
    }
}
