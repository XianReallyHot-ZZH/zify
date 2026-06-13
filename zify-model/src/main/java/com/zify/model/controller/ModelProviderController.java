package com.zify.model.controller;

import com.zify.common.web.PageResult;
import com.zify.common.web.Result;
import com.zify.model.api.dto.provider.CreateProviderRequest;
import com.zify.model.api.dto.provider.ProviderApiKeyResponse;
import com.zify.model.api.dto.provider.ProviderListQuery;
import com.zify.model.api.dto.provider.ProviderResponse;
import com.zify.model.api.dto.provider.ProviderTestResult;
import com.zify.model.api.dto.provider.UpdateProviderRequest;
import com.zify.model.api.dto.provider.UpdateProviderStatusRequest;
import com.zify.model.domain.ModelProviderService;
import com.zify.model.domain.ModelService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 供应商管理 Controller
 */
@RestController
@RequestMapping("/api/model/providers")
public class ModelProviderController {

    private final ModelProviderService providerService;
    private final ModelService modelService;

    public ModelProviderController(ModelProviderService providerService,
                                   ModelService modelService) {
        this.providerService = providerService;
        this.modelService = modelService;
    }

    @PostMapping
    public Result<ProviderResponse> createProvider(@Valid @RequestBody CreateProviderRequest request) {
        return Result.ok(providerService.createProvider(request));
    }

    @GetMapping
    public Result<PageResult<ProviderResponse>> listProviders(ProviderListQuery query) {
        return Result.ok(providerService.listProviders(query));
    }

    @GetMapping("/{id}")
    public Result<ProviderResponse> getProvider(@PathVariable String id) {
        return Result.ok(providerService.getProvider(id));
    }

    @PutMapping("/{id}")
    public Result<ProviderResponse> updateProvider(@PathVariable String id,
                                                    @Valid @RequestBody UpdateProviderRequest request) {
        return Result.ok(providerService.updateProvider(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteProvider(@PathVariable String id) {
        providerService.deleteProvider(id);
        return Result.ok();
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable String id,
                                     @Valid @RequestBody UpdateProviderStatusRequest request) {
        providerService.updateStatus(id, request.getStatus());
        return Result.ok();
    }

    @PostMapping("/{id}/test")
    public Result<ProviderTestResult> testProvider(@PathVariable String id) {
        return Result.ok(modelService.testProvider(id));
    }

    @GetMapping("/{id}/api-key")
    public Result<ProviderApiKeyResponse> getProviderApiKey(@PathVariable String id,
                                                            @RequestParam(defaultValue = "false") boolean reveal) {
        return Result.ok(providerService.getProviderApiKey(id, reveal));
    }
}
