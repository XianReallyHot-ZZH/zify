package com.zify.model.infrastructure.converter;

import com.zify.model.api.dto.model.CreateModelRequest;
import com.zify.model.api.dto.model.ModelResponse;
import com.zify.model.api.dto.model.ModelSummary;
import com.zify.model.api.dto.model.UpdateModelRequest;
import com.zify.model.infrastructure.entity.ModelEntity;

/**
 * 模型 Entity <-> DTO 转换器
 */
public final class ModelConverter {

    private ModelConverter() {
    }

    /**
     * Entity 转 ModelResponse（带供应商信息）
     */
    public static ModelResponse toResponse(ModelEntity entity,
                                            String providerName,
                                            String providerType,
                                            String providerStatus) {
        ModelResponse response = new ModelResponse();
        response.setId(entity.getId());
        response.setProviderId(entity.getProviderId());
        response.setModelName(entity.getModelName());
        response.setDisplayName(entity.getDisplayName());
        response.setModelType(entity.getModelType());
        response.setEnabled(entity.getEnabled() != null && entity.getEnabled() == 1);
        response.setDefaultParams(entity.getDefaultParams());
        response.setProviderName(providerName);
        response.setProviderType(providerType);
        response.setProviderStatus(providerStatus);
        response.setContextWindow(entity.getContextWindow());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    /**
     * Entity 转 ModelResponse（不带 defaultParams，用于列表接口）
     */
    public static ModelResponse toListResponse(ModelEntity entity,
                                                String providerName,
                                                String providerType,
                                                String providerStatus) {
        ModelResponse response = new ModelResponse();
        response.setId(entity.getId());
        response.setProviderId(entity.getProviderId());
        response.setModelName(entity.getModelName());
        response.setDisplayName(entity.getDisplayName());
        response.setModelType(entity.getModelType());
        response.setEnabled(entity.getEnabled() != null && entity.getEnabled() == 1);
        // 列表接口不返回 defaultParams
        response.setProviderName(providerName);
        response.setProviderType(providerType);
        response.setProviderStatus(providerStatus);
        response.setContextWindow(entity.getContextWindow());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    /**
     * CreateModelRequest 转 Entity
     */
    public static ModelEntity toEntity(CreateModelRequest request, String providerId) {
        ModelEntity entity = new ModelEntity();
        entity.setProviderId(providerId);
        entity.setModelName(request.getModelName());
        entity.setDisplayName(request.getDisplayName());
        entity.setModelType(request.getModelType());
        entity.setEnabled(Boolean.TRUE.equals(request.getEnabled()) ? 1 : 0);
        return entity;
    }

    /**
     * UpdateModelRequest 更新到已有 Entity
     */
    public static void updateEntity(ModelEntity entity, UpdateModelRequest request) {
        if (request.getDisplayName() != null) {
            entity.setDisplayName(request.getDisplayName());
        }
        if (request.getModelType() != null) {
            entity.setModelType(request.getModelType());
        }
        if (request.getEnabled() != null) {
            entity.setEnabled(request.getEnabled() ? 1 : 0);
        }
        // defaultParams 为 null 时表示不修改；非 null 时覆盖（包括空 Map 表示清除）
        if (request.getDefaultParams() != null) {
            entity.setDefaultParams(request.getDefaultParams());
        }
    }

    /**
     * Entity 转 ModelSummary（供其他模块下拉框使用）
     */
    public static ModelSummary toSummary(ModelEntity entity, String providerName, String providerType) {
        ModelSummary summary = new ModelSummary();
        summary.setId(entity.getId());
        summary.setDisplayName(entity.getDisplayName());
        summary.setModelName(entity.getModelName());
        summary.setProviderName(providerName);
        summary.setProviderType(providerType);
        return summary;
    }
}
