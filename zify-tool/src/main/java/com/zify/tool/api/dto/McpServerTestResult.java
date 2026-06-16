package com.zify.tool.api.dto;

import java.util.List;

/**
 * MCP Server 连接测试结果（不抛异常，data.success 表达）。
 */
public class McpServerTestResult {

    private boolean success;
    private String message;
    private long latencyMs;
    private List<McpToolPreview> discoveredTools;

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }
    public List<McpToolPreview> getDiscoveredTools() { return discoveredTools; }
    public void setDiscoveredTools(List<McpToolPreview> discoveredTools) { this.discoveredTools = discoveredTools; }
}
