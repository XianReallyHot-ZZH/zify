package com.zify.tool.domain.openapi;

import java.util.List;

/**
 * OpenAPI 解析结果（glm-docs/13 §10.2 / P2 §六）。
 */
public class OpenApiParseResult {

    /** 首个 server URL（可被调用方覆盖）；spec 无 server 时为 null。 */
    private String baseUrl;
    private List<OperationPreview> operations;

    public OpenApiParseResult() {
    }

    public OpenApiParseResult(String baseUrl, List<OperationPreview> operations) {
        this.baseUrl = baseUrl;
        this.operations = operations;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public List<OperationPreview> getOperations() {
        return operations;
    }

    public void setOperations(List<OperationPreview> operations) {
        this.operations = operations;
    }
}
