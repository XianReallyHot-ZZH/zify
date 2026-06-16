package com.zify.tool.domain;

/**
 * 工具内部视图（HttpTool/McpTool.toView 产物，不跨 Facade）。
 * <p>
 * 字段与 {@link com.zify.tool.api.dto.ToolViewDTO} 一致，由 {@code ToolFacadeImpl} 转为对外 DTO。
 */
public class ToolView {

    private String id;
    private String name;
    private String description;
    private String inputSchema;
    private String sourceType;

    public ToolView() {
    }

    public ToolView(String id, String name, String description, String inputSchema, String sourceType) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
        this.sourceType = sourceType;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getInputSchema() {
        return inputSchema;
    }

    public void setInputSchema(String inputSchema) {
        this.inputSchema = inputSchema;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }
}
