package com.zify.tool.api.dto;

/**
 * 启用/禁用开关请求（tool 与 mcp server 复用）。
 */
public class UpdateEnabledRequest {

    private Boolean enabled;

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
