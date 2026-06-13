package com.zify.model.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zify.common.exception.BusinessException;
import com.zify.common.exception.ErrorCode;
import com.zify.common.security.SecretEncryptor;
import com.zify.common.web.PageResult;
import com.zify.model.api.dto.model.CreateModelRequest;
import com.zify.model.api.dto.model.ModelListQuery;
import com.zify.model.api.dto.model.ModelResponse;
import com.zify.model.api.dto.model.ModelSummary;
import com.zify.model.api.dto.model.ModelTestResult;
import com.zify.model.api.dto.model.UpdateModelRequest;
import com.zify.model.api.dto.provider.ProviderTestResult;
import com.zify.model.domain.handler.ProviderTestHandler;
import com.zify.model.infrastructure.converter.ModelConverter;
import com.zify.model.infrastructure.entity.ModelEntity;
import com.zify.model.infrastructure.entity.ModelProviderEntity;
import com.zify.model.infrastructure.mapper.ModelMapper;
import com.zify.model.infrastructure.mapper.ModelProviderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 模型管理 Service
 */
@Service
public class ModelService {

    private static final Logger log = LoggerFactory.getLogger(ModelService.class);

    private final ModelMapper modelMapper;
    private final ModelProviderMapper providerMapper;
    private final SecretEncryptor secretEncryptor;
    private final EnumMap<ProviderType, ProviderTestHandler> handlerMap;

    public ModelService(ModelMapper modelMapper,
                        ModelProviderMapper providerMapper,
                        SecretEncryptor secretEncryptor,
                        List<ProviderTestHandler> handlers) {
        this.modelMapper = modelMapper;
        this.providerMapper = providerMapper;
        this.secretEncryptor = secretEncryptor;
        this.handlerMap = new EnumMap<>(ProviderType.class);
        for (ProviderTestHandler handler : handlers) {
            for (ProviderType type : handler.supportedTypes()) {
                this.handlerMap.put(type, handler);
            }
        }
    }

    // ─── 模型 CRUD ────────────────────────────────────────────

    /**
     * 分页查询全局模型列表
     */
    public PageResult<ModelResponse> listModels(ModelListQuery query) {
        LambdaQueryWrapper<ModelEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getModelType() != null, ModelEntity::getModelType, query.getModelType());
        wrapper.eq(query.getEnabled() != null, ModelEntity::getEnabled, query.getEnabled() ? 1 : 0);
        wrapper.eq(query.getProviderId() != null, ModelEntity::getProviderId, query.getProviderId());
        wrapper.orderByDesc(ModelEntity::getCreatedAt);

        Page<ModelEntity> page = modelMapper.selectPage(
                new Page<>(query.getPage(), query.getPageSize()), wrapper);

        List<ModelResponse> responses = page.getRecords().stream()
                .map(entity -> {
                    ProviderInfo info = getProviderInfo(entity.getProviderId());
                    return ModelConverter.toListResponse(entity, info.name, info.type, info.status);
                })
                .collect(Collectors.toList());

        return PageResult.of(responses, page.getTotal(), query.getPage(), query.getPageSize());
    }

    /**
     * 查询指定供应商下的模型列表（不分页）
     */
    public List<ModelResponse> listProviderModels(String providerId) {
        getProviderOrThrow(providerId);

        LambdaQueryWrapper<ModelEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelEntity::getProviderId, providerId);
        wrapper.orderByAsc(ModelEntity::getModelType, ModelEntity::getModelName);

        List<ModelEntity> entities = modelMapper.selectList(wrapper);
        ProviderInfo info = getProviderInfo(providerId);

        return entities.stream()
                .map(entity -> ModelConverter.toListResponse(entity, info.name, info.type, info.status))
                .collect(Collectors.toList());
    }

    /**
     * 查询模型详情
     */
    public ModelResponse getModel(String id) {
        ModelEntity entity = getModelOrThrow(id);
        ProviderInfo info = getProviderInfo(entity.getProviderId());
        return ModelConverter.toResponse(entity, info.name, info.type, info.status);
    }

    /**
     * 创建模型
     */
    public ModelResponse createModel(String providerId, CreateModelRequest request) {
        getProviderOrThrow(providerId);
        checkModelNameUnique(providerId, request.getModelName(), null);

        ModelEntity entity = ModelConverter.toEntity(request, providerId);
        modelMapper.insert(entity);
        log.info("Model created: id={}, name={}, providerId={}", entity.getId(), entity.getModelName(), providerId);

        ProviderInfo info = getProviderInfo(providerId);
        return ModelConverter.toResponse(entity, info.name, info.type, info.status);
    }

    /**
     * 更新模型
     */
    public ModelResponse updateModel(String id, UpdateModelRequest request) {
        ModelEntity entity = getModelOrThrow(id);

        ModelConverter.updateEntity(entity, request);
        modelMapper.updateById(entity);
        log.info("Model updated: id={}", id);

        ProviderInfo info = getProviderInfo(entity.getProviderId());
        return ModelConverter.toResponse(entity, info.name, info.type, info.status);
    }

    /**
     * 删除模型
     */
    public void deleteModel(String id) {
        getModelOrThrow(id);
        modelMapper.deleteById(id);
        log.info("Model deleted: id={}", id);
    }

    /**
     * 更新模型启用状态
     */
    public void updateEnabled(String id, Boolean enabled) {
        getModelOrThrow(id);

        modelMapper.update(null,
                new LambdaUpdateWrapper<ModelEntity>()
                        .eq(ModelEntity::getId, id)
                        .set(ModelEntity::getEnabled, enabled ? 1 : 0));

        log.info("Model enabled updated: id={}, enabled={}", id, enabled);
    }

    // ─── Facade 调用 ──────────────────────────────────────────

    /**
     * 查询可用的模型列表（供 Agent / 工作流 / 知识库下拉框使用）
     * 条件：model.enabled=1 AND provider.status=ACTIVE AND 均未删除
     */
    public List<ModelSummary> listAvailableModels(String modelType) {
        LambdaQueryWrapper<ModelEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelEntity::getEnabled, 1);
        if (modelType != null && !modelType.isBlank()) {
            wrapper.eq(ModelEntity::getModelType, modelType);
        }
        wrapper.orderByAsc(ModelEntity::getModelType, ModelEntity::getModelName);

        List<ModelEntity> entities = modelMapper.selectList(wrapper);

        List<ModelSummary> result = new ArrayList<>();
        for (ModelEntity entity : entities) {
            ModelProviderEntity provider = providerMapper.selectById(entity.getProviderId());
            if (provider != null
                    && provider.getIsDeleted() != null
                    && provider.getIsDeleted() == 0
                    && "ACTIVE".equals(provider.getStatus())) {
                result.add(ModelConverter.toSummary(entity, provider.getName(), provider.getProviderType()));
            }
        }
        return result;
    }

    // ─── 健康测试 ─────────────────────────────────────────────

    /**
     * 测试供应商连接
     */
    public ProviderTestResult testProvider(String providerId) {
        ModelProviderEntity provider = getProviderOrThrow(providerId);
        String apiKey = provider.getApiKey() != null ? secretEncryptor.decrypt(provider.getApiKey()) : null;
        String baseUrl = provider.getBaseUrl();

        long start = System.currentTimeMillis();
        try {
            ProviderTestHandler handler = getHandler(provider.getProviderType());
            return handler.testConnection(baseUrl, apiKey, provider.getExtraConfig(), start);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - start;
            log.warn("Provider test failed: providerId={}, error={}", providerId, e.getMessage());
            ProviderTestResult result = new ProviderTestResult();
            result.setSuccess(false);
            result.setMessage(extractErrorMessage(e));
            result.setLatencyMs(latencyMs);
            return result;
        }
    }

    /**
     * 测试模型可用性
     */
    public ModelTestResult testModel(String modelId) {
        ModelEntity model = getModelOrThrow(modelId);
        ModelProviderEntity provider = providerMapper.selectById(model.getProviderId());

        // 校验供应商状态
        if (provider == null || provider.getIsDeleted() != 0) {
            return buildTestResult(false, "供应商已删除", 0L, "供应商不存在或已删除");
        }
        if (!"ACTIVE".equals(provider.getStatus())) {
            return buildTestResult(false, "供应商已禁用", 0L, "供应商状态为 " + provider.getStatus());
        }

        String apiKey = provider.getApiKey() != null ? secretEncryptor.decrypt(provider.getApiKey()) : null;
        String baseUrl = provider.getBaseUrl();
        String modelName = model.getModelName();

        long start = System.currentTimeMillis();
        try {
            ProviderTestHandler handler = getHandler(provider.getProviderType());
            if ("EMBEDDING".equals(model.getModelType())) {
                return handler.testEmbeddingModel(baseUrl, apiKey, modelName, start);
            } else {
                return handler.testLlmModel(baseUrl, apiKey, modelName, provider.getExtraConfig(), start);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - start;
            log.warn("Model test failed: modelId={}, error={}", modelId, e.getMessage());
            return buildTestResult(false, "模型不可用", latencyMs, extractErrorMessage(e));
        }
    }

    // ─── 私有方法 ──────────────────────────────────────────────

    private ModelEntity getModelOrThrow(String id) {
        ModelEntity entity = modelMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.MODEL_NOT_FOUND);
        }
        return entity;
    }

    private ModelProviderEntity getProviderOrThrow(String providerId) {
        ModelProviderEntity provider = providerMapper.selectById(providerId);
        if (provider == null) {
            throw new BusinessException(ErrorCode.PROVIDER_NOT_FOUND);
        }
        return provider;
    }

    private void checkModelNameUnique(String providerId, String modelName, String excludeId) {
        LambdaQueryWrapper<ModelEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelEntity::getProviderId, providerId);
        wrapper.eq(ModelEntity::getModelName, modelName);
        wrapper.ne(excludeId != null, ModelEntity::getId, excludeId);
        wrapper.select(ModelEntity::getId);

        if (modelMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ErrorCode.MODEL_NAME_DUPLICATE);
        }
    }

    /**
     * 获取供应商简要信息，避免重复查库
     */
    private ProviderInfo getProviderInfo(String providerId) {
        ModelProviderEntity provider = providerMapper.selectById(providerId);
        if (provider != null) {
            return new ProviderInfo(provider.getName(), provider.getProviderType(), provider.getStatus());
        }
        return new ProviderInfo("", "", "");
    }

    /**
     * 根据供应商类型字符串获取对应的测试 Handler
     */
    private ProviderTestHandler getHandler(String providerType) {
        ProviderType type = ProviderType.fromString(providerType);
        ProviderTestHandler handler = handlerMap.get(type);
        if (handler == null) {
            throw new BusinessException(ErrorCode.PROVIDER_TEST_ERROR,
                    "不支持的供应商类型: " + providerType);
        }
        return handler;
    }

    private ModelTestResult buildTestResult(boolean success, String message, long latencyMs, String errorDetail) {
        ModelTestResult result = new ModelTestResult();
        result.setSuccess(success);
        result.setMessage(message);
        result.setLatencyMs(latencyMs);
        result.setErrorDetail(errorDetail);
        return result;
    }

    private String extractErrorMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return "连接失败";
        }
        return message.length() > 200 ? message.substring(0, 200) : message;
    }

    /**
     * 供应商简要信息，避免反复查询
     */
    private static class ProviderInfo {
        final String name;
        final String type;
        final String status;

        ProviderInfo(String name, String type, String status) {
            this.name = name;
            this.type = type;
            this.status = status;
        }
    }
}
