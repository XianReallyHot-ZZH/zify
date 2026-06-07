package com.zify.model.controller;

import com.zify.common.web.PageResult;
import com.zify.common.web.Result;
import com.zify.model.api.dto.model.CreateModelRequest;
import com.zify.model.api.dto.model.ModelListQuery;
import com.zify.model.api.dto.model.ModelResponse;
import com.zify.model.api.dto.model.ModelTestResult;
import com.zify.model.api.dto.model.UpdateModelEnabledRequest;
import com.zify.model.api.dto.model.UpdateModelRequest;
import com.zify.model.domain.ModelService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 模型管理 Controller
 */
@RestController
@RequestMapping("/api/model")
public class ModelController {

    private final ModelService modelService;

    public ModelController(ModelService modelService) {
        this.modelService = modelService;
    }

    @PostMapping("/providers/{providerId}/models")
    public Result<ModelResponse> createModel(@PathVariable String providerId,
                                              @Valid @RequestBody CreateModelRequest request) {
        return Result.ok(modelService.createModel(providerId, request));
    }

    @GetMapping("/models")
    public Result<PageResult<ModelResponse>> listModels(ModelListQuery query) {
        return Result.ok(modelService.listModels(query));
    }

    @GetMapping("/providers/{providerId}/models")
    public Result<List<ModelResponse>> listProviderModels(@PathVariable String providerId) {
        return Result.ok(modelService.listProviderModels(providerId));
    }

    @GetMapping("/models/{id}")
    public Result<ModelResponse> getModel(@PathVariable String id) {
        return Result.ok(modelService.getModel(id));
    }

    @PutMapping("/models/{id}")
    public Result<ModelResponse> updateModel(@PathVariable String id,
                                               @Valid @RequestBody UpdateModelRequest request) {
        return Result.ok(modelService.updateModel(id, request));
    }

    @DeleteMapping("/models/{id}")
    public Result<Void> deleteModel(@PathVariable String id) {
        modelService.deleteModel(id);
        return Result.ok();
    }

    @PutMapping("/models/{id}/enabled")
    public Result<Void> updateEnabled(@PathVariable String id,
                                       @Valid @RequestBody UpdateModelEnabledRequest request) {
        modelService.updateEnabled(id, request.getEnabled());
        return Result.ok();
    }

    @PostMapping("/models/{id}/test")
    public Result<ModelTestResult> testModel(@PathVariable String id) {
        return Result.ok(modelService.testModel(id));
    }
}
