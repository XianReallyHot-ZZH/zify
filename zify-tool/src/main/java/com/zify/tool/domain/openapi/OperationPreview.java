package com.zify.tool.domain.openapi;

/**
 * OpenAPI operation 预览（解析产物，供导入向导展示 + 勾选）。
 */
public class OperationPreview {

    private String operationId;
    private String method;
    private String path;
    private String summary;
    /** 建议工具名（operationId 缺失用 method_path，小写蛇形；冲突由解析层去重）。 */
    private String suggestedName;
    private boolean hasRequestBody;
    private boolean selected = true;

    public OperationPreview() {
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getSuggestedName() {
        return suggestedName;
    }

    public void setSuggestedName(String suggestedName) {
        this.suggestedName = suggestedName;
    }

    public boolean isHasRequestBody() {
        return hasRequestBody;
    }

    public void setHasRequestBody(boolean hasRequestBody) {
        this.hasRequestBody = hasRequestBody;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }
}
