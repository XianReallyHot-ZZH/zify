package com.zify.tool.api.dto;

/**
 * OpenAPI operation 预览（解析响应项）。
 */
public class OpenApiOperationPreviewResponse {

    private String operationId;
    private String method;
    private String path;
    private String summary;
    private String suggestedName;
    private boolean hasRequestBody;

    public String getOperationId() { return operationId; }
    public void setOperationId(String operationId) { this.operationId = operationId; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getSuggestedName() { return suggestedName; }
    public void setSuggestedName(String suggestedName) { this.suggestedName = suggestedName; }
    public boolean isHasRequestBody() { return hasRequestBody; }
    public void setHasRequestBody(boolean hasRequestBody) { this.hasRequestBody = hasRequestBody; }
}
