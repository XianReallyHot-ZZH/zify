package com.zify.engine.domain;

import com.zify.agent.api.dto.AgentConfigDTO;
import com.zify.engine.api.dto.ChatMessage;
import com.zify.engine.config.ChatContextProperties;
import com.zify.model.api.ModelFacade;
import com.zify.model.api.dto.chat.ChatCompletionCommand;
import com.zify.model.api.dto.chat.ChatCompletionResult;
import com.zify.model.api.dto.chat.ToolCallDTO;
import com.zify.model.api.dto.model.ModelSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 上下文管理：窗口预算 + 摘要压缩 + 尾部截断兜底（glm-docs/02 §5.5 / prompt-04 任务 14）。
 * <p>
 * P2 §17：折叠/截断<b>以 turn 为整体</b>（1 USER + 其后 ASSISTANT/TOOL），绝不拆散 toolCall↔TOOL 配对
 * （否则模型丢失配对、多轮工具上下文断裂，OpenAI/Anthropic 也会因序列非法报错）。
 * 摘要生成走 ModelFacade.chatStream（收集型 sink，不接用户 SSE），不在事务内。
 * <p>
 * 输入：agent 配置 + 当前 running summary + 活窗口历史（chat 已按 summary_covered 截取，含 USER/ASSISTANT/TOOL）。
 * 输出：最终发给 LLM 的 messages + 可选 newSummary/newSummaryCoveredMessageId（仅压缩时非空）。
 */
@Component
public class ContextManager {

    private static final Logger log = LoggerFactory.getLogger(ContextManager.class);

    /** 预留输出 token（engine 无法读 model.defaultParams.maxTokens，按固定值预留）。 */
    private static final int RESERVED_OUTPUT_TOKENS = 4096;
    private static final String ROLE_USER = "USER";

    private static final String SUMMARY_SYSTEM_PROMPT =
            "你是对话摘要助手。请把以下历史对话压缩成一段简洁的摘要，保留关键事实、用户意图与已达成结论，"
                    + "不要丢失重要信息，只输出摘要正文。";

    private final ModelFacade modelFacade;
    private final ChatContextProperties properties;
    private final TokenEstimator tokenEstimator;

    public ContextManager(ModelFacade modelFacade, ChatContextProperties properties, TokenEstimator tokenEstimator) {
        this.modelFacade = modelFacade;
        this.properties = properties;
        this.tokenEstimator = tokenEstimator;
    }

    public PrepareResult prepare(AgentConfigDTO agent, String summary, List<ChatMessage> history) {
        String systemPrompt = agent.getSystemPrompt();
        int contextWindow = resolveContextWindow(agent.getModelId());
        int systemTokens = estimate(systemPrompt);
        int budget = Math.max(1, contextWindow - systemTokens - RESERVED_OUTPUT_TOKENS
                - properties.getSummaryOverheadTokens());
        double threshold = properties.getBudgetThreshold();

        List<ChatMessage> activeWindow = history != null ? new ArrayList<>(history) : new ArrayList<>();
        int summaryTokens = estimate(summary);

        String newSummary = null;
        String newCoveredId = null;

        // 第一层：turn 级摘要压缩（超阈值时折叠最旧若干完整 turn，绝不含本轮）
        if (systemTokens + summaryTokens + estimateWindow(activeWindow) > budget * threshold) {
            int foldEnd = turnAlignedFoldEnd(activeWindow, properties.getCompactionBatch());
            if (foldEnd > 0) {
                List<ChatMessage> toCompact = new ArrayList<>(activeWindow.subList(0, foldEnd));
                String generated = generateSummary(agent.getModelId(), summary, toCompact);
                if (generated != null && !generated.isBlank()) {
                    newSummary = generated;
                    newCoveredId = toCompact.get(toCompact.size() - 1).getMessageId();
                    activeWindow = new ArrayList<>(activeWindow.subList(foldEnd, activeWindow.size()));
                    summaryTokens = estimate(newSummary);
                }
            }
        }

        // 第二层：turn 级尾部截断兜底（压缩后仍超预算 → 丢弃最旧完整 turn，绝不丢本轮）
        while (turnCount(activeWindow) > 1
                && systemTokens + summaryTokens + estimateWindow(activeWindow) > budget) {
            int dropEnd = nextTurnBoundary(activeWindow, 1);
            if (dropEnd <= 0 || dropEnd >= activeWindow.size()) {
                break;
            }
            activeWindow.subList(0, dropEnd).clear();
        }

        // 组装最终 messages：[SYSTEM: systemPrompt] + (effectiveSummary? SYSTEM) + 活窗口（保留 toolCalls/toolCallId）
        List<com.zify.model.api.dto.chat.ChatMessage> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new com.zify.model.api.dto.chat.ChatMessage("SYSTEM", systemPrompt));
        }
        String effectiveSummary = newSummary != null ? newSummary : summary;
        if (effectiveSummary != null && !effectiveSummary.isBlank()) {
            messages.add(new com.zify.model.api.dto.chat.ChatMessage("SYSTEM", effectiveSummary));
        }
        for (ChatMessage m : activeWindow) {
            messages.add(toModelMessage(m));
        }
        return new PrepareResult(messages, newSummary, newCoveredId);
    }

    /**
     * 把 engine ChatMessage 映射为 model ChatMessage，保留 ASSISTANT toolCalls / TOOL toolCallId。
     */
    private com.zify.model.api.dto.chat.ChatMessage toModelMessage(ChatMessage m) {
        com.zify.model.api.dto.chat.ChatMessage out =
                new com.zify.model.api.dto.chat.ChatMessage(m.getRole(), m.getContent());
        if ("ASSISTANT".equals(m.getRole()) && m.getToolCalls() != null && !m.getToolCalls().isEmpty()) {
            List<ToolCallDTO> dtos = new ArrayList<>();
            for (ToolCallDTO tc : m.getToolCalls()) {
                dtos.add(new ToolCallDTO(tc.getId(), tc.getName(), tc.getArgs()));
            }
            out.setToolCalls(dtos);
        } else if ("TOOL".equals(m.getRole())) {
            out.setToolCallId(m.getToolCallId());
        }
        return out;
    }

    /**
     * 找到可折叠的 turn 对齐前缀末尾（exclusive index），使折叠条数尽量大但 ≤ maxFold，
     * 且至少保留最后一个 turn。turn 边界 = USER 消息位置。
     */
    private int turnAlignedFoldEnd(List<ChatMessage> window, int maxFold) {
        int foldEnd = 0;
        int folded = 0;
        int i = 0;
        while (i < window.size()) {
            int turnStart = i;
            i++;
            while (i < window.size() && !ROLE_USER.equals(role(window, i))) {
                i++;
            }
            int turnLen = i - turnStart;
            // 保留至少最后一个 turn：i < size 才可折叠该 turn
            if (i < window.size() && folded + turnLen <= maxFold) {
                foldEnd = i;
                folded += turnLen;
            } else {
                break;
            }
        }
        return foldEnd;
    }

    /** 从 start 开始的下一个 turn 边界（下一个 USER 位置，或末尾）。 */
    private int nextTurnBoundary(List<ChatMessage> window, int start) {
        for (int i = start; i < window.size(); i++) {
            if (ROLE_USER.equals(role(window, i))) {
                return i;
            }
        }
        return window.size();
    }

    /** turn 数量（USER 消息数；首条非 USER 视为半个 turn）。 */
    private int turnCount(List<ChatMessage> window) {
        int count = 0;
        for (ChatMessage m : window) {
            if (ROLE_USER.equals(m.getRole())) {
                count++;
            }
        }
        return Math.max(1, count);
    }

    private String role(List<ChatMessage> window, int i) {
        return window.get(i).getRole();
    }

    /**
     * 解析上下文窗口：取 model.context_window，为空/模型不可用时用全局默认。
     */
    private int resolveContextWindow(String modelId) {
        if (modelId == null) {
            return properties.getDefaultWindow();
        }
        return modelFacade.listAvailableModels("LLM").stream()
                .filter(m -> modelId.equals(m.getId()))
                .map(ModelSummary::getContextWindow)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(properties.getDefaultWindow());
    }

    private int estimateWindow(List<ChatMessage> window) {
        int sum = 0;
        for (ChatMessage m : window) {
            sum += tokenEstimator.estimate(m.getContent());
        }
        return sum;
    }

    private int estimate(String text) {
        return tokenEstimator.estimate(text);
    }

    /**
     * 生成摘要：对「旧 summary + K 条」调用模型（收集型 sink）。失败返回 null（降级为截断）。
     */
    private String generateSummary(String modelId, String oldSummary, List<ChatMessage> toCompact) {
        List<com.zify.model.api.dto.chat.ChatMessage> messages = new ArrayList<>();
        messages.add(new com.zify.model.api.dto.chat.ChatMessage("SYSTEM", SUMMARY_SYSTEM_PROMPT));

        StringBuilder userContent = new StringBuilder();
        if (oldSummary != null && !oldSummary.isBlank()) {
            userContent.append("已有摘要：\n").append(oldSummary).append("\n\n");
        }
        userContent.append("请压缩以下对话：\n");
        for (ChatMessage m : toCompact) {
            userContent.append("[").append(m.getRole()).append("] ").append(m.getContent()).append("\n");
        }
        messages.add(new com.zify.model.api.dto.chat.ChatMessage("USER", userContent.toString()));

        ChatCompletionCommand command = new ChatCompletionCommand(modelId, messages, null);
        StringBuilder acc = new StringBuilder();
        try {
            ChatCompletionResult result = modelFacade.chatStream(command,
                    delta -> { if (delta != null) { acc.append(delta); } });
            return acc.length() == 0 ? result.getContent() : acc.toString();
        } catch (RuntimeException e) {
            log.warn("Summary generation failed, falling back to truncation: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 上下文准备结果。
     */
    public static final class PrepareResult {
        private final List<com.zify.model.api.dto.chat.ChatMessage> messages;
        private final String newSummary;
        private final String newSummaryCoveredMessageId;

        public PrepareResult(List<com.zify.model.api.dto.chat.ChatMessage> messages,
                             String newSummary, String newSummaryCoveredMessageId) {
            this.messages = messages;
            this.newSummary = newSummary;
            this.newSummaryCoveredMessageId = newSummaryCoveredMessageId;
        }

        public List<com.zify.model.api.dto.chat.ChatMessage> getMessages() {
            return messages;
        }

        public String getNewSummary() {
            return newSummary;
        }

        public String getNewSummaryCoveredMessageId() {
            return newSummaryCoveredMessageId;
        }
    }
}
