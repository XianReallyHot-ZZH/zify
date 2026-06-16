package com.zify.tool.api.dto;

import com.zify.common.web.CursorPageRequest;

/**
 * 工具调用日志查询（Keyset）。至少传一个过滤维度（conversationId/agentId/toolId）。
 */
public class ToolCallLogQuery extends CursorPageRequest {

    private String conversationId;
    private String agentId;
    private String toolId;

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getToolId() { return toolId; }
    public void setToolId(String toolId) { this.toolId = toolId; }
}
