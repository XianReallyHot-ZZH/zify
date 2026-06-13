package com.zify.chat.api.dto;

import java.time.LocalDateTime;

/**
 * 发送用户消息结果（POST 第一步返回，供前端开 SSE 流）。
 */
public class SendMessageResult {

    private String userMessageId;
    private LocalDateTime createdAt;

    public SendMessageResult() {
    }

    public SendMessageResult(String userMessageId, LocalDateTime createdAt) {
        this.userMessageId = userMessageId;
        this.createdAt = createdAt;
    }

    public String getUserMessageId() {
        return userMessageId;
    }

    public void setUserMessageId(String userMessageId) {
        this.userMessageId = userMessageId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
