package com.zify.model.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.zify.common.persistence.entity.BaseEntity;

import java.util.Map;

/**
 * 模型配置实体
 */
@TableName(value = "model", autoResultMap = true)
public class ModelEntity extends BaseEntity {

    private String providerId;

    private String modelName;

    private String displayName;

    private String modelType;

    private Integer enabled;

    @TableField(value = "default_params", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> defaultParams;

    /**
     * 模型上下文窗口大小（token），NULL 时用全局默认值。
     */
    private Integer contextWindow;

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }

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

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }

    public Map<String, Object> getDefaultParams() {
        return defaultParams;
    }

    public void setDefaultParams(Map<String, Object> defaultParams) {
        this.defaultParams = defaultParams;
    }

    public Integer getContextWindow() {
        return contextWindow;
    }

    public void setContextWindow(Integer contextWindow) {
        this.contextWindow = contextWindow;
    }
}
