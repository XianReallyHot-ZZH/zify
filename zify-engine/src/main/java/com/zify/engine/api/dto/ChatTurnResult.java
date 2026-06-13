package com.zify.engine.api.dto;

/**
 * 单轮对话编排结果（engine → chat）。
 * <p>
 * newSummary / newSummaryCoveredMessageId 仅当本轮触发摘要压缩时非空（本任务恒为 null）。
 */
public class ChatTurnResult {

    private String content;
    private String finishReason;
    private TokenUsage usage;
    private String newSummary;
    private String newSummaryCoveredMessageId;

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
}
