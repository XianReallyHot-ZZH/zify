package com.zify.agent.infrastructure.facade;

import com.zify.agent.api.AgentFacade;
import com.zify.agent.api.dto.AgentConfigDTO;
import com.zify.agent.domain.AgentService;
import org.springframework.stereotype.Service;

/**
 * Agent Facade 实现，转发到 {@link AgentService}。
 */
@Service
public class AgentFacadeImpl implements AgentFacade {

    private final AgentService agentService;

    public AgentFacadeImpl(AgentService agentService) {
        this.agentService = agentService;
    }

    @Override
    public AgentConfigDTO getAgentConfig(String agentId) {
        return agentService.getAgentConfig(agentId);
    }

    @Override
    public boolean isModelAvailable(String modelId) {
        return agentService.isModelAvailable(modelId);
    }
}
