package com.zify.chat.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 新建会话请求。
 */
public class CreateConversationRequest {

    @NotBlank(message = "Agent 不能为空")
    private String agentId;

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }
}
