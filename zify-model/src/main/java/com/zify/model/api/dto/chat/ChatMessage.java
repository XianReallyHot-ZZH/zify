package com.zify.model.api.dto.chat;

import java.util.List;

/**
 * 单条对话消息（user / assistant / system / tool）。
 * <p>
 * 与数据库无关的纯数据结构，供跨模块编排组装 Prompt 使用。
 * P2 扩展：ASSISTANT 带 toolCalls（多轮工具调用请求）；TOOL 带 toolCallId（工具结果）。
 */
public class ChatMessage {

    /** 角色：USER / ASSISTANT / SYSTEM / TOOL */
    private String role;

    /** 消息正文 */
    private String content;

    /** ASSISTANT：本轮请求的工具调用（可空）。 */
    private List<ToolCallDTO> toolCalls;

    /** TOOL：对应的 ASSISTANT toolCall id（可空）。 */
    private String toolCallId;

    public ChatMessage() {
    }

    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<ToolCallDTO> getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(List<ToolCallDTO> toolCalls) {
        this.toolCalls = toolCalls;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }
}
