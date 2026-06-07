package com.zify.model.api.dto.model;

import com.zify.common.web.PageRequest;

/**
 * 模型列表查询参数
 */
public class ModelListQuery extends PageRequest {

    private String modelType;
    private Boolean enabled;
    private String providerId;

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

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }
}
