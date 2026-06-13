package com.zify.agent.api;

import com.zify.agent.api.dto.AgentConfigDTO;

/**
 * Agent 模块 Facade 接口，供其他模块（engine / chat）跨模块调用。
 */
public interface AgentFacade {

    /**
     * 获取 Agent 配置（id/name/agentType/status/systemPrompt/modelId）。
     * 不存在/已删除抛 AGENT_NOT_FOUND。
     */
    AgentConfigDTO getAgentConfig(String agentId);
}
