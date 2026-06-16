package com.zify.tool.infrastructure.exception;

/**
 * 可重试的工具调用异常（13 §5.2）：建连失败（sent=false，幂等/非幂等均可重试）、
 * 幂等工具的读超时/5xx/429（sent=true）。
 */
public class ToolRetryableException extends ToolException {

    public ToolRetryableException(String message, String toolId, String toolName, String scenario) {
        super(message, toolId, toolName, scenario, true, false);
    }

    public ToolRetryableException(String message, String toolId, String toolName, String scenario, Throwable cause) {
        super(message, toolId, toolName, scenario, true, false, cause);
    }

    /** 显式标注请求是否已送达（sent=true=读超时/5xx/429；sent=false=建连失败）。 */
    public ToolRetryableException(String message, String toolId, String toolName, String scenario, boolean sent) {
        super(message, toolId, toolName, scenario, true, sent);
    }
}
