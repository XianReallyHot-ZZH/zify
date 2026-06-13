package com.zify.engine.domain;

import com.zify.agent.api.AgentFacade;
import com.zify.agent.api.dto.AgentConfigDTO;
import com.zify.common.web.TextStreamSink;
import com.zify.engine.api.dto.ChatMessage;
import com.zify.engine.api.dto.ChatTurnCommand;
import com.zify.engine.api.dto.ChatTurnResult;
import com.zify.engine.api.dto.TokenUsage;
import com.zify.model.api.ModelFacade;
import com.zify.model.api.dto.chat.ChatCompletionCommand;
import com.zify.model.api.dto.chat.ChatCompletionResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 对话引擎 Service：纯编排（取 Agent 配置 → 组装 Prompt → 调 ModelFacade.chatStream → 经 sink 流出）。
 * <p>
 * 不读写任何数据库表。失败直接抛 {@code LlmException}，由 chat 决定发 run_error。
 * 本任务不含上下文压缩（summary 原样透传，不截断不压缩）。
 */
@Service
public class EngineService {

    private final AgentFacade agentFacade;
    private final ModelFacade modelFacade;

    public EngineService(AgentFacade agentFacade, ModelFacade modelFacade) {
        this.agentFacade = agentFacade;
        this.modelFacade = modelFacade;
    }

    public ChatTurnResult runChatTurn(ChatTurnCommand cmd, TextStreamSink sink) {
        AgentConfigDTO agent = agentFacade.getAgentConfig(cmd.getAgentId());

        List<com.zify.model.api.dto.chat.ChatMessage> messages = new ArrayList<>();

        // 组装 system：选择「合并进单条 SYSTEM 消息」（避免多 system 消息对部分 Provider 不友好）。
        // 顺序：agent.systemPrompt 在前，summary 在后。
        String systemContent = joinNonBlank("\n\n", agent.getSystemPrompt(), cmd.getSummary());
        if (systemContent != null && !systemContent.isBlank()) {
            messages.add(new com.zify.model.api.dto.chat.ChatMessage("SYSTEM", systemContent));
        }

        // 追加历史（末尾即本轮 user 输入，已由 chat 落库并加载）
        if (cmd.getHistory() != null) {
            for (ChatMessage m : cmd.getHistory()) {
                messages.add(new com.zify.model.api.dto.chat.ChatMessage(m.getRole(), m.getContent()));
            }
        }

        // 本任务不做 token 预算 / 截断 / 压缩，options 透传 null（由 model 网关回退 model.default_params）
        ChatCompletionCommand command = new ChatCompletionCommand(agent.getModelId(), messages, null);
        ChatCompletionResult result = modelFacade.chatStream(command, sink);

        // newSummary 恒 null（压缩在 Task 14 实现）
        return new ChatTurnResult(result.getContent(), result.getFinishReason(), toUsage(result.getUsage()),
                null, null);
    }

    private TokenUsage toUsage(com.zify.model.api.dto.chat.TokenUsage usage) {
        if (usage == null) {
            return null;
        }
        return new TokenUsage(usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
    }

    private String joinNonBlank(String separator, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                if (!sb.isEmpty()) {
                    sb.append(separator);
                }
                sb.append(part);
            }
        }
        return sb.toString();
    }
}
