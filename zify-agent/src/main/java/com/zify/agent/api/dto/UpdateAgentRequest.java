package com.zify.agent.api.dto;

import jakarta.validation.constraints.Size;

/**
 * 更新 Agent 请求。不包含 agentType（创建后不可修改）。
 */
public class UpdateAgentRequest {

    @Size(max = 128, message = "Agent 名称最长 128 字符")
    private String name;

    @Size(max = 512, message = "Agent 描述最长 512 字符")
    private String description;

    private String systemPrompt;
    private String modelId;
    private String status;

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
