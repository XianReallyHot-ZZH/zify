package com.zify.tool.infrastructure.exception;

/**
 * 可重试的工具调用异常：429 / 5xx / 建连失败 / 幂等工具的读超时（13 §5.2）。
 */
public class ToolRetryableException extends ToolException {

    public ToolRetryableException(String message, String toolId, String toolName, String scenario) {
        super(message, toolId, toolName, scenario, true);
    }

    public ToolRetryableException(String message, String toolId, String toolName, String scenario, Throwable cause) {
        super(message, toolId, toolName, scenario, true, cause);
    }
}
