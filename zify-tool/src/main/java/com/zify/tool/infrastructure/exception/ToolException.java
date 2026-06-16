package com.zify.tool.infrastructure.exception;

/**
 * 工具调用异常基类（对齐 glm-docs/13 §七）。
 * <p>
 * 携带 toolId / toolName / scenario / retryable / sent，用于结构化日志与重试决策。
 * <b>禁止包含鉴权凭据。</b>对 engine 只暴露 ToolExecutionResultDTO，不暴露异常类型。
 * <p>
 * sent=true 表示请求已送达对端（读超时/5xx/429 等），驱动重试：建连失败 sent=false
 * 对幂等/非幂等均可重试；请求已发出后失败仅在幂等时可重试（13 §5.2）。
 */
public class ToolException extends RuntimeException {

    private final String toolId;
    private final String toolName;
    private final String scenario;
    private final boolean retryable;
    private final boolean sent;

    public ToolException(String message, String toolId, String toolName, String scenario, boolean retryable) {
        this(message, toolId, toolName, scenario, retryable, false, null);
    }

    public ToolException(String message, String toolId, String toolName, String scenario,
                         boolean retryable, Throwable cause) {
        this(message, toolId, toolName, scenario, retryable, false, cause);
    }

    public ToolException(String message, String toolId, String toolName, String scenario,
                         boolean retryable, boolean sent) {
        this(message, toolId, toolName, scenario, retryable, sent, null);
    }

    public ToolException(String message, String toolId, String toolName, String scenario,
                         boolean retryable, boolean sent, Throwable cause) {
        super(message, cause);
        this.toolId = toolId;
        this.toolName = toolName;
        this.scenario = scenario;
        this.retryable = retryable;
        this.sent = sent;
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

    /** 请求是否已送达对端（true=读超时/5xx/429 等，false=建连失败）。 */
    public boolean isSent() {
        return sent;
    }
}
