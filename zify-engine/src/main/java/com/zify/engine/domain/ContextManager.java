package com.zify.engine.domain;

import com.zify.agent.api.dto.AgentConfigDTO;
import com.zify.engine.api.dto.ChatMessage;
import com.zify.engine.config.ChatContextProperties;
import com.zify.model.api.ModelFacade;
import com.zify.model.api.dto.chat.ChatCompletionCommand;
import com.zify.model.api.dto.chat.ChatCompletionResult;
import com.zify.model.api.dto.model.ModelSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 上下文管理：窗口预算 + 摘要压缩 + 尾部截断兜底（glm-docs/02 §5.5 / prompt-04 任务 14）。
 * <p>
 * 输入：agent 配置 + 当前 running summary + 活窗口历史（chat 已按 summary_covered 截取）。
 * 输出：最终发给 LLM 的 messages + 可选 newSummary/newSummaryCoveredMessageId（仅压缩时非空）。
 * <p>
 * 摘要生成走 ModelFacade.chatStream（收集型 sink，不接用户 SSE），同样受 bulkhead/retry/超时约束，不在事务内。
 */
@Component
public class ContextManager {

    private static final Logger log = LoggerFactory.getLogger(ContextManager.class);

    /** 预留输出 token（engine 无法读 model.defaultParams.maxTokens，按固定值预留）。 */
    private static final int RESERVED_OUTPUT_TOKENS = 4096;

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

        // 第一层：摘要压缩（超阈值时折叠最旧 K 条，绝不含本轮 user 即保留末尾至少一条）
        if (systemTokens + summaryTokens + estimateWindow(activeWindow) > budget * threshold) {
            int compactCount = Math.min(properties.getCompactionBatch(), activeWindow.size() - 1);
            if (compactCount > 0) {
                List<ChatMessage> toCompact = new ArrayList<>(activeWindow.subList(0, compactCount));
                String generated = generateSummary(agent.getModelId(), summary, toCompact);
                if (generated != null && !generated.isBlank()) {
                    newSummary = generated;
                    newCoveredId = toCompact.get(toCompact.size() - 1).getMessageId();
                    activeWindow = new ArrayList<>(activeWindow.subList(compactCount, activeWindow.size()));
                    summaryTokens = estimate(newSummary);
                }
            }
        }

        // 第二层：尾部截断兜底（压缩后仍超预算 → 从最旧起丢弃，绝不丢本轮 user）
        while (activeWindow.size() > 1
                && systemTokens + summaryTokens + estimateWindow(activeWindow) > budget) {
            activeWindow.remove(0);
        }

        // 组装最终 messages：[SYSTEM: systemPrompt] + (effectiveSummary? SYSTEM) + 活窗口
        List<com.zify.model.api.dto.chat.ChatMessage> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new com.zify.model.api.dto.chat.ChatMessage("SYSTEM", systemPrompt));
        }
        String effectiveSummary = newSummary != null ? newSummary : summary;
        if (effectiveSummary != null && !effectiveSummary.isBlank()) {
            messages.add(new com.zify.model.api.dto.chat.ChatMessage("SYSTEM", effectiveSummary));
        }
        for (ChatMessage m : activeWindow) {
            messages.add(new com.zify.model.api.dto.chat.ChatMessage(m.getRole(), m.getContent()));
        }
        return new PrepareResult(messages, newSummary, newCoveredId);
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
