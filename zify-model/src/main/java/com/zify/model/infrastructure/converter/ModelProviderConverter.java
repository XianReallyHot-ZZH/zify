package com.zify.model.infrastructure.converter;

import com.zify.model.api.dto.provider.CreateProviderRequest;
import com.zify.model.api.dto.provider.ProviderResponse;
import com.zify.model.api.dto.provider.UpdateProviderRequest;
import com.zify.model.infrastructure.entity.ModelProviderEntity;

/**
 * 供应商 Entity <-> DTO 转换器
 */
public final class ModelProviderConverter {

    private ModelProviderConverter() {
    }

    /**
     * Entity 转 ProviderResponse
     */
    public static ProviderResponse toResponse(ModelProviderEntity entity, int modelCount) {
        ProviderResponse response = new ProviderResponse();
        response.setId(entity.getId());
        response.setName(entity.getName());
        response.setProviderType(entity.getProviderType());
        response.setBaseUrl(entity.getBaseUrl());
        response.setExtraConfig(entity.getExtraConfig());
        response.setStatus(entity.getStatus());
        response.setHasApiKey(entity.getApiKey() != null);
        response.setModelCount(modelCount);
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    /**
     * CreateProviderRequest 转 Entity
     */
    public static ModelProviderEntity toEntity(CreateProviderRequest request) {
        ModelProviderEntity entity = new ModelProviderEntity();
        entity.setName(request.getName());
        entity.setProviderType(request.getProviderType());
        entity.setApiKey(request.getApiKey());
        entity.setBaseUrl(request.getBaseUrl());
        entity.setExtraConfig(request.getExtraConfig());
        entity.setStatus("ACTIVE");
        return entity;
    }

    /**
     * UpdateProviderRequest 更新到已有 Entity
     */
    public static void updateEntity(ModelProviderEntity entity, UpdateProviderRequest request) {
        if (request.getName() != null) {
            entity.setName(request.getName());
        }
        if (request.getApiKey() != null) {
            entity.setApiKey(request.getApiKey());
        }
        if (request.getBaseUrl() != null) {
            entity.setBaseUrl(request.getBaseUrl());
        }
        if (request.getExtraConfig() != null) {
            entity.setExtraConfig(request.getExtraConfig());
        }
    }
}
