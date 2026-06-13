package com.zify.engine.api.dto;

/**
 * 编排用对话消息（user / assistant / system）。由 chat 组装历史时传入。
 * <p>
 * messageId 可选：chat 组装历史时填，供上下文压缩定位「摘要覆盖到第 K 条」。
 */
public class ChatMessage {

    /** 角色：USER / ASSISTANT / SYSTEM */
    private String role;
    private String content;
    /** 可选：对应 message.id，供上下文压缩定位 newCoveredId。 */
    private String messageId;

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
}
