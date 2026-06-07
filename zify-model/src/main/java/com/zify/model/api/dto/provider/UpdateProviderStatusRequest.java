package com.zify.model.api.dto.provider;

import jakarta.validation.constraints.NotBlank;

/**
 * 更新供应商状态请求
 */
public class UpdateProviderStatusRequest {

    @NotBlank(message = "状态不能为空")
    private String status;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
