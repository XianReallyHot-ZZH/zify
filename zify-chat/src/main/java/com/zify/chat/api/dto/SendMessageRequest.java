package com.zify.chat.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 发送用户消息请求（POST 第一步，不在此调用 LLM）。
 */
public class SendMessageRequest {

    @NotBlank(message = "消息内容不能为空")
    private String content;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
