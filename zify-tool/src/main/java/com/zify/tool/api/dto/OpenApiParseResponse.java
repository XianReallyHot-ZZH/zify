package com.zify.tool.api.dto;

import java.util.List;

/**
 * OpenAPI 解析响应（预览，不持久化）。
 */
public class OpenApiParseResponse {

    private String baseUrl;
    private List<OpenApiOperationPreviewResponse> operations;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public List<OpenApiOperationPreviewResponse> getOperations() { return operations; }
    public void setOperations(List<OpenApiOperationPreviewResponse> operations) { this.operations = operations; }
}
