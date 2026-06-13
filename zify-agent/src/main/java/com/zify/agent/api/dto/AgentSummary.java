package com.zify.agent.api.dto;

import java.time.LocalDateTime;

/**
 * Agent 列表卡片摘要（轻量，无 systemPrompt）。
 */
public class AgentSummary {

    private String id;
    private String name;
    private String description;
    private String agentType;
    private String status;
    private String modelName;
    private LocalDateTime lastConversationAt;
    private LocalDateTime createdAt;

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

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public LocalDateTime getLastConversationAt() {
        return lastConversationAt;
    }

    public void setLastConversationAt(LocalDateTime lastConversationAt) {
        this.lastConversationAt = lastConversationAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
