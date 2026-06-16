package com.zify.tool.api.dto;

/**
 * 工具执行结果 DTO（中立，ToolFacade → engine）。
 * <p>
 * 失败返回 status=ERROR（不抛异常），engine 把 output 回灌模型；仅致命错误（用户中断等）才上抛。
 * output 为截断后内容；toolCallLogId 指向 tool_call_log（已由执行点写入）。
 */
public class ToolExecutionResultDTO {

    /** 执行状态：SUCCESS / ERROR。 */
    private String status;
    /** 结果或回灌文本（截断后）。 */
    private String output;
    private long durationMs;
    private String toolCallLogId;
    /** 失败时的精简错误（不含凭据），可空。 */
    private String error;

    public ToolExecutionResultDTO() {
    }

    public ToolExecutionResultDTO(String status, String output, long durationMs, String toolCallLogId, String error) {
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
