package com.zify.model.api.dto.provider;

import com.zify.common.web.PageRequest;

/**
 * 供应商列表查询参数
 */
public class ProviderListQuery extends PageRequest {

    private String providerType;

    private String status;

    public String getProviderType() {
        return providerType;
    }

    public void setProviderType(String providerType) {
        this.providerType = providerType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
