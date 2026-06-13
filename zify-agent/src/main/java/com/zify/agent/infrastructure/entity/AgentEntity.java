package com.zify.agent.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zify.common.persistence.entity.BaseEntity;

/**
 * Agent 实体（映射 agent 表）。system_prompt 为 MEDIUMTEXT，映射为 String。
 */
@TableName("agent")
public class AgentEntity extends BaseEntity {

    private String createdBy;
    private String updatedBy;
    private String name;
    private String description;
    private String agentType;
    private String status;
    private String systemPrompt;
    private String modelId;
    private String workflowId;

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
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

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }
}
