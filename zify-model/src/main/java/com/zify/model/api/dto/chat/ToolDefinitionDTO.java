package com.zify.model.api.dto.chat;

/**
 * 工具定义（中立 DTO，engine 透传给 model；model 内部转 Spring AI ToolDefinition）。
 */
public class ToolDefinitionDTO {

    private String name;
    private String description;
    /** 工具输入参数 JSON Schema（字符串）。 */
    private String inputSchema;

    public ToolDefinitionDTO() {
    }

    public ToolDefinitionDTO(String name, String description, String inputSchema) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
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
}
