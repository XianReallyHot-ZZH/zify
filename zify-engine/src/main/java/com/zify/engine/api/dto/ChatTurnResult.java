package com.zify.engine.api.dto;

import java.util.List;

/**
 * 单轮对话编排结果（engine → chat）。
 * <p>
 * P2 扩展：newMessages 为本轮新增消息序列（ASSISTANT-toolCall / TOOL / 最终 ASSISTANT），由 chat 批量落库；
 * finalAssistantMessageId 指向最终回复（多轮 ReAct 里为最后一轮的 roundId）。
 * newSummary / newSummaryCoveredMessageId 仅当本轮触发摘要压缩时非空（P2 §17 接入，本任务先透传）。
 */
public class ChatTurnResult {

    private String content;
    private String finishReason;
    private TokenUsage usage;
    private String newSummary;
    private String newSummaryCoveredMessageId;
    /** 本轮新增消息序列（含 ASSISTANT-toolCall + TOOL + 最终 ASSISTANT）。 */
    private List<ChatMessage> newMessages;
    /** 最终 ASSISTANT 消息 id（done 事件用）。 */
    private String finalAssistantMessageId;

    public ChatTurnResult() {
    }

    public ChatTurnResult(String content, String finishReason, TokenUsage usage,
                          String newSummary, String newSummaryCoveredMessageId) {
        this.content = content;
        this.finishReason = finishReason;
        this.usage = usage;
        this.newSummary = newSummary;
        this.newSummaryCoveredMessageId = newSummaryCoveredMessageId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getFinishReason() {
        return finishReason;
    }

    public void setFinishReason(String finishReason) {
        this.finishReason = finishReason;
    }

    public TokenUsage getUsage() {
        return usage;
    }

    public void setUsage(TokenUsage usage) {
        this.usage = usage;
    }

    public String getNewSummary() {
        return newSummary;
    }

    public void setNewSummary(String newSummary) {
        this.newSummary = newSummary;
    }

    public String getNewSummaryCoveredMessageId() {
        return newSummaryCoveredMessageId;
    }

    public void setNewSummaryCoveredMessageId(String newSummaryCoveredMessageId) {
        this.newSummaryCoveredMessageId = newSummaryCoveredMessageId;
    }

    public List<ChatMessage> getNewMessages() {
        return newMessages;
    }

    public void setNewMessages(List<ChatMessage> newMessages) {
        this.newMessages = newMessages;
    }

    public String getFinalAssistantMessageId() {
        return finalAssistantMessageId;
    }

    public void setFinalAssistantMessageId(String finalAssistantMessageId) {
        this.finalAssistantMessageId = finalAssistantMessageId;
    }
}
