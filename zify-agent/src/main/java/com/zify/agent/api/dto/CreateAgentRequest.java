package com.zify.agent.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建 Agent 请求。
 */
public class CreateAgentRequest {

    @NotBlank(message = "Agent 名称不能为空")
    @Size(max = 128, message = "Agent 名称最长 128 字符")
    private String name;

    @Size(max = 512, message = "Agent 描述最长 512 字符")
    private String description;

    @NotBlank(message = "Agent 类型不能为空")
    private String agentType;

    private String systemPrompt;

    @NotBlank(message = "模型不能为空")
    private String modelId;

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

    public String getAgentType() {
        return agentType;
    }

    public void setAgentType(String agentType) {
        this.agentType = agentType;
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
