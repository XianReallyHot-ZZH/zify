package com.zify.model.api.dto.chat;

import java.util.List;

/**
 * 流式 Chat 调用最终结果（流结束后返回）。
 * <p>
 * P2 扩展：toolCalls 非空表示模型请求工具调用（finishReason=TOOL_CALLS），engine 据此驱动 ReAct。
 */
public class ChatCompletionResult {

    /** 累计全文 */
    private String content;

    /** 结束原因：STOP / LENGTH / TIMEOUT / CANCELLED / TOOL_CALLS ... */
    private String finishReason;

    /** Token 用量，可空 */
    private TokenUsage usage;

    /** 模型请求的工具调用（可空，finishReason=TOOL_CALLS 时非空） */
    private List<ToolCallDTO> toolCalls;

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

    public List<ToolCallDTO> getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(List<ToolCallDTO> toolCalls) {
        this.toolCalls = toolCalls;
    }
}
