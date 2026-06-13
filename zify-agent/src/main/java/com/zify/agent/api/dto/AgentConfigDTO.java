package com.zify.agent.api.dto;

/**
 * Agent 配置 DTO（Facade 用：给 engine / chat 获取 Agent 配置）。
 * 不暴露工具/知识库（P1 无）。
 */
public class AgentConfigDTO {

    private String id;
    private String name;
    private String agentType;
    private String status;
    private String systemPrompt;
    private String modelId;

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

    public String getAgentType() {
        return agentType;
    }

    public void setAgentType(String agentType) {
        this.agentType = agentType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }
}
