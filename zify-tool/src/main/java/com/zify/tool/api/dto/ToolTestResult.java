package com.zify.tool.api.dto;

/**
 * 工具测试调用结果（不抛，data.success 表达）。
 */
public class ToolTestResult {

    private boolean success;
    private String status;
    private String output;
    private long durationMs;
    private String error;
    private String toolCallLogId;

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getOutput() { return output; }
    public void setOutput(String output) { this.output = output; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public String getToolCallLogId() { return toolCallLogId; }
    public void setToolCallLogId(String toolCallLogId) { this.toolCallLogId = toolCallLogId; }
}
