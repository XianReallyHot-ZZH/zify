package com.zify.model.api.dto.chat;

/**
 * 流式 Chat 调用最终结果（流结束后返回）。
 */
public class ChatCompletionResult {

    /** 累计全文 */
    private String content;

    /** 结束原因：STOP / LENGTH / TIMEOUT / CANCELLED ... */
    private String finishReason;

    /** Token 用量，可空 */
    private TokenUsage usage;

    public ChatCompletionResult() {
    }

    public ChatCompletionResult(String content, String finishReason, TokenUsage usage) {
        this.content = content;
        this.finishReason = finishReason;
        this.usage = usage;
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
}
