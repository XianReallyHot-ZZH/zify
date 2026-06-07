package com.zify.model.api.dto.model;

import jakarta.validation.constraints.NotNull;

/**
 * 更新模型启用状态请求
 */
public class UpdateModelEnabledRequest {

    @NotNull(message = "启用状态不能为空")
    private Boolean enabled;

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
