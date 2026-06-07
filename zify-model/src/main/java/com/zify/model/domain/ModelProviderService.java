package com.zify.model.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zify.common.exception.BusinessException;
import com.zify.common.exception.ErrorCode;
import com.zify.common.security.SecretEncryptor;
import com.zify.common.web.PageResult;
import com.zify.model.api.dto.provider.CreateProviderRequest;
import com.zify.model.api.dto.provider.ProviderListQuery;
import com.zify.model.api.dto.provider.ProviderResponse;
import com.zify.model.api.dto.provider.UpdateProviderRequest;
import com.zify.model.infrastructure.converter.ModelProviderConverter;
import com.zify.model.infrastructure.entity.ModelEntity;
import com.zify.model.infrastructure.entity.ModelProviderEntity;
import com.zify.model.infrastructure.mapper.ModelMapper;
import com.zify.model.infrastructure.mapper.ModelProviderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 供应商管理 Service
 */
@Service
public class ModelProviderService {

    private static final Logger log = LoggerFactory.getLogger(ModelProviderService.class);

    private final ModelProviderMapper providerMapper;
    private final ModelMapper modelMapper;
    private final SecretEncryptor secretEncryptor;

    public ModelProviderService(ModelProviderMapper providerMapper,
                                ModelMapper modelMapper,
                                SecretEncryptor secretEncryptor) {
        this.providerMapper = providerMapper;
        this.modelMapper = modelMapper;
        this.secretEncryptor = secretEncryptor;
    }

    /**
     * 分页查询供应商列表
     */
    public PageResult<ProviderResponse> listProviders(ProviderListQuery query) {
        LambdaQueryWrapper<ModelProviderEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getProviderType() != null, ModelProviderEntity::getProviderType, query.getProviderType());
        wrapper.eq(query.getStatus() != null, ModelProviderEntity::getStatus, query.getStatus());
        wrapper.orderByDesc(ModelProviderEntity::getCreatedAt);

        Page<ModelProviderEntity> page = providerMapper.selectPage(
                new Page<>(query.getPage(), query.getPageSize()), wrapper);

        // 批量查询每个供应商的模型数量
        Map<String, Integer> modelCountMap = getModelCountMap(page.getRecords());

        List<ProviderResponse> responses = page.getRecords().stream()
                .map(entity -> ModelProviderConverter.toResponse(entity,
                        modelCountMap.getOrDefault(entity.getId(), 0)))
                .collect(Collectors.toList());

        return PageResult.of(responses, page.getTotal(), query.getPage(), query.getPageSize());
    }

    /**
     * 查询供应商详情
     */
    public ProviderResponse getProvider(String id) {
        ModelProviderEntity entity = getProviderOrThrow(id);
        int modelCount = getModelCount(entity.getId());
        return ModelProviderConverter.toResponse(entity, modelCount);
    }

    /**
     * 创建供应商
     */
    public ProviderResponse createProvider(CreateProviderRequest request) {
        // 校验名称唯一
        checkNameUnique(request.getName(), null);

        ModelProviderEntity entity = ModelProviderConverter.toEntity(request);

        // Base URL 去掉末尾 /
        entity.setBaseUrl(stripTrailingSlash(entity.getBaseUrl()));

        // API Key 加密
        if (entity.getApiKey() != null) {
            entity.setApiKey(secretEncryptor.encrypt(entity.getApiKey()));
        }

        providerMapper.insert(entity);
        log.info("Provider created: id={}, name={}", entity.getId(), entity.getName());

        return ModelProviderConverter.toResponse(entity, 0);
    }

    /**
     * 更新供应商
     */
    public ProviderResponse updateProvider(String id, UpdateProviderRequest request) {
        ModelProviderEntity entity = getProviderOrThrow(id);

        // 校验名称唯一（名称变更时）
        if (request.getName() != null && !request.getName().equals(entity.getName())) {
            checkNameUnique(request.getName(), id);
        }

        // 更新字段
        ModelProviderConverter.updateEntity(entity, request);

        // Base URL 去掉末尾 /
        if (entity.getBaseUrl() != null) {
            entity.setBaseUrl(stripTrailingSlash(entity.getBaseUrl()));
        }

        // API Key 加密（UpdateProviderConverter 已设置 apiKey，这里需要加密）
        if (request.getApiKey() != null) {
            entity.setApiKey(secretEncryptor.encrypt(request.getApiKey()));
        }

        providerMapper.updateById(entity);
        log.info("Provider updated: id={}", id);

        int modelCount = getModelCount(id);
        return ModelProviderConverter.toResponse(entity, modelCount);
    }

    /**
     * 删除供应商（级联软删除其下所有模型）
     */
    @Transactional
    public void deleteProvider(String id) {
        ModelProviderEntity entity = getProviderOrThrow(id);

        // 软删除供应商
        providerMapper.deleteById(id);

        // 软删除该供应商下所有模型
        modelMapper.update(null,
                new LambdaUpdateWrapper<ModelEntity>()
                        .eq(ModelEntity::getProviderId, id)
                        .eq(ModelEntity::getIsDeleted, 0)
                        .set(ModelEntity::getIsDeleted, 1));

        log.info("Provider deleted: id={}, name={}", id, entity.getName());
    }

    /**
     * 更新供应商状态
     */
    public void updateStatus(String id, String status) {
        getProviderOrThrow(id);

        if (!"ACTIVE".equals(status) && !"INACTIVE".equals(status)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "状态值必须为 ACTIVE 或 INACTIVE");
        }

        providerMapper.update(null,
                new LambdaUpdateWrapper<ModelProviderEntity>()
                        .eq(ModelProviderEntity::getId, id)
                        .set(ModelProviderEntity::getStatus, status));

        log.info("Provider status updated: id={}, status={}", id, status);
    }

    // ─── 内部方法 ────────────────────────────────────────────

    /**
     * 按 ID 查询供应商，不存在则抛异常
     */
    private ModelProviderEntity getProviderOrThrow(String id) {
        ModelProviderEntity entity = providerMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.PROVIDER_NOT_FOUND);
        }
        return entity;
    }

    /**
     * 校验供应商名称在未删除数据中唯一
     */
    private void checkNameUnique(String name, String excludeId) {
        LambdaQueryWrapper<ModelProviderEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelProviderEntity::getName, name);
        wrapper.ne(excludeId != null, ModelProviderEntity::getId, excludeId);
        wrapper.select(ModelProviderEntity::getId);

        if (providerMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ErrorCode.PROVIDER_NAME_DUPLICATE);
        }
    }

    /**
     * 查询指定供应商的模型数量
     */
    private int getModelCount(String providerId) {
        LambdaQueryWrapper<ModelEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelEntity::getProviderId, providerId);
        wrapper.select(ModelEntity::getId);
        return Math.toIntExact(modelMapper.selectCount(wrapper));
    }

    /**
     * 批量查询供应商的模型数量
     */
    private Map<String, Integer> getModelCountMap(List<ModelProviderEntity> providers) {
        if (providers == null || providers.isEmpty()) {
            return Map.of();
        }
        // 简化实现：逐个查询。供应商数量少（≤50），性能可接受
        return providers.stream()
                .collect(Collectors.toMap(
                        ModelProviderEntity::getId,
                        p -> getModelCount(p.getId())));
    }

    /**
     * 去掉 URL 末尾的 /
     */
    private String stripTrailingSlash(String url) {
        if (url != null && url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }
}
