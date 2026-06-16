package com.zify.agent.api;

import com.zify.agent.api.dto.AgentConfigDTO;

import java.util.List;

/**
 * Agent 模块 Facade 接口，供其他模块（engine / chat）跨模块调用。
 */
public interface AgentFacade {

    /**
     * 获取 Agent 配置（id/name/agentType/status/systemPrompt/modelId）。
     * 不存在/已删除抛 AGENT_NOT_FOUND。
     */
    AgentConfigDTO getAgentConfig(String agentId);

    /**
     * 取 Agent 已绑定的工具 ID（P2，供 engine ReAct 取工具经 ToolFacade.listAvailableTools）。
     */
    List<String> getBoundToolIds(String agentId);

    /**
     * 校验给定模型 ID 是否为可用 LLM（model.enabled=1 AND provider.status=ACTIVE AND 均未删除）。
     * <p>
     * agent 模块依赖 model，由其在 Facade 上代为校验，避免 chat/engine 直接依赖 model。
     */
    boolean isModelAvailable(String modelId);
}
