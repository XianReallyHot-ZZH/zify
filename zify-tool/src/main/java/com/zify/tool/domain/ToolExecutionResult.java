package com.zify.tool.domain;

/**
 * 工具内部执行结果（HttpTool/McpTool.execute 产物，不跨 Facade）。
 * <p>
 * 字段与 {@link com.zify.tool.api.dto.ToolExecutionResultDTO} 一致，由 {@code ToolFacadeImpl} 转为对外 DTO。
 */
public class ToolExecutionResult {

    private String status;
    private String output;
    private long durationMs;
    private String toolCallLogId;
    private String error;

    public ToolExecutionResult() {
    }

    public ToolExecutionResult(String status, String output, long durationMs, String toolCallLogId, String error) {
        this.status = status;
        this.output = output;
        this.durationMs = durationMs;
        this.toolCallLogId = toolCallLogId;
        this.error = error;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public String getToolCallLogId() {
        return toolCallLogId;
    }

    public void setToolCallLogId(String toolCallLogId) {
        this.toolCallLogId = toolCallLogId;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
