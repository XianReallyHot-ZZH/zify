package com.zify.tool.api.dto;

/**
 * MCP 工具预览（测试连接返回，不入库）。
 */
public class McpToolPreview {

    private String name;
    private String description;
    private String inputSchema;

    public McpToolPreview() {
    }

    public McpToolPreview(String name, String description, String inputSchema) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getInputSchema() { return inputSchema; }
    public void setInputSchema(String inputSchema) { this.inputSchema = inputSchema; }
}
