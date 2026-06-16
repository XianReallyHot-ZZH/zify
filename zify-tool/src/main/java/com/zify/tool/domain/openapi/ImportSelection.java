package com.zify.tool.domain.openapi;

/**
 * OpenAPI 导入时单个 operation 的勾选/改名（前端 → 后端，匹配用 operationId 或 method+path）。
 */
public class ImportSelection {

    private String operationId;
    private String method;
    private String path;
    /** 用户确认/修改后的工具名。 */
    private String name;
    private boolean selected = true;

    public ImportSelection() {
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }
}
