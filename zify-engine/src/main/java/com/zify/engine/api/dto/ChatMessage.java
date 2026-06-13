package com.zify.engine.api.dto;

/**
 * 编排用对话消息（user / assistant / system）。由 chat 组装历史时传入。
 * <p>
 * Task 14 会在此基础上追加可选 {@code messageId} 字段，供上下文压缩定位。
 */
public class ChatMessage {

    /** 角色：USER / ASSISTANT / SYSTEM */
    private String role;
    private String content;

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
}
