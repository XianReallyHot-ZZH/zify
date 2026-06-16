package com.zify.tool.api.dto;

/**
 * 工具视图 DTO（中立，跨 Facade 供 engine/agent 使用）。
 * <p>
 * inputSchema 为 JSON Schema 字符串，运行时原样透传给 model 模块转 Spring AI ToolDefinition。
 */
public class ToolViewDTO {

    private String id;
    private String name;
    private String description;
    /** 工具输入参数 JSON Schema（字符串）。 */
    private String inputSchema;
    /** 工具来源：HTTP / MCP / WORKFLOW。 */
    private String sourceType;

    public ToolViewDTO() {
    }

    public ToolViewDTO(String id, String name, String description, String inputSchema, String sourceType) {
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
