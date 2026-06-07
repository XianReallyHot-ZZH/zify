package com.zify.model.api.dto.provider;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * 创建供应商请求
 */
public class CreateProviderRequest {

    @NotBlank(message = "供应商名称不能为空")
    private String name;

    @NotBlank(message = "供应商类型不能为空")
    private String providerType;

    private String apiKey;

    @NotBlank(message = "Base URL 不能为空")
    private String baseUrl;

    private Map<String, Object> extraConfig;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProviderType() {
        return providerType;
    }

    public void setProviderType(String providerType) {
        this.providerType = providerType;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Map<String, Object> getExtraConfig() {
        return extraConfig;
    }

    public void setExtraConfig(Map<String, Object> extraConfig) {
        this.extraConfig = extraConfig;
    }
}
