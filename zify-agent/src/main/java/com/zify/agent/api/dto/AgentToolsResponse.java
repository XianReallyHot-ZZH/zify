package com.zify.agent.api.dto;

import com.zify.tool.api.dto.BoundToolDTO;

import java.util.List;

/**
 * Agent 绑定的工具视图（{toolIds, tools:[BoundToolDTO]}）。
 */
public class AgentToolsResponse {

    private List<String> toolIds;
    private List<BoundToolDTO> tools;

    public List<String> getToolIds() { return toolIds; }
    public void setToolIds(List<String> toolIds) { this.toolIds = toolIds; }
    public List<BoundToolDTO> getTools() { return tools; }
    public void setTools(List<BoundToolDTO> tools) { this.tools = tools; }
}
