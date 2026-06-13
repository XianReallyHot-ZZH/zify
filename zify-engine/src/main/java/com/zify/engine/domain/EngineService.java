package com.zify.engine.domain;

import com.zify.agent.api.AgentFacade;
import com.zify.agent.api.dto.AgentConfigDTO;
import com.zify.common.web.TextStreamSink;
import com.zify.engine.api.dto.ChatTurnCommand;
import com.zify.engine.api.dto.ChatTurnResult;
import com.zify.engine.api.dto.TokenUsage;
import com.zify.model.api.ModelFacade;
import com.zify.model.api.dto.chat.ChatCompletionCommand;
import com.zify.model.api.dto.chat.ChatCompletionResult;
import org.springframework.stereotype.Service;

/**
 * 对话引擎 Service：纯编排（取 Agent 配置 → 上下文管理 → 组装 Prompt → 调 ModelFacade.chatStream → 经 sink 流出）。
 * <p>
 * 不读写任何数据库表。失败直接抛 {@code LlmException}，由 chat 决定发 run_error。
 * system + summary + 活窗口的组装、摘要压缩、截断兜底由 {@link ContextManager} 负责。
 */
@Service
public class EngineService {

    private final AgentFacade agentFacade;
    private final ModelFacade modelFacade;
    private final ContextManager contextManager;

    public EngineService(AgentFacade agentFacade, ModelFacade modelFacade, ContextManager contextManager) {
        this.agentFacade = agentFacade;
        this.modelFacade = modelFacade;
        this.contextManager = contextManager;
    }

    public ChatTurnResult runChatTurn(ChatTurnCommand cmd, TextStreamSink sink) {
        AgentConfigDTO agent = agentFacade.getAgentConfig(cmd.getAgentId());

        ContextManager.PrepareResult prepared = contextManager.prepare(agent, cmd.getSummary(), cmd.getHistory());

        ChatCompletionCommand command = new ChatCompletionCommand(agent.getModelId(), prepared.getMessages(), null);
        ChatCompletionResult result = modelFacade.chatStream(command, sink);

        return new ChatTurnResult(result.getContent(), result.getFinishReason(), toUsage(result.getUsage()),
                prepared.getNewSummary(), prepared.getNewSummaryCoveredMessageId());
    }

    private TokenUsage toUsage(com.zify.model.api.dto.chat.TokenUsage usage) {
        if (usage == null) {
            return null;
        }
        return new TokenUsage(usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
    }
}
