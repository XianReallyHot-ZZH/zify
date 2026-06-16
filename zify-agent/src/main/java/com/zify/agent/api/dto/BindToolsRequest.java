package com.zify.agent.api.dto;

import java.util.List;

/**
 * Agent 工具绑定请求（全量覆盖 toolIds）。
 */
public class BindToolsRequest {

    private List<String> toolIds;

    public List<String> getToolIds() { return toolIds; }
    public void setToolIds(List<String> toolIds) { this.toolIds = toolIds; }
}
