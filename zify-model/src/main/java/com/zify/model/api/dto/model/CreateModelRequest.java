package com.zify.model.api.dto.model;

import jakarta.validation.constraints.NotBlank;

/**
 * 创建模型请求
 */
public class CreateModelRequest {

    @NotBlank(message = "模型标识不能为空")
    private String modelName;

    private String displayName;

    @NotBlank(message = "模型类型不能为空")
    private String modelType;

    private Boolean enabled = true;

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getModelType() {
        return modelType;
    }

    public void setModelType(String modelType) {
        this.modelType = modelType;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
