package com.zify.agent.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 更新 Agent 状态请求（ACTIVE / INACTIVE）。
 */
public class UpdateAgentStatusRequest {

    @NotBlank(message = "状态不能为空")
    private String status;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
