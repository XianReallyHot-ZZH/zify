package com.zify.agent.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zify.common.exception.BusinessException;
import com.zify.common.exception.ErrorCode;
import com.zify.common.persistence.id.IdGenerator;
import com.zify.agent.api.dto.AgentToolsResponse;
import com.zify.agent.infrastructure.entity.AgentToolEntity;
import com.zify.agent.infrastructure.mapper.AgentToolMapper;
import com.zify.tool.api.ToolFacade;
import com.zify.tool.api.dto.BoundToolDTO;
import com.zify.tool.api.dto.ToolViewDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Agent 工具绑定（glm-docs/13 §二 / P2 §13）。
 * <p>
 * agent_tool 归 agent 模块；可用性/校验经 {@link ToolFacade}（agent→tool 依赖合法）。
 * bindTools 仅 DB 写（短事务，不调外部）。
 */
@Service
public class AgentToolService {

    private static final Logger log = LoggerFactory.getLogger(AgentToolService.class);

    private final AgentToolMapper mapper;
    private final ToolFacade toolFacade;

    public AgentToolService(AgentToolMapper mapper, ToolFacade toolFacade) {
        this.mapper = mapper;
        this.toolFacade = toolFacade;
    }

    public AgentToolsResponse getBoundTools(String agentId) {
        List<String> toolIds = getBoundToolIds(agentId);
        List<BoundToolDTO> tools = toolFacade.listToolBindings(toolIds);
        AgentToolsResponse response = new AgentToolsResponse();
        response.setToolIds(toolIds);
        response.setTools(tools);
        return response;
    }

    /**
     * 全量覆盖绑定：校验每个 toolId 可用（缺失/禁用 → TOOL_NOT_AVAILABLE）；
     * 事务内软删旧绑定、插入新绑定。
     */
    @Transactional
    public AgentToolsResponse bindTools(String agentId, List<String> toolIds) {
        List<String> requested = toolIds == null ? List.of() : new ArrayList<>(toolIds);
        if (!requested.isEmpty()) {
            List<ToolViewDTO> available = toolFacade.listAvailableTools(requested);
            Set<String> availableIds = available.stream().map(ToolViewDTO::getId).collect(Collectors.toSet());
            for (String id : requested) {
                if (!availableIds.contains(id)) {
                    throw new BusinessException(ErrorCode.TOOL_NOT_AVAILABLE);
                }
            }
        }
        // 软删旧绑定
        mapper.delete(new LambdaQueryWrapper<AgentToolEntity>().eq(AgentToolEntity::getAgentId, agentId));
        // 插入新绑定（去重）
        Set<String> inserted = new HashSet<>();
        for (String toolId : requested) {
            if (inserted.add(toolId)) {
                AgentToolEntity entity = new AgentToolEntity();
                entity.setId(IdGenerator.uuid());
                entity.setAgentId(agentId);
                entity.setToolId(toolId);
                mapper.insert(entity);
            }
        }
        log.info("Agent tools bound: agentId={}, count={}", agentId, inserted.size());
        return getBoundTools(agentId);
    }

    /** 供 engine 取绑定（经 AgentFacade）。 */
    public List<String> getBoundToolIds(String agentId) {
        return mapper.selectList(new LambdaQueryWrapper<AgentToolEntity>()
                        .eq(AgentToolEntity::getAgentId, agentId)
                        .orderByAsc(AgentToolEntity::getCreatedAt)).stream()
                .map(AgentToolEntity::getToolId)
                .toList();
    }
}
