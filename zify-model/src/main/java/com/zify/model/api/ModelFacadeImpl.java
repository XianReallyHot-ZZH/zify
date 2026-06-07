package com.zify.model.api;

import com.zify.model.api.dto.model.ModelSummary;
import com.zify.model.domain.ModelService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 模型模块 Facade 实现，委托给 ModelService
 */
@Service
public class ModelFacadeImpl implements ModelFacade {

    private final ModelService modelService;

    public ModelFacadeImpl(ModelService modelService) {
        this.modelService = modelService;
    }

    @Override
    public List<ModelSummary> listAvailableModels(String modelType) {
        return modelService.listAvailableModels(modelType);
    }
}
