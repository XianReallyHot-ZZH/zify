package com.zify.agent.infrastructure.converter;

import com.zify.agent.api.dto.AgentConfigDTO;
import com.zify.agent.api.dto.AgentResponse;
import com.zify.agent.api.dto.AgentSummary;
import com.zify.agent.api.dto.CreateAgentRequest;
import com.zify.agent.api.dto.UpdateAgentRequest;
import com.zify.agent.infrastructure.entity.AgentEntity;
import com.zify.common.persistence.id.IdGenerator;

/**
 * Agent Entity <-> DTO 转换器（静态工具）。
 */
public final class AgentConverter {

    /** Agent 默认状态。 */
    public static final String DEFAULT_STATUS = "ACTIVE";

    private AgentConverter() {
    }

    /**
     * Entity 转 AgentResponse（详情，含 systemPrompt）。
     */
    public static AgentResponse toResponse(AgentEntity entity, String modelName) {
        AgentResponse response = new AgentResponse();
        response.setId(entity.getId());
        response.setName(entity.getName());
        response.setDescription(entity.getDescription());
        response.setAgentType(entity.getAgentType());
        response.setStatus(entity.getStatus());
        response.setSystemPrompt(entity.getSystemPrompt());
        response.setModelId(entity.getModelId());
        response.setModelName(modelName);
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    /**
     * Entity 转 AgentSummary（列表卡片）。modelName / lastConversationAt 由 Service 补。
     */
    public static AgentSummary toSummary(AgentEntity entity) {
        AgentSummary summary = new AgentSummary();
        summary.setId(entity.getId());
        summary.setName(entity.getName());
        summary.setDescription(entity.getDescription());
        summary.setAgentType(entity.getAgentType());
        summary.setStatus(entity.getStatus());
        summary.setCreatedAt(entity.getCreatedAt());
        return summary;
    }

    /**
     * Entity 转 AgentConfigDTO（Facade 用，给 engine/chat）。
     */
    public static AgentConfigDTO toConfigDTO(AgentEntity entity) {
        AgentConfigDTO dto = new AgentConfigDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setAgentType(entity.getAgentType());
        dto.setStatus(entity.getStatus());
        dto.setSystemPrompt(entity.getSystemPrompt());
        dto.setModelId(entity.getModelId());
        return dto;
    }

    /**
     * CreateAgentRequest 转 Entity（生成 id、默认状态 ACTIVE、workflow_id 恒 NULL）。
     */
    public static AgentEntity toEntity(CreateAgentRequest request) {
        AgentEntity entity = new AgentEntity();
        entity.setId(IdGenerator.uuid());
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        entity.setAgentType(request.getAgentType());
        entity.setStatus(DEFAULT_STATUS);
        entity.setSystemPrompt(request.getSystemPrompt());
        entity.setModelId(request.getModelId());
        entity.setWorkflowId(null);
        return entity;
    }

    /**
     * 用 UpdateAgentRequest 更新已有 Entity（仅覆盖非 null 字段；agentType 不出现=不可改）。
     */
    public static void updateEntity(AgentEntity entity, UpdateAgentRequest request) {
        if (request.getName() != null) {
            entity.setName(request.getName());
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        if (request.getSystemPrompt() != null) {
            entity.setSystemPrompt(request.getSystemPrompt());
        }
        if (request.getModelId() != null) {
            entity.setModelId(request.getModelId());
        }
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }
    }
}
