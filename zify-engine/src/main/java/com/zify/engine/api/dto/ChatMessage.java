package com.zify.engine.api.dto;

import com.zify.model.api.dto.chat.ToolCallDTO;

import java.util.List;

/**
 * 编排用对话消息（user / assistant / system / tool）。由 chat 组装历史时传入。
 * <p>
 * P2 扩展：ASSISTANT 带 toolCalls（模型本轮工具调用请求）；TOOL 带 toolCallId（工具结果）。
 * messageId 可选：chat 组装历史时填，供上下文压缩定位「摘要覆盖到第 K 条」；engine 本轮新增消息也填（供 chat 落库定位）。
 */
public class ChatMessage {

    /** 角色：USER / ASSISTANT / SYSTEM / TOOL */
    private String role;
    private String content;
    /** 可选：对应 message.id，供上下文压缩定位 newCoveredId / chat 落库定位。 */
    private String messageId;
    /** ASSISTANT：本轮请求的工具调用（可空）。 */
    private List<ToolCallDTO> toolCalls;
    /** TOOL：对应的 ASSISTANT toolCall id（可空）。 */
    private String toolCallId;
    /** TOOL：工具名快照（落 message.metadata.toolName，渲染工具卡片）。 */
    private String toolName;
    /** TOOL：tool_call_log.id（落 message.metadata.toolCallLogId，下钻）。 */
    private String toolCallLogId;

    public ChatMessage() {
    }

    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public ChatMessage(String role, String content, String messageId) {
        this.role = role;
        this.content = content;
        this.messageId = messageId;
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

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
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

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getToolCallLogId() {
        return toolCallLogId;
    }

    public void setToolCallLogId(String toolCallLogId) {
        this.toolCallLogId = toolCallLogId;
    }
}
