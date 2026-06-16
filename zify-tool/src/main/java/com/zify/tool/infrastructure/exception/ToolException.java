package com.zify.tool.infrastructure.exception;

/**
 * 工具调用异常基类（对齐 glm-docs/13 §七）。
 * <p>
 * 携带 toolId / toolName / scenario / retryable，用于结构化日志与重试决策。
 * <b>禁止包含鉴权凭据。</b>对 engine 只暴露 ToolExecutionResultDTO，不暴露异常类型。
 */
public class ToolException extends RuntimeException {

    private final String toolId;
    private final String toolName;
    private final String scenario;
    private final boolean retryable;

    public ToolException(String message, String toolId, String toolName, String scenario, boolean retryable) {
        super(message);
        this.toolId = toolId;
        this.toolName = toolName;
        this.scenario = scenario;
        this.retryable = retryable;
    }

    public ToolException(String message, String toolId, String toolName, String scenario,
                         boolean retryable, Throwable cause) {
        super(message, cause);
        this.toolId = toolId;
        this.toolName = toolName;
        this.scenario = scenario;
        this.retryable = retryable;
    }

    public String getToolId() {
        return toolId;
    }

    public String getToolName() {
        return toolName;
    }

    public String getScenario() {
        return scenario;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
