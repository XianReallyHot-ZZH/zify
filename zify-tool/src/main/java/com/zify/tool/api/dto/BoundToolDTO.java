package com.zify.tool.api.dto;

/**
 * 工具绑定视图（agent 绑定页/详情用，含 enabled + available；不喂给 LLM）。
 * <p>
 * 归 tool 模块（工具数据 owner），agent 经 {@code ToolFacade.listToolBindings} 复用。
 */
public class BoundToolDTO {

    private String id;
    private String name;
    private String description;
    private String sourceType;
    private Integer enabled;
    /** 运行时可用性（enabled+未删；MCP 需 server 在线）。 */
    private boolean available;

    public BoundToolDTO() {
    }

    public BoundToolDTO(String id, String name, String description, String sourceType, Integer enabled, boolean available) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.sourceType = sourceType;
        this.enabled = enabled;
        this.available = available;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
}
