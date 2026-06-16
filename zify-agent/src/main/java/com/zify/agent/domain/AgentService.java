package com.zify.agent.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zify.agent.api.dto.AgentConfigDTO;
import com.zify.agent.api.dto.AgentListQuery;
import com.zify.agent.api.dto.AgentResponse;
import com.zify.agent.api.dto.CreateAgentRequest;
import com.zify.agent.api.dto.UpdateAgentRequest;
import com.zify.agent.infrastructure.converter.AgentConverter;
import com.zify.agent.infrastructure.entity.AgentEntity;
import com.zify.agent.infrastructure.mapper.AgentMapper;
import com.zify.common.exception.BusinessException;
import com.zify.common.exception.ErrorCode;
import com.zify.common.web.PageResult;
import com.zify.model.api.ModelFacade;
import com.zify.model.api.dto.model.ModelSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Agent 管理 Service。
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    /** P1 唯一支持的 Agent 类型。 */
    public static final String AGENT_TYPE_REACT = "REACT";
    private static final Set<String> VALID_STATUSES = Set.of("ACTIVE", "INACTIVE");

    private final AgentMapper agentMapper;
    private final ModelFacade modelFacade;
    private final AgentToolService agentToolService;

    public AgentService(AgentMapper agentMapper, ModelFacade modelFacade, AgentToolService agentToolService) {
        this.agentMapper = agentMapper;
        this.modelFacade = modelFacade;
        this.agentToolService = agentToolService;
    }

    public PageResult<AgentResponse> listAgents(AgentListQuery query) {
        LambdaQueryWrapper<AgentEntity> wrapper = new LambdaQueryWrapper<>();
        if (query.getName() != null && !query.getName().isBlank()) {
            // 前缀匹配（禁前导 %），命中 uk_agent_active_name / 类型索引
            wrapper.likeRight(AgentEntity::getName, query.getName());
        }
        if (query.getAgentType() != null && !query.getAgentType().isBlank()) {
            wrapper.eq(AgentEntity::getAgentType, query.getAgentType());
        }
        if (query.getStatus() != null && !query.getStatus().isBlank()) {
            wrapper.eq(AgentEntity::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(AgentEntity::getCreatedAt).orderByDesc(AgentEntity::getId);

        Page<AgentEntity> page = agentMapper.selectPage(
                new Page<>(query.getPage(), query.getPageSize()), wrapper);

        Map<String, String> modelNameMap = buildModelNameMap();
        List<AgentResponse> records = page.getRecords().stream()
                .map(entity -> AgentConverter.toResponse(entity, modelNameMap.get(entity.getModelId())))
                .collect(Collectors.toList());

        return PageResult.of(records, page.getTotal(), query.getPage(), query.getPageSize());
    }

    public AgentResponse getAgent(String id) {
        AgentEntity entity = getAgentOrThrow(id);
        AgentResponse response = AgentConverter.toResponse(entity, modelNameFor(entity.getModelId()));
        com.zify.agent.api.dto.AgentToolsResponse bound = agentToolService.getBoundTools(id);
        response.setToolIds(bound.getToolIds());
        response.setToolSummaries(bound.getTools());
        return response;
    }

    /**
     * 获取 Agent 配置（供 engine/chat 跨模块使用，经 Facade）。不存在/已删抛 AGENT_NOT_FOUND。
     */
    public AgentConfigDTO getAgentConfig(String agentId) {
        AgentEntity entity = getAgentOrThrow(agentId);
        AgentConfigDTO dto = AgentConverter.toConfigDTO(entity);
        dto.setBoundToolIds(agentToolService.getBoundToolIds(agentId));
        return dto;
    }

    public AgentResponse createAgent(CreateAgentRequest request) {
        if (!AGENT_TYPE_REACT.equalsIgnoreCase(request.getAgentType())) {
            throw new BusinessException(ErrorCode.AGENT_TYPE_INVALID);
        }
        checkNameUnique(request.getName(), null);
        validateModelAvailable(request.getModelId());

        AgentEntity entity = AgentConverter.toEntity(request);
        agentMapper.insert(entity);
        log.info("Agent created: id={}, name={}", entity.getId(), entity.getName());

        return AgentConverter.toResponse(entity, modelNameFor(entity.getModelId()));
    }

    public AgentResponse updateAgent(String id, UpdateAgentRequest request) {
        AgentEntity entity = getAgentOrThrow(id);

        if (request.getName() != null) {
            checkNameUnique(request.getName(), id);
        }
        if (request.getModelId() != null) {
            validateModelAvailable(request.getModelId());
        }
        AgentConverter.updateEntity(entity, request);
        agentMapper.updateById(entity);
        log.info("Agent updated: id={}", id);

        return AgentConverter.toResponse(entity, modelNameFor(entity.getModelId()));
    }

    public void deleteAgent(String id) {
        getAgentOrThrow(id);
        agentMapper.deleteById(id);
        log.info("Agent deleted (soft): id={}", id);
    }

    public void updateStatus(String id, String status) {
        if (status == null || !VALID_STATUSES.contains(status.toUpperCase())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "状态必须为 ACTIVE 或 INACTIVE");
        }
        getAgentOrThrow(id);
        agentMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AgentEntity>()
                        .eq(AgentEntity::getId, id)
                        .set(AgentEntity::getStatus, status.toUpperCase()));
        log.info("Agent status updated: id={}, status={}", id, status);
    }

    private AgentEntity getAgentOrThrow(String id) {
        AgentEntity entity = agentMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.AGENT_NOT_FOUND);
        }
        return entity;
    }

    private void checkNameUnique(String name, String excludeId) {
        LambdaQueryWrapper<AgentEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentEntity::getName, name);
        wrapper.ne(excludeId != null, AgentEntity::getId, excludeId);
        wrapper.select(AgentEntity::getId);
        if (agentMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ErrorCode.AGENT_NAME_DUPLICATE);
        }
    }

    private void validateModelAvailable(String modelId) {
        if (!isModelAvailable(modelId)) {
            throw new BusinessException(ErrorCode.MODEL_UNAVAILABLE);
        }
    }

    /**
     * 给定模型是否为可用 LLM（供 AgentFacade 暴露给 chat/engine 做发送前/会话前校验）。
     */
    public boolean isModelAvailable(String modelId) {
        if (modelId == null) {
            return false;
        }
        return modelFacade.listAvailableModels("LLM").stream()
                .anyMatch(m -> modelId.equals(m.getId()));
    }

    private String modelNameFor(String modelId) {
        if (modelId == null) {
            return null;
        }
        return buildModelNameMap().get(modelId);
    }

    /**
     * 构建 modelId -> 展示名 的映射（来自可用 LLM 列表）。模型不可用时不在其中。
     */
    private Map<String, String> buildModelNameMap() {
        List<ModelSummary> models = modelFacade.listAvailableModels("LLM");
        Map<String, String> map = new HashMap<>(models.size());
        for (ModelSummary m : models) {
            String display = m.getDisplayName() != null && !m.getDisplayName().isBlank()
                    ? m.getDisplayName() : m.getModelName();
            map.put(m.getId(), display);
        }
        return map;
    }
}
