package com.zify.agent.infrastructure.facade;

import com.zify.agent.api.AgentFacade;
import com.zify.agent.api.dto.AgentConfigDTO;
import com.zify.agent.domain.AgentService;
import com.zify.agent.domain.AgentToolService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Agent Facade 实现，转发到 {@link AgentService} / {@link AgentToolService}。
 */
@Service
public class AgentFacadeImpl implements AgentFacade {

    private final AgentService agentService;
    private final AgentToolService agentToolService;

    public AgentFacadeImpl(AgentService agentService, AgentToolService agentToolService) {
        this.agentService = agentService;
        this.agentToolService = agentToolService;
    }

    @Override
    public AgentConfigDTO getAgentConfig(String agentId) {
        return agentService.getAgentConfig(agentId);
    }

    @Override
    public boolean isModelAvailable(String modelId) {
        return agentService.isModelAvailable(modelId);
    }

    @Override
    public List<String> getBoundToolIds(String agentId) {
        return agentToolService.getBoundToolIds(agentId);
    }
}
