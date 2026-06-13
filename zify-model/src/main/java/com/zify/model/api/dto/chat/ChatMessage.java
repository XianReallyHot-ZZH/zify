package com.zify.model.api.dto.chat;

/**
 * 单条对话消息（user / assistant / system）。
 * <p>
 * 与数据库无关的纯数据结构，供跨模块编排组装 Prompt 使用。
 */
public class ChatMessage {

    /** 角色：USER / ASSISTANT / SYSTEM */
    private String role;

    /** 消息正文 */
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
